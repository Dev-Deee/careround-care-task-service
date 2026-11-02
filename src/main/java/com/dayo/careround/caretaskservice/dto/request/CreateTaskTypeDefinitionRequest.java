package com.dayo.careround.caretaskservice.dto.request;

import com.dayo.careround.caretaskservice.data.models.enums.PriorityLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTaskTypeDefinitionRequest(
        @NotBlank String name,
        Integer defaultPriority, // deprecated: send defaultPriorityLevel; still accepted
        @NotNull Boolean requiresDoctorOrder,
        PriorityLevel defaultPriorityLevel
) {}