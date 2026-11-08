package com.example.project_flow.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "error_logs")
public class ErrorLog extends ProjectRecord {

    @Column(nullable = false)
    private LocalDate occurredOn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String situation;

    @Column(columnDefinition = "TEXT")
    private String cause;

    @Column(columnDefinition = "TEXT")
    private String attempts;

    @Column(columnDefinition = "TEXT")
    private String solution;

    @Column(columnDefinition = "TEXT")
    private String changedFiles;

    @Column(columnDefinition = "TEXT")
    private String verification;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String links;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private ErrorStatus status = ErrorStatus.OPEN;

    @OneToMany(mappedBy = "errorLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("happenedAt ASC, id ASC")
    private List<ErrorLogEvent> events = new ArrayList<>();

    public void update(LocalDate occurredOn, Task task, String title, String situation, String cause, String attempts,
                       String solution, String changedFiles, String verification, String result, String links) {
        this.occurredOn = occurredOn;
        this.task = task;
        this.title = title;
        this.situation = situation;
        this.cause = cause;
        this.attempts = attempts;
        this.solution = solution;
        this.changedFiles = changedFiles;
        this.verification = verification;
        this.result = result;
        this.links = links;
    }

    /** Status changes are written to the timeline so the path to the fix stays readable later. */
    public void changeStatus(ErrorStatus next, LocalDateTime now) {
        if (next != status) {
            status = next;
            addEvent(next == ErrorStatus.RESOLVED ? "Marked as resolved" : "Reopened", now);
        }
    }

    public void addEvent(String note, LocalDateTime now) {
        events.add(new ErrorLogEvent(this, note, now));
    }

    public LocalDate getOccurredOn() { return occurredOn; }
    public Task getTask() { return task; }
    public String getTitle() { return title; }
    public String getSituation() { return situation; }
    public String getCause() { return cause; }
    public String getAttempts() { return attempts; }
    public String getSolution() { return solution; }
    public String getChangedFiles() { return changedFiles; }
    public String getVerification() { return verification; }
    public String getResult() { return result; }
    public String getLinks() { return links; }
    public ErrorStatus getStatus() { return status; }
    public List<ErrorLogEvent> getEvents() { return events; }
}
