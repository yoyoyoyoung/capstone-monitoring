package com.capstone.monitoringserver.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TelegramMappingRepository extends JpaRepository<TelegramMappingEntity, String> {
    Optional<TelegramMappingEntity> findByAgentId(String agentId);

    Optional<TelegramMappingEntity> findFirstByAgentIdContaining(String uuid);
}