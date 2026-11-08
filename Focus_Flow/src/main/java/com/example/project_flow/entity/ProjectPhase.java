package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/** One stage of the team's overall schedule. Reference information, never a personal task. */
@Entity
@Table(name = "project_phases")
public class ProjectPhase extends ProjectRecord {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Column(length = 2000)
    private String items;

    public void update(String name, LocalDate startDate, LocalDate endDate, String items) {
        this.name = name;
        this.startDate = startDate;
        this.endDate = endDate;
        this.items = items;
    }

    public String getName() { return name; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public String getItems() { return items; }
}
