package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.TemplateRequest;
import com.example.project_flow.dto.RecordDtos.TemplateResponse;
import com.example.project_flow.entity.WorkflowTemplate;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.WorkflowTemplateRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class WorkflowTemplateService {

    private final WorkflowTemplateRepository templates;
    private final ActivityLogService activity;

    public WorkflowTemplateService(WorkflowTemplateRepository templates, ActivityLogService activity) {
        this.templates = templates;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return templates.findAll(Sort.by("id")).stream().map(WorkflowTemplateService::toResponse).toList();
    }

    @Transactional
    public TemplateResponse create(TemplateRequest request) {
        WorkflowTemplate template = new WorkflowTemplate();
        apply(template, request);
        templates.save(template);
        activity.record("TEMPLATE_CREATED", "Created workflow template \"" + template.getName() + "\"");
        return toResponse(template);
    }

    @Transactional
    public TemplateResponse update(Long id, TemplateRequest request) {
        WorkflowTemplate template = require(id);
        apply(template, request);
        activity.record("TEMPLATE_UPDATED", "Updated workflow template \"" + template.getName() + "\"");
        return toResponse(template);
    }

    @Transactional
    public void delete(Long id) {
        WorkflowTemplate template = require(id);
        templates.delete(template);
        activity.record("TEMPLATE_DELETED", "Deleted workflow template \"" + template.getName() + "\"");
    }

    private void apply(WorkflowTemplate template, TemplateRequest q) {
        List<String> steps = new ArrayList<>();
        for (String step : q.steps()) {
            if (step != null && !step.isBlank()) {
                steps.add(step.trim());
            }
        }
        if (steps.isEmpty()) {
            throw new ConflictException("Add at least one step.");
        }
        template.update(q.name().trim(), References.clean(q.description()), steps);
    }

    private WorkflowTemplate require(Long id) {
        return templates.findById(id).orElseThrow(() -> new NotFoundException("That template no longer exists."));
    }

    private static TemplateResponse toResponse(WorkflowTemplate t) {
        return new TemplateResponse(t.getId(), t.getName(), t.getDescription(), List.copyOf(t.getSteps()));
    }
}
