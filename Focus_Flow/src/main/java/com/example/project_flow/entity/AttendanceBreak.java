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

import java.time.Duration;
import java.time.LocalDateTime;

@Entity
@Table(name = "attendance_breaks")
public class AttendanceBreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "record_id", nullable = false)
    private AttendanceRecord record;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    protected AttendanceBreak() {
    }

    AttendanceBreak(AttendanceRecord record, LocalDateTime startedAt) {
        this.record = record;
        this.startedAt = startedAt;
    }

    void end(LocalDateTime at) {
        this.endedAt = at;
    }

    boolean isOpen() {
        return endedAt == null;
    }

    /** Length of this break; a break still in progress is measured up to {@code now}. */
    public long seconds(LocalDateTime now) {
        LocalDateTime end = endedAt != null ? endedAt : now;
        return Math.max(0, Duration.between(startedAt, end).getSeconds());
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }
}
