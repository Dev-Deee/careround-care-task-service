package com.dayo.careround.caretaskservice.overdue;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.OutboxEvent;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.models.enums.OutboxStatus;
import com.dayo.careround.caretaskservice.data.models.enums.PriorityLevel;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import com.dayo.careround.caretaskservice.data.repositories.OutboxEventRepository;
import com.dayo.careround.caretaskservice.services.MedicationRule;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// Fires one task's timer: in one transaction, lock the row, and if it is still PENDING or IN_PROGRESS set OVERDUE
// and write the outbox event. Anything else is a no-op, so stale queue entries and double firing are harmless.
@Component
public class OverdueProcessor {

    public enum Result { OVERDUE_WITH_EVENT, OVERDUE_NO_EVENT, OVERDUE_SILENT_BACKFILL, NOT_OPEN, NOT_FOUND }

    private final CareTaskRepository careTaskRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final MedicationRule medicationRule;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Duration backfillMaxAge;

    public OverdueProcessor(CareTaskRepository careTaskRepository, OutboxEventRepository outboxEventRepository,
                            MedicationRule medicationRule, ObjectMapper objectMapper, Clock clock,
                            @Value("${app.overdue.backfill-max-age-hours:24}") long backfillMaxAgeHours) {
        this.careTaskRepository = careTaskRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.medicationRule = medicationRule;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.backfillMaxAge = Duration.ofHours(backfillMaxAgeHours);
    }

    @Transactional
    public Result fire(UUID taskPublicId) {
        CareTask task = careTaskRepository.findForUpdateByPublicId(taskPublicId).orElse(null);
        if (task == null) return Result.NOT_FOUND;
        if (task.getStatus() != CareTaskStatus.PENDING && task.getStatus() != CareTaskStatus.IN_PROGRESS) return Result.NOT_OPEN;
        Instant now = clock.instant();
        if (task.getDeadline().isAfter(now)) return Result.NOT_OPEN; // deadline moved later; it is queued again

        task.setStatus(CareTaskStatus.OVERDUE);
        task.setOverdueAt(now);
        careTaskRepository.save(task);

        if (Duration.between(task.getDeadline(), now).compareTo(backfillMaxAge) > 0) {
            return Result.OVERDUE_SILENT_BACKFILL; // old data on first run: no alert flood
        }
        if (medicationRule.isMedication(task)) {
            Map<String, Object> p = basePayload(task, now);
            p.put("prescribedBy", task.getCreatedBy().toString());
            outbox("medication-overdue-" + task.getId(), "MEDICATION_OVERDUE", p);
            return Result.OVERDUE_WITH_EVENT;
        }
        if (PriorityLevel.fromInt(task.getPriority()) == PriorityLevel.HIGH) {
            Map<String, Object> p = basePayload(task, now);
            p.put("type", task.getType().getName());
            outbox("task-overdue-" + task.getId(), "TASK_OVERDUE", p);
            return Result.OVERDUE_WITH_EVENT;
        }
        return Result.OVERDUE_NO_EVENT; // passive dashboard flag only (FR-2.7)
    }

    private static Map<String, Object> basePayload(CareTask task, Instant now) {
        Map<String, Object> p = new HashMap<>();
        p.put("taskPublicId", task.getPublicId().toString());
        p.put("patientId", task.getPatientId().toString());
        p.put("wardId", task.getWardId() != null ? task.getWardId().toString() : null);
        p.put("priority", PriorityLevel.fromInt(task.getPriority()).name());
        p.put("deadline", task.getDeadline().toString());
        p.put("occurredAt", now.toString());
        return p;
    }

    private void outbox(String key, String type, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setIdempotencyKey(key);
        event.setEventType(type);
        event.setStatus(OutboxStatus.PENDING);
        try {
            event.setPayload(objectMapper.writeValueAsString(payload));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise " + type, e); // rolls back the OVERDUE flip too
        }
        event.setAttempts(0);
        event.setNextRetryAt(clock.instant());
        outboxEventRepository.save(event);
    }
}
