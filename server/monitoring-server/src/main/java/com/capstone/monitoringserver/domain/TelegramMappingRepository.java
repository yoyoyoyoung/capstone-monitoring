package com.capstone.monitoringserver.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface TelegramMappingRepository extends JpaRepository<TelegramMappingEntity, String> {
    // 에이전트 ID로 텔레그램 Chat ID를 찾기 위한 쿼리 메소드
    Optional<TelegramMappingEntity> findByAgentId(String agentId);

    // 🔒 [버그 박멸] 닉네임이 변경되어 여러 장부가 발견되더라도 에러 없이 딱 1개(LIMIT 1)만 안전하게 긁어오는 메서드로 진화
    Optional<TelegramMappingEntity> findFirstByAgentIdContaining(String uuid);
}