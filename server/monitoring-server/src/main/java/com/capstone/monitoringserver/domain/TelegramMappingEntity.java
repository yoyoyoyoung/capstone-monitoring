package com.capstone.monitoringserver.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "telegram_mapping")
@Getter @Setter
@NoArgsConstructor
public class TelegramMappingEntity {

    @Id
    private String agentId;

    private String chatId;

    public TelegramMappingEntity(String agentId, String chatId) {
        this.agentId = agentId;
        this.chatId = chatId;
    }
}