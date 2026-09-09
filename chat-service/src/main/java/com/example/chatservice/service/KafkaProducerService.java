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
        log.info("Sending Kafka message for room {} (sender={}): {}", message.getRoomId(), message.getSender(), message.getContent());
        kafkaTemplate.send(TOPIC, key, message)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to send Kafka message for room {}: {}", message.getRoomId(), ex.getMessage(), ex);
                    } else {
                        log.info("Kafka message sent to topic {} partition {} offset {} for room {}",
                                TOPIC, result.getRecordMetadata().partition(), result.getRecordMetadata().offset(), message.getRoomId());
                    }
                });
    }
}
