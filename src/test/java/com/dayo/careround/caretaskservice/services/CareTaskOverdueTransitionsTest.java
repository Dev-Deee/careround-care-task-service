package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.MedicationDetails;
import com.dayo.careround.caretaskservice.data.models.OutboxEvent;
import com.dayo.careround.caretaskservice.data.models.TaskTypeDefinition;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.repositories.*;
import com.dayo.careround.caretaskservice.dto.request.RefuseCareTaskRequest;
import com.dayo.careround.caretaskservice.exception.InvalidTaskStateException;
import com.dayo.careround.caretaskservice.grpc.PatientLookupClient;
import com.dayo.careround.caretaskservice.overdue.OverdueScheduler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

// OVERDUE keeps the work deliverable: it can be claimed and completed; refuse is unchanged (PENDING/IN_PROGRESS only).
class CareTaskOverdueTransitionsTest {

    private final CareTaskRepository tasks = mock(CareTaskRepository.class);
    private final OutboxEventRepository outbox = mock(OutboxEventRepository.class);
    private final MedicationDetailsRepository details = mock(MedicationDetailsRepository.class);
    private final OverdueScheduler scheduler = mock(OverdueScheduler.class);
    private final CareTaskService service = new CareTaskService(tasks, mock(TaskTypeDefinitionRepository.class), outbox,
            mock(CarePlanRepository.class), mock(PatientLookupClient.class), new ObjectMapper(), new MedicationRule(details), scheduler);

    private CareTask overdue(UUID assignee) {
        CareTask t = new CareTask();
        t.setId(UUID.randomUUID());
        t.setPatientId(UUID.randomUUID());
        t.setType(new TaskTypeDefinition());
        t.setOrigin(CareTaskOrigin.DOCTOR_ORDER);
        t.setStatus(CareTaskStatus.OVERDUE);
        t.setAssignedNurseId(assignee);
        t.setCreatedBy(UUID.randomUUID());
        t.setDeadline(Instant.now().minusSeconds(60));
        t.setUndoWindowExpiresAt(Instant.now());
        when(tasks.findByPublicId(t.getPublicId())).thenReturn(Optional.of(t));
        when(details.findByCareTaskId(t.getId())).thenReturn(Optional.empty());
        return t;
    }

    @Test
    void overdueUnassignedTaskCanBeClaimedAndStaysOverdue() {
        UUID nurse = UUID.randomUUID();
        CareTask t = overdue(null);
        service.claimOrStart(t.getPublicId(), nurse);
        assertEquals(nurse, t.getAssignedNurseId());
        assertEquals(CareTaskStatus.OVERDUE, t.getStatus());
    }

    @Test
    void overdueTaskCompletesLateAndStillPublishesMedicationAdministered() {
        UUID nurse = UUID.randomUUID();
        CareTask t = overdue(nurse);
        MedicationDetails md = new MedicationDetails();
        md.setDrugName("Paracetamol");
        md.setAmount(BigDecimal.ONE);
        when(details.findByCareTaskId(t.getId())).thenReturn(Optional.of(md));
        service.complete(t.getPublicId(), nurse);
        assertEquals(CareTaskStatus.COMPLETED, t.getStatus());
        assertNotNull(t.getCompletedAt());
        ArgumentCaptor<OutboxEvent> ev = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outbox).save(ev.capture());
        assertEquals("MEDICATION_ADMINISTERED", ev.getValue().getEventType());
        verify(scheduler).removeAfterCommit(t.getPublicId());
    }

    @Test
    void overdueTaskCompletedByAnotherNurseIsRejected() {
        CareTask t = overdue(UUID.randomUUID());
        assertThrows(InvalidTaskStateException.class, () -> service.complete(t.getPublicId(), UUID.randomUUID()));
    }

    @Test
    void refuseOfAnOverdueTaskIsUnchanged() {
        UUID nurse = UUID.randomUUID();
        CareTask t = overdue(nurse);
        assertThrows(InvalidTaskStateException.class,
                () -> service.refuse(t.getPublicId(), nurse, new RefuseCareTaskRequest("unsafe")));
    }

    @Test
    void medicationRuleNeedsADoctorOrderAndDetails() {
        MedicationRule rule = new MedicationRule(details);
        CareTask t = overdue(null);
        assertFalse(rule.isMedication(t));
        when(details.findByCareTaskId(t.getId())).thenReturn(Optional.of(new MedicationDetails()));
        assertTrue(rule.isMedication(t));
        t.setOrigin(CareTaskOrigin.NURSING_CARE_PLAN);
        assertFalse(rule.isMedication(t)); // details only count on a doctor order
    }
}
