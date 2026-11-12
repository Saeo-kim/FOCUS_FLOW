package com.example.project_flow.admin.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Who is responsible for a task: one person per task. Kept beside the existing tasks table rather
 * than in it, so no existing column or row changes; a deleted task simply leaves this row unused.
 */
@Entity
@Table(name = "task_assignments")
public class TaskAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long taskId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    @Column(length = 60)
    private String assignedBy;

    protected TaskAssignment() {
    }

    public TaskAssignment(Long taskId, Long userId, LocalDateTime assignedAt, String assignedBy) {
        this.taskId = taskId;
        this.userId = userId;
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
    }

    public void reassign(Long userId, LocalDateTime at, String by) {
        this.userId = userId;
        this.assignedAt = at;
        this.assignedBy = by;
    }

    public Long getId() { return id; }
    public Long getTaskId() { return taskId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public String getAssignedBy() { return assignedBy; }
}
