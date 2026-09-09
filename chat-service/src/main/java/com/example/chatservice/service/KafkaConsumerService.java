package com.example.chatservice.service;

import com.example.chatservice.dto.KafkaChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaConsumerService {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "chat-messages", groupId = "chat-service-group",
                   containerFactory = "kafkaListenerContainerFactory")
    public void consumeMessage(KafkaChatMessage message) {
        log.info("Consumed Kafka message from room {}: sender={}, type={}, content={}",
                message.getRoomId(), message.getSender(), message.getType(), message.getContent());
        String destination = "/topic/room/" + message.getRoomId();
        messagingTemplate.convertAndSend(destination, message);
        log.info("Successfully pushed message to STOMP topic: {}", destination);
    }
}
