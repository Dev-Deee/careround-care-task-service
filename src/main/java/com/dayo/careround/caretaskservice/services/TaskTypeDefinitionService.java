package com.dayo.careround.caretaskservice.services;

import com.dayo.careround.caretaskservice.data.models.TaskTypeDefinition;
import com.dayo.careround.caretaskservice.data.repositories.TaskTypeDefinitionRepository;
import com.dayo.careround.caretaskservice.dto.request.CreateTaskTypeDefinitionRequest;
import com.dayo.careround.caretaskservice.dto.response.TaskTypeDefinitionResponse;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskTypeDefinitionService {

    private final TaskTypeDefinitionRepository taskTypeDefinitionRepository;

    @Transactional
    public TaskTypeDefinitionResponse create(CreateTaskTypeDefinitionRequest request) {
        TaskTypeDefinition taskType = new TaskTypeDefinition();
        taskType.setName(request.name());
        taskType.setDefaultPriority(request.defaultPriority());
        taskType.setRequiresDoctorOrder(request.requiresDoctorOrder());
        taskTypeDefinitionRepository.save(taskType);
        return toResponse(taskType);
    }

    public List<TaskTypeDefinitionResponse> getAll() {
        return taskTypeDefinitionRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    private TaskTypeDefinitionResponse toResponse(TaskTypeDefinition taskType) {
        return new TaskTypeDefinitionResponse(
                taskType.getId(), taskType.getName(),
                taskType.getDefaultPriority(), taskType.isRequiresDoctorOrder()
        );
    }
}