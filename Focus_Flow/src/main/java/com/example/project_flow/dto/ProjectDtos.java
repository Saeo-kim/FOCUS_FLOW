package com.example.project_flow.dto;

import com.example.project_flow.entity.Project;
import com.example.project_flow.entity.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record ProjectRequest(
            @NotBlank(message = "Enter a project name.")
            @Size(max = 120, message = "Project names can be at most 120 characters.")
            String name,
            @Size(max = 1000, message = "Descriptions can be at most 1000 characters.")
            String description,
            @NotNull(message = "Choose a status.")
            ProjectStatus status,
            LocalDate dueDate,
            @Size(max = 80, message = "Project type is too long.")
            String projectType,
            @Size(max = 80, message = "Team name is too long.")
            String teamName,
            Integer teamNumber,
            LocalDate startDate,
            LocalDate devStartDate,
            LocalDate devEndDate,
            @Size(max = 1000, message = "Tech stack is too long.")
            String techStack,
            @Size(max = 1000, message = "Service features are too long.")
            String serviceFeatures,
            @Size(max = 2000, message = "Purpose is too long.")
            String purpose) {
    }

    public record ProjectResponse(
            Long id,
            String name,
            String description,
            ProjectStatus status,
            LocalDate dueDate,
            long taskCount,
            long completedTaskCount,
            LocalDateTime createdAt,
            String projectType,
            String teamName,
            Integer teamNumber,
            LocalDate startDate,
            LocalDate devStartDate,
            LocalDate devEndDate,
            String techStack,
            String serviceFeatures,
            String purpose) {

        public static ProjectResponse from(Project project, long taskCount, long completedTaskCount) {
            return new ProjectResponse(
                    project.getId(),
                    project.getName(),
                    project.getDescription(),
                    project.getStatus(),
                    project.getDueDate(),
                    taskCount,
                    completedTaskCount,
                    project.getCreatedAt(),
                    project.getProjectType(),
                    project.getTeamName(),
                    project.getTeamNumber(),
                    project.getStartDate(),
                    project.getDevStartDate(),
                    project.getDevEndDate(),
                    project.getTechStack(),
                    project.getServiceFeatures(),
                    project.getPurpose());
        }
    }
}
