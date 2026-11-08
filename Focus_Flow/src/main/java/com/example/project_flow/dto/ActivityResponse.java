package com.example.project_flow.dto;

import com.example.project_flow.entity.ActivityLog;

import java.time.LocalDateTime;

public record ActivityResponse(Long id, String type, String message, LocalDateTime createdAt) {

    public static ActivityResponse from(ActivityLog log) {
        return new ActivityResponse(log.getId(), log.getType(), log.getMessage(), log.getCreatedAt());
    }
}
