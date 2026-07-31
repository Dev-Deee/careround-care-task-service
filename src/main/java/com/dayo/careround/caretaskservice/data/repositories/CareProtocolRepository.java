package com.dayo.careround.caretaskservice.data.repositories;

import com.dayo.careround.caretaskservice.data.models.CareProtocol;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CareProtocolRepository extends JpaRepository<CareProtocol, UUID> {
}