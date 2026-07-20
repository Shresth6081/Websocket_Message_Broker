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
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final KafkaProducerService kafkaProducerService;
    private final ChatMessageRepository chatMessageRepository;
    private final MetricsService metricsService;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload MessagePayload payload, SimpMessageHeaderAccessor headerAccessor) {
        Map<String, Object> attrs = headerAccessor.getSessionAttributes();
        String sender = attrs != null ? (String) attrs.get("username") : "anonymous";
        String displayName = attrs != null ? (String) attrs.get("displayName") : sender;

        // Persist message
        ChatMessage saved = chatMessageRepository.save(ChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(payload.getContent())
                .type(ChatMessage.MessageType.CHAT)
                .build());

        // Publish to Kafka for broadcast
        KafkaChatMessage kafkaMsg = KafkaChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(payload.getContent())
                .type(ChatMessage.MessageType.CHAT)
                .timestamp(saved.getCreatedAt())
                .build();
        kafkaProducerService.sendMessage(kafkaMsg);
        metricsService.incrementMessageCount();
        log.info("Message from {} in room {}: {}", sender, payload.getRoomId(), payload.getContent());
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
                .content(displayName + " joined the room")
                .type(ChatMessage.MessageType.JOIN)
                .build());

        KafkaChatMessage kafkaMsg = KafkaChatMessage.builder()
                .roomId(payload.getRoomId())
                .sender(sender)
                .displayName(displayName)
                .content(displayName + " joined the room")
                .type(ChatMessage.MessageType.JOIN)
                .timestamp(saved.getCreatedAt())
                .build();
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
                .content(displayName + " left the room")
                .type(ChatMessage.MessageType.LEAVE)
                .timestamp(LocalDateTime.now())
                .build();
        kafkaProducerService.sendMessage(kafkaMsg);
        log.info("User {} left room {}", sender, payload.getRoomId());
    }
}
