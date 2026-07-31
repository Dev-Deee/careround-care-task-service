package com.dayo.careround.caretaskservice.dto.response;

import java.util.UUID;

public record TaskTypeDefinitionResponse(
        UUID id,
        String name,
        int defaultPriority,
        boolean requiresDoctorOrder
) {}