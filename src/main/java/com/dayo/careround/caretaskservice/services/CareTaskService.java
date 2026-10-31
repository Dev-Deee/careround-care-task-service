package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.OutboxEvent;
import com.dayo.careround.caretaskservice.data.models.TaskTypeDefinition;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.models.enums.OutboxStatus;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import com.dayo.careround.caretaskservice.data.repositories.MedicationDetailsRepository;
import com.dayo.careround.caretaskservice.data.repositories.OutboxEventRepository;
import com.dayo.careround.caretaskservice.data.repositories.TaskTypeDefinitionRepository;
import com.dayo.careround.caretaskservice.dto.request.CancelCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.request.CreateCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.request.RefuseCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.response.CareTaskResponse;
import com.dayo.careround.caretaskservice.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import com.dayo.careround.caretaskservice.data.models.CarePlan;
import com.dayo.careround.caretaskservice.data.repositories.CarePlanRepository;
import com.dayo.careround.caretaskservice.exception.CarePlanNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CareTaskService {

    private static final Duration UNDO_WINDOW = Duration.ofSeconds(10);

    private final CareTaskRepository careTaskRepository;
    private final TaskTypeDefinitionRepository taskTypeDefinitionRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final MedicationDetailsRepository medicationDetailsRepository;
    private final CarePlanRepository carePlanRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CareTaskResponse create(UUID createdBy, String creatorRole, CreateCareTaskRequest request) {
        TaskTypeDefinition taskType = taskTypeDefinitionRepository.findById(request.taskTypeId())
                .orElseThrow(() -> new TaskTypeNotFoundException("No task type with that id"));

        // Rule 1: if this task type requires a doctor's order, origin MUST be DOCTOR_ORDER,
        // regardless of who is creating it.
        if (taskType.isRequiresDoctorOrder() && request.origin() != CareTaskOrigin.DOCTOR_ORDER) {
            throw new InvalidTaskOriginException(
                    "This task type requires a doctor's order — origin must be DOCTOR_ORDER");
        }

        // Rule 1b: only a Doctor may create a DOCTOR_ORDER task; only a Nurse may create a
        // NURSING_CARE_PLAN task.
        if (request.origin() == CareTaskOrigin.DOCTOR_ORDER && !"DOCTOR".equals(creatorRole)) {
            throw new InvalidTaskOriginException("Only a doctor may create a DOCTOR_ORDER task");
        }
        if (request.origin() == CareTaskOrigin.NURSING_CARE_PLAN && !"NURSE".equals(creatorRole)) {
            throw new InvalidTaskOriginException("Only a nurse may create a NURSING_CARE_PLAN task");
        }

        CareTask task = new CareTask();
        task.setPatientId(request.patientId());
        task.setType(taskType);
        task.setPriority(request.priority());
        task.setOrigin(request.origin());
        task.setCarePlanId(request.carePlanId());
        if (request.carePlanId() != null) {
            // carePlanId from a client is the plan's publicId (the id the API returns).
            CarePlan plan = carePlanRepository.findByPublicId(request.carePlanId())
                    .orElseThrow(() -> new CarePlanNotFoundException("No care plan with that public id"));
            task.setCarePlanPublicId(plan.getPublicId());
        }
        task.setAssignedNurseId(request.assignedNurseId()); // nullable — leaves it poolable
        task.setWindowStart(request.windowStart());
        task.setDeadline(request.deadline());
        task.setStatus(CareTaskStatus.PENDING);
        task.setCreatedBy(createdBy);
        task.setUndoWindowExpiresAt(Instant.now().plus(UNDO_WINDOW));

        careTaskRepository.save(task);
        return toResponse(task);
    }

    /**
     * Undo — only within the window, only by the creator. Soft delete: status → RETRACTED,
     * row stays for audit, invisible to normal task-list queries from here on.
     */
    @Transactional
    public void retract(UUID publicId, UUID callerId) {
        CareTask task = findOrThrow(publicId);

        if (!task.getCreatedBy().equals(callerId)) {
            throw new InvalidTaskStateException("Only the creating staff member may retract this task");
        }
        if (Instant.now().isAfter(task.getUndoWindowExpiresAt())) {
            throw new UndoWindowExpiredException("The undo window for this task has passed");
        }

        task.setStatus(CareTaskStatus.RETRACTED);
        careTaskRepository.save(task);
    }

    /**
     * Claim-by-acting: any nurse can move an unassigned PENDING task to IN_PROGRESS,
     * which sets assignedNurseId at that moment. If already assigned, only that nurse
     * may make this transition.
     */
    @Transactional
    public CareTaskResponse claimOrStart(UUID publicId, UUID nurseId) {
        CareTask task = findOrThrow(publicId);

        if (task.getStatus() != CareTaskStatus.PENDING) {
            throw new InvalidTaskStateException("Only a PENDING task can be started — current state: " + task.getStatus());
        }

        if (task.getAssignedNurseId() == null) {
            task.setAssignedNurseId(nurseId); // claim
        } else if (!task.getAssignedNurseId().equals(nurseId)) {
            throw new TaskAlreadyClaimedException("This task is already assigned to a different nurse");
        }

        task.setStatus(CareTaskStatus.IN_PROGRESS);
        careTaskRepository.save(task);
        return toResponse(task);
    }

    @Transactional
    public CareTaskResponse complete(UUID publicId, UUID nurseId) {
        CareTask task = findOrThrow(publicId);

        if (task.getStatus() != CareTaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException("Only an IN_PROGRESS task can be completed");
        }
        if (!nurseId.equals(task.getAssignedNurseId())) {
            throw new InvalidTaskStateException("Only the assigned nurse may complete this task");
        }

        task.setStatus(CareTaskStatus.COMPLETED);
        careTaskRepository.save(task);

        publishMedicationAdministeredIfApplicable(task, nurseId);

        return toResponse(task);
    }

    private void publishMedicationAdministeredIfApplicable(CareTask task, UUID administeredBy) {
        medicationDetailsRepository.findByCareTaskId(task.getId()).ifPresent(details -> {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("patientId", task.getPatientId().toString());
                payload.put("careTaskId", task.getId().toString()); // deprecated: internal id
                payload.put("careTaskPublicId", task.getPublicId().toString());
                payload.put("prescribedBy", task.getCreatedBy().toString()); // the doctor who ordered it
                payload.put("drugName", details.getDrugName());
                payload.put("administeredBy", administeredBy.toString());

                OutboxEvent event = new OutboxEvent();
                event.setIdempotencyKey("medication-administered-" + task.getId());
                event.setEventType("MEDICATION_ADMINISTERED");
                event.setStatus(OutboxStatus.PENDING);
                event.setPayload(objectMapper.writeValueAsString(payload));
                event.setAttempts(0);
                event.setNextRetryAt(Instant.now());
                outboxEventRepository.save(event);
            } catch (Exception e) {
                log.error("Failed to build MEDICATION_ADMINISTERED outbox event: {}", e.getMessage(), e);
            }
        });
    }

    @Transactional
    public CareTaskResponse cancel(UUID publicId, UUID callerId, CancelCareTaskRequest request) {
        CareTask task = findOrThrow(publicId);

        boolean isCreatorOrAssigned = callerId.equals(task.getCreatedBy())
                || callerId.equals(task.getAssignedNurseId());
        if (!isCreatorOrAssigned) {
            throw new InvalidTaskStateException("Only the creator or assigned nurse may cancel this task");
        }
        if (task.getStatus() == CareTaskStatus.COMPLETED || task.getStatus() == CareTaskStatus.CANCELLED) {
            throw new InvalidTaskStateException("Cannot cancel a task in state " + task.getStatus());
        }

        task.setStatus(CareTaskStatus.CANCELLED);
        task.setCancelledBy(callerId);
        task.setCancellationReason(request.reason());
        careTaskRepository.save(task);
        return toResponse(task);
    }

    /**
     * A nurse's legal right to refuse an unsafe order. Only the assigned nurse, only for
     * a DOCTOR_ORDER task — refusing your own NURSING_CARE_PLAN task is just cancellation.
     */
    @Transactional
    public CareTaskResponse refuse(UUID publicId, UUID nurseId, RefuseCareTaskRequest request) {
        CareTask task = findOrThrow(publicId);

        if (task.getOrigin() != CareTaskOrigin.DOCTOR_ORDER) {
            throw new InvalidRefusalException("Only a DOCTOR_ORDER task can be refused — cancel it instead");
        }
        if (!nurseId.equals(task.getAssignedNurseId())) {
            throw new InvalidRefusalException("Only the assigned nurse may refuse this task");
        }
        if (task.getStatus() != CareTaskStatus.PENDING && task.getStatus() != CareTaskStatus.IN_PROGRESS) {
            throw new InvalidTaskStateException("Cannot refuse a task in state " + task.getStatus());
        }

        task.setStatus(CareTaskStatus.REFUSED);
        task.setRefusedBy(nurseId);
        task.setRefusalReason(request.reason());
        task.setRefusedAt(Instant.now());
        careTaskRepository.save(task);

        // TODO: route through the Alert mechanism (§7.3-style guaranteed-acknowledgement pattern)
        // to notify the ordering doctor — parked, same as PATIENT_EMERGENCY_DECLARED notification hooks

        return toResponse(task);
    }

    @Transactional
    public CareTaskResponse getByPublicId(UUID publicId) {
        return toResponse(findOrThrow(publicId));
    }

    private CareTask findOrThrow(UUID publicId) {
        return careTaskRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CareTaskNotFoundException("No care task with that public id"));
    }

    private CareTaskResponse toResponse(CareTask task) {
        return new CareTaskResponse(
                task.getPublicId(), task.getPatientId(), task.getType().getName(),
                task.getPriority(), task.getOrigin(), task.getCarePlanId(),
                task.getAssignedNurseId(), task.getWindowStart(), task.getDeadline(),
                task.getStatus(), task.getCreatedBy(), task.getUndoWindowExpiresAt(),
                task.getCarePlanPublicId()
        );
    }
}