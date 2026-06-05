package com.capstone.monitoringserver.controller;

import com.capstone.monitoringserver.service.TelegramService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/telegram")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // 리액트에서 접근 가능하도록 허용
public class TelegramController {

    private final TelegramService telegramService;

    private String bot_Name = "yj_mntring_bot";

    /**
     * 리액트가 특정 에이전트 ID를 던지며 토큰을 달라고 요청하는 API (POST 보안 고도화)
     * 호출 규격: POST /api/telegram/token
     * HTTP Body 데이터 예시: { "agentId": "My-PC(721c1dd2)" }
     */
    @PostMapping("/token") // 1️⃣ 기존 @GetMapping에서 @PostMapping으로 전면 변경
    public ResponseEntity<Map<String, String>> getLinkToken(@RequestBody Map<String, String> requestBody) {

        // URL 파라미터가 아닌, 숨겨진 HTTP Body(JSON)에서 agentId를 안전하게 꺼냅니다.
        String agentId = requestBody.get("agentId");

        // [방어 코드] 혹시나 agentId가 누락되어 들어왔을 경우 예외 처리
        if (agentId == null || agentId.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "agentId가 누락되었습니다.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // 6자리 보안 토큰 생성 및 메모리 적재
        String token = telegramService.generateVerificationToken(agentId);

        // 리액트에게 텔레그램 공식 딥링크 주소와 생숫자를 함께 커스텀 반환
        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("deepLinkUrl", "https://t.me/" + bot_Name + "?start=" + token);

        return ResponseEntity.ok(response);
    }
}