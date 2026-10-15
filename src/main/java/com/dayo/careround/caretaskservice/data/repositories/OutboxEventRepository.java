package com.dayo.careround.caretaskservice.data.repositories;

import com.dayo.careround.caretaskservice.data.models.OutboxEvent;
import com.dayo.careround.caretaskservice.data.models.enums.OutboxStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    Page<OutboxEvent> findByStatusAndNextRetryAtLessThanEqual(
            OutboxStatus status, Instant now, Pageable pageable);
    boolean existsByIdempotencyKey(String idempotencyKey);
    List<OutboxEvent>findByStatus(OutboxStatus status);
}