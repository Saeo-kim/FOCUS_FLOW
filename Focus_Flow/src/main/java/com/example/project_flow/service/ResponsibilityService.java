package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.ResponsibilityRequest;
import com.example.project_flow.dto.RecordDtos.ResponsibilityResponse;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.repository.ResponsibilityRepository;
import com.example.project_flow.repository.TaskRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class ResponsibilityService extends ProjectRecordService<Responsibility, ResponsibilityRequest, ResponsibilityResponse> {

    private final TaskRepository tasks;

    public ResponsibilityService(ResponsibilityRepository repository, TaskRepository tasks,
                                 References refs, ActivityLogService activity) {
        super(repository, refs, activity);
        this.tasks = tasks;
    }

    @Override
    protected Responsibility newEntity() {
        return new Responsibility();
    }

    @Override
    protected void apply(Responsibility r, ResponsibilityRequest q) {
        References.requireOrder(q.startDate(), q.dueDate(), "The deadline cannot be before the start date.");
        r.update(q.title().trim(), References.clean(q.description()), References.clean(q.owner()), q.startDate(),
                q.dueDate(), q.priority(), q.status(), References.clean(q.deliverables()));
    }

    /** Progress counts tasks that are actually done. */
    @Override
    protected ResponsibilityResponse toResponse(Responsibility r) {
        long total = 0;
        long completed = 0;
        for (Object[] row : tasks.countByResponsibility()) {
            if (r.getId().equals(row[0])) {
                total = ((Number) row[1]).longValue();
                completed = ((Number) row[2]).longValue();
            }
        }
        int progress = total == 0 ? 0 : (int) (completed * 100 / total);
        return new ResponsibilityResponse(r.getId(), r.getProjectId(), r.getTitle(), r.getDescription(), r.getOwner(),
                r.getStartDate(), r.getDueDate(), r.getPriority(), r.getStatus(), r.getDeliverables(),
                total, completed, progress);
    }

    @Override
    protected void beforeDelete(Responsibility r) {
        refs.unlinkResponsibility(r.getId());
    }

    @Override
    protected String noun() {
        return "responsibility";
    }

    @Override
    protected String titleOf(Responsibility r) {
        return r.getTitle();
    }

    @Override
    protected Sort sort() {
        return Sort.by("id");
    }
}
