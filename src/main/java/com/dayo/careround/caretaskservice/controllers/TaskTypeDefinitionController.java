package com.dayo.careround.caretaskservice.controllers;

import com.dayo.careround.caretaskservice.dto.request.CreateTaskTypeDefinitionRequest;
import com.dayo.careround.caretaskservice.dto.response.TaskTypeDefinitionResponse;
import com.dayo.careround.caretaskservice.services.TaskTypeDefinitionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/task-type-definitions")
@RequiredArgsConstructor
public class TaskTypeDefinitionController {

    private final TaskTypeDefinitionService taskTypeDefinitionService;

    // Hospital configuration: admin with MANAGE_SYSTEM_CONFIG.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('PERM_MANAGE_SYSTEM_CONFIG')")
    public ResponseEntity<TaskTypeDefinitionResponse> create(@Valid @RequestBody CreateTaskTypeDefinitionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskTypeDefinitionService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<TaskTypeDefinitionResponse>> getAll() {
        return ResponseEntity.ok(taskTypeDefinitionService.getAll());
    }
}