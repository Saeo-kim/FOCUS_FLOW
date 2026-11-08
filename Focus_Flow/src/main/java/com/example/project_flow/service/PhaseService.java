package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.PhaseRequest;
import com.example.project_flow.dto.RecordDtos.PhaseResponse;
import com.example.project_flow.entity.ProjectPhase;
import com.example.project_flow.repository.ProjectPhaseRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class PhaseService extends ProjectRecordService<ProjectPhase, PhaseRequest, PhaseResponse> {

    public PhaseService(ProjectPhaseRepository repository, References refs, ActivityLogService activity) {
        super(repository, refs, activity);
    }

    @Override
    protected ProjectPhase newEntity() {
        return new ProjectPhase();
    }

    @Override
    protected void apply(ProjectPhase phase, PhaseRequest q) {
        References.requireOrder(q.startDate(), q.endDate(), "A phase cannot end before it starts.");
        phase.update(q.name().trim(), q.startDate(), q.endDate(), References.clean(q.items()));
    }

    @Override
    protected PhaseResponse toResponse(ProjectPhase p) {
        return new PhaseResponse(p.getId(), p.getProjectId(), p.getName(), p.getStartDate(), p.getEndDate(), p.getItems());
    }

    @Override
    protected String noun() {
        return "schedule phase";
    }

    @Override
    protected String titleOf(ProjectPhase phase) {
        return phase.getName();
    }

    @Override
    protected Sort sort() {
        return Sort.by("startDate", "id");
    }
}
