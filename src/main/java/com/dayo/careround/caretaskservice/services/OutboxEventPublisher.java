package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.OutboxEvent;
import com.dayo.careround.caretaskservice.data.models.enums.OutboxStatus;
import com.dayo.careround.caretaskservice.data.repositories.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaEventSender kafkaEventSender;
    private final OutboxRetryPolicy retryPolicy;
    private final OutboxCircuitBreaker circuitBreaker;

    @Scheduled(fixedDelay = 5000)
    public void processPendingEvents() {
        if (circuitBreaker.isOpen()) {
            log.warn("Circuit open — skipping outbox poll");
            return;
        }

        Page<OutboxEvent> pending = outboxEventRepository
                .findByStatusAndNextRetryAtLessThanEqual(
                        OutboxStatus.PENDING,
                        Instant.now(),
                        PageRequest.of(0, BATCH_SIZE)
                );

        for (OutboxEvent event : pending) {
            boolean success = kafkaEventSender.send(event.getEventType(), event.getIdempotencyKey(), event.getPayload());

            if (success) {
                retryPolicy.recordSuccess(event);
                circuitBreaker.recordSuccess();
            } else {
                retryPolicy.recordFailure(event);
                circuitBreaker.recordFailure();
            }

            outboxEventRepository.save(event);
        }
    }

    @Scheduled(fixedDelay = 60000)
    public void checkCircuit() {
        circuitBreaker.attemptRecovery();
    }
}