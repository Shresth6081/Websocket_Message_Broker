package com.example.chatservice.controller;

import com.example.chatservice.dto.ChatMessageDTO;
import com.example.chatservice.dto.RoomRequest;
import com.example.chatservice.model.ChatMessage;
import com.example.chatservice.model.ChatRoom;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.service.ChatRoomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RoomController {

    private final ChatRoomService chatRoomService;
    private final ChatMessageRepository chatMessageRepository;

    @GetMapping("/rooms")
    public ResponseEntity<List<ChatRoom>> getAllRooms(
            @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username) {
        return ResponseEntity.ok(chatRoomService.getAllRoomsForUser(username));
    }

    @PostMapping("/rooms")
    public ResponseEntity<?> createRoom(@Valid @RequestBody RoomRequest request,
                                        @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username) {
        try {
            ChatRoom room = chatRoomService.createRoom(request, username);
            return ResponseEntity.status(HttpStatus.CREATED).body(room);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/rooms/{roomId}")
    public ResponseEntity<?> getRoom(@PathVariable Long roomId,
                                     @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username) {
        if (!chatRoomService.isUserMember(roomId, username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have access to this room."));
        }
        return ResponseEntity.ok(chatRoomService.getRoomById(roomId));
    }

    @PostMapping("/rooms/{roomId}/invite")
    public ResponseEntity<?> inviteUser(
            @PathVariable Long roomId,
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username) {
        String targetUser = body.get("username");
        if (targetUser == null || targetUser.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username is required."));
        }
        try {
            chatRoomService.inviteUser(roomId, targetUser.trim(), username);
            return ResponseEntity.ok(Map.of("status", "success", "message", "User invited successfully."));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/rooms/dm")
    public ResponseEntity<?> createDmRoom(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username) {
        String targetUser = body.get("username");
        if (targetUser == null || targetUser.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Target username is required."));
        }
        try {
            ChatRoom room = chatRoomService.getOrCreateDmRoom(username, targetUser.trim());
            return ResponseEntity.ok(room);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/rooms/{roomId}/messages")
    public ResponseEntity<?> getMessages(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username) {
        if (!chatRoomService.isUserMember(roomId, username)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "You do not have access to this room's messages."));
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByCreatedAtDesc(roomId, pageable);
        return ResponseEntity.ok(messages.map(ChatMessageDTO::from));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "chat-service"));
    }
}
