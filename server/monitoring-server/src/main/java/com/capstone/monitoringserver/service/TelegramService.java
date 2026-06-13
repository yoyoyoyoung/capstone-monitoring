package com.capstone.monitoringserver.service;

import com.capstone.monitoringserver.domain.TelegramMappingEntity;
import com.capstone.monitoringserver.domain.TelegramMappingRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class TelegramService {

    private final TelegramMappingRepository telegramMappingRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    private final String BOT_TOKEN = "8727083989:AAHm9bSPR5bUzTeQhk7rskj8z86VfdF-aJ8";

    private final long serverStartTime = System.currentTimeMillis() / 1000;

    private int lastUpdateId = 0;

    private final Map<String, String> tokenCache = new ConcurrentHashMap<>();

    /**
     * 6자리 랜덤 인증 토큰 생성
     */
    public String generateVerificationToken(String agentId) {
        Random random = new Random();
        String token = String.valueOf(100000 + random.nextInt(900000));

        tokenCache.put(token, agentId);

        new Thread(() -> {
            try {
                Thread.sleep(300000);
                tokenCache.remove(token);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();

        return token;
    }

    /**
     * 특정 대화방으로 메시지를 발송
     */
    public void sendMessage(String chatId, String text) {
        String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendMessage";
        UriComponents builder = UriComponentsBuilder.fromHttpUrl(url)
                .queryParam("chat_id", chatId)
                .queryParam("text", text)
                .build();
        try {
            restTemplate.getForObject(builder.toUri(), String.class);
        } catch (Exception e) {
            System.err.println("텔레그램 발송 실패: " + e.getMessage());
        }
    }

    /**
     * /start [토큰] 폴링
     */
    @Scheduled(fixedDelay = 2000)
    public void pollTelegramUpdates() {
        String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/getUpdates?offset=" + (lastUpdateId + 1);

        try {
            JsonNode root = restTemplate.getForObject(url, JsonNode.class);
            if (root != null && root.has("result")) {
                JsonNode result = root.get("result");

                if (result.isArray()) {
                    for (JsonNode update : result) {
                        int updateId = update.get("update_id").asInt();
                        lastUpdateId = updateId;

                        if (update.has("message")) {
                            JsonNode message = update.get("message");

                            if (message.has("date")) {
                                long messageTime = message.get("date").asLong();
                                if (messageTime < serverStartTime) {
                                    continue;
                                }
                            }

                            if (message.has("text") && message.has("chat")) {
                                String text = message.get("text").asText();
                                String chatId = message.get("chat").get("id").asText();

                                if (text.equals("/start")) {
                                    sendMessage(chatId,
                                            "👋 안녕하세요! 서버 모니터링 알림 봇입니다.\n\n" +
                                                    "현재 이 채팅방은 서버 에이전트와 동기화되지 않은 상태입니다. 연동을 위해 아래 단계를 진행해 주세요:\n\n" +
                                                    "1️⃣ 웹 대시보드 화면에 접속합니다.\n" +
                                                    "2️⃣ API 주소창에서 [deepLinkUrl] 부분을 복사합니다.\n" +
                                                    "3️⃣ 새 브라우저 창에 복사한 주소를 붙여넣어 실행하거나, 발급된 6자리 숫자를 이 채팅방에 입력해 주세요.\n\n" +
                                                    "⚠️ 주의: 보안을 위해 연동 토큰은 발급 후 5분간만 유효합니다."
                                    );
                                    continue;
                                }
                                if (text.startsWith("/start ")) {
                                    String submittedToken = text.replace("/start ", "").strip();

                                    if (tokenCache.containsKey(submittedToken)) {
                                        String matchedAgentId = tokenCache.get(submittedToken);

                                        // 💾 정식 DB 장부에 저장
                                        TelegramMappingEntity mapping = new TelegramMappingEntity(matchedAgentId, chatId);
                                        telegramMappingRepository.save(mapping);

                                        tokenCache.remove(submittedToken);

                                        System.out.println("🔗 [딥링크 동기화 대성공] 에이전트: " + matchedAgentId);
                                        sendMessage(chatId, "🔒 [보안 인증 완료]\n요청하신 에이전트 [" + matchedAgentId + "] 서버와 성공적으로 동기화되었습니다.");
                                    } else {
                                        sendMessage(chatId, "❌ 인증 실패\n토큰이 유효하지 않거나 만료되었습니다.");
                                    }
                                }

                                if (text.trim().startsWith("/status")) {
                                    telegramMappingRepository.findAll().stream()
                                            .filter(mapping -> chatId.equals(mapping.getChatId()))
                                            .findFirst()
                                            .ifPresentOrElse(
                                                    mapping -> {
                                                        String agentId = mapping.getAgentId();

                                                        String uuid = agentId;
                                                        if (agentId.contains("(") && agentId.contains(")")) {
                                                            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
                                                        }

                                                        java.time.LocalDateTime lastSeen = com.capstone.monitoringserver.MonitoringService.lastSeenMap.get(uuid);
                                                        String currentStatus = com.capstone.monitoringserver.MonitoringService.statusMap.getOrDefault(uuid, "INACTIVE");
                                                        String currentName = com.capstone.monitoringserver.MonitoringService.latestNameMap.getOrDefault(uuid, agentId);

                                                        String statusEmoji;
                                                        if ("ACTIVE".equals(currentStatus)) {
                                                            statusEmoji = "🟢 정상 가동 중 (Active)";
                                                        } else if ("DOWN".equals(currentStatus)) {
                                                            statusEmoji = "🚨 응답 없음 (Critical Down)";
                                                        } else {
                                                            statusEmoji = "⚪ 비활성화 상태 (Inactive)";
                                                        }

                                                        String replyMessage = String.format(
                                                                "🖥️ [실시간 원격 인프라 관제 조회]\n\n" +
                                                                        "• 연동 서버명: %s\n" +
                                                                        "• 현재 가동 상태: %s\n" +
                                                                        "• 최종 신호 수신: %s\n\n" +
                                                                        "ℹ️ 본 정보는 중앙 관제탑 세션 메모리 장부로부터 파싱된 실시간 지표입니다.",
                                                                currentName,
                                                                statusEmoji,
                                                                lastSeen != null ? lastSeen.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : "기록 없음"
                                                        );

                                                        sendMessage(chatId, replyMessage);
                                                    },
                                                    () -> {
                                                        sendMessage(chatId, "❌ 현재 이 텔레그램 채팅방과 연동된 인프라 서버 장부가 존재하지 않습니다.\n웹 대시보드 로그인 후 [🔔 텔레그램 알림 활성화]를 통해 먼저 다리를 놓아주세요.");
                                                    }
                                            );
                                }

                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("텔레그램 업데이트 확인 실패: " + e.getMessage());
        }
    }
}