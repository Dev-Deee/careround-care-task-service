package com.dayo.careround.caretaskservice.dto.response;

import com.dayo.careround.caretaskservice.data.models.enums.CareTaskOrigin;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;

import java.time.Instant;
import java.util.UUID;

public record CareTaskResponse(
        UUID publicId,
        UUID patientId,
        String taskTypeName,
        int priority,
        CareTaskOrigin origin,
        UUID carePlanId,
        UUID assignedNurseId,
        Instant windowStart,
        Instant deadline,
        CareTaskStatus status,
        UUID createdBy,
        Instant undoWindowExpiresAt
) {}