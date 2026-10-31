package com.dayo.careround.caretaskservice.data.repositories;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareTaskRepository extends JpaRepository<CareTask, UUID>, JpaSpecificationExecutor<CareTask> {
    Optional<CareTask> findByPublicId(UUID publicId);
    List<CareTask> findByPatientId(UUID patientId);
    List<CareTask> findByAssignedNurseIdAndStatus(UUID assignedNurseId, CareTaskStatus status);
    List<CareTask> findByPatientIdAndStatusIn(UUID patientId, List<CareTaskStatus> statuses);
    List<CareTask> findByStatusAndDeadlineBefore(CareTaskStatus status, Instant now);
    List<CareTask> findByCarePlanIdOrderByDeadlineDesc(UUID carePlanId);

    @Query("select t.status, count(t) from CareTask t where t.wardId = :wardId and t.deadline >= :from"
            + " and t.deadline < :to and t.status <> com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus.RETRACTED"
            + " group by t.status")
    List<Object[]> countByStatus(@Param("wardId") UUID wardId, @Param("from") Instant from, @Param("to") Instant to);

    @Query("select t.priority, count(t) from CareTask t where t.wardId = :wardId and t.deadline >= :from"
            + " and t.deadline < :to and t.status <> com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus.RETRACTED"
            + " group by t.priority")
    List<Object[]> countByPriority(@Param("wardId") UUID wardId, @Param("from") Instant from, @Param("to") Instant to);

    long countByWardIdAndStatusInAndDeadlineBefore(UUID wardId, Collection<CareTaskStatus> statuses, Instant now);

    Optional<CareTask> findFirstByWardIdAndStatusInAndDeadlineBeforeOrderByDeadlineAsc(
            UUID wardId, Collection<CareTaskStatus> statuses, Instant now);

    // Medication orders (tasks with medication details) a prescriber created for a patient.
    @Query(value = "select t from CareTask t where t.patientId = :patientId and t.createdBy = :createdBy"
            + " and exists (select 1 from MedicationDetails m where m.careTaskId = t.id)",
            countQuery = "select count(t) from CareTask t where t.patientId = :patientId and t.createdBy = :createdBy"
            + " and exists (select 1 from MedicationDetails m where m.careTaskId = t.id)")
    Page<CareTask> findMedicationOrders(@Param("patientId") UUID patientId, @Param("createdBy") UUID createdBy,
                                        Pageable pageable);
}