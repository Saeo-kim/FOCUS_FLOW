package com.example.project_flow.controller;

import com.example.project_flow.dto.RecordDtos.ProjectRecordRequest;
import com.example.project_flow.service.ProjectRecordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.List;

public abstract class ProjectRecordController<Q extends ProjectRecordRequest, R> {

    private final ProjectRecordService<?, Q, R> service;

    protected ProjectRecordController(ProjectRecordService<?, Q, R> service) {
        this.service = service;
    }

    @GetMapping
    public List<R> list(@RequestParam(required = false) Long projectId) {
        return service.list(projectId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public R create(@Valid @RequestBody Q request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public R update(@PathVariable Long id, @Valid @RequestBody Q request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
