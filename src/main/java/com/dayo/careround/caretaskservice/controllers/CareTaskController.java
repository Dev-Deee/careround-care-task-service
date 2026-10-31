package com.dayo.careround.caretaskservice.controllers;

import com.dayo.careround.caretaskservice.dto.request.CancelCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.request.CreateCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.request.RefuseCareTaskRequest;
import com.dayo.careround.caretaskservice.dto.response.CareTaskResponse;
import com.dayo.careround.caretaskservice.security.CareRoundPrincipal;
import com.dayo.careround.caretaskservice.services.CareTaskService;
import jakarta.validation.Valid;
import com.dayo.careround.caretaskservice.data.models.enums.CareTaskStatus;
import com.dayo.careround.caretaskservice.dto.response.PageResponse;
import com.dayo.careround.caretaskservice.exception.BadRequestException;
import com.dayo.careround.caretaskservice.services.CareTaskQueryService;
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
    private final CareTaskQueryService careTaskQueryService;

    // Filtered, paged task list. At least one of wardId, patientId, assignedNurseId or mine=true is required.
    // mine=true means "assigned to me" (the caller's id). Sorted by deadline, then createdAt; size 20, max 100.
    // RETRACTED tasks only with status=RETRACTED. overdueDerived=true: deadline passed while PENDING/IN_PROGRESS.
    @GetMapping
    public ResponseEntity<PageResponse<CareTaskResponse>> list(
            @RequestParam(required = false) UUID wardId,
            @RequestParam(required = false) UUID patientId,
            @RequestParam(required = false) UUID assignedNurseId,
            @RequestParam(defaultValue = "false") boolean mine,
            @RequestParam(defaultValue = "false") boolean unassigned,
            @RequestParam(required = false) CareTaskStatus status,
            @RequestParam(required = false) Integer priority,
            @RequestParam(defaultValue = "false") boolean overdueDerived,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @AuthenticationPrincipal CareRoundPrincipal principal) {
        if (mine && assignedNurseId != null && !assignedNurseId.equals(principal.userId())) {
            throw new BadRequestException("mine=true cannot be combined with another assignedNurseId");
        }
        UUID nurse = mine ? principal.userId() : assignedNurseId;
        var filter = new CareTaskQueryService.Filter(wardId, patientId, nurse, unassigned, status, priority, overdueDerived);
        return ResponseEntity.ok(PageResponse.of(careTaskQueryService.list(filter, page, size), careTaskService::toResponse));
    }

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