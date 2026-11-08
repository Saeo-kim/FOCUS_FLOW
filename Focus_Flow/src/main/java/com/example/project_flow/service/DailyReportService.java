package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.DailyReportRequest;
import com.example.project_flow.dto.RecordDtos.DailyReportResponse;
import com.example.project_flow.entity.DailyReport;
import com.example.project_flow.entity.ErrorLog;
import com.example.project_flow.entity.Task;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.repository.DailyReportRepository;
import com.example.project_flow.repository.ErrorLogRepository;
import com.example.project_flow.repository.TaskRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Service
public class DailyReportService extends ProjectRecordService<DailyReport, DailyReportRequest, DailyReportResponse> {

    private final DailyReportRepository reports;
    private final TaskRepository tasks;
    private final ErrorLogRepository errors;

    public DailyReportService(DailyReportRepository repository, TaskRepository tasks, ErrorLogRepository errors,
                              References refs, ActivityLogService activity) {
        super(repository, refs, activity);
        this.reports = repository;
        this.tasks = tasks;
        this.errors = errors;
    }

    @Override
    protected DailyReport newEntity() {
        return new DailyReport();
    }

    @Override
    protected void apply(DailyReport d, DailyReportRequest q) {
        reports.findByProject_IdAndReportDate(q.projectId(), q.reportDate())
                .filter(existing -> !Objects.equals(existing.getId(), d.getId()))
                .ifPresent(existing -> {
                    throw new ConflictException("A report for that date already exists. Edit it instead.");
                });
        d.update(q.reportDate(), References.clean(q.goal()), References.clean(q.actualWork()),
                References.clean(q.problemsSolved()), References.clean(q.incompleteReason()),
                References.clean(q.nextWork()), References.clean(q.evidenceNote()));
    }

    @Override
    protected DailyReportResponse toResponse(DailyReport d) {
        LocalDate date = d.getReportDate();
        List<String> completed = tasks.findCompletedBetween(d.getProjectId(), date.atStartOfDay(), date.plusDays(1).atStartOfDay())
                .stream().map(Task::getTitle).toList();
        List<String> inProgress = tasks.findInProgress(d.getProjectId()).stream().map(Task::getTitle).toList();
        List<String> logged = errors.findByProject_IdAndOccurredOn(d.getProjectId(), date)
                .stream().map(ErrorLog::getTitle).toList();
        return new DailyReportResponse(d.getId(), d.getProjectId(), date, d.getGoal(), d.getActualWork(),
                d.getProblemsSolved(), d.getIncompleteReason(), d.getNextWork(), d.getEvidenceNote(),
                completed, inProgress, logged);
    }

    @Override
    protected String noun() {
        return "daily report";
    }

    @Override
    protected String titleOf(DailyReport d) {
        return d.getReportDate().toString();
    }

    @Override
    protected Sort sort() {
        return Sort.by(Sort.Direction.DESC, "reportDate", "id");
    }
}
