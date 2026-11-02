package com.dayo.careround.caretaskservice.dto.response;

import com.dayo.careround.caretaskservice.data.models.enums.PriorityLevel;
import java.util.UUID;

public record TaskTypeDefinitionResponse(
        UUID id,
        String name,
        int defaultPriority, // deprecated: use defaultPriorityLevel
        boolean requiresDoctorOrder,
        PriorityLevel defaultPriorityLevel
) {}