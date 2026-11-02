package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.MedicationDetails;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.repositories.MedicationDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

// The one definition of a medication task: a DOCTOR_ORDER with a MedicationDetails row. Used for
// MEDICATION_ADMINISTERED and for choosing MEDICATION_OVERDUE vs TASK_OVERDUE, and always evaluated when the event
// is built (details can be attached after the task was created). A doctor order without details is a normal task.
@Component
@RequiredArgsConstructor
public class MedicationRule {

    private final MedicationDetailsRepository medicationDetailsRepository;

    public Optional<MedicationDetails> medicationDetails(CareTask task) {
        if (task.getOrigin() != CareTaskOrigin.DOCTOR_ORDER) return Optional.empty();
        return medicationDetailsRepository.findByCareTaskId(task.getId());
    }

    public boolean isMedication(CareTask task) {
        return medicationDetails(task).isPresent();
    }
}
