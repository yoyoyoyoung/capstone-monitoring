package com.capstone.monitoringserver.service;

import com.capstone.monitoringserver.MonitoringService;
import com.capstone.monitoringserver.domain.MetricEntity;
import com.capstone.monitoringserver.domain.MetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiOpsService {

    private final MetricRepository metricRepository;

    public Map<String, Object> analyzeServerHealth(String agentId) {
        Map<String, Object> result = new HashMap<>();

        String uuid = agentId;
        if (agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }

        String currentAgentId = MonitoringService.latestNameMap.getOrDefault(uuid, agentId);
        List<MetricEntity> metrics = metricRepository.findTop20ByAgentIdContainingOrderByTimestampDesc(uuid);

        int healthScore = 100;
        StringBuilder rcaReason = new StringBuilder();
        String healthGrade = "🟢 최적 (Excellent)";

        if (metrics == null || metrics.isEmpty()) {
            result.put("agentId", currentAgentId);
            result.put("healthScore", 100);
            result.put("healthGrade", "🔵 데이터 대기 중");
            result.put("rcaAnalysis", "🟢 초기 패킷 동기화가 진행 중입니다.");
            result.put("diskPrediction", "안정 (데이터 부족)");
            result.put("memPrediction", "안정 (데이터 부족)");
            return result;
        }

        MetricEntity latest = metrics.get(0);
        double cpu = latest.getCpuUsage();
        double mem = latest.getMemoryUsage();
        double disk = latest.getDiskUsage();
        double nwDownload = latest.getNetDownloadSpeed();
        double nwUpload = latest.getNetUploadSpeed();
        double latency = latest.getNetworkLatency();
        boolean isJavaAlive = latest.getIsJavaAlive();
        boolean isMysqlAlive = latest.getIsMysqlAlive();

        String currentStatus = MonitoringService.statusMap.getOrDefault(uuid, "INACTIVE");

        if ("DOWN".equals(currentStatus) || (!isJavaAlive && !isMysqlAlive)) {
            healthScore = 0;
            rcaReason.append("[치명적 인프라 다운] 무단 단선, 정전 암전 또는 호스트의 물리적인 크래시 다운타임 상황입니다.");
        } else {
            if (!isJavaAlive) {
                healthScore -= 50;
                rcaReason.append("[서블릿 다운] Java/Spring Boot 웹 서버 어플리케이션이 셧다운되었습니다. ");
            }
            if (!isMysqlAlive) {
                healthScore -= 30;
                rcaReason.append("[영속성 엔진 다운] MySQL 데이터베이스 프로세스 가동이 중지되었습니다. ");
            }
            if (latency >= 300) {
                healthScore -= 15;
                rcaReason.append("[WAN망 회선 병목] 광역 네트워크 통신 응답 지연(RTT)이 지체 중입니다. ");
            } else if (latency >= 100) {
                healthScore -= 7;
            }
            if (cpu >= 85) {
                healthScore -= 15;
                if (nwDownload > 50000 || nwUpload > 50000) {
                    rcaReason.append("[DDoS 외부 공격 의심] 급격한 패킷 인입과 연산 자원 점유가 동반된 과부하 공격 징후입니다. ");
                } else {
                    rcaReason.append("[프로세스 폭주] 특정 스레드의 백그라운드 루프 버그 또는 고부하 연산 독점 상태입니다. ");
                }
            } else if (cpu >= 70) {
                healthScore -= 5;
            }
            if (mem >= 90) {
                healthScore -= 15;
                rcaReason.append("[RAM 자원 고갈] 가용 메모리 임계치 포화로 인해 OOM 가동 위기입니다. ");
            } else if (mem >= 75) {
                healthScore -= 5;
                rcaReason.append("[메모리 누수 의심] GC의 자원 미반환에 따른 지속적인 계단식 Memory Leak 패턴이 의심됩니다. ");
            }
            if (disk >= 95) {
                healthScore -= 20;
                rcaReason.append("[디스크 적재 마비] 저장 공간 포화 완료로 인해 실시간 시스템 로그 및 데이터 적재 불능 상태입니다. ");
            } else if (disk >= 85) {
                healthScore -= 10;
                rcaReason.append("[스토리지 포화 조짐] 디스크 잔여 공간 부족으로 시스템 파일 생성 실패 위험이 상존합니다. ");
            }
        }

        if (healthScore < 0) healthScore = 0;

        if (healthScore <= 40) {
            healthGrade = "🚨 위험";
        } else if (healthScore <= 75) {
            healthGrade = "⚠️ 경고";
        } else if (healthScore <= 90) {
            healthGrade = "🔵 정상";
        }

        if (rcaReason.length() == 0) {
            rcaReason.append("🟢 [정상 가동 중] 모든 시스템 자원 및 핵심 프로세스가 최적 상태를 유지 중입니다.");
        }

        Map<String, Object> diskPrediction = runLinearRegression(metrics, "DISK", disk);
        Map<String, Object> memPrediction = runLinearRegression(metrics, "MEMORY", mem);

        result.put("agentId", currentAgentId);
        result.put("healthScore", healthScore);
        result.put("healthGrade", healthGrade);
        result.put("rcaAnalysis", rcaReason.toString());
        result.put("diskPredictMessage", diskPrediction.get("message"));
        result.put("diskPredictMinutes", diskPrediction.get("minutesLeft"));
        result.put("memPredictMessage", memPrediction.get("message"));
        result.put("memPredictMinutes", memPrediction.get("minutesLeft"));

        return result;
    }

    private Map<String, Object> runLinearRegression(List<MetricEntity> descMetrics, String type, double currentVal) {
        Map<String, Object> predResult = new HashMap<>();
        predResult.put("minutesLeft", -1.0);
        predResult.put("message", "🟢 안정 (사용량 유지 또는 우하향 패턴)");

        if (descMetrics.size() < 5) {
            predResult.put("message", "🔵 분석 중 (시계열 데이터 축적 중)");
            return predResult;
        }

        int n = descMetrics.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumXX = 0;

        MetricEntity baseMetric = descMetrics.get(n - 1);

        for (int i = 0; i < n; i++) {
            MetricEntity m = descMetrics.get(i);

            double x = Duration.between(baseMetric.getTimestamp(), m.getTimestamp()).getSeconds();
            double y = "DISK".equals(type) ? m.getDiskUsage() : m.getMemoryUsage();

            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumXX += x * x;
        }

        double denominator = n * sumXX - sumX * sumX;
        if (denominator == 0) {
            return predResult;
        }

        double a = (n * sumXY - sumX * sumY) / denominator;
        double b = (sumY - a * sumX) / n;

        if (a > 0) {
            double targetX = (100.0 - b) / a;
            MetricEntity latestMetric = descMetrics.get(0);
            double latestX = Duration.between(baseMetric.getTimestamp(), latestMetric.getTimestamp()).getSeconds();

            double secondsLeft = targetX - latestX;

            if (secondsLeft > 0) {
                double minutesLeft = secondsLeft / 60.0;
                predResult.put("minutesLeft", Math.round(minutesLeft * 10) / 10.0);

                if (minutesLeft >= 1440) {
                    double daysLeft = minutesLeft / 1440.0;
                    predResult.put("message", String.format("[전조 예측] 현 사용량 증가 추세 지속 시, 약 [ %.1f일 ] 후 100%% 포화 다운 위기 예상", daysLeft));
                } else {
                    predResult.put("message", String.format("[치명 전조] 현 가동 상태 유지 시, 약 [ %.1f분 ] 후 전 자원 고갈 셧다운 위험", minutesLeft));
                }
            }
        }

        if ("MEMORY".equals(type) && a > 0.3 && currentVal >= 70.0) {
            predResult.put("message", "🚨 [메모리 폭주 예측] 위험 수준의 수직 상승 Leak 패턴 포착. 조기 OOM 대피 권고");
        }

        return predResult;
    }
}