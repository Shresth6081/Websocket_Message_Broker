package com.example.chatservice.service;

import com.example.chatservice.dto.RoomRequest;
import com.example.chatservice.model.ChatRoom;
import com.example.chatservice.model.RoomMember;
import com.example.chatservice.model.RoomType;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.RoomMemberRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final RestClient restClient;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                           RoomMemberRepository roomMemberRepository,
                           SimpMessagingTemplate messagingTemplate,
                           JdbcTemplate jdbcTemplate,
                           @Value("${USER_SERVICE_URL:https://user-service-fcxc.onrender.com}") String userServiceUrl) {
        this.chatRoomRepository = chatRoomRepository;
        this.roomMemberRepository = roomMemberRepository;
        this.messagingTemplate = messagingTemplate;
        this.jdbcTemplate = jdbcTemplate;
        log.info("Configured ChatRoomService with user-service URL: {}", userServiceUrl);
        this.restClient = RestClient.builder()
                .baseUrl(userServiceUrl)
                .build();
    }

    @Transactional
    public ChatRoom createRoom(RoomRequest request, String createdBy) {
        if (chatRoomRepository.existsByName(request.getName())) {
            throw new IllegalArgumentException("Room already exists: " + request.getName());
        }

        ChatRoom room = ChatRoom.builder()
                .name(request.getName())
                .description(request.getDescription())
                .createdBy(createdBy)
                .type(request.getType() != null ? request.getType() : RoomType.PUBLIC)
                .build();

        ChatRoom saved = chatRoomRepository.save(room);

        // Add creator as member
        RoomMember member = RoomMember.builder()
                .roomId(saved.getId())
                .username(createdBy)
                .build();
        roomMemberRepository.save(member);

        log.info("Created chat room '{}' ({}) by user {}", request.getName(), room.getType(), createdBy);
        return saved;
    }

    public List<ChatRoom> getAllRoomsForUser(String username) {
        // Fetch all public rooms
        List<ChatRoom> rooms = chatRoomRepository.findAll().stream()
                .filter(r -> r.getType() == RoomType.PUBLIC)
                .collect(Collectors.toList());

        // Fetch private/DM rooms where user is a member
        List<RoomMember> memberships = roomMemberRepository.findByUsername(username);
        List<Long> memberRoomIds = memberships.stream()
                .map(RoomMember::getRoomId)
                .collect(Collectors.toList());

        if (!memberRoomIds.isEmpty()) {
            List<ChatRoom> privateRooms = chatRoomRepository.findAllById(memberRoomIds).stream()
                    .filter(r -> r.getType() == RoomType.PRIVATE || r.getType() == RoomType.DM)
                    .collect(Collectors.toList());
            rooms.addAll(privateRooms);
        }

        return rooms;
    }

    public ChatRoom getRoomById(Long id) {
        return chatRoomRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Room not found: " + id));
    }

    public boolean isUserMember(Long roomId, String username) {
        ChatRoom room = getRoomById(roomId);
        if (room.getType() == RoomType.PUBLIC) {
            return true;
        }
        return roomMemberRepository.existsByRoomIdAndUsername(roomId, username);
    }

    @Transactional
    public void inviteUser(Long roomId, String usernameToInvite, String inviter) {
        ChatRoom room = getRoomById(roomId);

        // Verify room is not DM
        if (room.getType() == RoomType.DM) {
            throw new IllegalArgumentException("Cannot invite members to a Direct Message room.");
        }

        // Verify inviter is member of the private room
        if (room.getType() == RoomType.PRIVATE && !roomMemberRepository.existsByRoomIdAndUsername(roomId, inviter)) {
            throw new IllegalStateException("Only members of this private channel can invite others.");
        }

        // Verify target user exists
        Boolean userExists = checkUserExists(usernameToInvite);
        if (userExists == null || !userExists) {
            throw new IllegalArgumentException("User '" + usernameToInvite + "' does not exist.");
        }

        // Check if already a member
        if (roomMemberRepository.existsByRoomIdAndUsername(roomId, usernameToInvite)) {
            throw new IllegalArgumentException("User '" + usernameToInvite + "' is already a member of this channel.");
        }

        RoomMember newMember = RoomMember.builder()
                .roomId(roomId)
                .username(usernameToInvite)
                .build();
        roomMemberRepository.save(newMember);
        log.info("User '{}' invited to room '{}' by {}", usernameToInvite, room.getName(), inviter);
        notifyUserOfNewRoom(usernameToInvite, room);
    }

    @Transactional
    public ChatRoom getOrCreateDmRoom(String userA, String userB) {
        if (userA.equalsIgnoreCase(userB)) {
            throw new IllegalArgumentException("Cannot create a DM room with yourself.");
        }

        // Verify userB exists
        Boolean userExists = checkUserExists(userB);
        if (userExists == null || !userExists) {
            throw new IllegalArgumentException("User '" + userB + "' does not exist.");
        }

        // Unique deterministic name for DM room (case-insensitive sorted)
        String normA = userA.toLowerCase().trim();
        String normB = userB.toLowerCase().trim();
        String dmRoomName = normA.compareTo(normB) < 0
                ? "dm-" + normA + "-" + normB
                : "dm-" + normB + "-" + normA;

        Optional<ChatRoom> existingRoom = chatRoomRepository.findByName(dmRoomName);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        ChatRoom newRoom = ChatRoom.builder()
                .name(dmRoomName)
                .description("Direct Message between @" + userA + " and @" + userB)
                .createdBy("system")
                .type(RoomType.DM)
                .build();
        ChatRoom savedRoom = chatRoomRepository.save(newRoom);

        // Add both users as members
        roomMemberRepository.save(RoomMember.builder().roomId(savedRoom.getId()).username(userA).build());
        roomMemberRepository.save(RoomMember.builder().roomId(savedRoom.getId()).username(userB).build());

        log.info("Created new DM room: {}", dmRoomName);

        // Notify both users via WebSocket about new room
        notifyUserOfNewRoom(userA, savedRoom);
        notifyUserOfNewRoom(userB, savedRoom);

        return savedRoom;
    }

    private Boolean checkUserExists(String username) {
        if (username == null || username.trim().isEmpty()) {
            return false;
        }
        String cleanUser = username.trim();

        // 1. Direct database check against the shared PostgreSQL DB
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM users WHERE LOWER(username) = LOWER(?)",
                    Integer.class,
                    cleanUser
            );
            if (count != null && count > 0) {
                log.info("Verified user '{}' directly from database.", cleanUser);
                return true;
            }
        } catch (Exception dbEx) {
            log.warn("Database user check fallback to HTTP for '{}': {}", cleanUser, dbEx.getMessage());
        }

        // 2. Fallback to HTTP REST call
        try {
            Boolean exists = restClient.get()
                    .uri("/api/users/exists/{username}", cleanUser)
                    .retrieve()
                    .body(Boolean.class);
            return exists != null && exists;
        } catch (Exception e) {
            log.error("Failed to verify user existence with user-service for user '{}': {}", cleanUser, e.getMessage());
            return false;
        }
    }

    @Transactional
    public void initializeDefaultRooms() {
        if (chatRoomRepository.count() == 0) {
            createRoom(buildRoomRequest("general", "General discussion channel"), "system");
            createRoom(buildRoomRequest("tech", "Tech talk & coding discussions"), "system");
            createRoom(buildRoomRequest("random", "Random conversations"), "system");
            log.info("Default rooms created.");
        }
    }

    private RoomRequest buildRoomRequest(String name, String desc) {
        RoomRequest r = new RoomRequest();
        r.setName(name);
        r.setDescription(desc);
        r.setType(RoomType.PUBLIC);
        return r;
    }

    private void notifyUserOfNewRoom(String username, ChatRoom room) {
        try {
            messagingTemplate.convertAndSend("/topic/user/" + username, java.util.Map.of(
                "type", "NEW_ROOM",
                "room", room
            ));
        } catch (Exception e) {
            log.error("Failed to send room notification to user {}: {}", username, e.getMessage());
        }
    }
}
