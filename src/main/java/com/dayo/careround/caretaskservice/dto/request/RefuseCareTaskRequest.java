package com.dayo.careround.caretaskservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RefuseCareTaskRequest(
        @NotBlank String reason
) {}