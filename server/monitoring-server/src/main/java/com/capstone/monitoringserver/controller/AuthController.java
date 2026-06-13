package com.capstone.monitoringserver.controller;

import com.capstone.monitoringserver.domain.UserEntity;
import com.capstone.monitoringserver.domain.UserRepository;
import com.capstone.monitoringserver.domain.MetricEntity; // 🔒 장애 지표 엔티티 임포트 유지
import com.capstone.monitoringserver.domain.MetricRepository; // 🔒 장애 지표 레포지토리 임포트 유지

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List; // 🔒 List 컬렉션 임포트 유지
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class AuthController {

    private final UserRepository userRepository;
    private final MetricRepository metricRepository;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 회원가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String orgCode = request.get("orgCode");

        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body("이미 존재하는 아이디입니다.");
        }

        String passwordPattern = "^(?=.*[A-Z])(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,}$";
        if (password == null || !password.matches(passwordPattern)) {
            return ResponseEntity.badRequest().body("비밀번호는 8자리 이상이며, 대문자와 특수문자를 최소 1개 이상 포함해야 합니다.");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setOrgCode(orgCode.toUpperCase());
        user.setRole("USER");

        userRepository.save(user);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    /**
     * 로그인 API
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        Map<String, Object> response = new HashMap<>();

        // 유저 존재 여부 확인
        UserEntity user = userRepository.findByUsername(username)
                .orElse(null);

        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            response.put("success", false);
            response.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
            return ResponseEntity.badRequest().body(response);
        }

        String mockToken = "TOKEN-" + UUID.randomUUID().toString().substring(0, 8);

        response.put("success", true);
        response.put("token", mockToken);
        response.put("username", user.getUsername());
        response.put("orgCode", user.getOrgCode());
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
    }

    /**
     * 비밀번호 변경 API
     */
    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String currentPassword = request.get("currentPassword");
        String newPassword = request.get("newPassword");

        Map<String, Object> response = new HashMap<>();

        UserEntity user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            response.put("success", false);
            response.put("message", "현재 비밀번호가 일치하지 않습니다.");
            return ResponseEntity.badRequest().body(response);
        }

        String passwordPattern = "^(?=.*[A-Z])(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,}$";
        if (newPassword == null || !newPassword.matches(passwordPattern)) {
            response.put("success", false);
            response.put("message", "새 비밀번호는 8자리 이상이며, 대문자와 특수문자를 포함해야 합니다.");
            return ResponseEntity.badRequest().body(response);
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        response.put("success", true);
        response.put("message", "비밀번호가 성공적으로 변경되었습니다.");
        return ResponseEntity.ok(response);
    }

    /**
     * 실시간 가변형 임계치 설정 API
     */
    @PostMapping("/threshold")
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestBody Map<String, Object> request) {
        String agentId = (String) request.get("agentId");
        double cpuThreshold = Double.parseDouble(request.get("cpuThreshold").toString());
        double memThreshold = Double.parseDouble(request.get("memThreshold").toString());

        Map<String, Object> response = new HashMap<>();

        String uuid = agentId;
        if (agentId != null && agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }

        if (uuid == null || uuid.isEmpty()) {
            response.put("success", false);
            response.put("message", "유효한 에이전트가 선택되지 않았습니다.");
            return ResponseEntity.badRequest().body(response);
        }

        com.capstone.monitoringserver.MonitoringService.cpuThresholdMap.put(uuid, cpuThreshold);
        com.capstone.monitoringserver.MonitoringService.memThresholdMap.put(uuid, memThreshold);

        response.put("success", true);
        response.put("message", "임계치가 서버 및 텔레그램 엔진에 실시간 반영되었습니다.");
        return ResponseEntity.ok(response);
    }

    /**
     * 과거 인프라 장애 이력 조회 API
     */
    @GetMapping("/incidents")
    public ResponseEntity<List<MetricEntity>> getRecentIncidents(
            @RequestParam String orgCode,
            @RequestParam String role) {

        String searchOrg = "ADMIN".equalsIgnoreCase(role) ? "" : orgCode.toUpperCase();

        List<MetricEntity> incidents = metricRepository.findByCpuUsageAndAgentIdContainingOrderByTimestampDesc(0.0, searchOrg);

        if (incidents.size() > 10) {
            incidents = incidents.subList(0, 10);
        }

        return ResponseEntity.ok(incidents);
    }

}