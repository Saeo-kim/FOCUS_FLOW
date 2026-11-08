package com.example.project_flow.dto;

import com.example.project_flow.entity.CheckStatus;
import com.example.project_flow.entity.DeadlineCategory;
import com.example.project_flow.entity.DeadlineScope;
import com.example.project_flow.entity.ErrorStatus;
import com.example.project_flow.entity.EvidenceType;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.WorkStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Request and response shapes share field names, so the edit forms can be filled from a response. */
public final class RecordDtos {

    private static final String TOO_LONG = "That text is too long.";
    private static final String NEED_PROJECT = "Choose a project.";

    private RecordDtos() {
    }

    public interface ProjectRecordRequest {
        Long projectId();
    }

    public record PhaseRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotBlank(message = "Enter a phase name.") @Size(max = 120, message = TOO_LONG) String name,
            @NotNull(message = "Enter a start date.") LocalDate startDate,
            @NotNull(message = "Enter an end date.") LocalDate endDate,
            @Size(max = 2000, message = TOO_LONG) String items) implements ProjectRecordRequest {
    }

    public record PhaseResponse(Long id, Long projectId, String name, LocalDate startDate, LocalDate endDate, String items) {
    }

    public record TeamRoleRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotBlank(message = "Enter a role.") @Size(max = 80, message = TOO_LONG) String roleName,
            @Size(max = 1000, message = TOO_LONG) String duties,
            boolean mine) implements ProjectRecordRequest {
    }

    public record TeamRoleResponse(Long id, Long projectId, String roleName, String duties, boolean mine) {
    }

    public record ResponsibilityRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotBlank(message = "Enter a responsibility title.") @Size(max = 200, message = TOO_LONG) String title,
            @Size(max = 5000, message = TOO_LONG) String description,
            @Size(max = 80, message = TOO_LONG) String owner,
            LocalDate startDate,
            LocalDate dueDate,
            @NotNull(message = "Choose a priority.") Priority priority,
            @NotNull(message = "Choose a status.") WorkStatus status,
            @Size(max = 5000, message = TOO_LONG) String deliverables) implements ProjectRecordRequest {
    }

    public record ResponsibilityResponse(
            Long id, Long projectId, String title, String description, String owner, LocalDate startDate,
            LocalDate dueDate, Priority priority, WorkStatus status, String deliverables,
            long taskCount, long completedTaskCount, int progress) {
    }

    public record DeadlineRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotBlank(message = "Enter a title.") @Size(max = 200, message = TOO_LONG) String title,
            @NotNull(message = "Choose a category.") DeadlineCategory category,
            @NotNull(message = "Choose team or personal.") DeadlineScope scope,
            @NotNull(message = "Enter a due date.") LocalDate dueDate,
            @Size(max = 200, message = TOO_LONG) String owner,
            @Size(max = 1000, message = TOO_LONG) String personalAction,
            boolean done,
            @Size(max = 1000, message = TOO_LONG) String note) implements ProjectRecordRequest {
    }

    public record DeadlineResponse(
            Long id, Long projectId, String title, DeadlineCategory category, DeadlineScope scope, LocalDate dueDate,
            String owner, String personalAction, boolean done, String note) {
    }

    public record RequirementRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotBlank(message = "Enter a requirement name.") @Size(max = 200, message = TOO_LONG) String title,
            @Size(max = 5000, message = TOO_LONG) String detail,
            @Size(max = 200, message = TOO_LONG) String relatedFeature,
            Long responsibilityId,
            Long taskId,
            boolean completed,
            boolean tested,
            boolean verified,
            @Size(max = 5000, message = TOO_LONG) String verificationResult,
            @Size(max = 5000, message = TOO_LONG) String evidenceNote) implements ProjectRecordRequest {
    }

    public record RequirementResponse(
            Long id, Long projectId, String title, String detail, String relatedFeature,
            Long responsibilityId, String responsibilityTitle, Long taskId, String taskTitle,
            boolean completed, boolean tested, boolean verified, String verificationResult, String evidenceNote) {
    }

    public record ErrorLogRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotNull(message = "Enter the date it happened.") LocalDate occurredOn,
            Long taskId,
            @NotBlank(message = "Enter an error title.") @Size(max = 200, message = TOO_LONG) String title,
            @Size(max = 10000, message = TOO_LONG) String situation,
            @Size(max = 10000, message = TOO_LONG) String cause,
            @Size(max = 10000, message = TOO_LONG) String attempts,
            @Size(max = 10000, message = TOO_LONG) String solution,
            @Size(max = 5000, message = TOO_LONG) String changedFiles,
            @Size(max = 5000, message = TOO_LONG) String verification,
            @Size(max = 5000, message = TOO_LONG) String result,
            @Size(max = 5000, message = TOO_LONG) String links,
            @NotNull(message = "Choose a status.") ErrorStatus status) implements ProjectRecordRequest {
    }

    public record ErrorEventRequest(
            @NotBlank(message = "Describe what happened.") @Size(max = 1000, message = TOO_LONG) String note) {
    }

    public record ErrorEventResponse(LocalDateTime happenedAt, String note) {
    }

    public record ErrorLogResponse(
            Long id, Long projectId, LocalDate occurredOn, Long taskId, String taskTitle, String title, String situation,
            String cause, String attempts, String solution, String changedFiles, String verification, String result,
            String links, ErrorStatus status, List<ErrorEventResponse> events) {
    }

    public record AiVerificationRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotBlank(message = "Enter a work title.") @Size(max = 200, message = TOO_LONG) String title,
            Long taskId,
            @Size(max = 100, message = TOO_LONG) String aiTool,
            @Size(max = 10000, message = TOO_LONG) String requestText,
            @Size(max = 5000, message = TOO_LONG) String changedFiles,
            @Size(max = 10000, message = TOO_LONG) String aiActions,
            @Size(max = 5000, message = TOO_LONG) String testItems,
            @Size(max = 10000, message = TOO_LONG) String testResult,
            @NotNull(message = "Choose a test status.") CheckStatus testStatus,
            @Size(max = 10000, message = TOO_LONG) String buildResult,
            @NotNull(message = "Choose a build status.") CheckStatus buildStatus,
            @Size(max = 10000, message = TOO_LONG) String humanReview,
            boolean verified,
            @Size(max = 5000, message = TOO_LONG) String followUp,
            boolean approved) implements ProjectRecordRequest {
    }

    public record AiVerificationResponse(
            Long id, Long projectId, String title, Long taskId, String taskTitle, String aiTool, String requestText,
            String changedFiles, String aiActions, String testItems, String testResult, CheckStatus testStatus,
            String buildResult, CheckStatus buildStatus, String humanReview, boolean verified, String followUp,
            boolean approved, LocalDateTime createdAt) {
    }

    public record DailyReportRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotNull(message = "Enter the report date.") LocalDate reportDate,
            @Size(max = 10000, message = TOO_LONG) String goal,
            @Size(max = 10000, message = TOO_LONG) String actualWork,
            @Size(max = 10000, message = TOO_LONG) String problemsSolved,
            @Size(max = 10000, message = TOO_LONG) String incompleteReason,
            @Size(max = 10000, message = TOO_LONG) String nextWork,
            @Size(max = 5000, message = TOO_LONG) String evidenceNote) implements ProjectRecordRequest {
    }

    /** {@code completedTasks}, {@code tasksInProgress} and {@code errors} are derived, never stored. */
    public record DailyReportResponse(
            Long id, Long projectId, LocalDate reportDate, String goal, String actualWork, String problemsSolved,
            String incompleteReason, String nextWork, String evidenceNote,
            List<String> completedTasks, List<String> tasksInProgress, List<String> errors) {
    }

    public record EvidenceRequest(
            @NotNull(message = NEED_PROJECT) Long projectId,
            @NotNull(message = "Choose a type.") EvidenceType type,
            @NotBlank(message = "Enter a title.") @Size(max = 200, message = TOO_LONG) String title,
            @NotBlank(message = "Enter a link or file path.") @Size(max = 1000, message = TOO_LONG) String location,
            @Size(max = 1000, message = TOO_LONG) String note,
            Long taskId,
            Long responsibilityId) implements ProjectRecordRequest {
    }

    public record EvidenceResponse(
            Long id, Long projectId, EvidenceType type, String title, String location, String note,
            Long taskId, String taskTitle, Long responsibilityId, String responsibilityTitle, LocalDateTime createdAt) {
    }

    public record StepRequest(
            @NotBlank(message = "Enter a step title.") @Size(max = 200, message = TOO_LONG) String title,
            @Size(max = 5000, message = TOO_LONG) String description,
            WorkStatus status,
            LocalDate startDate,
            LocalDate completedDate,
            Integer progress,
            @Size(max = 5000, message = TOO_LONG) String result,
            @Size(max = 5000, message = TOO_LONG) String errorNote,
            @Size(max = 1000, message = TOO_LONG) String link,
            @Size(max = 5000, message = TOO_LONG) String memo) {
    }

    public record StepResponse(
            Long id, Long taskId, String title, String description, WorkStatus status, LocalDate startDate,
            LocalDate completedDate, int progress, String result, String errorNote, String link, String memo, int sortOrder) {
    }

    public record StepMoveRequest(int direction) {
    }

    public record TemplateRequest(
            @NotBlank(message = "Enter a template name.") @Size(max = 120, message = TOO_LONG) String name,
            @Size(max = 1000, message = TOO_LONG) String description,
            @NotNull(message = "Add at least one step.") @Size(min = 1, message = "Add at least one step.") List<String> steps) {
    }

    public record TemplateResponse(Long id, String name, String description, List<String> steps) {
    }
}
