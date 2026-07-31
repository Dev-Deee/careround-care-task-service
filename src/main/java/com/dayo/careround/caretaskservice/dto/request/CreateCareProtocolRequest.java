package com.dayo.careround.caretaskservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record CreateCareProtocolRequest(
        @NotBlank String name,
        @NotNull UUID taskTypeId,
        @NotBlank String cadence,

        @Positive Integer intervalHours
) {}