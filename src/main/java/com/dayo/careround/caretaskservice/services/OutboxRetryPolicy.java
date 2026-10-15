package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.OutboxEvent;
import com.dayo.careround.caretaskservice.data.models.enums.OutboxStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
public class OutboxRetryPolicy {

    private static final int MAX_ATTEMPTS = 5;

    public void recordSuccess(OutboxEvent event) {
        event.setStatus(OutboxStatus.SENT);
    }

    public void recordFailure(OutboxEvent event) {
        event.setAttempts(event.getAttempts() + 1);
        event.setLastAttemptAt(Instant.now());

        if (event.getAttempts() >= MAX_ATTEMPTS) {
            event.setStatus(OutboxStatus.DEAD_LETTER);
            log.error("Outbox event {} moved to DEAD_LETTER after {} attempts", event.getId(), MAX_ATTEMPTS);
        } else {
            long backoffSeconds = (long) Math.pow(2, event.getAttempts()) * 5;
            event.setNextRetryAt(Instant.now().plusSeconds(backoffSeconds));
        }
    }
}