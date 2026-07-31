package com.dayo.careround.caretaskservice.data.models;

import jakarta.persistence.*;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "care_plans")
public class CarePlan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false)
    private UUID patientId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private CareProtocol protocol;

    @Column(nullable = false)
    private String cadence;

    @Column(nullable = false)
    private boolean active = true;
}