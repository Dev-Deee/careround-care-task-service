package com.dayo.careround.caretaskservice.controllers;

import com.dayo.careround.caretaskservice.dto.request.CreateCareProtocolRequest;
import com.dayo.careround.caretaskservice.dto.response.CareProtocolResponse;
import com.dayo.careround.caretaskservice.services.CareProtocolService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/care-protocols")
@RequiredArgsConstructor
public class CareProtocolController {

    private final CareProtocolService careProtocolService;

    @PostMapping
    public ResponseEntity<CareProtocolResponse> create(@Valid @RequestBody CreateCareProtocolRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(careProtocolService.create(request));
    }

    @GetMapping
    public ResponseEntity<List<CareProtocolResponse>> getAll() {
        return ResponseEntity.ok(careProtocolService.getAll());
    }
}