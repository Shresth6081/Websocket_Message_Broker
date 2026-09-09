package com.example.chatservice.controller;

import com.example.chatservice.dto.KafkaChatMessage;
import com.example.chatservice.dto.MessagePayload;
import com.example.chatservice.model.ChatMessage;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.service.KafkaProducerService;
import com.example.chatservice.service.MetricsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final KafkaProducerService kafkaProducerService;
    private final ChatMessageRepository chatMessageRepository;
    private final MetricsService metricsService;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload MessagePayload payload, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String sender = attrs != null ? (String) attrs.get("username") : "anonymous";
        String displayName = attrs != null ? (String) attrs.get("displayName") : sender;

        // 1. Persist message to database
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(payload.getContent())
                .type(ChatMessage.MessageType.CHAT)
                .build());

        // 2. Build Kafka & WebSocket DTO
        LocalDateTime msgTime = saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now();
        KafkaChatMessage kafkaMsg = KafkaChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(payload.getContent())
                .type(ChatMessage.MessageType.CHAT)
                .timestamp(msgTime)
                .build();

        // 3. Broadcast to all active WebSocket subscribers in real time
        String destination = "/topic/room/" + payload.getRoomId();
        messagingTemplate.convertAndSend(destination, kafkaMsg);

        // 4. Publish to Kafka for distributed streaming & persistence
        kafkaProducerService.sendMessage(kafkaMsg);
        metricsService.incrementMessageCount();
        log.info("Message broadcasted and published from {} in room {}: {}", sender, payload.getRoomId(), payload.getContent());
    }

    @MessageMapping("/chat.join")
    public void joinRoom(@Payload MessagePayload payload, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String sender = attrs != null ? (String) attrs.get("username") : "anonymous";
        String displayName = attrs != null ? (String) attrs.get("displayName") : sender;

        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(displayName + " joined the channel.")
                .type(ChatMessage.MessageType.JOIN)
                .build());

        LocalDateTime joinTime = saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now();
        KafkaChatMessage kafkaMsg = KafkaChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(displayName + " joined the channel.")
                .type(ChatMessage.MessageType.JOIN)
                .timestamp(joinTime)
                .build();

        String destination = "/topic/room/" + payload.getRoomId();
        messagingTemplate.convertAndSend(destination, kafkaMsg);
        kafkaProducerService.sendMessage(kafkaMsg);
        log.info("User {} joined room {}", sender, payload.getRoomId());
    }

    @MessageMapping("/chat.leave")
    public void leaveRoom(@Payload MessagePayload payload, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String sender = attrs != null ? (String) attrs.get("username") : "anonymous";
        String displayName = attrs != null ? (String) attrs.get("displayName") : sender;

        KafkaChatMessage kafkaMsg = KafkaChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(displayName + " left the channel.")
                .type(ChatMessage.MessageType.LEAVE)
                .timestamp(LocalDateTime.now())
                .build();

        String destination = "/topic/room/" + payload.getRoomId();
        messagingTemplate.convertAndSend(destination, kafkaMsg);
        kafkaProducerService.sendMessage(kafkaMsg);
        log.info("User {} left room {}", sender, payload.getRoomId());
    }
}
