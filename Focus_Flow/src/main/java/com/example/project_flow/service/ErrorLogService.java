package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.ErrorEventResponse;
import com.example.project_flow.dto.RecordDtos.ErrorLogRequest;
import com.example.project_flow.dto.RecordDtos.ErrorLogResponse;
import com.example.project_flow.entity.ErrorLog;
import com.example.project_flow.entity.Task;
import com.example.project_flow.repository.ErrorLogRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class ErrorLogService extends ProjectRecordService<ErrorLog, ErrorLogRequest, ErrorLogResponse> {

    private final Clock clock;

    public ErrorLogService(ErrorLogRepository repository, References refs, ActivityLogService activity, Clock clock) {
        super(repository, refs, activity);
        this.clock = clock;
    }

    @Override
    protected ErrorLog newEntity() {
        ErrorLog log = new ErrorLog();
        log.addEvent("Error logged", LocalDateTime.now(clock));
        return log;
    }

    @Override
    protected void apply(ErrorLog e, ErrorLogRequest q) {
        e.update(q.occurredOn(), refs.task(q.taskId(), e.getProject()), q.title().trim(), References.clean(q.situation()),
                References.clean(q.cause()), References.clean(q.attempts()), References.clean(q.solution()),
                References.clean(q.changedFiles()), References.clean(q.verification()), References.clean(q.result()),
                References.clean(q.links()));
        e.changeStatus(q.status(), LocalDateTime.now(clock));
    }

    @Transactional
    public ErrorLogResponse addEvent(Long id, String note) {
        ErrorLog log = require(id);
        log.addEvent(note.trim(), LocalDateTime.now(clock));
        repository.saveAndFlush(log);
        return toResponse(log);
    }

    @Override
    protected ErrorLogResponse toResponse(ErrorLog e) {
        Task task = e.getTask();
        return new ErrorLogResponse(e.getId(), e.getProjectId(), e.getOccurredOn(),
                task != null ? task.getId() : null, task != null ? task.getTitle() : null, e.getTitle(), e.getSituation(),
                e.getCause(), e.getAttempts(), e.getSolution(), e.getChangedFiles(), e.getVerification(), e.getResult(),
                e.getLinks(), e.getStatus(),
                e.getEvents().stream().map(ev -> new ErrorEventResponse(ev.getHappenedAt(), ev.getNote())).toList());
    }

    @Override
    protected String noun() {
        return "error log";
    }

    @Override
    protected String titleOf(ErrorLog e) {
        return e.getTitle();
    }

    @Override
    protected Sort sort() {
        return Sort.by(Sort.Direction.DESC, "occurredOn", "id");
    }
}
