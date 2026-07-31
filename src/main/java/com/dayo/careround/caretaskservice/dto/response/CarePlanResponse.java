package com.dayo.careround.caretaskservice.dto.response;

import java.util.UUID;

public record CarePlanResponse(
        UUID publicId,
        UUID patientId,
        String protocolName,
        String cadence,
        int intervalHours,
        boolean active
) {}