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
import java.util.concurrent.ConcurrentHashMap;

import com.capstone.monitoringserver.service.TelegramService;

@Slf4j
@GrpcService // gRPC 서버 자동 개방
@RequiredArgsConstructor // Repository 주입
public class MonitoringService extends MonitoringServiceGrpc.MonitoringServiceImplBase {

    public static final ConcurrentHashMap<String, LocalDateTime> lastSeenMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> statusMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, String> latestNameMap = new ConcurrentHashMap<>();

    private final MetricRepository metricRepository;
    private final TelegramService telegramService;
    private final TelegramMappingRepository telegramMappingRepository;

    private static final ConcurrentHashMap<String, LocalDateTime> lastAlertTimeMap = new ConcurrentHashMap<>();

    // 🔒 [가변형 임계치 장부] 에이전트(UUID)별로 커스텀 설정된 임계치를 실시간 저장하는 서랍 개설
    public static final ConcurrentHashMap<String, Double> cpuThresholdMap = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Double> memThresholdMap = new ConcurrentHashMap<>();

    @Override
    public void sendMetrics(MetricRequest request, StreamObserver<MetricResponse> responseObserver) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 에이전트 데이터 읽기
        String agentId = request.getAgentId();
        double cpu = request.getCpuUsage();
        double mem = request.getMemoryUsage();

        // 🔒 [버그 박멸 기믹] 닉네임 파편화로 인한 30초 주기 분열을 막기 위해 괄호 안의 고유 UUID만 추출하여 Key로 삼습니다.
        String uuid = agentId;
        if (agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }
        latestNameMap.put(uuid, agentId); // 최신 이름표 업데이트

        // 규격 데이터 필드 추출
        double disk = request.getDiskUsage();
        double netDownload = request.getNetDownloadSpeed();
        double netUpload = request.getNetUploadSpeed();
        double latency = request.getNetworkLatency();
        boolean isJavaAlive = request.getIsJavaAlive();
        boolean isMysqlAlive = request.getIsMysqlAlive();
        boolean isTelegramLinked = telegramMappingRepository.findFirstByAgentIdContaining(uuid).isPresent();

        String alertMessage = null;

        boolean isThresholdBreached = false; // (테스트 대비) 진짜 임계치 돌파 여부 플래그 추가

        String statusMessage = isTelegramLinked ? "SUCCESS_LINKED" : "WARN_NOT_LINKED";

        // 임계치 기본값을 90으로 설정
        double targetCpuThreshold = cpuThresholdMap.getOrDefault(uuid, 90.0);
        double targetMemThreshold = memThresholdMap.getOrDefault(uuid, 90.0);

        // 텔레그램 알림 로직
        if (cpu >= targetCpuThreshold || mem >= targetMemThreshold) {
            alertMessage = String.format(
                    "⚠️ [임계치 초과 알림]\n서버 ID: %s\n• CPU: %.1f%%\n• RAM: %.1f%%\n• 디스크: %.1f%%\n• 네트워크 지연: %.1f ms\n발생 시각: %s\n즉시 확인 필요",
                    agentId, cpu, mem, disk, latency, now
            );
            isThresholdBreached = true;
        }
        // 예외 처리: 에이전트가 정상 종료를 신고한 경우
        else if (cpu == -99.0) {
            statusMap.put(uuid, "INACTIVE"); // 정상 종료 상태로 변경 (감시 대상에서 제외)
            lastSeenMap.remove(uuid);       // 🛑 [버그 박멸] 전송 중지 시 감시 장부에서 흔적을 완벽히 지워 알림 오작동 차단!
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
            String finalAlertMessage = alertMessage; // 람다식 내부 사용을 위한 상용 조치

            telegramMappingRepository.findFirstByAgentIdContaining(uuid).ifPresentOrElse(
                    mapping -> {
                        telegramService.sendMessage(mapping.getChatId(), finalAlertMessage);
                    },
                    () -> {
                        log.warn("⚠️ [알림 발생] 에이전트 [{}]와 동기화된 텔레그램 Chat ID 장부가 없어 발송을 건너뜁니다.", agentId);
                    }
            );

            // 임계치 돌파 알림이 터졌을 때, 리액트 장애 이력창(findByCpuUsage(0.0))에 즉시 나타나도록 CPU를 0.0으로 강제 마킹한 로그를 DB에 한 줄 더 적재
            if (isThresholdBreached) {
                MetricEntity alertIncident = new MetricEntity();
                alertIncident.setAgentId(agentId);
                alertIncident.setCpuUsage(0.0); // 리액트 화면 인식용 트리거 수치
                alertIncident.setMemoryUsage(mem);
                alertIncident.setDiskUsage(disk);
                alertIncident.setNetDownloadSpeed(netDownload);
                alertIncident.setNetUploadSpeed(netUpload);
                alertIncident.setNetworkLatency(latency);
                alertIncident.setIsJavaAlive(isJavaAlive);
                alertIncident.setIsMysqlAlive(isMysqlAlive);
                alertIncident.setTimestamp(LocalDateTime.now());

                metricRepository.save(alertIncident); // 진짜 장애 상황일 때만 한 줄 추가 적재
            }
        }

        // DB 저장 객체 생성 및 저장 (기존 3개 인자 생성자 대신 Setter 방식으로 확장 대응)
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
        entity.setTimestamp(LocalDateTime.now()); // 시간대 저장

        metricRepository.save(entity);

        // 콘솔에 로그 찍기
        log.info("데이터 수신 -> [ID: {}] CPU: {}%, RAM: {}%, 디스크: {}%, Ping: {}ms, Java: {}, MySQL: {}",
                agentId, cpu, mem, disk, latency, isJavaAlive, isMysqlAlive);

        // 에이전트에게 답장 보내기
        MetricResponse response = MetricResponse.newBuilder()
                .setSuccess(true)
                .setMessage(statusMessage)
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    /**
     * 🕵️‍♂️ [10초 주기 무단결근 감시관 알고리즘 - 문구 통일 버전]
     * 최초 장애든, 지속 장애든 유저가 요청하신 동일한 포맷으로 1분마다 알림을 반복 송신합니다.
     */
    @Scheduled(fixedRate = 10000)
    public void checkAgentHeartbeat() {
        LocalDateTime now = LocalDateTime.now();

        lastSeenMap.forEach((uuid, lastSeen) -> {
            // 🔒 최신 이름 데이터 풀에서 실시간 변경된 닉네임 문자열 획득
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