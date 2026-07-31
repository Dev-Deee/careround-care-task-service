package com.dayo.careround.caretaskservice.dto.response;

import java.util.UUID;

public record CareProtocolResponse(
        UUID id,
        String name,
        String taskTypeName,
        String cadence,
        int intervalHours
) {}