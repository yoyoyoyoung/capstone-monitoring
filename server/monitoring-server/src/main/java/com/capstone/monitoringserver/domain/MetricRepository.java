package com.capstone.monitoringserver.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MetricRepository extends JpaRepository<MetricEntity, Long> {
    // 중복 제거된 에이전트 ID 목록 가져오기
    @Query("SELECT DISTINCT m.agentId FROM MetricEntity m")
    List<String> findDistinctAgentIds();

    // 특정 에이전트의 최신 데이터 20개만 가져오기 (기존 exact 매칭용)
    List<MetricEntity> findTop20ByAgentIdOrderByTimestampDesc(String agentId);

    // 🔒 [버그 박멸 기믹] 닉네임이 변경되어도 괄호 안의 고유 UUID를 포함(Containing)하는 과거 이력 데이터까지 최신순으로 20개 가져오기
    List<MetricEntity> findTop20ByAgentIdContainingOrderByTimestampDesc(String uuid);

    // 🔒 [장애 이력 방어 기믹] 특정 조직코드가 포함된 에이전트 중, CPU가 0.0인 장애 데이터만 최신순으로 정렬하여 반환
    List<MetricEntity> findByCpuUsageAndAgentIdContainingOrderByTimestampDesc(double cpuUsage, String orgCode);
}