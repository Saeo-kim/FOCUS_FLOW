package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.EvidenceRequest;
import com.example.project_flow.dto.RecordDtos.EvidenceResponse;
import com.example.project_flow.entity.Evidence;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.repository.EvidenceRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class EvidenceService extends ProjectRecordService<Evidence, EvidenceRequest, EvidenceResponse> {

    private final Clock clock;

    public EvidenceService(EvidenceRepository repository, References refs, ActivityLogService activity, Clock clock) {
        super(repository, refs, activity);
        this.clock = clock;
    }

    @Override
    protected Evidence newEntity() {
        return new Evidence(LocalDateTime.now(clock));
    }

    @Override
    protected void apply(Evidence e, EvidenceRequest q) {
        e.update(q.type(), q.title().trim(), q.location().trim(), References.clean(q.note()),
                refs.task(q.taskId(), e.getProject()), refs.responsibility(q.responsibilityId(), e.getProject()));
    }

    @Override
    protected EvidenceResponse toResponse(Evidence e) {
        Task task = e.getTask();
        Responsibility responsibility = e.getResponsibility();
        return new EvidenceResponse(e.getId(), e.getProjectId(), e.getType(), e.getTitle(), e.getLocation(), e.getNote(),
                task != null ? task.getId() : null, task != null ? task.getTitle() : null,
                responsibility != null ? responsibility.getId() : null, responsibility != null ? responsibility.getTitle() : null,
                e.getCreatedAt());
    }

    @Override
    protected String noun() {
        return "evidence";
    }

    @Override
    protected String titleOf(Evidence e) {
        return e.getTitle();
    }

    @Override
    protected Sort sort() {
        return Sort.by(Sort.Direction.DESC, "createdAt", "id");
    }
}
