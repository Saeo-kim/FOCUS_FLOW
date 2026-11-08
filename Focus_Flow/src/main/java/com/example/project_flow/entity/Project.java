package com.example.project_flow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private ProjectStatus status = ProjectStatus.ACTIVE;

    private LocalDate dueDate;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(length = 80)
    private String projectType;

    @Column(length = 80)
    private String teamName;

    private Integer teamNumber;

    private LocalDate startDate;

    private LocalDate devStartDate;

    private LocalDate devEndDate;

    @Column(length = 1000)
    private String techStack;

    @Column(length = 1000)
    private String serviceFeatures;

    protected Project() {
    }

    public Project(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void update(String name, String description, ProjectStatus status, LocalDate dueDate) {
        this.name = name;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
    }

    public void updateDetails(String projectType, String teamName, Integer teamNumber, LocalDate startDate,
                              LocalDate devStartDate, LocalDate devEndDate, String techStack, String serviceFeatures) {
        this.projectType = projectType;
        this.teamName = teamName;
        this.teamNumber = teamNumber;
        this.startDate = startDate;
        this.devStartDate = devStartDate;
        this.devEndDate = devEndDate;
        this.techStack = techStack;
        this.serviceFeatures = serviceFeatures;
    }

    public String getProjectType() { return projectType; }
    public String getTeamName() { return teamName; }
    public Integer getTeamNumber() { return teamNumber; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDevStartDate() { return devStartDate; }
    public LocalDate getDevEndDate() { return devEndDate; }
    public String getTechStack() { return techStack; }
    public String getServiceFeatures() { return serviceFeatures; }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
