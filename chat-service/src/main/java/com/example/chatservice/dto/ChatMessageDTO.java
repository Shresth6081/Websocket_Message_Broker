package com.example.chatservice.dto;

import com.example.chatservice.model.ChatMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatMessageDTO {
    private Long id;
    private Long roomId;
    private String sender;
    private String displayName;
    private String content;
    private ChatMessage.MessageType type;
    private LocalDateTime createdAt;

    public static ChatMessageDTO from(ChatMessage msg) {
        return ChatMessageDTO.builder()
                .id(msg.getId())
                .roomId(msg.getRoomId())
                .sender(msg.getSender())
                .displayName(msg.getDisplayName())
                .content(msg.getContent())
                .type(msg.getType())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
