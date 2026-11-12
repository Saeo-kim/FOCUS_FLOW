package com.example.project_flow.admin.submission;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** The review history: every submit, resubmit, approval, rejection and reopen, in order. */
@Entity
@Table(name = "submission_events")
public class SubmissionEvent {

    public enum Type { SUBMITTED, RESUBMITTED, APPROVED, REJECTED, REOPENED, EDITED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id", nullable = false)
    private Submission submission;

    @Column(nullable = false, length = 20)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Type type;

    @Column(nullable = false)
    private int version;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected SubmissionEvent() {
    }

    public SubmissionEvent(Submission submission, Type type, String comment, String actor, LocalDateTime createdAt) {
        this.submission = submission;
        this.type = type;
        this.version = submission.getCurrentVersion();
        this.comment = comment;
        this.actor = actor;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public Submission getSubmission() { return submission; }
    public Type getType() { return type; }
    public int getVersion() { return version; }
    public String getComment() { return comment; }
    public String getActor() { return actor; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
