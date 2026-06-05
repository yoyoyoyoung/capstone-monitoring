package com.capstone.monitoringserver.controller;

import com.capstone.monitoringserver.domain.MetricEntity;
import com.capstone.monitoringserver.domain.MetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class MonitoringRestController {

    private final MetricRepository metricRepository;

    @GetMapping("/api/metrics")
    public List<MetricEntity> getLatestMetrics() {
        // 최근 데이터 20개
        return metricRepository.findAll();
    }

    @GetMapping("/api/agents")
    public List<String> getAgents() {
        return metricRepository.findDistinctAgentIds();
    }

    @GetMapping("/api/metrics/{agentId}")
    public List<MetricEntity> getMetricsByAgent(@PathVariable String agentId) {
        // 🔒 [히스토리 통합 기믹] "새이름(721c1dd2)" 문자열에서 괄호 안의 고유 UUID만 쏙 추출합니다.
        String uuid = agentId;
        if (agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }

        // 닉네임이 바뀐 과거 데이터까지 UUID를 포함(Containing)하는 기준으로 최신 20개를 긁어모아 줍니다.
        return metricRepository.findTop20ByAgentIdContainingOrderByTimestampDesc(uuid);
    }
}