package com.dayo.careround.caretaskservice.data.repositories;

import com.dayo.careround.caretaskservice.data.models.CarePlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CarePlanRepository extends JpaRepository<CarePlan, UUID> {
    Optional<CarePlan> findByPublicId(UUID publicId);
    List<CarePlan> findByPatientIdAndActive(UUID patientId, boolean active);
    List<CarePlan> findByActive(boolean active);
}