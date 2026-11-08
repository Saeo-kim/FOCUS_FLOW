package com.example.project_flow.controller;

import com.example.project_flow.dto.RecordDtos.StepMoveRequest;
import com.example.project_flow.dto.RecordDtos.StepRequest;
import com.example.project_flow.dto.RecordDtos.StepResponse;
import com.example.project_flow.service.WorkStepService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class WorkStepController {

    private final WorkStepService service;

    public WorkStepController(WorkStepService service) {
        this.service = service;
    }

    @GetMapping("/tasks/{taskId}/steps")
    public List<StepResponse> list(@PathVariable Long taskId) {
        return service.list(taskId);
    }

    @PostMapping("/tasks/{taskId}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepResponse add(@PathVariable Long taskId, @Valid @RequestBody StepRequest request) {
        return service.add(taskId, request);
    }

    @PostMapping("/tasks/{taskId}/apply-template/{templateId}")
    public List<StepResponse> applyTemplate(@PathVariable Long taskId, @PathVariable Long templateId) {
        return service.applyTemplate(taskId, templateId);
    }

    @PutMapping("/steps/{id}")
    public StepResponse update(@PathVariable Long id, @Valid @RequestBody StepRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/steps/{id}/move")
    public List<StepResponse> move(@PathVariable Long id, @RequestBody StepMoveRequest request) {
        return service.move(id, request.direction());
    }

    @DeleteMapping("/steps/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
