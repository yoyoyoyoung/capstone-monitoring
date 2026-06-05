package com.capstone.monitoringserver.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<UserEntity, Long> {
    // 로그인 시 ID로 유저를 찾기 위한 쿼리 메소드
    Optional<UserEntity> findByUsername(String username);
}