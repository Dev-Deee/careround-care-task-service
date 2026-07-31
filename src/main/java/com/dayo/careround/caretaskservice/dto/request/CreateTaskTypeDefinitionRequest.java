package com.dayo.careround.caretaskservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateTaskTypeDefinitionRequest(
        @NotBlank String name,
        @NotNull Integer defaultPriority,
        @NotNull Boolean requiresDoctorOrder
) {}