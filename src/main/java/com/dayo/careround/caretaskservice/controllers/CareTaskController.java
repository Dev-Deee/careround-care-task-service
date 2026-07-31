package com.dayo.careround.caretaskservice.controllers;

import com.dayo.careround.caretaskservice.dto.request.CancelCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.request.CreateCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.request.RefuseCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.response.CareTaskResponse;
import com.dayo.careround.caretaskservice.security.CareRoundPrincipal;
import com.dayo.careround.caretaskservice.services.CareTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/care-tasks")
@RequiredArgsConstructor
public class CareTaskController {

    private final CareTaskService careTaskService;

    @PostMapping
    @PreAuthorize("hasRole('DOCTOR') or hasRole('NURSE')")
    public ResponseEntity<CareTaskResponse> create(
            @Valid @RequestBody CreateCareTaskRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(careTaskService.create(principal.userId(), principal.role(), request));
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<CareTaskResponse> getByPublicId(@PathVariable UUID publicId) {
        return ResponseEntity.ok(careTaskService.getByPublicId(publicId));
    }

    @DeleteMapping("/{publicId}")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('NURSE')")
    public ResponseEntity<Void> retract(
            @PathVariable UUID publicId,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        careTaskService.retract(publicId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{publicId}/start")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<CareTaskResponse> claimOrStart(
            @PathVariable UUID publicId,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.ok(careTaskService.claimOrStart(publicId, principal.userId()));
    }

    @PostMapping("/{publicId}/complete")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<CareTaskResponse> complete(
            @PathVariable UUID publicId,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.ok(careTaskService.complete(publicId, principal.userId()));
    }

    @PostMapping("/{publicId}/cancel")
    @PreAuthorize("hasRole('DOCTOR') or hasRole('NURSE')")
    public ResponseEntity<CareTaskResponse> cancel(
            @PathVariable UUID publicId,
            @Valid @RequestBody CancelCareTaskRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.ok(careTaskService.cancel(publicId, principal.userId(), request));
    }

    @PostMapping("/{publicId}/refuse")
    @PreAuthorize("hasRole('NURSE')")
    public ResponseEntity<CareTaskResponse> refuse(
            @PathVariable UUID publicId,
            @Valid @RequestBody RefuseCareTaskRequest request,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        return ResponseEntity.ok(careTaskService.refuse(publicId, principal.userId(), request));
    }
}