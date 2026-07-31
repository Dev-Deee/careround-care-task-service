package com.dayo.careround.caretaskservice.dto.request;

import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record CreateCareTaskRequest(
        @NotNull UUID patientId,
        @NotNull UUID taskTypeId,
        @NotNull Integer priority,
        @NotNull CareTaskOrigin origin,
        UUID carePlanId,
        UUID assignedNurseId, // nullable — leave unassigned to make it poolable
        @NotNull Instant windowStart,
        @NotNull Instant deadline
) {}