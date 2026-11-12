package com.example.project_flow.admin.work;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A person on a project's team. Plain ids, no foreign keys: the existing projects table gains no
 * constraint, so deleting a project in FOCUS FLOW works exactly as before.
 */
@Entity
@Table(name = "project_members")
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private LocalDateTime addedAt;

    protected ProjectMember() {
    }

    public ProjectMember(Long projectId, Long userId, LocalDateTime addedAt) {
        this.projectId = projectId;
        this.userId = userId;
        this.addedAt = addedAt;
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public Long getUserId() { return userId; }
    public LocalDateTime getAddedAt() { return addedAt; }
}
