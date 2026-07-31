package com.dayo.careround.caretaskservice.data.repositories;

import com.dayo.careround.caretaskservice.data.models.MedicationDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface MedicationDetailsRepository extends JpaRepository<MedicationDetails, UUID> {
    Optional<MedicationDetails> findByCareTaskId(UUID careTaskId);
}