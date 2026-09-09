package com.example.chatservice.config;

import com.example.chatservice.dto.KafkaChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${KAFKA_SECURITY_PROTOCOL:${spring.kafka.properties.security.protocol:PLAINTEXT}}")
    private String securityProtocol;

    @Value("${KAFKA_SASL_MECHANISM:${spring.kafka.properties.sasl.mechanism:SCRAM-SHA-256}}")
    private String saslMechanism;

    @Value("${KAFKA_SASL_JAAS_CONFIG:${spring.kafka.properties.sasl.jaas.config:}}")
    private String saslJaasConfig;

    @Value("${KAFKA_SASL_USERNAME:avnadmin}")
    private String saslUsername;

    @Value("${KAFKA_SASL_PASSWORD:}")
    private String saslPassword;

    private Map<String, Object> getCommonConfigs() {
        Map<String, Object> props = new HashMap<>();
        props.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        if (securityProtocol != null && securityProtocol.trim().toUpperCase().startsWith("SASL")) {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, securityProtocol.trim());
            props.put(SaslConfigs.SASL_MECHANISM, saslMechanism != null && !saslMechanism.trim().isEmpty() ? saslMechanism.trim() : "SCRAM-SHA-256");

            String jaas = resolveJaasConfig();
            if (jaas != null && !jaas.trim().isEmpty()) {
                props.put(SaslConfigs.SASL_JAAS_CONFIG, jaas.trim());
                log.info("Configured Kafka SASL authentication (protocol={}, mechanism={}, username={})",
                        securityProtocol, saslMechanism, saslUsername);
            } else {
                log.error("====================================================================");
                log.error("[ERROR] Kafka security protocol is '{}' but NO password was provided!", securityProtocol);
                log.error("[ACTION REQUIRED] Set 'KAFKA_SASL_PASSWORD' in Render Dashboard > chat-service > Environment!");
                log.error("====================================================================");
                throw new IllegalStateException(
                    "Missing Kafka SASL Password! Please set the 'KAFKA_SASL_PASSWORD' environment variable in your Render dashboard for chat-service."
                );
            }
        } else {
            props.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "PLAINTEXT");
        }
        return props;
    }

    private String resolveJaasConfig() {
        if (saslJaasConfig != null && !saslJaasConfig.trim().isEmpty()) {
            return saslJaasConfig.trim();
        }
        String user = (saslUsername != null && !saslUsername.trim().isEmpty()) ? saslUsername.trim() : "avnadmin";
        if (saslPassword != null && !saslPassword.trim().isEmpty()) {
            return String.format(
                "org.apache.kafka.common.security.scram.ScramLoginModule required username=\"%s\" password=\"%s\";",
                user, saslPassword.trim()
            );
        }
        return null;
    }

    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = getCommonConfigs();
        return new KafkaAdmin(configs);
    }

    @Bean
    public ProducerFactory<String, KafkaChatMessage> producerFactory() {
        Map<String, Object> configProps = getCommonConfigs();
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, false);
        return new DefaultKafkaProducerFactory<>(configProps);
    }

    @Bean
    public KafkaTemplate<String, KafkaChatMessage> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, KafkaChatMessage> consumerFactory() {
        Map<String, Object> props = getCommonConfigs();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "chat-service-group");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        JsonDeserializer<KafkaChatMessage> jsonDeserializer = new JsonDeserializer<>(KafkaChatMessage.class);
        jsonDeserializer.addTrustedPackages("*");
        jsonDeserializer.setRemoveTypeHeaders(false);
        jsonDeserializer.setUseTypeMapperForKey(false);

        return new DefaultKafkaConsumerFactory<>(
                props,
                new StringDeserializer(),
                jsonDeserializer
        );
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, KafkaChatMessage> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, KafkaChatMessage> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setConcurrency(3);
        return factory;
    }

    @Bean
    public NewTopic chatMessagesTopic() {
        return TopicBuilder.name("chat-messages")
                .partitions(3)
                .replicas(1)
                .build();
    }
}