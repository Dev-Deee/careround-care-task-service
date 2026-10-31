package com.dayo.careround.caretaskservice.controllers;

import com.dayo.careround.caretaskservice.dto.request.AttachMedicationDetailsRequest;
import com.dayo.careround.caretaskservice.dto.response.MedicationDetailsResponse;
import com.dayo.careround.caretaskservice.services.MedicationDetailsService;
import jakarta.validation.Valid;
import com.dayo.careround.caretaskservice.security.CareRoundPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-tasks/{careTaskId}/medication-details")
@RequiredArgsConstructor
public class MedicationDetailsController {

    private final MedicationDetailsService medicationDetailsService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<MedicationDetailsResponse> attach(
            @PathVariable UUID careTaskId,
            @Valid @RequestBody AttachMedicationDetailsRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED).body(medicationDetailsService.attach(careTaskId, principal.userId(), request));
    }

    @GetMapping
    public ResponseEntity<MedicationDetailsResponse> getByCareTask(@PathVariable UUID careTaskId) {
        return ResponseEntity.ok(medicationDetailsService.getByCareTask(careTaskId));
    }
}