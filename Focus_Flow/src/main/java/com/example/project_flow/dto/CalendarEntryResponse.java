package com.example.project_flow.dto;

import com.example.project_flow.entity.Priority;

import java.time.LocalDate;

/**
 * @param type      TASK_DUE, TASK_SCHEDULED or PROJECT_DUE
 * @param priority  null for project entries
 * @param taskId    null for project entries
 * @param category  deadline category name (for example PRESENTATION); null for other entries
 */
public record CalendarEntryResponse(
        String type,
        LocalDate date,
        String title,
        Priority priority,
        boolean important,
        boolean done,
        Long taskId,
        Long projectId,
        String projectName,
        String category) {
}
