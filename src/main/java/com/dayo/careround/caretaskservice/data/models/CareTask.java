package com.dayo.careround.caretaskservice.data.models;

import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "care_tasks")
public class CareTask {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false)
    private UUID patientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_type_id", nullable = false)
    private TaskTypeDefinition type;

    @Column(nullable = false)
    private int priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareTaskOrigin origin;

    @Column
    private UUID carePlanId;

    @Column
    private UUID assignedNurseId;

    @Column(nullable = false)
    private Instant windowStart;

    @Column(nullable = false)
    private Instant deadline;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CareTaskStatus status = CareTaskStatus.PENDING;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant undoWindowExpiresAt;

    @Column
    private UUID cancelledBy;

    @Column
    private String cancellationReason;

    @Column
    private UUID refusedBy;

    @Column
    private String refusalReason;

    @Column
    private Instant refusedAt;
}