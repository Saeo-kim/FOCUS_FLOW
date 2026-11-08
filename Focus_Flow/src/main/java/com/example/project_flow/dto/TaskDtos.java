package com.example.project_flow.dto;

import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record TaskRequest(
            @NotBlank(message = "Enter a task title.")
            @Size(max = 200, message = "Task titles can be at most 200 characters.")
            String title,
            @Size(max = 1000, message = "Descriptions can be at most 1000 characters.")
            String description,
            @NotNull(message = "Choose a priority.")
            Priority priority,
            LocalDate dueDate,
            LocalDate scheduledDate,
            Long projectId,
            Long responsibilityId,
            WorkStatus status) {
    }

    public record TaskDoneRequest(boolean done) {
    }

    public record TaskStatusRequest(@NotNull(message = "Choose a status.") WorkStatus status) {
    }

    public record TaskResponse(
            Long id,
            String title,
            String description,
            Priority priority,
            boolean done,
            LocalDate dueDate,
            LocalDate scheduledDate,
            Long projectId,
            String projectName,
            LocalDateTime completedAt,
            LocalDateTime createdAt,
            WorkStatus status,
            Long responsibilityId,
            String responsibilityTitle,
            long stepCount,
            long completedStepCount,
            int progress) {

        /** With steps, progress is finished steps over all steps; without them, done or not. */
        public static TaskResponse from(Task task, long stepCount, long completedStepCount) {
            int progress = stepCount > 0
                    ? (int) (completedStepCount * 100 / stepCount)
                    : task.isDone() ? 100 : 0;
            Responsibility responsibility = task.getResponsibility();
            return new TaskResponse(
                    task.getId(),
                    task.getTitle(),
                    task.getDescription(),
                    task.getPriority(),
                    task.isDone(),
                    task.getDueDate(),
                    task.getScheduledDate(),
                    task.getProject() != null ? task.getProject().getId() : null,
                    task.getProject() != null ? task.getProject().getName() : null,
                    task.getCompletedAt(),
                    task.getCreatedAt(),
                    task.getStatus(),
                    responsibility != null ? responsibility.getId() : null,
                    responsibility != null ? responsibility.getTitle() : null,
                    stepCount,
                    completedStepCount,
                    progress);
        }
    }
}
