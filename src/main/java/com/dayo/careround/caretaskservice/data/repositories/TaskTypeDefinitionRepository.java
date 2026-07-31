package com.dayo.careround.caretaskservice.data.repositories;

import com.dayo.careround.caretaskservice.data.models.TaskTypeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TaskTypeDefinitionRepository extends JpaRepository<TaskTypeDefinition, UUID> {
}