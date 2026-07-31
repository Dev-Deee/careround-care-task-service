package com.dayo.careround.caretaskservice.data.models;

import com.dayo.careround.caretaskservice.data.models.enums.MedicationRoute;
import com.dayo.careround.caretaskservice.data.models.enums.MedicationUnit;
import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Entity
@Table(name = "medication_details")
public class MedicationDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "care_task_id", nullable = false, unique = true)
    private UUID careTaskId;

    @Column(nullable = false)
    private String drugName;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicationUnit unit;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MedicationRoute route;

    @Column(nullable = false)
    private String frequency;

    @Column
    private String instructions;

    @Column
    private BigDecimal actualAmountGiven;
}