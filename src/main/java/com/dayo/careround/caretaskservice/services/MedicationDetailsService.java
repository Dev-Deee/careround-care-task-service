package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.MedicationDetails;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import com.dayo.careround.caretaskservice.data.repositories.MedicationDetailsRepository;
import com.dayo.careround.caretaskservice.dto.request.AttachMedicationDetailsRequest;
import com.dayo.careround.caretaskservice.dto.response.MedicationDetailsResponse;
import com.dayo.careround.caretaskservice.exception.CareTaskNotFoundException;
import com.dayo.careround.caretaskservice.exception.MedicationDetailsAlreadyExistException;
import jakarta.transaction.Transactional;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.exception.InvalidTaskStateException;
import org.springframework.security.access.AccessDeniedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MedicationDetailsService {

    private final MedicationDetailsRepository medicationDetailsRepository;
    private final CareTaskRepository careTaskRepository;

    @Transactional
    /**
     * Medication details belong to a doctor's order that has not been started: the task must be DOCTOR_ORDER
     * (there is no separate MEDICATION task category; FR-12.1 defines a medication task as a doctor's order) and
     * still PENDING, and only the ordering doctor (createdBy) may attach them.
     */
    public MedicationDetailsResponse attach(UUID careTaskPublicId, UUID callerId, AttachMedicationDetailsRequest request) {
        CareTask task = careTaskRepository.findByPublicId(careTaskPublicId)
                .orElseThrow(() -> new CareTaskNotFoundException("No care task with that public id"));

        if (!task.getCreatedBy().equals(callerId)) {
            throw new AccessDeniedException("Only the doctor who ordered this task may attach medication details");
        }
        if (task.getOrigin() != CareTaskOrigin.DOCTOR_ORDER) {
            throw new InvalidTaskStateException("Medication details can only be attached to a DOCTOR_ORDER task");
        }
        if (task.getStatus() != CareTaskStatus.PENDING) {
            throw new InvalidTaskStateException("Medication details can only be attached while the task is PENDING (it is " + task.getStatus() + ")");
        }

        if (medicationDetailsRepository.findByCareTaskId(task.getId()).isPresent()) {
            throw new MedicationDetailsAlreadyExistException("Medication details already attached to this task");
        }

        MedicationDetails details = new MedicationDetails();
        details.setCareTaskId(task.getId());
        details.setDrugName(request.drugName());
        details.setAmount(request.amount());
        details.setUnit(request.unit());
        details.setRoute(request.route());
        details.setFrequency(request.frequency());
        details.setInstructions(request.instructions());

        medicationDetailsRepository.save(details);
        return toResponse(details);
    }

    public MedicationDetailsResponse getByCareTask(UUID careTaskPublicId) {
        CareTask task = careTaskRepository.findByPublicId(careTaskPublicId)
                .orElseThrow(() -> new CareTaskNotFoundException("No care task with that public id"));

        MedicationDetails details = medicationDetailsRepository.findByCareTaskId(task.getId())
                .orElseThrow(() -> new CareTaskNotFoundException("No medication details attached to this task"));

        return toResponse(details);
    }

    private MedicationDetailsResponse toResponse(MedicationDetails details) {
        return new MedicationDetailsResponse(
                details.getId(), details.getCareTaskId(), details.getDrugName(),
                details.getAmount(), details.getUnit(), details.getRoute(),
                details.getFrequency(), details.getInstructions(), details.getActualAmountGiven()
        );
    }
}