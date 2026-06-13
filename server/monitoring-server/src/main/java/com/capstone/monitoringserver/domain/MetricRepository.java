package com.capstone.monitoringserver.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MetricRepository extends JpaRepository<MetricEntity, Long> {
    @Query("SELECT DISTINCT m.agentId FROM MetricEntity m")
    List<String> findDistinctAgentIds();

    List<MetricEntity> findTop20ByAgentIdOrderByTimestampDesc(String agentId);

    List<MetricEntity> findTop20ByAgentIdContainingOrderByTimestampDesc(String uuid);

    List<MetricEntity> findByCpuUsageAndAgentIdContainingOrderByTimestampDesc(double cpuUsage, String orgCode);
}