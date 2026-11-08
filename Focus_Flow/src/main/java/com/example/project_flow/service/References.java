package com.example.project_flow.service;

import com.example.project_flow.entity.Project;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.ResponsibilityRepository;
import com.example.project_flow.repository.TaskRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** Resolves links between records and refuses links that cross project boundaries. */
@Component
public class References {

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ResponsibilityRepository responsibilities;
    private final EntityManager entityManager;

    public References(ProjectRepository projects, TaskRepository tasks,
                      ResponsibilityRepository responsibilities, EntityManager entityManager) {
        this.projects = projects;
        this.tasks = tasks;
        this.responsibilities = responsibilities;
        this.entityManager = entityManager;
    }

    public Project project(Long id) {
        return projects.findById(id).orElseThrow(() -> new NotFoundException("That project no longer exists."));
    }

    public Task task(Long id, Project project) {
        if (id == null) {
            return null;
        }
        Task task = tasks.findById(id).orElseThrow(() -> new NotFoundException("That task no longer exists."));
        if (task.getProject() != null && !Objects.equals(task.getProject().getId(), project.getId())) {
            throw new ConflictException("That task belongs to a different project.");
        }
        return task;
    }

    public Responsibility responsibility(Long id, Project project) {
        if (id == null) {
            return null;
        }
        Responsibility responsibility = responsibilities.findById(id)
                .orElseThrow(() -> new NotFoundException("That responsibility no longer exists."));
        if (project != null && !Objects.equals(responsibility.getProjectId(), project.getId())) {
            throw new ConflictException("That responsibility belongs to a different project.");
        }
        return responsibility;
    }

    /** Records that pointed at a deleted task stay on file and simply lose the link. */
    public void unlinkTask(Long taskId) {
        for (String entity : List.of("Requirement", "ErrorLog", "AiVerification", "Evidence")) {
            entityManager.createQuery("update " + entity + " x set x.task = null where x.task.id = :id")
                    .setParameter("id", taskId)
                    .executeUpdate();
        }
    }

    public void unlinkResponsibility(Long responsibilityId) {
        for (String entity : List.of("Task", "Requirement", "Evidence")) {
            entityManager.createQuery("update " + entity + " x set x.responsibility = null where x.responsibility.id = :id")
                    .setParameter("id", responsibilityId)
                    .executeUpdate();
        }
    }

    public static void requireOrder(LocalDate start, LocalDate end, String message) {
        if (start != null && end != null && end.isBefore(start)) {
            throw new ConflictException(message);
        }
    }

    public static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
