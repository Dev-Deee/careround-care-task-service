package com.dayo.careround.caretaskservice.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxCircuitBreaker {

    private static final int OPEN_THRESHOLD = 10;

    private final KafkaEventSender kafkaEventSender;

    private int consecutiveFailures = 0;
    private boolean open = false;

    public boolean isOpen() {
        return open;
    }

    public void recordSuccess() {
        consecutiveFailures = 0;
    }

    public void recordFailure() {
        consecutiveFailures++;
        if (consecutiveFailures >= OPEN_THRESHOLD) {
            open = true;
            log.error("Circuit opened — Kafka appears to be down");
        }
    }

    public void attemptRecovery() {
        if (!open) return;

        log.info("Checking if Kafka has recovered...");
        if (kafkaEventSender.pingHealthCheck()) {
            open = false;
            consecutiveFailures = 0;
            log.info("Kafka recovered — circuit closed");
        } else {
            log.warn("Kafka still down — circuit remains open");
        }
    }
}