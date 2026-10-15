package com.dayo.careround.caretaskservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventSender {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public boolean send(String topic, String key, String payload) {
        try {
            kafkaTemplate.send(topic, key, payload);
            return true;
        } catch (Exception e) {
            log.error("Failed to publish event to topic {}: {}", topic, e.getMessage());
            return false;
        }
    }

    public boolean pingHealthCheck() {
        try {
            kafkaTemplate.send("health-check", "ping").get();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}