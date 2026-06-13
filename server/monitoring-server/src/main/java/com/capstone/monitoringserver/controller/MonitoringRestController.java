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
        return metricRepository.findAll();
    }

    @GetMapping("/api/agents")
    public List<String> getAgents() {
        return metricRepository.findDistinctAgentIds();
    }

    @GetMapping("/api/metrics/{agentId}")
    public List<MetricEntity> getMetricsByAgent(@PathVariable String agentId) {
        String uuid = agentId;
        if (agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }

        return metricRepository.findTop20ByAgentIdContainingOrderByTimestampDesc(uuid);
    }
}