package com.example.chatservice.dto;

import lombok.Data;

@Data
public class MessagePayload {
    private Long roomId;
    private String content;
}
