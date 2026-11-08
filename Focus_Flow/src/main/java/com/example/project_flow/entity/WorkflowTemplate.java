package com.example.project_flow.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workflow_templates")
public class WorkflowTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "workflow_template_steps", joinColumns = @JoinColumn(name = "template_id"))
    @OrderColumn(name = "position")
    @Column(name = "title", nullable = false, length = 200)
    private List<String> steps = new ArrayList<>();

    public void update(String name, String description, List<String> steps) {
        this.name = name;
        this.description = description;
        this.steps.clear();
        this.steps.addAll(steps);
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public List<String> getSteps() { return steps; }
}
