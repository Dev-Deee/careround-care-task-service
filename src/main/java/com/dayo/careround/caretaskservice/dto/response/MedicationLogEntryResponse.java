package com.dayo.careround.caretaskservice.dto.response;

import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.data.models.enums.MedicationRoute;
import com.dayo.careround.caretaskservice.data.models.enums.MedicationUnit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

// One medication order in the prescriber's log (FR-12.4): what was ordered and whether/when/by whom it was given.
public record MedicationLogEntryResponse(
        UUID careTaskPublicId,
        UUID patientId,
        UUID wardId,
        String drugName,
        BigDecimal amount,
        MedicationUnit unit,
        MedicationRoute route,
        String frequency,
        String instructions,
        BigDecimal actualAmountGiven,
        CareTaskStatus status,
        Instant windowStart,
        Instant deadline,
        Instant createdAt,
        Instant administeredAt,     // completedAt; null unless COMPLETED
        UUID administeredByNurseId, // the assigned nurse who completed it; null unless COMPLETED
        boolean overdueDerived
) {}
