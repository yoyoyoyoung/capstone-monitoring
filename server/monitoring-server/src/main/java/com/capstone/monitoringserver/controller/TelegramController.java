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
@CrossOrigin(origins = "*")
public class TelegramController {

    private final TelegramService telegramService;

    private String bot_Name = "yj_mntring_bot";

    /**
     * 토큰 요청  API
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> getLinkToken(@RequestBody Map<String, String> requestBody) {

        String agentId = requestBody.get("agentId");

        if (agentId == null || agentId.isEmpty()) {
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "agentId가 누락되었습니다.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        String token = telegramService.generateVerificationToken(agentId);

        Map<String, String> response = new HashMap<>();
        response.put("token", token);
        response.put("deepLinkUrl", "https://t.me/" + bot_Name + "?start=" + token);

        return ResponseEntity.ok(response);
    }
}