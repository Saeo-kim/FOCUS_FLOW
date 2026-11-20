package com.example.project_flow.legal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One specialist responsibility under a position. */
@Entity
@Table(name = "org_position_functions")
public class OrgPositionFunction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String positionKey;

    @Column(nullable = false, length = 60)
    private String functionKey;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int sortOrder;

    protected OrgPositionFunction() {
    }

    public OrgPositionFunction(String positionKey, String functionKey, String title, String description, int sortOrder) {
        this.positionKey = positionKey;
        this.functionKey = functionKey;
        this.title = title;
        this.description = description;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getPositionKey() { return positionKey; }
    public String getFunctionKey() { return functionKey; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
}
