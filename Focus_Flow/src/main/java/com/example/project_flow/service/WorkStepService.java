package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.StepRequest;
import com.example.project_flow.dto.RecordDtos.StepResponse;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkStatus;
import com.example.project_flow.entity.WorkStep;
import com.example.project_flow.entity.WorkflowTemplate;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.repository.WorkStepRepository;
import com.example.project_flow.repository.WorkflowTemplateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
public class WorkStepService {

    private final WorkStepRepository steps;
    private final TaskRepository tasks;
    private final WorkflowTemplateRepository templates;
    private final ActivityLogService activity;
    private final Clock clock;

    public WorkStepService(WorkStepRepository steps, TaskRepository tasks, WorkflowTemplateRepository templates,
                           ActivityLogService activity, Clock clock) {
        this.steps = steps;
        this.tasks = tasks;
        this.templates = templates;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<StepResponse> list(Long taskId) {
        requireTask(taskId);
        return steps.findByTask_IdOrderBySortOrderAscIdAsc(taskId).stream().map(WorkStepService::toResponse).toList();
    }

    @Transactional
    public StepResponse add(Long taskId, StepRequest request) {
        Task task = requireTask(taskId);
        WorkStep step = new WorkStep(task, request.title().trim(), nextOrder(taskId));
        apply(step, request);
        steps.saveAndFlush(step);
        syncTask(task);
        activity.record("STEP_CREATED", "Added step \"" + step.getTitle() + "\" to \"" + task.getTitle() + "\"");
        return toResponse(step);
    }

    @Transactional
    public StepResponse update(Long id, StepRequest request) {
        WorkStep step = require(id);
        WorkStatus before = step.getStatus();
        apply(step, request);
        steps.saveAndFlush(step);
        syncTask(step.getTask());
        if (before != step.getStatus()) {
            activity.record("STEP_STATUS_CHANGED",
                    "Step \"" + step.getTitle() + "\" moved to " + step.getStatus());
        }
        return toResponse(step);
    }

    @Transactional
    public void delete(Long id) {
        WorkStep step = require(id);
        Task task = step.getTask();
        String title = step.getTitle();
        steps.delete(step);
        steps.flush();
        syncTask(task);
        activity.record("STEP_DELETED", "Deleted step \"" + title + "\" from \"" + task.getTitle() + "\"");
    }

    /** @param direction negative moves the step up, positive moves it down */
    @Transactional
    public List<StepResponse> move(Long id, int direction) {
        WorkStep step = require(id);
        Long taskId = step.getTask().getId();
        List<WorkStep> ordered = new java.util.ArrayList<>(steps.findByTask_IdOrderBySortOrderAscIdAsc(taskId));
        int from = ordered.indexOf(step);
        int to = from + Integer.signum(direction);
        if (to >= 0 && to < ordered.size()) {
            Collections.swap(ordered, from, to);
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).moveTo(i);
            }
        }
        return ordered.stream().map(WorkStepService::toResponse).toList();
    }

    @Transactional
    public List<StepResponse> applyTemplate(Long taskId, Long templateId) {
        Task task = requireTask(taskId);
        WorkflowTemplate template = templates.findById(templateId)
                .orElseThrow(() -> new NotFoundException("That template no longer exists."));
        int order = nextOrder(taskId);
        for (String title : template.getSteps()) {
            steps.save(new WorkStep(task, title, order++));
        }
        steps.flush();
        syncTask(task);
        activity.record("STEP_TEMPLATE_APPLIED",
                "Applied template \"" + template.getName() + "\" to \"" + task.getTitle() + "\"");
        return steps.findByTask_IdOrderBySortOrderAscIdAsc(taskId).stream().map(WorkStepService::toResponse).toList();
    }

    /**
     * Starting a step starts its task, and unfinished steps reopen a completed task. Finishing
     * every step deliberately does not complete the task; that stays the person's decision.
     */
    private void syncTask(Task task) {
        List<WorkStep> all = steps.findByTask_IdOrderBySortOrderAscIdAsc(task.getId());
        LocalDateTime now = LocalDateTime.now(clock);
        boolean anyOpen = all.stream().anyMatch(step -> !step.isCompleted());
        boolean anyStarted = all.stream().anyMatch(step -> step.getStatus() != WorkStatus.NOT_STARTED);
        if (task.isDone() && anyOpen) {
            task.changeStatus(WorkStatus.IN_PROGRESS, now);
        } else if (task.getStatus() == WorkStatus.NOT_STARTED && anyStarted) {
            task.changeStatus(WorkStatus.IN_PROGRESS, now);
        }
    }

    private void apply(WorkStep step, StepRequest q) {
        References.requireOrder(q.startDate(), q.completedDate(), "A step cannot finish before it starts.");
        step.describe(q.title().trim(), References.clean(q.description()), References.clean(q.result()),
                References.clean(q.errorNote()), References.clean(q.link()), References.clean(q.memo()));
        WorkStatus status = q.status() != null ? q.status() : WorkStatus.NOT_STARTED;
        step.track(status, q.progress() != null ? q.progress() : 0, q.startDate(), q.completedDate(), LocalDate.now(clock));
    }

    private int nextOrder(Long taskId) {
        return steps.findByTask_IdOrderBySortOrderAscIdAsc(taskId).stream()
                .mapToInt(WorkStep::getSortOrder).max().orElse(-1) + 1;
    }

    private Task requireTask(Long id) {
        return tasks.findById(id).orElseThrow(() -> new NotFoundException("That task no longer exists."));
    }

    private WorkStep require(Long id) {
        return steps.findById(id).orElseThrow(() -> new NotFoundException("That step no longer exists."));
    }

    private static StepResponse toResponse(WorkStep s) {
        return new StepResponse(s.getId(), s.getTask().getId(), s.getTitle(), s.getDescription(), s.getStatus(),
                s.getStartDate(), s.getCompletedDate(), s.getProgress(), s.getResult(), s.getErrorNote(),
                s.getLink(), s.getMemo(), s.getSortOrder());
    }
}
