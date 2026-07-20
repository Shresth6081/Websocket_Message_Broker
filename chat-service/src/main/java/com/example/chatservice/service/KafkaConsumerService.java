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
        log.debug("Consumed Kafka message from room {}: {}", message.getRoomId(), message.getSender());
        String destination = "/topic/room/" + message.getRoomId();
        messagingTemplate.convertAndSend(destination, message);
    }
}
