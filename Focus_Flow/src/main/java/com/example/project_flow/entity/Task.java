package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.MEDIUM;

    @Column(nullable = false)
    private boolean done;

    private LocalDate dueDate;

    private LocalDate scheduledDate;

    private LocalDateTime completedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    /** Null on tasks created before statuses existed; see {@link #getStatus()}. */
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(length = 20)
    private WorkStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsibility_id")
    private Responsibility responsibility;

    protected Task() {
    }

    public Task(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void update(String title, String description, Priority priority,
                       LocalDate dueDate, LocalDate scheduledDate, Project project) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueDate = dueDate;
        this.scheduledDate = scheduledDate;
        this.project = project;
    }

    public void markDone(boolean done, LocalDateTime now) {
        changeStatus(done ? WorkStatus.COMPLETED : WorkStatus.IN_PROGRESS, now);
    }

    /** Keeps the status and the original done flag in step; every existing view reads done. */
    public void changeStatus(WorkStatus next, LocalDateTime now) {
        boolean completing = next == WorkStatus.COMPLETED;
        if (completing != done) {
            done = completing;
            completedAt = completing ? now : null;
        }
        status = next;
    }

    public void assignResponsibility(Responsibility responsibility) {
        this.responsibility = responsibility;
    }

    public WorkStatus getStatus() {
        if (status != null) {
            return status;
        }
        return done ? WorkStatus.COMPLETED : WorkStatus.NOT_STARTED;
    }

    public Responsibility getResponsibility() {
        return responsibility;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Priority getPriority() {
        return priority;
    }

    public boolean isDone() {
        return done;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDate getScheduledDate() {
        return scheduledDate;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Project getProject() {
        return project;
    }
}
