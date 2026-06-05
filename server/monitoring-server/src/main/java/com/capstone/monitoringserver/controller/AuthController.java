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
    private final MetricRepository metricRepository; // 🔒 [의존성 주입] 지표 조회를 위해 레포지토리 서랍 연결
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 1. 회원가입 API
     */
    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");
        String orgCode = request.get("orgCode");

        // 중복 가입 방지
        if (userRepository.findByUsername(username).isPresent()) {
            return ResponseEntity.badRequest().body("이미 존재하는 아이디입니다.");
        }

        // 비밀번호 정규식 검사
        String passwordPattern = "^(?=.*[A-Z])(?=.*[!@#$%^&*(),.?\":{}|<>]).{8,}$";
        if (password == null || !password.matches(passwordPattern)) {
            return ResponseEntity.badRequest().body("비밀번호는 8자리 이상이며, 대문자와 특수문자를 최소 1개 이상 포함해야 합니다.");
        }

        UserEntity user = new UserEntity();
        user.setUsername(username);
        // [보안 핵심] 비밀번호를 BCrypt로 단방향 암호화하여 저장
        user.setPassword(passwordEncoder.encode(password));
        user.setOrgCode(orgCode.toUpperCase()); // 대문자로 통일
        user.setRole("USER"); // 웹 회원가입은 무조건 일반 사용자(USER) 등급으로 고정

        userRepository.save(user);
        return ResponseEntity.ok("회원가입이 완료되었습니다.");
    }

    /**
     * 2. 로그인 API (인증 성공 시 디지털 신분증 발급)
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> request) {
        String username = request.get("username");
        String password = request.get("password");

        Map<String, Object> response = new HashMap<>();

        // 유저 존재 여부 확인
        UserEntity user = userRepository.findByUsername(username)
                .orElse(null);

        // [보안 핵심] 유저가 없거나 비밀번호 해시값이 다르면 인증 실패
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            response.put("success", false);
            response.put("message", "아이디 또는 비밀번호가 일치하지 않습니다.");
            return ResponseEntity.badRequest().body(response);
        }

        // 로그인 성공 시 세션 대용으로 쓸 간이 토큰 생성 (UUID)
        String mockToken = "TOKEN-" + UUID.randomUUID().toString().substring(0, 8);

        // 리액트가 기억해야 할 필수 비민감 정보만 담아서 반환
        response.put("success", true);
        response.put("token", mockToken);
        response.put("username", user.getUsername());
        response.put("orgCode", user.getOrgCode()); // 리액트가 이 코드를 보고 화면을 격리함
        response.put("role", user.getRole());

        return ResponseEntity.ok(response);
    }

    /**
     * 3. 비밀번호 변경 API (보안 세션 내 가동)
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

        // 변경할 신규 비밀번호도 동일하게 안전 정규식 락 장착
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
     * 4. 실시간 가변형 임계치 설정 API
     */
    @PostMapping("/threshold")
    public ResponseEntity<Map<String, Object>> updateThreshold(@RequestBody Map<String, Object> request) {
        String agentId = (String) request.get("agentId");
        double cpuThreshold = Double.parseDouble(request.get("cpuThreshold").toString());
        double memThreshold = Double.parseDouble(request.get("memThreshold").toString());

        Map<String, Object> response = new HashMap<>();

        // 고유 UUID만 안전하게 추출
        String uuid = agentId;
        if (agentId != null && agentId.contains("(") && agentId.contains(")")) {
            uuid = agentId.substring(agentId.indexOf("(") + 1, agentId.indexOf(")"));
        }

        if (uuid == null || uuid.isEmpty()) {
            response.put("success", false);
            response.put("message", "유효한 에이전트가 선택되지 않았습니다.");
            return ResponseEntity.badRequest().body(response);
        }

        // MonitoringService 메모리 장부에 실시간 다이렉트 주입!
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

        // [보안 설계] 최고 관리자(ADMIN)라면 모든 회사의 장애를 다 긁어오기 위해 빈 문자열("") 대입
        // SQL Containing 특성상 빈 문자열을 넣으면 모든 조직코드가 프리패스로 통과되어 매칭
        String searchOrg = "ADMIN".equalsIgnoreCase(role) ? "" : orgCode.toUpperCase();

        // 정확한 레포지토리 메서드로 호출 체결 (CPU 0.0타겟 조회)
        List<MetricEntity> incidents = metricRepository.findByCpuUsageAndAgentIdContainingOrderByTimestampDesc(0.0, searchOrg);

        // 웹이 느려지는 대시보드 과부하를 막기 위해 최신 10건만 슬라이싱하여 리턴
        if (incidents.size() > 10) {
            incidents = incidents.subList(0, 10);
        }

        return ResponseEntity.ok(incidents);
    }

}