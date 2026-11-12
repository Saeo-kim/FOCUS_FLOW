package com.example.project_flow.admin.notification;

import com.example.project_flow.admin.submission.SubmissionRepository;
import com.example.project_flow.admin.submission.SubmissionStatus;
import com.example.project_flow.admin.work.ProjectMember;
import com.example.project_flow.admin.work.ProjectMemberRepository;
import com.example.project_flow.admin.work.TaskAssignment;
import com.example.project_flow.admin.work.TaskAssignmentRepository;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.entity.ProjectPhase;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.ProjectPhaseRepository;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class NotificationService {

    /** Remind this many days before a phase ends when nothing for it has been approved. */
    private static final int DUE_SOON_DAYS = 2;
    /** Flag phases that ended this recently with nothing handed in at all. */
    private static final int OVERDUE_LOOKBACK_DAYS = 7;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("MMM d", Locale.US);

    private final NotificationRepository notifications;
    private final ProjectPhaseRepository phases;
    private final SubmissionRepository submissions;
    private final ProjectMemberRepository projectMembers;
    private final TaskAssignmentRepository assignments;
    private final TaskRepository tasks;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, ProjectPhaseRepository phases,
                               SubmissionRepository submissions, ProjectMemberRepository projectMembers,
                               TaskAssignmentRepository assignments, TaskRepository tasks, Clock clock) {
        this.notifications = notifications;
        this.phases = phases;
        this.submissions = submissions;
        this.projectMembers = projectMembers;
        this.assignments = assignments;
        this.tasks = tasks;
        this.clock = clock;
    }

    public record NotificationResponse(Long id, String type, String title, String message, String link,
                                       boolean read, LocalDateTime createdAt) {
        static NotificationResponse from(Notification n) {
            return new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                    n.getReadAt() != null, n.getCreatedAt());
        }
    }

    public record Inbox(long unread, List<NotificationResponse> items) {
    }

    /** Adds a notice for every administrator. */
    @Transactional
    public void notifyAdmins(String type, String title, String message, String link) {
        notifications.save(new Notification(null, type, title, message, link, null, LocalDateTime.now(clock)));
    }

    /** Adds a notice for one person only. */
    @Transactional
    public void notifyUser(Long userId, String type, String title, String message, String link) {
        notifications.save(new Notification(userId, type, title, message, link, null, LocalDateTime.now(clock)));
    }

    /** Not one transaction on purpose: reminders are saved one by one under a lock, so two open tabs cannot add the same reminder twice. */
    public Inbox inbox(Long userId, boolean admin, int limit) {
        if (admin) {
            createPhaseReminders();
        } else {
            createMemberReminders(userId);
        }
        int size = Math.max(1, Math.min(limit, 100));
        List<NotificationResponse> items = notifications.findVisible(userId, admin, Limit.of(size)).stream()
                .map(NotificationResponse::from).toList();
        return new Inbox(notifications.countUnread(userId, admin), items);
    }

    @Transactional
    public void markRead(Long userId, boolean admin, Long id) {
        Notification n = notifications.findById(id)
                .filter(x -> x.getRecipientUserId() == null ? admin : Objects.equals(x.getRecipientUserId(), userId))
                .orElseThrow(() -> new NotFoundException("That notification no longer exists."));
        n.markRead(LocalDateTime.now(clock));
    }

    @Transactional
    public void markAllRead(Long userId, boolean admin) {
        LocalDateTime now = LocalDateTime.now(clock);
        notifications.findUnread(userId, admin).forEach(n -> n.markRead(now));
    }

    /**
     * Reads the existing 회차 schedule (never changes it) and adds one reminder per phase that is
     * about to end without an approved submission, or that ended recently with no submission.
     */
    private synchronized void createPhaseReminders() {
        LocalDate today = LocalDate.now(clock);
        Set<Long> submitted = new HashSet<>();
        Set<Long> approved = new HashSet<>();
        submissions.findAll().forEach(s -> {
            submitted.add(s.getPhaseId());
            if (s.getStatus() == SubmissionStatus.APPROVED) {
                approved.add(s.getPhaseId());
            }
        });
        LocalDateTime now = LocalDateTime.now(clock);
        for (ProjectPhase phase : phases.findAll()) {
            LocalDate end = phase.getEndDate();
            String link = "#/submissions?phase=" + phase.getId();
            if (!end.isBefore(today) && !end.isAfter(today.plusDays(DUE_SOON_DAYS)) && !approved.contains(phase.getId())) {
                String key = "PHASE_DUE:" + phase.getId() + ":" + end;
                if (!notifications.existsByDedupeKey(key)) {
                    String when = end.equals(today) ? "today" : "on " + DAY.format(end);
                    notifications.save(new Notification(null, "PHASE_DUE", phase.getName() + " submission due " + when,
                            submitted.contains(phase.getId()) ? "A submission is waiting for review." : "Nothing has been submitted for this phase yet.",
                            link, key, now));
                }
            } else if (end.isBefore(today) && !end.isBefore(today.minusDays(OVERDUE_LOOKBACK_DAYS)) && !submitted.contains(phase.getId())) {
                String key = "PHASE_OVERDUE:" + phase.getId() + ":" + end;
                if (!notifications.existsByDedupeKey(key)) {
                    notifications.save(new Notification(null, "PHASE_OVERDUE", phase.getName() + " ended with no submission",
                            "The phase ended on " + DAY.format(end) + ".", link, key, now));
                }
            }
        }
    }

    /**
     * A member's own reminders: a phase of one of their projects ends within two days and they
     * have nothing approved for it yet.
     */
    private synchronized void createMemberReminders(Long userId) {
        LocalDate today = LocalDate.now(clock);
        Set<Long> projectIds = new HashSet<>();
        projectMembers.findByUserId(userId).stream().map(ProjectMember::getProjectId).forEach(projectIds::add);
        tasks.findAllById(assignments.findByUserId(userId).stream().map(TaskAssignment::getTaskId).toList())
                .forEach(t -> { if (t.getProject() != null) projectIds.add(t.getProject().getId()); });
        if (projectIds.isEmpty()) {
            return;
        }
        Set<Long> approved = new HashSet<>();
        Set<Long> submitted = new HashSet<>();
        submissions.findAll().stream().filter(s -> userId.equals(s.getSubmitterUserId())).forEach(s -> {
            submitted.add(s.getPhaseId());
            if (s.getStatus() == SubmissionStatus.APPROVED) {
                approved.add(s.getPhaseId());
            }
        });
        LocalDateTime now = LocalDateTime.now(clock);
        for (ProjectPhase phase : phases.findAll()) {
            LocalDate end = phase.getEndDate();
            if (!projectIds.contains(phase.getProjectId()) || approved.contains(phase.getId())
                    || end.isBefore(today) || end.isAfter(today.plusDays(DUE_SOON_DAYS))) {
                continue;
            }
            String key = "PHASE_DUE:" + phase.getId() + ":" + end + ":u" + userId;
            if (!notifications.existsByDedupeKey(key)) {
                String when = end.equals(today) ? "today" : "on " + DAY.format(end);
                notifications.save(new Notification(userId, "PHASE_DUE", phase.getName() + " submission due " + when,
                        submitted.contains(phase.getId()) ? "Your submission is waiting for review." : "You haven't submitted for this phase yet.",
                        "#/submissions?phase=" + phase.getId(), key, now));
            }
        }
    }
}
