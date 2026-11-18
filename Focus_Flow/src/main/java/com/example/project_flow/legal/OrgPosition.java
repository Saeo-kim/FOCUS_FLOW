package com.example.project_flow.legal;

import com.example.project_flow.admin.account.Department;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A position in the organization. {@code assigneeUserId} stays null until the owner assigns
 * someone; FOCUS FLOW never fills a position on its own.
 */
@Entity
@Table(name = "org_positions")
public class OrgPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "position_key", nullable = false, unique = true, length = 60)
    private String positionKey;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 120)
    private String koreanTitle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Department department;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mission;

    @Column(nullable = false, length = 10)
    private String status = "VACANT";

    private Long assigneeUserId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected OrgPosition() {
    }

    public OrgPosition(String positionKey, String title, String koreanTitle, Department department, String mission, LocalDateTime now) {
        this.positionKey = positionKey;
        this.title = title;
        this.koreanTitle = koreanTitle;
        this.department = department;
        this.mission = mission;
        this.createdAt = now;
    }

    public Long getId() { return id; }
    public String getPositionKey() { return positionKey; }
    public String getTitle() { return title; }
    public String getKoreanTitle() { return koreanTitle; }
    public Department getDepartment() { return department; }
    public String getMission() { return mission; }
    public String getStatus() { return status; }
    public Long getAssigneeUserId() { return assigneeUserId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
