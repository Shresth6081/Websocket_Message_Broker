package com.example.chatservice.service;

import com.example.chatservice.dto.RoomRequest;
import com.example.chatservice.model.ChatRoom;
import com.example.chatservice.model.RoomMember;
import com.example.chatservice.model.RoomType;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.repository.RoomMemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;
    
    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://user-service:8082")
            .build();

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
        
        // Verify room is PRIVATE (or PUBLIC - but no need to invite to public, though we can allow it)
        if (room.getType() == RoomType.DM) {
            throw new IllegalArgumentException("Cannot invite members to a Direct Message room.");
        }
        
        // Verify inviter is member of the private room
        if (room.getType() == RoomType.PRIVATE && !roomMemberRepository.existsByRoomIdAndUsername(roomId, inviter)) {
            throw new IllegalStateException("Only members of this private channel can invite others.");
        }
        
        // Verify target user exists in user-service
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
        if (userA.equals(userB)) {
            throw new IllegalArgumentException("Cannot create a DM room with yourself.");
        }
        
        // Verify userB exists in user-service
        Boolean userExists = checkUserExists(userB);
        if (userExists == null || !userExists) {
            throw new IllegalArgumentException("User '" + userB + "' does not exist.");
        }

        // Unique deterministic name for DM room
        String dmRoomName = userA.compareTo(userB) < 0 
                ? "dm-" + userA + "-" + userB 
                : "dm-" + userB + "-" + userA;
                
        java.util.Optional<ChatRoom> existingRoom = chatRoomRepository.findByName(dmRoomName);
        if (existingRoom.isPresent()) {
            return existingRoom.get();
        }

        ChatRoom newRoom = ChatRoom.builder()
                .name(dmRoomName)
                .description("Direct Message between " + userA + " and " + userB)
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
        try {
            return restClient.get()
                    .uri("/api/users/exists/{username}", username)
                    .retrieve()
                    .body(Boolean.class);
        } catch (Exception e) {
            log.error("Failed to verify user existence with user-service: {}", e.getMessage());
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
