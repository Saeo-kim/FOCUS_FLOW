package com.example.project_flow.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SettingsDto(
        @NotBlank(message = "Enter a display name.")
        @Size(max = 60, message = "Display names can be at most 60 characters.")
        String displayName,
        @Min(value = 60, message = "The daily target must be at least 1 hour.")
        @Max(value = 1440, message = "The daily target cannot exceed 24 hours.")
        int dailyTargetMinutes) {
}
