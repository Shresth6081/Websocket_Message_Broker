package com.example.chatservice.service;

import com.example.chatservice.dto.KafkaChatMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class KafkaProducerService {

    private static final String TOPIC = "chat-messages";
    private final KafkaTemplate<String, KafkaChatMessage> kafkaTemplate;

    public void sendMessage(KafkaChatMessage message) {
        String key = String.valueOf(message.getRoomId());
        kafkaTemplate.send(TOPIC, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send Kafka message for room {}: {}", message.getRoomId(), ex.getMessage());
                    } else {
                        log.debug("Kafka message sent to topic {} partition {} for room {}",
                                TOPIC, result.getRecordMetadata().partition(), message.getRoomId());
                    }
                });
    }
}
