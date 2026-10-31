package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CarePlan;
import com.dayo.careround.caretaskservice.data.models.CareProtocol;
import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.repositories.CarePlanRepository;
import com.dayo.careround.caretaskservice.data.repositories.CareProtocolRepository;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import com.dayo.careround.caretaskservice.dto.request.CreateCarePlanRequest;
import com.dayo.careround.caretaskservice.dto.response.CarePlanResponse;
import com.dayo.careround.caretaskservice.exception.CarePlanNotFoundException;
import com.dayo.careround.caretaskservice.exception.CareProtocolNotFoundException;
import jakarta.transaction.Transactional;
import com.dayo.careround.caretaskservice.exception.InvalidTaskOriginException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CarePlanService {

    private final CarePlanRepository carePlanRepository;
    private final CareProtocolRepository careProtocolRepository;
    private final CareTaskRepository careTaskRepository;

    @Transactional
    public CarePlanResponse create(UUID createdBy, String creatorRole, CreateCarePlanRequest request) {
        CareProtocol protocol = careProtocolRepository.findById(request.protocolId())
                .orElseThrow(() -> new CareProtocolNotFoundException("No care protocol with that id"));

        // Generated tasks take their origin from the task type. A nurse-created plan must only generate
        // NURSING_CARE_PLAN tasks (FR-2.9/FR-11.1), so a protocol whose task type needs a doctor's order can only be
        // started by a doctor - the same rule as creating such a task by hand.
        if (protocol.getTaskType().isRequiresDoctorOrder() && !"DOCTOR".equals(creatorRole)) {
            throw new InvalidTaskOriginException(
                    "This protocol's task type requires a doctor's order - only a doctor may start this care plan");
        }

        CarePlan plan = new CarePlan();
        plan.setPatientId(request.patientId());
        plan.setProtocol(protocol);
        plan.setCadence(protocol.getCadence());
        plan.setIntervalHours(protocol.getIntervalHours());
        plan.setActive(true);
        plan.setCreatedBy(createdBy);

        carePlanRepository.save(plan);
        generateNextTask(plan);

        return toResponse(plan);
    }

    /**
     * Generates the next CareTask occurrence for an active CarePlan.
     * createdBy is always the plan's own creator — accurate, since creation and
     * activation are the same event in this design; every auto-generated task
     * traces back to the real clinician whose standing order is driving it.
     */
    @Transactional
    public void generateNextTask(CarePlan plan) {
        CareTask task = new CareTask();
        task.setPatientId(plan.getPatientId());
        task.setType(plan.getProtocol().getTaskType());
        task.setPriority(plan.getProtocol().getTaskType().getDefaultPriority());
        task.setOrigin(plan.getProtocol().getTaskType().isRequiresDoctorOrder()
                ? CareTaskOrigin.DOCTOR_ORDER
                : CareTaskOrigin.NURSING_CARE_PLAN);
        task.setCarePlanId(plan.getId());
        task.setCarePlanPublicId(plan.getPublicId());
        task.setWindowStart(Instant.now());
        task.setDeadline(Instant.now().plus(Duration.ofHours(plan.getIntervalHours())));
        task.setStatus(CareTaskStatus.PENDING);
        task.setCreatedBy(plan.getCreatedBy());
        task.setUndoWindowExpiresAt(Instant.now().plusSeconds(10));

        careTaskRepository.save(task);
    }

    /**
     * Scheduled sweep — for every active CarePlan, checks whether its most recently
     * generated task's deadline has passed, and if so, generates the next occurrence.
     */
    @Transactional
    public void generateDueTasks() {
        List<CarePlan> activePlans = carePlanRepository.findByActive(true);

        for (CarePlan plan : activePlans) {
            List<CareTask> existingForPlan = careTaskRepository.findByCarePlanIdOrderByDeadlineDesc(plan.getId());

            boolean shouldGenerate = existingForPlan.isEmpty()
                    || Instant.now().isAfter(existingForPlan.get(0).getDeadline());

            if (shouldGenerate) {
                generateNextTask(plan);
            }
        }
    }

    @Transactional
    public CarePlanResponse deactivate(UUID publicId) {
        CarePlan plan = carePlanRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CarePlanNotFoundException("No care plan with that public id"));
        plan.setActive(false);
        carePlanRepository.save(plan);
        return toResponse(plan);
    }

    @Transactional
    public CarePlanResponse getByPublicId(UUID publicId) {
        return toResponse(carePlanRepository.findByPublicId(publicId)
                .orElseThrow(() -> new CarePlanNotFoundException("No care plan with that public id")));
    }

    private CarePlanResponse toResponse(CarePlan plan) {
        return new CarePlanResponse(
                plan.getPublicId(), plan.getPatientId(), plan.getProtocol().getName(),
                plan.getCadence(), plan.getIntervalHours(), plan.isActive()
        );
    }
}