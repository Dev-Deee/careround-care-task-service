package com.dayo.careround.caretaskservice.data.models;

import com.dayo.careround.caretaskservice.data.models.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_outbox_status_retry", columnList = "status, next_retry_at")
})
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String idempotencyKey;

    @Column(nullable = false)
    private String eventType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private int attempts = 0;

    @Column
    private Instant lastAttemptAt;

    @Column(nullable = false)
    private Instant nextRetryAt = Instant.now();

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}