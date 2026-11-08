package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.DeadlineRequest;
import com.example.project_flow.dto.RecordDtos.DeadlineResponse;
import com.example.project_flow.entity.Deadline;
import com.example.project_flow.repository.DeadlineRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Service
public class DeadlineService extends ProjectRecordService<Deadline, DeadlineRequest, DeadlineResponse> {

    public DeadlineService(DeadlineRepository repository, References refs, ActivityLogService activity) {
        super(repository, refs, activity);
    }

    @Override
    protected Deadline newEntity() {
        return new Deadline();
    }

    @Override
    protected void apply(Deadline d, DeadlineRequest q) {
        d.update(q.title().trim(), q.category(), q.scope(), q.dueDate(), References.clean(q.owner()),
                References.clean(q.personalAction()), q.done(), References.clean(q.note()));
    }

    @Override
    protected DeadlineResponse toResponse(Deadline d) {
        return new DeadlineResponse(d.getId(), d.getProjectId(), d.getTitle(), d.getCategory(), d.getScope(), d.getDueDate(),
                d.getOwner(), d.getPersonalAction(), d.isDone(), d.getNote());
    }

    @Override
    protected String noun() {
        return "deadline";
    }

    @Override
    protected String titleOf(Deadline d) {
        return d.getTitle();
    }

    @Override
    protected Sort sort() {
        return Sort.by("dueDate", "id");
    }
}
