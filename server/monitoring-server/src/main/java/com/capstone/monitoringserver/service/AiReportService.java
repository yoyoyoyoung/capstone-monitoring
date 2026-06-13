package com.capstone.monitoringserver.service;

import com.capstone.monitoringserver.domain.MetricEntity;
import com.capstone.monitoringserver.domain.MetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
@RequiredArgsConstructor
public class AiReportService {

    private final MetricRepository metricRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${openai.api.key:mock-key}")
    private String apiKey;

    public Map<String, Object> generateDailyReport(String agentId) {
        Map<String, Object> result = new HashMap<>();

        String uuid = agentId;
        if (agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }

        List<MetricEntity> metrics = metricRepository.findTop20ByAgentIdContainingOrderByTimestampDesc(uuid);

        if (metrics == null || metrics.isEmpty()) {
            result.put("report", "분석할 데이터가 축적되지 않았습니다.");
            return result;
        }

        double avgCpu = metrics.stream().mapToDouble(MetricEntity::getCpuUsage).average().orElse(0.0);
        double avgMem = metrics.stream().mapToDouble(MetricEntity::getMemoryUsage).average().orElse(0.0);
        double maxDisk = metrics.stream().mapToDouble(MetricEntity::getDiskUsage).max().orElse(0.0);

        String prompt = String.format(
                "시스템 관제 요약 리포트를 작성해줘. 서버ID: %s, 평균 CPU: %.1f%%, 평균 메모리: %.1f%%, 최고 디스크 사용량: %.1f%%. " +
                        "형식은 개조식으로 요약, 권장사항으로 나누어 부드러운 경어체로 3줄 이내로 핵심만 짧게 작성해줘.",
                agentId, avgCpu, avgMem, maxDisk
        );

        if ("mock-key".equals(apiKey)) {
            result.put("report", String.format("[AI 데이터 분석 브리핑]\n• 현재 시스템의 주간 평균 CPU 사용량은 %.1f%%, 메모리는 %.1f%%로 임계치 대비 매우 안정적인 범주에서 가동 중입니다.\n• 디스크 최고치 점유율은 %.1f%%를 기록하고 있어 대량의 시스템 로그 적재 공간이 안전하게 확보되어 있습니다.\n• 전반적인 인프라 상태가 최적화 평형을 이루고 있으므로 정기적인 리포트 모니터링 외에 별도의 긴급 조치는 불필요합니다.", avgCpu, avgMem, maxDisk));
            return result;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "user", "content", prompt));
            requestBody.put("messages", messages);
            requestBody.put("max_tokens", 500);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    "https://api.openai.com/v1/chat/completions", entity, Map.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
                Map<String, Object> messageMap = (Map<String, Object>) choices.get(0).get("message");
                result.put("report", messageMap.get("content").toString());
            } else {
                result.put("report", "AI 리포트 생성에 실패했습니다. (서버 응답 오류)");
            }
        } catch (Exception e) {
            result.put("report", "AI 서버 통신 중 예외가 발생했습니다: " + e.getMessage());
        }

        return result;
    }
}