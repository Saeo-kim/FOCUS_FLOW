package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** Work the user personally owns. Team-wide work lives in phases and team roles instead. */
@Entity
@Table(name = "responsibilities")
public class Responsibility extends ProjectRecord {

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 80)
    private String owner;

    private LocalDate startDate;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WorkStatus status = WorkStatus.NOT_STARTED;

    @Column(columnDefinition = "TEXT")
    private String deliverables;

    public void update(String title, String description, String owner, LocalDate startDate, LocalDate dueDate,
                       Priority priority, WorkStatus status, String deliverables) {
        this.title = title;
        this.description = description;
        this.owner = owner;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.priority = priority;
        this.status = status;
        this.deliverables = deliverables;
    }

    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getOwner() { return owner; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public Priority getPriority() { return priority; }
    public WorkStatus getStatus() { return status; }
    public String getDeliverables() { return deliverables; }
}
