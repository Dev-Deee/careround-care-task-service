package com.dayo.careround.caretaskservice.data.repositories;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareTaskRepository extends JpaRepository<CareTask, UUID> {
    Optional<CareTask> findByPublicId(UUID publicId);
    List<CareTask> findByPatientId(UUID patientId);
    List<CareTask> findByAssignedNurseIdAndStatus(UUID assignedNurseId, CareTaskStatus status);
    List<CareTask> findByPatientIdAndStatusIn(UUID patientId, List<CareTaskStatus> statuses);
    List<CareTask> findByStatusAndDeadlineBefore(CareTaskStatus status, Instant now);
}