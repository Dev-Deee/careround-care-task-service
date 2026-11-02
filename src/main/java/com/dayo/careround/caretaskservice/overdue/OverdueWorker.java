package com.dayo.careround.caretaskservice.overdue;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import com.dayo.careround.caretaskservice.services.CareTaskQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Polls the deadline queue and fires due tasks; reconciles the queue with Postgres at startup and periodically.
@Slf4j
@Component
public class OverdueWorker {

    private final DeadlineQueue queue;
    private final OverdueProcessor processor;
    private final CareTaskRepository careTaskRepository;
    private final Clock clock;
    private final int batchSize;
    private final long retryBackoffMs;

    public OverdueWorker(DeadlineQueue queue, OverdueProcessor processor, CareTaskRepository careTaskRepository, Clock clock,
                         @Value("${app.overdue.batch-size:100}") int batchSize,
                         @Value("${app.overdue.retry-backoff-ms:30000}") long retryBackoffMs) {
        this.queue = queue;
        this.processor = processor;
        this.careTaskRepository = careTaskRepository;
        this.clock = clock;
        this.batchSize = batchSize;
        this.retryBackoffMs = retryBackoffMs;
    }

    @Scheduled(fixedDelayString = "${app.overdue.poll-ms:2000}")
    public void poll() {
        List<UUID> due;
        try {
            due = queue.due(clock.instant(), batchSize);
        } catch (RuntimeException e) {
            log.warn("Overdue queue unavailable, will retry: {}", e.getMessage());
            return;
        }
        for (UUID id : due) {
            boolean claimed;
            try {
                claimed = queue.claim(id);
            } catch (RuntimeException e) {
                log.warn("Overdue queue unavailable while claiming {}, will retry: {}", id, e.getMessage());
                return;
            }
            if (claimed) fireOrRequeue(id);
        }
    }

    // Startup: right away (so past-deadline tasks flip without waiting a full period). Then every reconcile-ms.
    @EventListener(ApplicationReadyEvent.class)
    public void reconcileOnStartup() {
        reconcile();
    }

    @Scheduled(initialDelayString = "${app.overdue.reconcile-ms:300000}", fixedDelayString = "${app.overdue.reconcile-ms:300000}")
    public void reconcile() {
        Instant now = clock.instant();
        List<CareTask> open = careTaskRepository.findByStatusIn(CareTaskQueryService.OPEN);
        Map<OverdueProcessor.Result, Integer> fired = new EnumMap<>(OverdueProcessor.Result.class);
        int queued = 0, queueFailures = 0;
        for (CareTask task : open) {
            if (!task.getDeadline().isAfter(now)) {
                // Past due: process from Postgres directly, so this works even with Redis down.
                try {
                    fired.merge(processor.fire(task.getPublicId()), 1, Integer::sum);
                } catch (RuntimeException e) {
                    log.warn("Reconciliation could not mark task {} overdue: {}", task.getPublicId(), e.getMessage());
                }
            } else if (queueFailures == 0) {
                try {
                    queue.schedule(task.getPublicId(), task.getDeadline());
                    queued++;
                } catch (RuntimeException e) {
                    queueFailures++;
                    log.warn("Overdue queue unavailable during reconciliation ({}); open tasks will be queued next run", e.getMessage());
                }
            }
        }
        int silent = fired.getOrDefault(OverdueProcessor.Result.OVERDUE_SILENT_BACKFILL, 0);
        if (silent > 0) {
            log.info("Overdue reconciliation: {} open task(s) past deadline by more than the backfill limit set OVERDUE without events", silent);
        }
        log.info("Overdue reconciliation: {} open, {} queued, fired {}", open.size(), queued, fired);
    }

    private void fireOrRequeue(UUID id) {
        try {
            processor.fire(id);
        } catch (RuntimeException e) {
            Instant retryAt = clock.instant().plusMillis(retryBackoffMs);
            log.warn("Marking task {} overdue failed, retrying at {}: {}", id, retryAt, e.getMessage());
            try {
                queue.schedule(id, retryAt);
            } catch (RuntimeException again) {
                log.warn("Could not re-queue task {} ({}); reconciliation will pick it up", id, again.getMessage());
            }
        }
    }
}
