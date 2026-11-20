package com.example.project_flow.service;

import com.example.project_flow.dto.CalendarEntryResponse;
import com.example.project_flow.entity.Deadline;
import com.example.project_flow.entity.DeadlineScope;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.Project;
import com.example.project_flow.entity.ProjectPhase;
import com.example.project_flow.entity.ProjectStatus;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkStatus;
import com.example.project_flow.repository.DeadlineRepository;
import com.example.project_flow.repository.ProjectPhaseRepository;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.ResponsibilityRepository;
import com.example.project_flow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class CalendarService {

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final DeadlineRepository deadlines;
    private final ResponsibilityRepository responsibilities;
    private final ProjectPhaseRepository phases;

    public CalendarService(TaskRepository tasks, ProjectRepository projects, DeadlineRepository deadlines,
                           ResponsibilityRepository responsibilities, ProjectPhaseRepository phases) {
        this.tasks = tasks;
        this.projects = projects;
        this.deadlines = deadlines;
        this.responsibilities = responsibilities;
        this.phases = phases;
    }

    @Transactional(readOnly = true)
    public List<CalendarEntryResponse> month(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate last = month.atEndOfMonth();
        List<CalendarEntryResponse> entries = new ArrayList<>();

        for (Task task : tasks.findDatedBetween(first, last)) {
            if (inRange(task.getDueDate(), first, last)) {
                entries.add(taskEntry("TASK_DUE", task.getDueDate(), task));
            }
            // A task scheduled on its deadline would otherwise appear twice on the same day.
            if (inRange(task.getScheduledDate(), first, last) && !task.getScheduledDate().equals(task.getDueDate())) {
                entries.add(taskEntry("TASK_SCHEDULED", task.getScheduledDate(), task));
            }
        }
        for (Project project : projects.findByDueDateBetween(first, last)) {
            entries.add(projectEntry(project));
        }
        for (Deadline deadline : deadlines.findByDueDateBetween(first, last)) {
            entries.add(deadlineEntry(deadline));
        }
        for (Responsibility responsibility : responsibilities.findByDueDateBetween(first, last)) {
            entries.add(responsibilityEntry(responsibility));
        }
        for (ProjectPhase phase : phases.findByStartDateBetweenOrEndDateBetween(first, last, first, last)) {
            if (inRange(phase.getStartDate(), first, last)) {
                entries.add(phaseEntry(phase, phase.getStartDate(), phase.getName() + " starts"));
            }
            if (inRange(phase.getEndDate(), first, last) && !phase.getEndDate().equals(phase.getStartDate())) {
                entries.add(phaseEntry(phase, phase.getEndDate(), phase.getName() + " ends"));
            }
        }

        entries.sort(Comparator.comparing(CalendarEntryResponse::date)
                .thenComparing(CalendarEntryResponse::done)
                .thenComparing(CalendarEntryResponse::important, Comparator.reverseOrder())
                .thenComparing(CalendarEntryResponse::title));
        return entries;
    }

    private static boolean inRange(LocalDate date, LocalDate first, LocalDate last) {
        return date != null && !date.isBefore(first) && !date.isAfter(last);
    }

    private static CalendarEntryResponse taskEntry(String type, LocalDate date, Task task) {
        Project project = task.getProject();
        return new CalendarEntryResponse(type, date, task.getTitle(), task.getPriority(),
                task.getPriority() == Priority.HIGH, task.isDone(), task.getId(),
                project != null ? project.getId() : null, project != null ? project.getName() : null, null);
    }

    private static CalendarEntryResponse projectEntry(Project project) {
        return new CalendarEntryResponse("PROJECT_DUE", project.getDueDate(), project.getName(), null, false,
                project.getStatus() == ProjectStatus.COMPLETED, null, project.getId(), project.getName(), null);
    }

    private static CalendarEntryResponse deadlineEntry(Deadline d) {
        String type = d.getScope() == DeadlineScope.TEAM ? "DEADLINE_TEAM" : "DEADLINE_PERSONAL";
        return new CalendarEntryResponse(type, d.getDueDate(), d.getTitle(), null, d.getCategory().isMajor(), d.isDone(),
                null, d.getProjectId(), d.getProject().getName(), d.getCategory().name());
    }

    private static CalendarEntryResponse responsibilityEntry(Responsibility r) {
        return new CalendarEntryResponse("RESPONSIBILITY_DUE", r.getDueDate(), r.getTitle(), r.getPriority(),
                r.getPriority() == Priority.HIGH, r.getStatus() == WorkStatus.COMPLETED,
                null, r.getProjectId(), r.getProject().getName(), null);
    }

    private static CalendarEntryResponse phaseEntry(ProjectPhase phase, LocalDate date, String title) {
        return new CalendarEntryResponse("TEAM_PHASE", date, title, null, false, false,
                null, phase.getProjectId(), phase.getProject().getName(), null);
    }
}
