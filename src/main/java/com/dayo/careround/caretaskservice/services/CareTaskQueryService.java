package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CareTask;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.repositories.CareTaskRepository;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    public record Filter(UUID wardId, UUID patientId, UUID assignedNurseId, boolean unassigned,
                         CareTaskStatus status, Integer priority, boolean overdueDerived) {}

    @Transactional(readOnly = true)
    public Page<CareTask> list(Filter f, Integer page, Integer size) {
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
        return careTaskRepository.findAll(spec, pageable);
    }

    public static boolean isOverdueDerived(CareTask task, Instant now) {
        return OPEN.contains(task.getStatus()) && task.getDeadline().isBefore(now);
    }
}
