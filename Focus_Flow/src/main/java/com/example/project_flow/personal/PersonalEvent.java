package com.example.project_flow.personal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * A member's own calendar item for daily life (errands, appointments, health, study...). It belongs to
 * one account: only that account can see, change or delete it. It never appears in the shared calendar.
 */
@Entity
@Table(name = "personal_events")
public class PersonalEvent {

    public enum Category { DAILY, HEALTH, FINANCE, FAMILY, LEARNING, OTHER }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false)
    private LocalDate eventDate;

    private LocalTime startTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Category category = Category.OTHER;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private boolean done;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected PersonalEvent() {
    }

    public PersonalEvent(Long userId, String title, LocalDate eventDate, LocalTime startTime, Category category,
                         String note, LocalDateTime now) {
        this.userId = userId;
        this.title = title;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.category = category;
        this.note = note;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void update(String title, LocalDate eventDate, LocalTime startTime, Category category, String note, LocalDateTime now) {
        this.title = title;
        this.eventDate = eventDate;
        this.startTime = startTime;
        this.category = category;
        this.note = note;
        this.updatedAt = now;
    }

    public void setDone(boolean done, LocalDateTime now) {
        this.done = done;
        this.updatedAt = now;
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getTitle() { return title; }
    public LocalDate getEventDate() { return eventDate; }
    public LocalTime getStartTime() { return startTime; }
    public Category getCategory() { return category; }
    public String getNote() { return note; }
    public boolean isDone() { return done; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
