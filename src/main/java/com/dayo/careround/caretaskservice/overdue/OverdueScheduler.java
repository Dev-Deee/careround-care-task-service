package com.dayo.careround.caretaskservice.overdue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

// Queue updates from the task write paths. They run after the transaction commits (so the worker never sees a
// member before its row exists) and fail open: Redis down only logs WARN; reconciliation re-queues later.
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueScheduler {

    private final DeadlineQueue queue;

    public void scheduleAfterCommit(UUID taskPublicId, Instant deadline) {
        afterCommit(() -> queue.schedule(taskPublicId, deadline), "queue", taskPublicId);
    }

    public void removeAfterCommit(UUID taskPublicId) {
        afterCommit(() -> queue.remove(taskPublicId), "dequeue", taskPublicId);
    }

    private void afterCommit(Runnable action, String what, UUID taskPublicId) {
        Runnable safe = () -> {
            try {
                action.run();
            } catch (RuntimeException e) {
                log.warn("Could not {} task {} on the overdue queue (reconciliation will fix it): {}", what, taskPublicId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safe.run();
                }
            });
        } else {
            safe.run();
        }
    }
}
