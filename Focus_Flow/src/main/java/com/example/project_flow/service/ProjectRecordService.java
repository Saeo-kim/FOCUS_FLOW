package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.ProjectRecordRequest;
import com.example.project_flow.entity.ProjectRecord;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.ProjectRecordRepository;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The create/read/update/delete cycle shared by every record that belongs to a project. */
public abstract class ProjectRecordService<E extends ProjectRecord, Q extends ProjectRecordRequest, R> {

    protected final ProjectRecordRepository<E> repository;
    protected final References refs;
    protected final ActivityLogService activity;

    protected ProjectRecordService(ProjectRecordRepository<E> repository, References refs, ActivityLogService activity) {
        this.repository = repository;
        this.refs = refs;
        this.activity = activity;
    }

    protected abstract E newEntity();

    protected abstract void apply(E entity, Q request);

    protected abstract R toResponse(E entity);

    /** Name of the record type as it reads in the Activity Log, e.g. "deadline". */
    protected abstract String noun();

    protected abstract String titleOf(E entity);

    protected abstract Sort sort();

    protected void beforeDelete(E entity) {
    }

    @Transactional(readOnly = true)
    public List<R> list(Long projectId) {
        List<E> rows = projectId != null ? repository.findByProject_Id(projectId, sort()) : repository.findAll(sort());
        return rows.stream().map(this::toResponse).toList();
    }

    @Transactional
    public R create(Q request) {
        E entity = newEntity();
        entity.setProject(refs.project(request.projectId()));
        apply(entity, request);
        repository.saveAndFlush(entity);
        log("CREATED", "Added", entity);
        return toResponse(entity);
    }

    @Transactional
    public R update(Long id, Q request) {
        E entity = require(id);
        entity.setProject(refs.project(request.projectId()));
        apply(entity, request);
        repository.saveAndFlush(entity);
        log("UPDATED", "Updated", entity);
        return toResponse(entity);
    }

    @Transactional
    public void delete(Long id) {
        E entity = require(id);
        beforeDelete(entity);
        repository.delete(entity);
        log("DELETED", "Deleted", entity);
    }

    protected E require(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("That record no longer exists."));
    }

    private void log(String action, String verb, E entity) {
        String type = noun().toUpperCase().replace(' ', '_') + "_" + action;
        activity.record(type, verb + " " + noun() + " \"" + titleOf(entity) + "\"");
    }
}
