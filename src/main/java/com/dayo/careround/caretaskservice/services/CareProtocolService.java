package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.CareProtocol;
import com.dayo.careround.caretaskservice.data.models.TaskTypeDefinition;
import com.dayo.careround.caretaskservice.data.repositories.CareProtocolRepository;
import com.dayo.careround.caretaskservice.data.repositories.TaskTypeDefinitionRepository;
import com.dayo.careround.caretaskservice.dto.request.CreateCareProtocolRequest;
import com.dayo.careround.caretaskservice.dto.response.CareProtocolResponse;
import com.dayo.careround.caretaskservice.exception.TaskTypeNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CareProtocolService {

    private final CareProtocolRepository careProtocolRepository;
    private final TaskTypeDefinitionRepository taskTypeDefinitionRepository;

    @Transactional
    public CareProtocolResponse create(CreateCareProtocolRequest request) {
        TaskTypeDefinition taskType = taskTypeDefinitionRepository.findById(request.taskTypeId())
                .orElseThrow(() -> new TaskTypeNotFoundException("No task type with that id"));

        CareProtocol protocol = new CareProtocol();
        protocol.setName(request.name());
        protocol.setTaskType(taskType);
        protocol.setCadence(request.cadence());
        protocol.setIntervalHours(request.intervalHours());

        careProtocolRepository.save(protocol);
        return toResponse(protocol);
    }
    @Transactional
    public List<CareProtocolResponse> getAll() {
        return careProtocolRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private CareProtocolResponse toResponse(CareProtocol protocol) {
        return new CareProtocolResponse(
                protocol.getId(), protocol.getName(), protocol.getTaskType().getName(),
                protocol.getCadence(), protocol.getIntervalHours()
        );
    }
}