package com.dayo.careround.caretaskservice.dto.response;

import com.dayo.careround.caretaskservice.data.models.enums.MedicationRoute;
import com.dayo.careround.caretaskservice.data.models.enums.MedicationUnit;

import java.math.BigDecimal;
import java.util.UUID;

public record MedicationDetailsResponse(
        UUID id,
        UUID careTaskId,
        String drugName,
        BigDecimal amount,
        MedicationUnit unit,
        MedicationRoute route,
        String frequency,
        String instructions,
        BigDecimal actualAmountGiven
) {}