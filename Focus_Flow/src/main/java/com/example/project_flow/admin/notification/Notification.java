package com.example.project_flow.admin.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A bell-menu item. {@code recipientUserId} null means "every administrator", which is the only
 * case today; a member account would receive notices addressed to its own id.
 */
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long recipientUserId;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String message;

    /** A hash route inside the admin page, such as "#/submission?id=3". */
    @Column(length = 300)
    private String link;

    /** Prevents generated reminders from being created twice. */
    @Column(unique = true, length = 120)
    private String dedupeKey;

    private LocalDateTime readAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Notification() {
    }

    public Notification(Long recipientUserId, String type, String title, String message, String link,
                        String dedupeKey, LocalDateTime createdAt) {
        this.recipientUserId = recipientUserId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.dedupeKey = dedupeKey;
        this.createdAt = createdAt;
    }

    public void markRead(LocalDateTime at) {
        if (readAt == null) {
            readAt = at;
        }
    }

    public Long getId() { return id; }
    public Long getRecipientUserId() { return recipientUserId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public String getLink() { return link; }
    public String getDedupeKey() { return dedupeKey; }
    public LocalDateTime getReadAt() { return readAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
