package com.capstone.monitoringserver.controller;

import com.capstone.monitoringserver.service.AiOpsService;
import com.capstone.monitoringserver.service.AiReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AiOpsController {

    private final AiOpsService aiOpsService;
    private final AiReportService aiReportService;

    @GetMapping("/analyze")
    public ResponseEntity<Map<String, Object>> getLiveIntelligence(@RequestParam String agentId) {
        Map<String, Object> analysisResult = aiOpsService.analyzeServerHealth(agentId);
        return ResponseEntity.ok(analysisResult);
    }

    @GetMapping("/report")
    public ResponseEntity<Map<String, Object>> getAiReport(@RequestParam String agentId) {
        Map<String, Object> reportResult = aiReportService.generateDailyReport(agentId);
        return ResponseEntity.ok(reportResult);
    }
}