package com.capstone.monitoringserver.domain;

import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
@Getter
@Setter
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String orgCode; // 멀티테넌시 증명용 핵심 필드

    @Column(nullable = false)
    private String role; // USER 또는 ADMIN
}