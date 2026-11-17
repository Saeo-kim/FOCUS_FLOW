package com.example.project_flow.admin.account;

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
 * Someone who signs in to FOCUS FLOW: an ADMIN who manages, or a MEMBER who executes.
 * Members are invited by an administrator and choose their own password with a one-time code.
 * Columns added after the first release are nullable so existing rows stay valid.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String username;

    @Column(unique = true, length = 120)
    private String email;

    @Column(nullable = false, length = 60)
    private String displayName;

    @Column(nullable = false, length = 200)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    /** Null on rows created before this column existed, which counts as active. */
    private Boolean active;

    /** False while an invited member has not chosen a password yet. Null on older rows means set. */
    private Boolean passwordSet;

    /** SHA-256 of the current invitation or reset code; the code itself is never stored. */
    @Column(unique = true, length = 64)
    private String inviteCodeHash;

    private LocalDateTime inviteExpiresAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastLoginAt;

    /** Null on older rows means HUMAN. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AccountKind kind;

    /** Job title, such as "Exam Coach". Free text, separate from the role, which sets permissions. */
    @Column(length = 60)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Department department;

    /** One emoji or up to two letters shown in the avatar circle. Null shows the first letter of the name. */
    @Column(length = 16)
    private String avatar;

    /** Avatar background as #rrggbb. */
    @Column(length = 7)
    private String avatarColor;

    protected AppUser() {
    }

    public AppUser(String username, String displayName, String passwordHash, UserRole role, LocalDateTime createdAt) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.createdAt = createdAt;
        this.active = true;
        this.passwordSet = true;
    }

    public void rename(String displayName) { this.displayName = displayName; }
    public void changeEmail(String email) { this.email = email; }
    public void setActive(boolean active) { this.active = active; }
    public void signedIn(LocalDateTime at) { this.lastLoginAt = at; }

    public void assignJob(AccountKind kind, String title, Department department) {
        this.kind = kind;
        this.title = title;
        this.department = department;
    }

    /**
     * Makes this account an AI seat: no password can match and no invitation code is issued, so it
     * can never sign in. Work reaches it through task assignment instead.
     */
    public void markAiSeat(String unusableHash) {
        this.passwordHash = unusableHash;
        this.passwordSet = false;
        this.inviteCodeHash = null;
        this.inviteExpiresAt = null;
    }

    public boolean isAi() {
        return getKind() == AccountKind.AI;
    }

    public void changeAvatar(String avatar, String avatarColor) {
        this.avatar = avatar;
        this.avatarColor = avatarColor;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.passwordSet = true;
        this.inviteCodeHash = null;
        this.inviteExpiresAt = null;
    }

    /** Issues a one-time code; with {@code lockPassword} the old password stops working until the code is used. */
    public void issueCode(String codeHash, LocalDateTime expiresAt, boolean lockPassword, String unusableHash) {
        this.inviteCodeHash = codeHash;
        this.inviteExpiresAt = expiresAt;
        if (lockPassword) {
            this.passwordHash = unusableHash;
            this.passwordSet = false;
        }
    }

    public boolean isActive() { return active == null || active; }
    public boolean isPasswordSet() { return passwordSet == null || passwordSet; }
    public boolean hasPendingCode() { return inviteCodeHash != null; }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public UserRole getRole() { return role; }
    public String getInviteCodeHash() { return inviteCodeHash; }
    public LocalDateTime getInviteExpiresAt() { return inviteExpiresAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public AccountKind getKind() { return kind == null ? AccountKind.HUMAN : kind; }
    public String getTitle() { return title; }
    public Department getDepartment() { return department; }
    public String getAvatar() { return avatar; }
    public String getAvatarColor() { return avatarColor; }
}
