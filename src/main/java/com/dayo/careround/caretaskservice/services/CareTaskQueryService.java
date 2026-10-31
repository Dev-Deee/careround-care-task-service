package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.models.MedicationDetails;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
import com.dayo.careround.caretaskservice.data.repositories.MedicationDetailsRepository;
import com.dayo.careround.caretaskservice.dto.response.CareTaskStatsResponse;
import com.dayo.careround.caretaskservice.dto.response.MedicationLogEntryResponse;
import com.dayo.careround.caretaskservice.dto.response.PageResponse;
import com.dayo.careround.caretaskservice.exception.BadRequestException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.UUID;

// Read side of care tasks: filtered, paged lists. "Overdue" is derived here (deadline passed while PENDING or
// IN_PROGRESS) and never stored; the overdue state machine is a later phase.
@Service
@RequiredArgsConstructor
public class CareTaskQueryService {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;
    public static final List<CareTaskStatus> OPEN = List.of(CareTaskStatus.PENDING, CareTaskStatus.IN_PROGRESS);
    private static final Sort ORDER = Sort.by("deadline", "createdAt", "id");

    private final CareTaskRepository careTaskRepository;
    private final MedicationDetailsRepository medicationDetailsRepository;

    public record Filter(UUID wardId, UUID patientId, UUID assignedNurseId, boolean unassigned,
                         CareTaskStatus status, Integer priority, boolean overdueDerived) {}

    @Transactional(readOnly = true)
    // Mapped inside the transaction: the task type is lazy.
    public <T> PageResponse<T> list(Filter f, Integer page, Integer size, Function<CareTask, T> mapper) {
        if (f.wardId() == null && f.patientId() == null && f.assignedNurseId() == null) {
            throw new BadRequestException("Give at least one of wardId, patientId, assignedNurseId or mine=true");
        }
        if (f.unassigned() && f.assignedNurseId() != null) {
            throw new BadRequestException("unassigned=true cannot be combined with assignedNurseId or mine=true");
        }
        Instant now = Instant.now();
        Specification<CareTask> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.wardId() != null) p.add(cb.equal(root.get("wardId"), f.wardId()));
            if (f.patientId() != null) p.add(cb.equal(root.get("patientId"), f.patientId()));
            if (f.assignedNurseId() != null) p.add(cb.equal(root.get("assignedNurseId"), f.assignedNurseId()));
            if (f.unassigned()) p.add(cb.isNull(root.get("assignedNurseId")));
            if (f.priority() != null) p.add(cb.equal(root.get("priority"), f.priority()));
            if (f.status() != null) p.add(cb.equal(root.get("status"), f.status()));
            else p.add(cb.notEqual(root.get("status"), CareTaskStatus.RETRACTED)); // undone tasks only on request
            if (f.overdueDerived()) {
                p.add(root.get("status").in(OPEN));
                p.add(cb.lessThan(root.get("deadline"), now));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE), ORDER);
        return PageResponse.of(careTaskRepository.findAll(spec, pageable), mapper);
    }

    // The prescriber view: the caller's own medication orders for one patient, newest deadline first.
    @Transactional(readOnly = true)
    public PageResponse<MedicationLogEntryResponse> medicationLog(UUID patientId, UUID doctorId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page == null ? 0 : Math.max(page, 0),
                size == null ? DEFAULT_SIZE : Math.min(Math.max(size, 1), MAX_SIZE),
                Sort.by(Sort.Order.desc("deadline"), Sort.Order.desc("createdAt"), Sort.Order.asc("id")));
        Page<CareTask> tasks = careTaskRepository.findMedicationOrders(patientId, doctorId, pageable);
        Map<UUID, MedicationDetails> details = medicationDetailsRepository
                .findByCareTaskIdIn(tasks.map(CareTask::getId).getContent()).stream()
                .collect(Collectors.toMap(MedicationDetails::getCareTaskId, d -> d));
        Instant now = Instant.now();
        return PageResponse.of(tasks, t -> {
            MedicationDetails d = details.get(t.getId());
            boolean given = t.getStatus() == CareTaskStatus.COMPLETED;
            return new MedicationLogEntryResponse(t.getPublicId(), t.getPatientId(), t.getWardId(),
                    d.getDrugName(), d.getAmount(), d.getUnit(), d.getRoute(), d.getFrequency(), d.getInstructions(),
                    d.getActualAmountGiven(), t.getStatus(), t.getWindowStart(), t.getDeadline(), t.getCreatedAt(),
                    given ? t.getCompletedAt() : null, given ? t.getAssignedNurseId() : null, isOverdueDerived(t, now));
        });
    }

    // Ward aggregate; window defaults to the last 24 hours. Authorisation is the caller's job (controller).
    @Transactional(readOnly = true)
    public CareTaskStatsResponse stats(UUID wardId, Instant from, Instant to) {
        Instant now = Instant.now();
        Instant end = to == null ? now : to;
        Instant start = from == null ? end.minus(Duration.ofHours(24)) : from;
        if (!start.isBefore(end)) throw new BadRequestException("from must be before to");
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (CareTaskStatus st : CareTaskStatus.values()) if (st != CareTaskStatus.RETRACTED) byStatus.put(st.name(), 0L);
        for (Object[] row : careTaskRepository.countByStatus(wardId, start, end)) byStatus.put(((CareTaskStatus) row[0]).name(), (Long) row[1]);
        Map<Integer, Long> byPriority = new TreeMap<>();
        for (Object[] row : careTaskRepository.countByPriority(wardId, start, end)) byPriority.put((Integer) row[0], (Long) row[1]);
        long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
        Double completionRate = total == 0 ? null : byStatus.get(CareTaskStatus.COMPLETED.name()) / (double) total;
        long overdue = careTaskRepository.countByWardIdAndStatusInAndDeadlineBefore(wardId, OPEN, now);
        Long oldest = careTaskRepository.findFirstByWardIdAndStatusInAndDeadlineBeforeOrderByDeadlineAsc(wardId, OPEN, now)
                .map(t -> Duration.between(t.getDeadline(), now).toSeconds()).orElse(null);
        return new CareTaskStatsResponse(wardId, start, end, total, byStatus, byPriority, completionRate, overdue, oldest, now);
    }

    public static boolean isOverdueDerived(CareTask task, Instant now) {
        return OPEN.contains(task.getStatus()) && task.getDeadline().isBefore(now);
    }
}
