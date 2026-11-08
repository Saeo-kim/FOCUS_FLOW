package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

@Entity
@Table(name = "evidence")
public class Evidence extends ProjectRecord {

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EvidenceType type = EvidenceType.OTHER;

    @Column(nullable = false, length = 200)
    private String title;

    /** A URL or a file path. */
    @Column(nullable = false, length = 1000)
    private String location;

    @Column(length = 1000)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsibility_id")
    private Responsibility responsibility;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Evidence() {
    }

    public Evidence(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void update(EvidenceType type, String title, String location, String note,
                       Task task, Responsibility responsibility) {
        this.type = type;
        this.title = title;
        this.location = location;
        this.note = note;
        this.task = task;
        this.responsibility = responsibility;
    }

    public EvidenceType getType() { return type; }
    public String getTitle() { return title; }
    public String getLocation() { return location; }
    public String getNote() { return note; }
    public Task getTask() { return task; }
    public Responsibility getResponsibility() { return responsibility; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
