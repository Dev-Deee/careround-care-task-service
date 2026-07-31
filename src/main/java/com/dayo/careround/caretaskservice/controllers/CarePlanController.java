package com.dayo.careround.caretaskservice.controllers;

import com.dayo.careround.caretaskservice.dto.request.CreateCarePlanRequest;
import com.dayo.careround.caretaskservice.dto.response.CarePlanResponse;
import com.dayo.careround.caretaskservice.security.CareRoundPrincipal;
import com.dayo.careround.caretaskservice.services.CarePlanService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-plans")
@RequiredArgsConstructor
public class CarePlanController {

    private final CarePlanService carePlanService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR') or hasRole('NURSE')")
    public ResponseEntity<CarePlanResponse> create(
            @Valid @RequestBody CreateCarePlanRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(carePlanService.create(principal.userId(), request));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<CarePlanResponse> getByPublicId(@PathVariable UUID publicId) {
        return ResponseEntity.ok(carePlanService.getByPublicId(publicId));
    }

    @PostMapping("/{publicId}/deactivate")
    @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<CarePlanResponse> deactivate(@PathVariable UUID publicId) {
        return ResponseEntity.ok(carePlanService.deactivate(publicId));
    }
}