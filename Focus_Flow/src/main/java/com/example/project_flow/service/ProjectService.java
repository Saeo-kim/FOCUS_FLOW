package com.example.project_flow.service;

import com.example.project_flow.dto.ProjectDtos.ProjectRequest;
import com.example.project_flow.dto.ProjectDtos.ProjectResponse;
import com.example.project_flow.entity.Project;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.ProjectRecordRepository;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final List<ProjectRecordRepository<?>> records;
    private final ActivityLogService activity;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, TaskRepository tasks, List<ProjectRecordRepository<?>> records,
                          ActivityLogService activity, Clock clock) {
        this.projects = projects;
        this.tasks = tasks;
        this.records = records;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> findAll() {
        Map<Long, long[]> counts = counts();
        return projects.findAllByOrderByCreatedAtDesc().stream().map(project -> toResponse(project, counts)).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse find(Long id) {
        return toResponse(require(id), counts());
    }

    @Transactional
    public ProjectResponse create(ProjectRequest request) {
        Project project = new Project(LocalDateTime.now(clock));
        apply(project, request);
        projects.save(project);
        activity.record("PROJECT_CREATED", "Created project \"" + project.getName() + "\"");
        return ProjectResponse.from(project, 0, 0);
    }

    @Transactional
    public void update(Long id, ProjectRequest request) {
        Project project = require(id);
        apply(project, request);
        activity.record("PROJECT_UPDATED", "Updated project \"" + project.getName() + "\"");
    }

    /**
     * Tasks are kept and simply lose their project. A project that holds schedules or logs cannot
     * be deleted at all, because that would silently destroy the work recorded under it.
     */
    @Transactional
    public void delete(Long id) {
        Project project = require(id);
        if (records.stream().anyMatch(repository -> repository.existsByProject_Id(id))) {
            throw new ConflictException("This project has schedules or records. Remove them first, or mark the project as completed.");
        }
        tasks.unassignProject(id);
        projects.delete(project);
        activity.record("PROJECT_DELETED", "Deleted project \"" + project.getName() + "\"");
    }

    private void apply(Project project, ProjectRequest q) {
        References.requireOrder(q.startDate(), q.dueDate(), "A project cannot end before it starts.");
        References.requireOrder(q.devStartDate(), q.devEndDate(), "Development cannot end before it starts.");
        project.update(q.name().trim(), References.clean(q.description()), q.status(), q.dueDate());
        project.updateDetails(References.clean(q.projectType()), References.clean(q.teamName()), q.teamNumber(),
                q.startDate(), q.devStartDate(), q.devEndDate(),
                References.clean(q.techStack()), References.clean(q.serviceFeatures()));
        project.changePurpose(References.clean(q.purpose()));
    }

    private Map<Long, long[]> counts() {
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : tasks.countByProject()) {
            counts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return counts;
    }

    private static ProjectResponse toResponse(Project project, Map<Long, long[]> counts) {
        long[] count = counts.getOrDefault(project.getId(), new long[]{0, 0});
        return ProjectResponse.from(project, count[0], count[1]);
    }

    private Project require(Long id) {
        return projects.findById(id).orElseThrow(() -> new NotFoundException("That project no longer exists."));
    }
}
