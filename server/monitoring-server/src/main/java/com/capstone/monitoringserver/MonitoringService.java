package com.capstone.monitoringserver;

import com.capstone.monitoring.*;
import com.capstone.monitoringserver.domain.MetricEntity;
import com.capstone.monitoringserver.domain.MetricRepository;
import com.capstone.monitoringserver.domain.TelegramMappingRepository;

import com.capstone.monitoring.MonitoringServiceGrpc;
import com.capstone.monitoring.MetricRequest;
import com.capstone.monitoring.MetricResponse;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import net.devh.boot.grpc.server.service.GrpcService;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.scheduling.annotation.Scheduled;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.capstone.monitoringserver.service.TelegramService;

@Slf4j
@GrpcService
@RequiredArgsConstructor
public class MonitoringService extends MonitoringServiceGrpc.MonitoringServiceImplBase {

    public static final ConcurrentHashMap<String, LocalDateTime> lastSeenMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> latestNameMap = new ConcurrentHashMap<>();

    private final MetricRepository metricRepository;
    private final TelegramService telegramService;
    private final TelegramMappingRepository telegramMappingRepository;

    private static final ConcurrentHashMap<String, LocalDateTime> lastAlertTimeMap = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, LocalDateTime> lastAiAlertTimeMap = new ConcurrentHashMap<>();

    public static final ConcurrentHashMap<String, Double> cpuThresholdMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Double> memThresholdMap = new ConcurrentHashMap<>();

    private final com.capstone.monitoringserver.service.AiOpsService aiOpsService;

    @Override
    public void sendMetrics(MetricRequest request, StreamObserver<MetricResponse> responseObserver) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String agentId = request.getAgentId();
        double cpu = request.getCpuUsage();
        double mem = request.getMemoryUsage();

        String uuid = agentId;
        if (agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }
        latestNameMap.put(uuid, agentId);

        double disk = request.getDiskUsage();
        double netDownload = request.getNetDownloadSpeed();
        double netUpload = request.getNetUploadSpeed();
        double latency = request.getNetworkLatency();
        boolean isJavaAlive = request.getIsJavaAlive();
        boolean isMysqlAlive = request.getIsMysqlAlive();
        boolean isTelegramLinked = telegramMappingRepository.findFirstByAgentIdContaining(uuid).isPresent();

        String alertMessage = null;

        boolean isThresholdBreached = false; // 테스트

        String statusMessage = isTelegramLinked ? "SUCCESS_LINKED" : "WARN_NOT_LINKED";

        // 임계치 기본값
        double targetCpuThreshold = cpuThresholdMap.getOrDefault(uuid, 90.0);
        double targetMemThreshold = memThresholdMap.getOrDefault(uuid, 90.0);

        if (cpu >= targetCpuThreshold || mem >= targetMemThreshold) {
            alertMessage = String.format(
                    "⚠️ [임계치 초과 알림]\n서버 ID: %s\n• CPU: %.1f%%\n• RAM: %.1f%%\n• 디스크: %.1f%%\n• 네트워크 지연: %.1f ms\n발생 시각: %s\n즉시 확인 필요",
                    agentId, cpu, mem, disk, latency, now
            );
            isThresholdBreached = true;
        }
        else if (cpu == -99.0) {
            statusMap.put(uuid, "INACTIVE");
            lastSeenMap.remove(uuid);
            lastAlertTimeMap.remove(uuid);
            log.info("👋 에이전트 [{}] 정상 종료 신고 수신. 장애 감시 대상에서 안전하게 제외합니다.", agentId);

            MetricResponse response = MetricResponse.newBuilder().setSuccess(true).setMessage("GOODBYE").build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }
        // 테스트 용!!! 테스트 이후 삭제 요망!!!
        else if (cpu <= 20.0 || mem <= 20.0) {
            alertMessage = String.format(
                    "⚠️ [테스트]\n서버 ID: %s\n• CPU: %.1f%%\n• RAM: %.1f%%\n• 디스크: %.1f%%\n• ⬇️다운로드 %.1f KB/s | ⬆️업로드: %.1f KB/s\n• 자바 상태: %s | DB 상태: %s",
                    agentId, cpu, mem, disk, netDownload, netUpload,
                    isJavaAlive ? "🟢가동중" : "🔴중지됨", isMysqlAlive ? "🟢가동중" : "🔴중지됨"
            );
        }

        lastSeenMap.put(uuid, LocalDateTime.now());
        statusMap.put(uuid, "ACTIVE");

        if (alertMessage != null) {
            String finalAlertMessage = alertMessage;

            telegramMappingRepository.findFirstByAgentIdContaining(uuid).ifPresentOrElse(
                    mapping -> {
                        telegramService.sendMessage(mapping.getChatId(), finalAlertMessage);
                    },
                    () -> {
                        log.warn("⚠️ [알림 발생] 에이전트 [{}]와 동기화된 텔레그램 Chat ID 장부가 없어 발송을 건너뜁니다.", agentId);
                    }
            );

            if (isThresholdBreached) {
                MetricEntity alertIncident = new MetricEntity();
                alertIncident.setAgentId(agentId);
                alertIncident.setCpuUsage(0.0);
                alertIncident.setMemoryUsage(mem);
                alertIncident.setDiskUsage(disk);
                alertIncident.setNetDownloadSpeed(netDownload);
                alertIncident.setNetUploadSpeed(netUpload);
                alertIncident.setNetworkLatency(latency);
                alertIncident.setIsJavaAlive(isJavaAlive);
                alertIncident.setIsMysqlAlive(isMysqlAlive);
                alertIncident.setTimestamp(LocalDateTime.now());

                metricRepository.save(alertIncident);
            }
        }

        MetricEntity entity = new MetricEntity();
        entity.setAgentId(agentId);
        entity.setCpuUsage(cpu);
        entity.setMemoryUsage(mem);
        entity.setDiskUsage(disk);
        entity.setNetDownloadSpeed(netDownload);
        entity.setNetUploadSpeed(netUpload);
        entity.setNetworkLatency(latency);
        entity.setIsJavaAlive(isJavaAlive);
        entity.setIsMysqlAlive(isMysqlAlive);
        entity.setTimestamp(LocalDateTime.now());

        metricRepository.save(entity);

        try {

            Map<String, Object> aiData = aiOpsService.analyzeServerHealth(agentId);
            double diskMins = (double) aiData.getOrDefault("diskPredictMinutes", -1.0);
            double memMins = (double) aiData.getOrDefault("memPredictMinutes", -1.0);

            LocalDateTime nowTime = LocalDateTime.now();
            if (lastAiAlertTimeMap.get(uuid) == null || lastAiAlertTimeMap.get(uuid).isBefore(nowTime.minusMinutes(10))) {

                String aiAlertMessage = null;
                if (diskMins > 0 && diskMins <= 360) {
                    aiAlertMessage = String.format("[AI 장애 전조 경보 - 스토리지 포화]\n서버 ID: %s\n%s\n즉시 디스크 볼륨 확장 및 로그 소거 조치가 권장됩니다.", agentId, aiData.get("diskPredictMessage"));
                } else if (memMins > 0 && memMins <= 60) {
                    aiAlertMessage = String.format("[AI 장애 전조 경보 - OOM 크래시 위기]\n서버 ID: %s\n%s\n메모리 누수 프로세스 강제 킬 및 세션 점검이 필수적입니다.", agentId, aiData.get("memPredictMessage"));
                }

                if (aiAlertMessage != null) {
                    String finalAiMsg = aiAlertMessage;
                    lastAiAlertTimeMap.put(uuid, nowTime);
                    telegramMappingRepository.findFirstByAgentIdContaining(uuid).ifPresent(mapping -> {
                        telegramService.sendMessage(mapping.getChatId(), finalAiMsg);
                    });
                }
            }
        } catch (Exception e) {
            log.debug("AI 전조 텔레그램 중계 대기 중...");
        }

        log.info("데이터 수신 -> [ID: {}] CPU: {}%, RAM: {}%, 디스크: {}%, Ping: {}ms, Java: {}, MySQL: {}",
                agentId, cpu, mem, disk, latency, isJavaAlive, isMysqlAlive);

        MetricResponse response = MetricResponse.newBuilder()
                .setSuccess(true)
                .setMessage(statusMessage)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * 1분 간격 알림 송신
     */
    @Scheduled(fixedRate = 10000)
    public void checkAgentHeartbeat() {
        LocalDateTime now = LocalDateTime.now();

        lastSeenMap.forEach((uuid, lastSeen) -> {
            String currentAgentId = latestNameMap.getOrDefault(uuid, uuid);

            boolean isJustDisconnected = "ACTIVE".equals(statusMap.get(uuid)) && lastSeen.isBefore(now.minusMinutes(1));
            boolean isStillDownAndNeedReminder = "DOWN".equals(statusMap.get(uuid)) &&
                    (lastAlertTimeMap.get(uuid) == null || lastAlertTimeMap.get(uuid).isBefore(now.minusMinutes(1)));

            if (isJustDisconnected || isStillDownAndNeedReminder) {
                if (isJustDisconnected) {
                    statusMap.put(uuid, "DOWN");
                }

                lastAlertTimeMap.put(uuid, now);
                log.error("🚨 [장애 상태 감지] 에이전트 [{}] 응답 없음!", currentAgentId);

                telegramMappingRepository.findFirstByAgentIdContaining(uuid).ifPresent(mapping -> {
                    String errorMessage = String.format(
                            "🚨 [인프라 장애 알림]\n서버 ID: %s\n• 현재 상태: 응답 없음 (서버 다운 의심)\n• 탐지 시각: %s\n\n⚠️ 에이전트 상태가 복구될 때까지 1분 주기로 이 알림이 지속됩니다. 즉시 확인 바랍니다.",
                            currentAgentId, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    );
                    telegramService.sendMessage(mapping.getChatId(), errorMessage);
                });

                if (isJustDisconnected) {
                    MetricEntity deadEntity = new MetricEntity();
                    deadEntity.setAgentId(currentAgentId);
                    deadEntity.setCpuUsage(0.0); deadEntity.setMemoryUsage(0.0); deadEntity.setDiskUsage(0.0);
                    deadEntity.setIsJavaAlive(false); deadEntity.setIsMysqlAlive(false);
                    deadEntity.setTimestamp(now);
                    metricRepository.save(deadEntity);
                }
            }
        });
    }

}