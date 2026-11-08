package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.RequirementRequest;
import com.example.project_flow.dto.RecordDtos.RequirementResponse;
import com.example.project_flow.entity.Requirement;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.repository.RequirementRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class RequirementService extends ProjectRecordService<Requirement, RequirementRequest, RequirementResponse> {

    public RequirementService(RequirementRepository repository, References refs, ActivityLogService activity) {
        super(repository, refs, activity);
    }

    @Override
    protected Requirement newEntity() {
        return new Requirement();
    }

    @Override
    protected void apply(Requirement r, RequirementRequest q) {
        r.describe(q.title().trim(), References.clean(q.detail()), References.clean(q.relatedFeature()),
                refs.responsibility(q.responsibilityId(), r.getProject()), refs.task(q.taskId(), r.getProject()),
                References.clean(q.evidenceNote()));
        r.recordCheck(q.completed(), q.tested(), q.verified(), References.clean(q.verificationResult()));
    }

    @Override
    protected RequirementResponse toResponse(Requirement r) {
        Responsibility responsibility = r.getResponsibility();
        Task task = r.getTask();
        return new RequirementResponse(r.getId(), r.getProjectId(), r.getTitle(), r.getDetail(), r.getRelatedFeature(),
                responsibility != null ? responsibility.getId() : null, responsibility != null ? responsibility.getTitle() : null,
                task != null ? task.getId() : null, task != null ? task.getTitle() : null,
                r.isCompleted(), r.isTested(), r.isVerified(), r.getVerificationResult(), r.getEvidenceNote());
    }

    @Override
    protected String noun() {
        return "requirement";
    }

    @Override
    protected String titleOf(Requirement r) {
        return r.getTitle();
    }

    @Override
    protected Sort sort() {
        return Sort.by("id");
    }
}
