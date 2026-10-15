package com.dayo.careround.caretaskservice.dto.request;

import com.dayo.careround.caretaskservice.data.models.enums.MedicationRoute;
import com.dayo.careround.caretaskservice.data.models.enums.MedicationUnit;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AttachMedicationDetailsRequest(
        @NotBlank String drugName,
        @NotNull @Positive BigDecimal amount,
        @NotNull MedicationUnit unit,
        @NotNull MedicationRoute route,
        @NotBlank String frequency,
        String instructions
) {}