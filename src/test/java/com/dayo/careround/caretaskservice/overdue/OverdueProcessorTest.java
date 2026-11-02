package com.dayo.careround.caretaskservice.overdue;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.MedicationDetails;
import com.dayo.careround.caretaskservice.data.models.OutboxEvent;
import com.dayo.careround.caretaskservice.data.models.TaskTypeDefinition;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import com.dayo.careround.caretaskservice.data.repositories.MedicationDetailsRepository;
import com.dayo.careround.caretaskservice.data.repositories.OutboxEventRepository;
import com.dayo.careround.caretaskservice.services.MedicationRule;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OverdueProcessorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");

    private final CareTaskRepository tasks = mock(CareTaskRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final MedicationDetailsRepository details = mock(MedicationDetailsRepository.class);
    private final ObjectMapper json = new ObjectMapper();
    private final OverdueProcessor processor = new OverdueProcessor(tasks, outbox, new MedicationRule(details), json,
            Clock.fixed(NOW, ZoneOffset.UTC), 24);

    private CareTask task(CareTaskStatus status, CareTaskOrigin origin, int priority, Duration late) {
        CareTask t = new CareTask();
        t.setId(UUID.randomUUID());
        t.setPatientId(UUID.randomUUID());
        t.setWardId(UUID.randomUUID());
        TaskTypeDefinition type = new TaskTypeDefinition();
        type.setName("Wound dressing");
        t.setType(type);
        t.setStatus(status);
        t.setOrigin(origin);
        t.setPriority(priority);
        t.setCreatedBy(UUID.randomUUID());
        t.setDeadline(NOW.minus(late));
        when(tasks.findForUpdateByPublicId(t.getPublicId())).thenReturn(Optional.of(t));
        when(details.findByCareTaskId(t.getId())).thenReturn(Optional.empty());
        return t;
    }

    private Map<?, ?> onlyOutboxPayload(String expectedType, String expectedKey) throws Exception {
        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(ev.capture());
        assertEquals(expectedType, ev.getValue().getEventType());
        assertEquals(expectedKey, ev.getValue().getIdempotencyKey());
        return json.readValue(ev.getValue().getPayload(), Map.class);
    }

    @Test
    void pendingBecomesOverdue() {
        CareTask t = task(CareTaskStatus.PENDING, CareTaskOrigin.NURSING_CARE_PLAN, 2, Duration.ofMinutes(1));
        assertEquals(OverdueProcessor.Result.OVERDUE_NO_EVENT, processor.fire(t.getPublicId()));
        assertEquals(CareTaskStatus.OVERDUE, t.getStatus());
        assertEquals(NOW, t.getOverdueAt());
        verify(outbox, never()).save(any()); // MEDIUM, not medication: passive flag only
    }

    @Test
    void inProgressBecomesOverdue() {
        CareTask t = task(CareTaskStatus.IN_PROGRESS, CareTaskOrigin.NURSING_CARE_PLAN, 1, Duration.ofMinutes(1));
        processor.fire(t.getPublicId());
        assertEquals(CareTaskStatus.OVERDUE, t.getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = CareTaskStatus.class, names = {"COMPLETED", "CANCELLED", "REFUSED", "RETRACTED", "OVERDUE"})
    void anythingElseIsANoOp(CareTaskStatus status) {
        CareTask t = task(status, CareTaskOrigin.DOCTOR_ORDER, 3, Duration.ofMinutes(1));
        assertEquals(OverdueProcessor.Result.NOT_OPEN, processor.fire(t.getPublicId()));
        assertEquals(status, t.getStatus());
        assertNull(t.getOverdueAt());
        verify(tasks, never()).save(any());
        verify(outbox, never()).save(any());
    }

    @Test
    void deadlineNotReachedIsANoOp() {
        CareTask t = task(CareTaskStatus.PENDING, CareTaskOrigin.NURSING_CARE_PLAN, 3, Duration.ofMinutes(-5));
        assertEquals(OverdueProcessor.Result.NOT_OPEN, processor.fire(t.getPublicId()));
        assertEquals(CareTaskStatus.PENDING, t.getStatus());
    }

    @Test
    void medicationOrderEmitsMedicationOverdue() throws Exception {
        CareTask t = task(CareTaskStatus.PENDING, CareTaskOrigin.DOCTOR_ORDER, 1, Duration.ofMinutes(1));
        when(details.findByCareTaskId(t.getId())).thenReturn(Optional.of(new MedicationDetails()));
        assertEquals(OverdueProcessor.Result.OVERDUE_WITH_EVENT, processor.fire(t.getPublicId()));
        Map<?, ?> p = onlyOutboxPayload("MEDICATION_OVERDUE", "medication-overdue-" + t.getId());
        assertEquals(t.getCreatedBy().toString(), p.get("prescribedBy"));
        assertEquals(t.getPublicId().toString(), p.get("taskPublicId"));
        assertEquals("LOW", p.get("priority")); // medication alerts do not depend on priority
    }

    @Test
    void detailsAttachedAfterCreationAreSeenWhenTheTimerFires() throws Exception {
        CareTask t = task(CareTaskStatus.PENDING, CareTaskOrigin.DOCTOR_ORDER, 3, Duration.ofMinutes(1));
        MedicationRule rule = new MedicationRule(details);
        assertEquals(false, rule.isMedication(t)); // at creation: no details yet
        when(details.findByCareTaskId(t.getId())).thenReturn(Optional.of(new MedicationDetails())); // attached later
        processor.fire(t.getPublicId());
        onlyOutboxPayload("MEDICATION_OVERDUE", "medication-overdue-" + t.getId());
    }

    @Test
    void doctorOrderWithoutDetailsIsANormalTask() throws Exception {
        CareTask high = task(CareTaskStatus.PENDING, CareTaskOrigin.DOCTOR_ORDER, 3, Duration.ofMinutes(1));
        processor.fire(high.getPublicId());
        Map<?, ?> p = onlyOutboxPayload("TASK_OVERDUE", "task-overdue-" + high.getId());
        assertEquals("Wound dressing", p.get("type"));
        assertEquals("HIGH", p.get("priority"));

        clearInvocations(outbox);
        CareTask medium = task(CareTaskStatus.PENDING, CareTaskOrigin.DOCTOR_ORDER, 2, Duration.ofMinutes(1));
        assertEquals(OverdueProcessor.Result.OVERDUE_NO_EVENT, processor.fire(medium.getPublicId()));
        verify(outbox, never()).save(any());
    }

    @Test
    void highPriorityNonMedicationEmitsTaskOverdue() throws Exception {
        CareTask t = task(CareTaskStatus.IN_PROGRESS, CareTaskOrigin.NURSING_CARE_PLAN, 3, Duration.ofMinutes(1));
        processor.fire(t.getPublicId());
        Map<?, ?> p = onlyOutboxPayload("TASK_OVERDUE", "task-overdue-" + t.getId());
        assertEquals(t.getWardId().toString(), p.get("wardId"));
    }

    @Test
    void oldBackfillFlipsSilently() {
        CareTask t = task(CareTaskStatus.PENDING, CareTaskOrigin.DOCTOR_ORDER, 3, Duration.ofHours(25));
        when(details.findByCareTaskId(t.getId())).thenReturn(Optional.of(new MedicationDetails()));
        assertEquals(OverdueProcessor.Result.OVERDUE_SILENT_BACKFILL, processor.fire(t.getPublicId()));
        assertEquals(CareTaskStatus.OVERDUE, t.getStatus());
        verify(outbox, never()).save(any());
    }

    @Test
    void missingTaskIsANoOp() {
        assertEquals(OverdueProcessor.Result.NOT_FOUND, processor.fire(UUID.randomUUID()));
    }
}
