package com.dayo.careround.caretaskservice.data.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
@Entity
@Table(name = "task_type_definitions")
public class TaskTypeDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    @NotBlank
    private String name;

    @Column(nullable = false)
    private int defaultPriority;

    @Column(nullable = false)
    private boolean requiresDoctorOrder;
}