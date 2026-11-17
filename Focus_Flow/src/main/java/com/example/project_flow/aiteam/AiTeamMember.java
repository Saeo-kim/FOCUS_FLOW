package com.example.project_flow.aiteam;

import com.example.project_flow.admin.account.Department;

import java.util.List;

/**
 * One AI teammate's seat: who it is, where it sits and what it is responsible for.
 * {@code integration} names the outside application that does the work ("owners-office"),
 * or is null for a teammate that lives inside FOCUS FLOW.
 */
public record AiTeamMember(
        String key,
        String username,
        String displayName,
        String title,
        Department department,
        String avatar,
        String avatarColor,
        List<String> responsibilities,
        String integration) {
}
