package com.example.project_flow.aiteam;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.admin.notification.NotificationService;
import com.example.project_flow.admin.work.TaskComment;
import com.example.project_flow.admin.work.TaskCommentRepository;
import com.example.project_flow.admin.work.WorkAccessService;
import com.example.project_flow.dto.TaskDtos.TaskRequest;
import com.example.project_flow.dto.TaskDtos.TaskResponse;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkStatus;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.integration.ownersoffice.OfficeTask;
import com.example.project_flow.integration.ownersoffice.OwnersOfficeClient;
import com.example.project_flow.integration.ownersoffice.OwnersOfficeException;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.repository.WorkStepRepository;
import com.example.project_flow.service.ActivityLogService;
import com.example.project_flow.service.TaskService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Sends work to AI teammates and brings their results back.
 * <ul>
 *   <li>Assigning a task to a teammate, or mentioning @CFO in a comment, creates a request.</li>
 *   <li>A request is sent to Owner's Office with an idempotency key. If that fails it stays
 *       QUEUED and the sync job retries it.</li>
 *   <li>The sync job reads progress. The task status follows it, and a report comes back as a
 *       comment from the teammate.</li>
 * </ul>
 */
@Service
public class AiRequestService {

    private static final Logger log = LoggerFactory.getLogger(AiRequestService.class);
    private static final int REPORT_NOTE_LIMIT = 1800;

    public record RequestView(Long id, String agentKey, String kind, String kindLabel, String title, Long taskId,
                              String taskTitle, String status, String summary, JsonNode result, String lastError,
                              int attempts, String requestedBy, LocalDateTime createdAt, LocalDateTime updatedAt,
                              LocalDateTime completedAt) {
    }

    public record SyncResult(int sent, int updated) {
    }

    private final AiRequestRepository requests;
    private final TaskRepository tasks;
    private final WorkStepRepository steps;
    private final TaskService taskService;
    private final WorkAccessService access;
    private final TaskCommentRepository comments;
    private final AiTeamService team;
    private final AiTeamCatalog catalog;
    private final AppUserRepository users;
    private final OwnersOfficeClient office;
    private final NotificationService notifications;
    private final ActivityLogService activity;
    private final ObjectMapper json;
    private final Clock clock;

    public AiRequestService(AiRequestRepository requests, TaskRepository tasks, WorkStepRepository steps,
                            TaskService taskService, WorkAccessService access, TaskCommentRepository comments,
                            AiTeamService team, AiTeamCatalog catalog, AppUserRepository users,
                            OwnersOfficeClient office, NotificationService notifications, ActivityLogService activity,
                            ObjectMapper json, Clock clock) {
        this.requests = requests;
        this.tasks = tasks;
        this.steps = steps;
        this.taskService = taskService;
        this.access = access;
        this.comments = comments;
        this.team = team;
        this.catalog = catalog;
        this.users = users;
        this.office = office;
        this.notifications = notifications;
        this.activity = activity;
        this.json = json;
        this.clock = clock;
    }

    /** Asks a teammate to do work on an existing task. Sends it at once when possible. */
    @Transactional
    public AiRequest ask(AppUser requester, Long taskId, String agentKey, String kind, String title,
                         String instructions, Long commentId) {
        AppUser seat = team.requireSeat(agentKey);
        if (!AiRequestKinds.isValidFor(agentKey, kind)) {
            throw new ConflictException("That report type is not available for this teammate.");
        }
        AiRequest request = requests.save(new AiRequest(agentKey, kind, title, instructions, taskId, commentId,
                requester.getId(), now()));
        activity.record("AI_REQUEST", requester.getDisplayName() + " asked " + seat.getDisplayName() + ": \"" + title + "\"");
        send(request);
        return request;
    }

    /** Creates a new task for a teammate, assigns it, and sends the request. */
    @Transactional
    public AiRequest askNew(AppUser actor, String agentKey, String kind, String title, String instructions, Long projectId) {
        AppUser seat = team.requireSeat(agentKey);
        TaskResponse task = taskService.create(new TaskRequest(title, instructions, Priority.MEDIUM, null, null,
                projectId, null, null));
        AiRequest request = ask(actor, task.id(), agentKey, kind, title, instructions, null);
        // The assignment finds the open request above and does not send a second one.
        access.assign(actor, task.id(), seat.getId());
        return request;
    }

    /** Called when someone assigns a task. A teammate taking a task sends it, unless it is already in flight. */
    @Transactional
    public void onAssigned(AppUser actor, Long taskId, Long assigneeUserId) {
        if (assigneeUserId == null) return; // the owner was removed: nothing to send
        AppUser assignee = users.findById(assigneeUserId).orElse(null);
        if (assignee == null || !assignee.isAi()) return;
        Optional<AiTeamMember> member = catalog.byUsername(assignee.getUsername());
        if (member.isEmpty() || requests.existsByTaskIdAndStatusIn(taskId, AiRequestStatus.OPEN)) return;
        Task task = tasks.findById(taskId).orElse(null);
        if (task == null) return;
        ask(actor, taskId, member.get().key(), null, task.getTitle(),
                task.getDescription() == null ? "" : task.getDescription(), null);
    }

    /** A comment that mentions @CFO asks the teammate to answer on that task. Returns the request, if one was made. */
    @Transactional
    public Optional<AiRequest> onMention(AppUser author, TaskComment comment, String taskTitle) {
        if (team.activeSeat("cfo").isEmpty()) return Optional.empty();
        return Optional.of(ask(author, comment.getTaskId(), "cfo", null, "댓글 요청: " + taskTitle, comment.getBody(), comment.getId()));
    }

    /**
     * Pulls progress for open requests and retries the ones not yet accepted. Runs from the
     * scheduler and from the "Sync now" button; synchronized so the two never post a report twice.
     */
    @Transactional
    public synchronized SyncResult sync() {
        int sent = 0;
        int updated = 0;
        for (AiRequest request : requests.findByStatusIn(AiRequestStatus.OPEN)) {
            if (request.getOfficeTaskId() == null) {
                if (!office.configured()) continue;
                send(request);
                if (request.getOfficeTaskId() != null) sent++;
                continue;
            }
            AiRequestStatus before = request.getStatus();
            try {
                apply(request, office.getTask(request.getOfficeTaskId()));
                if (request.getStatus() != before) updated++;
            } catch (OwnersOfficeException e) {
                request.noteError(e.getMessage(), now());
            }
        }
        return new SyncResult(sent, updated);
    }

    @Transactional(readOnly = true)
    public List<RequestView> list(String agentKey) {
        return requests.findTop100ByAgentKeyOrderByIdDesc(agentKey).stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public RequestView detail(Long id) {
        return requests.findById(id).map(this::view).orElseThrow(() -> new NotFoundException("That request no longer exists."));
    }

    @Transactional(readOnly = true)
    public List<RequestView> listForTask(Long taskId) {
        return requests.findByTaskIdOrderByIdAsc(taskId).stream().map(this::view).toList();
    }

    /* ---------- internals ---------- */

    private void send(AiRequest request) {
        LocalDateTime now = now();
        if (!office.configured()) {
            request.sendFailed("Owner's Office 연결 토큰(OWNERS_OFFICE_TOKEN)이 설정되지 않았습니다.", now);
            return;
        }
        try {
            OfficeTask accepted = office.createTask(request.getExternalRef(), request.getKind(), request.getTitle(), request.getInstructions());
            request.accepted(accepted.id(), AiRequestStatus.SENT, now);
            if (request.getTaskId() != null) startTask(request.getTaskId());
            apply(request, accepted);
        } catch (OwnersOfficeException e) {
            request.sendFailed(e.getMessage(), now);
        }
    }

    /** Moves the request and its task to the state Owner's Office reports. Side effects happen once, on the first final state. */
    private void apply(AiRequest request, OfficeTask report) {
        LocalDateTime now = now();
        AiRequestStatus next = toStatus(report.status());
        if (next == null) return;
        if (next == AiRequestStatus.COMPLETED && request.getStatus() != AiRequestStatus.COMPLETED) {
            request.completed(report.kind(), report.summary(), writeJson(report.result()), now);
            onCompleted(request, report);
        } else if (next == AiRequestStatus.FAILED && request.getStatus() != AiRequestStatus.FAILED) {
            request.failed(report.error() == null ? "처리에 실패했습니다." : report.error(), now);
            onFailed(request);
        } else if (next != AiRequestStatus.COMPLETED && next != AiRequestStatus.FAILED) {
            request.progress(next, now);
        }
    }

    private void onCompleted(AiRequest request, OfficeTask report) {
        String seatName = seatName(request.getAgentKey());
        notifications.notifyAdmins("AI_REPORT", seatName + " 보고서: " + request.getTitle(),
                report.summary() == null ? "" : report.summary(), "#/ai-team?request=" + request.getId());
        if (request.getTaskId() == null) return;
        tasks.findById(request.getTaskId()).ifPresent(task -> {
            postFromTeammate(request, task.getId(), reportNote(report));
            // A task with open steps cannot be marked complete, so it waits for review instead.
            boolean openSteps = steps.findByTask_IdOrderBySortOrderAscIdAsc(task.getId()).stream().anyMatch(s -> !s.isCompleted());
            taskService.setStatus(task.getId(), openSteps ? WorkStatus.REVIEW : WorkStatus.COMPLETED);
        });
    }

    private void onFailed(AiRequest request) {
        if (request.getTaskId() == null) return;
        tasks.findById(request.getTaskId()).ifPresent(task -> {
            postFromTeammate(request, task.getId(), "보고서를 만들지 못했습니다. 입력 내용을 확인한 뒤 다시 요청해 주세요.");
            taskService.setStatus(task.getId(), WorkStatus.BLOCKED);
        });
    }

    private void postFromTeammate(AiRequest request, Long taskId, String body) {
        AppUser seat = team.activeSeat(request.getAgentKey()).orElse(null);
        if (seat == null) return;
        comments.save(new TaskComment(taskId, seat.getId(), seat.getDisplayName(), true, body, request.getId(), now()));
    }

    private void startTask(Long taskId) {
        tasks.findById(taskId).filter(t -> t.getStatus() == WorkStatus.NOT_STARTED)
                .ifPresent(t -> taskService.setStatus(taskId, WorkStatus.IN_PROGRESS));
    }

    private String reportNote(OfficeTask report) {
        StringBuilder out = new StringBuilder();
        if (report.summary() != null) out.append(report.summary());
        appendList(out, report.result(), "recommendations", "권고", 3);
        appendList(out, report.result(), "limitations", "한계", 2);
        String text = out.toString();
        return text.length() > REPORT_NOTE_LIMIT ? text.substring(0, REPORT_NOTE_LIMIT) + "…" : text;
    }

    private static void appendList(StringBuilder out, JsonNode result, String field, String label, int max) {
        if (result == null) return;
        JsonNode items = result.get(field);
        if (items == null || !items.isArray() || items.isEmpty()) return;
        List<String> lines = new ArrayList<>();
        for (JsonNode item : items) {
            if (lines.size() >= max) break;
            if (item.isString()) lines.add(item.asString());
        }
        if (lines.isEmpty()) return;
        out.append("\n\n").append(label).append(":\n");
        for (String line : lines) out.append("• ").append(line).append('\n');
    }

    private String seatName(String agentKey) {
        return team.activeSeat(agentKey).map(AppUser::getDisplayName)
                .orElse(catalog.byKey(agentKey).map(AiTeamMember::displayName).orElse("AI 팀원"));
    }

    private static AiRequestStatus toStatus(String officeStatus) {
        if (officeStatus == null) return null;
        return switch (officeStatus) {
            case "queued" -> AiRequestStatus.SENT;
            case "in_progress" -> AiRequestStatus.IN_PROGRESS;
            case "completed" -> AiRequestStatus.COMPLETED;
            case "failed" -> AiRequestStatus.FAILED;
            default -> null;
        };
    }

    private String writeJson(JsonNode node) {
        return node == null ? null : json.writeValueAsString(node);
    }

    private RequestView view(AiRequest r) {
        JsonNode result = null;
        if (r.getResultJson() != null) {
            try {
                result = json.readTree(r.getResultJson());
            } catch (RuntimeException e) {
                log.warn("stored report for request {} could not be read", r.getId());
            }
        }
        String taskTitle = r.getTaskId() == null ? null : tasks.findById(r.getTaskId()).map(Task::getTitle).orElse(null);
        String requestedBy = users.findById(r.getRequestedByUserId()).map(AppUser::getDisplayName).orElse("—");
        return new RequestView(r.getId(), r.getAgentKey(), r.getKind(),
                r.getKind() == null ? null : AiRequestKinds.CFO.getOrDefault(r.getKind(), r.getKind()),
                r.getTitle(), r.getTaskId(), taskTitle, r.getStatus().name(), r.getSummary(), result, r.getLastError(),
                r.getAttempts(), requestedBy, r.getCreatedAt(), r.getUpdatedAt(), r.getCompletedAt());
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
