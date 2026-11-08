package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/** A dated commitment: a deliverable, report, deployment or presentation. */
@Entity
@Table(name = "deadlines")
public class Deadline extends ProjectRecord {

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private DeadlineCategory category = DeadlineCategory.MILESTONE;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private DeadlineScope scope = DeadlineScope.TEAM;

    @Column(nullable = false)
    private LocalDate dueDate;

    @Column(length = 200)
    private String owner;

    /** What the user personally has to write or check for this item. */
    @Column(length = 1000)
    private String personalAction;

    @Column(nullable = false)
    private boolean done;

    @Column(length = 1000)
    private String note;

    public void update(String title, DeadlineCategory category, DeadlineScope scope, LocalDate dueDate,
                       String owner, String personalAction, boolean done, String note) {
        this.title = title;
        this.category = category;
        this.scope = scope;
        this.dueDate = dueDate;
        this.owner = owner;
        this.personalAction = personalAction;
        this.done = done;
        this.note = note;
    }

    public String getTitle() { return title; }
    public DeadlineCategory getCategory() { return category; }
    public DeadlineScope getScope() { return scope; }
    public LocalDate getDueDate() { return dueDate; }
    public String getOwner() { return owner; }
    public String getPersonalAction() { return personalAction; }
    public boolean isDone() { return done; }
    public String getNote() { return note; }
}
