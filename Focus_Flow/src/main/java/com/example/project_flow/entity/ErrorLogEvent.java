package com.example.project_flow.entity;

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

@Entity
@Table(name = "error_log_events")
public class ErrorLogEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "error_log_id", nullable = false)
    private ErrorLog errorLog;

    @Column(nullable = false, length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime happenedAt;

    protected ErrorLogEvent() {
    }

    ErrorLogEvent(ErrorLog errorLog, String note, LocalDateTime happenedAt) {
        this.errorLog = errorLog;
        this.note = note;
        this.happenedAt = happenedAt;
    }

    public Long getId() { return id; }
    public String getNote() { return note; }
    public LocalDateTime getHappenedAt() { return happenedAt; }
}
