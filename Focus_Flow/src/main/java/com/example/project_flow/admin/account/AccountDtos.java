package com.example.project_flow.admin.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AccountDtos {

    private AccountDtos() {
    }

    private static final String USERNAME = "[A-Za-z0-9._-]{3,40}";
    private static final String USERNAME_MESSAGE = "Use 3–40 letters, numbers, dots, dashes or underscores for the username.";

    public record SetupRequest(
            @NotBlank(message = "Enter a username.") @Pattern(regexp = USERNAME, message = USERNAME_MESSAGE) String username,
            @NotBlank(message = "Enter your name.") @Size(max = 60, message = "Keep the name under 60 characters.") String displayName,
            @NotBlank(message = "Enter a password.") @Size(min = 8, max = 128, message = "Use a password of at least 8 characters.") String password) {
    }

    /** {@code username} accepts the username or the email address. */
    public record LoginRequest(
            @NotBlank(message = "Enter your username or email.") String username,
            @NotBlank(message = "Enter your password.") String password) {
    }

    public record InvitationRequest(
            @NotBlank(message = "Enter the code you received.") String code,
            @NotBlank(message = "Choose a password.") @Size(min = 8, max = 128, message = "Use a password of at least 8 characters.") String password) {
    }

    public record ProfileRequest(
            @NotBlank(message = "Enter your name.") @Size(max = 60, message = "Keep the name under 60 characters.") String displayName) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Enter your current password.") String currentPassword,
            @NotBlank(message = "Enter a new password.") @Size(min = 8, max = 128, message = "Use a new password of at least 8 characters.") String newPassword) {
    }

    public record MemberCreateRequest(
            @NotBlank(message = "Enter a username.") @Pattern(regexp = USERNAME, message = USERNAME_MESSAGE) String username,
            @NotBlank(message = "Enter the member's name.") @Size(max = 60, message = "Keep the name under 60 characters.") String displayName,
            @Email(message = "Enter a valid email address.") @Size(max = 120, message = "Keep the email under 120 characters.") String email,
            UserRole role) {
    }

    public record MemberUpdateRequest(
            @NotBlank(message = "Enter the member's name.") @Size(max = 60, message = "Keep the name under 60 characters.") String displayName,
            @Email(message = "Enter a valid email address.") @Size(max = 120, message = "Keep the email under 120 characters.") String email,
            Boolean active) {
    }

    public record UserResponse(Long id, String username, String email, String displayName, UserRole role,
                               boolean active, boolean passwordSet, boolean codePending, LocalDateTime codeExpiresAt,
                               LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getEmail(), user.getDisplayName(), user.getRole(),
                    user.isActive(), user.isPasswordSet(), user.hasPendingCode(), user.getInviteExpiresAt(),
                    user.getCreatedAt(), user.getLastLoginAt());
        }
    }

    /** The one-time code is returned only in this response, right after it is issued. */
    public record IssuedCode(UserResponse user, String code, LocalDateTime expiresAt) {
    }

    /**
     * {@code setupRequired} is true until the first administrator exists; {@code home} is where
     * the signed-in user belongs ("/admin" or "/member").
     */
    public record AuthStatus(boolean setupRequired, boolean authenticated, UserResponse user, String home) {
    }
}
