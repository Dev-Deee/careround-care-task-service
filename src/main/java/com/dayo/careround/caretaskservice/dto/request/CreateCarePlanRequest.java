package com.dayo.careround.caretaskservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateCarePlanRequest(
        @NotNull UUID patientId,
        @NotNull UUID protocolId
) {}