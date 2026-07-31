package com.dayo.careround.caretaskservice.data.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "care_protocols")
public class CareProtocol {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_type_id", nullable = false)
    private TaskTypeDefinition taskType;

    @Column(nullable = false)
    private String cadence;

    @Column(nullable = false)
    private int intervalHours; // machine-readable — what the generation logic actually reads
}