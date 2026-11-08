package com.example.project_flow.service;

import com.example.project_flow.dto.TaskDtos.TaskRequest;
import com.example.project_flow.dto.TaskDtos.TaskResponse;
import com.example.project_flow.entity.Project;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkStatus;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.repository.WorkStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class TaskService {

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final WorkStepRepository steps;
    private final References refs;
    private final ActivityLogService activity;
    private final Clock clock;

    public TaskService(TaskRepository tasks, ProjectRepository projects, WorkStepRepository steps,
                       References refs, ActivityLogService activity, Clock clock) {
        this.tasks = tasks;
        this.projects = projects;
        this.steps = steps;
        this.refs = refs;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> findAll() {
        Map<Long, long[]> counts = stepCounts();
        return tasks.findAllWithProject().stream().map(task -> toResponse(task, counts)).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse find(Long id) {
        return toResponse(require(id));
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = new Task(LocalDateTime.now(clock));
        apply(task, request);
        tasks.save(task);
        activity.record("TASK_CREATED", "Created task \"" + task.getTitle() + "\"");
        return toResponse(task);
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = require(id);
        apply(task, request);
        activity.record("TASK_UPDATED", "Updated task \"" + task.getTitle() + "\"");
        return toResponse(task);
    }

    @Transactional
    public TaskResponse setDone(Long id, boolean done) {
        Task task = require(id);
        if (task.isDone() != done) {
            if (done) {
                requireStepsFinished(task);
            }
            task.markDone(done, LocalDateTime.now(clock));
            activity.record(done ? "TASK_COMPLETED" : "TASK_REOPENED",
                    (done ? "Completed task \"" : "Reopened task \"") + task.getTitle() + "\"");
        }
        return toResponse(task);
    }

    @Transactional
    public TaskResponse setStatus(Long id, WorkStatus status) {
        Task task = require(id);
        changeStatus(task, status);
        return toResponse(task);
    }

    @Transactional
    public void delete(Long id) {
        Task task = require(id);
        steps.deleteAllOfTask(id);
        refs.unlinkTask(id);
        tasks.delete(task);
        activity.record("TASK_DELETED", "Deleted task \"" + task.getTitle() + "\"");
    }

    private void changeStatus(Task task, WorkStatus status) {
        if (task.getStatus() == status) {
            return;
        }
        if (status == WorkStatus.COMPLETED) {
            requireStepsFinished(task);
        }
        task.changeStatus(status, LocalDateTime.now(clock));
        activity.record("TASK_STATUS_CHANGED", "Task \"" + task.getTitle() + "\" moved to " + status);
    }

    /** A task with unfinished steps cannot be reported as done. */
    private void requireStepsFinished(Task task) {
        long open = steps.findByTask_IdOrderBySortOrderAscIdAsc(task.getId()).stream()
                .filter(step -> !step.isCompleted()).count();
        if (open > 0) {
            throw new ConflictException("Finish or remove the " + open + " open work step"
                    + (open == 1 ? "" : "s") + " before completing this task.");
        }
    }

    private void apply(Task task, TaskRequest request) {
        Project project = null;
        if (request.projectId() != null) {
            project = projects.findById(request.projectId())
                    .orElseThrow(() -> new NotFoundException("That project no longer exists."));
        }
        Responsibility responsibility = refs.responsibility(request.responsibilityId(), project);
        if (responsibility != null && project == null) {
            throw new ConflictException("Choose the project this responsibility belongs to.");
        }
        task.update(request.title().trim(), blankToNull(request.description()), request.priority(),
                request.dueDate(), request.scheduledDate(), project);
        task.assignResponsibility(responsibility);
        if (request.status() != null) {
            changeStatus(task, request.status());
        }
    }

    private Map<Long, long[]> stepCounts() {
        Map<Long, long[]> counts = new HashMap<>();
        for (Object[] row : steps.countByTask()) {
            counts.put((Long) row[0], new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return counts;
    }

    private TaskResponse toResponse(Task task) {
        long total = 0;
        long completed = 0;
        if (task.getId() != null) {
            var rows = steps.findByTask_IdOrderBySortOrderAscIdAsc(task.getId());
            total = rows.size();
            completed = rows.stream().filter(step -> step.isCompleted()).count();
        }
        return TaskResponse.from(task, total, completed);
    }

    private static TaskResponse toResponse(Task task, Map<Long, long[]> counts) {
        long[] count = counts.getOrDefault(task.getId(), new long[]{0, 0});
        return TaskResponse.from(task, count[0], count[1]);
    }

    private Task require(Long id) {
        return tasks.findById(id).orElseThrow(() -> new NotFoundException("That task no longer exists."));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
