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

@Entity
@Table(name = "work_steps")
public class WorkStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WorkStatus status = WorkStatus.NOT_STARTED;

    private LocalDate startDate;

    private LocalDate completedDate;

    @Column(nullable = false)
    private int progress;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String errorNote;

    @Column(length = 1000)
    private String link;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @Column(nullable = false)
    private int sortOrder;

    protected WorkStep() {
    }

    public WorkStep(Task task, String title, int sortOrder) {
        this.task = task;
        this.title = title;
        this.sortOrder = sortOrder;
    }

    public void describe(String title, String description, String result, String errorNote, String link, String memo) {
        this.title = title;
        this.description = description;
        this.result = result;
        this.errorNote = errorNote;
        this.link = link;
        this.memo = memo;
    }

    /**
     * Only a completed step counts as 100%, so progress cannot claim more than the status does.
     * Dates fill in from the status unless the person entered their own.
     */
    public void track(WorkStatus status, int requestedProgress, LocalDate startDate, LocalDate completedDate, LocalDate today) {
        this.status = status;
        this.progress = switch (status) {
            case COMPLETED -> 100;
            case NOT_STARTED -> 0;
            default -> Math.clamp(requestedProgress, 0, 99);
        };
        this.startDate = startDate != null || status == WorkStatus.NOT_STARTED ? startDate : today;
        if (status == WorkStatus.COMPLETED) {
            this.completedDate = completedDate != null ? completedDate : today;
        } else {
            this.completedDate = null;
        }
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isCompleted() {
        return status == WorkStatus.COMPLETED;
    }

    public Long getId() { return id; }
    public Task getTask() { return task; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public WorkStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getCompletedDate() { return completedDate; }
    public int getProgress() { return progress; }
    public String getResult() { return result; }
    public String getErrorNote() { return errorNote; }
    public String getLink() { return link; }
    public String getMemo() { return memo; }
    public int getSortOrder() { return sortOrder; }
}
