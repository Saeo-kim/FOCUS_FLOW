package com.example.project_flow.admin.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record SetupRequest(
            @NotBlank(message = "Enter a username.")
            @Pattern(regexp = "[A-Za-z0-9._-]{3,40}", message = "Use 3–40 letters, numbers, dots, dashes or underscores for the username.")
            String username,
            @NotBlank(message = "Enter your name.") @Size(max = 60, message = "Keep the name under 60 characters.")
            String displayName,
            @NotBlank(message = "Enter a password.") @Size(min = 8, max = 128, message = "Use a password of at least 8 characters.")
            String password) {
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your username.") String username,
            @NotBlank(message = "Enter your password.") String password) {
    }

    public record ProfileRequest(
            @NotBlank(message = "Enter your name.") @Size(max = 60, message = "Keep the name under 60 characters.")
            String displayName) {
    }

    public record PasswordRequest(
            @NotBlank(message = "Enter your current password.") String currentPassword,
            @NotBlank(message = "Enter a new password.") @Size(min = 8, max = 128, message = "Use a new password of at least 8 characters.")
            String newPassword) {
    }

    public record UserResponse(Long id, String username, String displayName, UserRole role,
                               LocalDateTime createdAt, LocalDateTime lastLoginAt) {
        public static UserResponse from(AppUser user) {
            return new UserResponse(user.getId(), user.getUsername(), user.getDisplayName(), user.getRole(),
                    user.getCreatedAt(), user.getLastLoginAt());
        }
    }

    /** {@code setupRequired} is true until the first administrator account exists. */
    public record AuthStatus(boolean setupRequired, boolean authenticated, UserResponse user) {
    }
}
