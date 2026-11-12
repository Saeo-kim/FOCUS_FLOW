package com.example.project_flow.admin.submission;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.notification.NotificationService;
import com.example.project_flow.admin.submission.SubmissionDtos.DashboardSummary;
import com.example.project_flow.admin.submission.SubmissionDtos.EventResponse;
import com.example.project_flow.admin.submission.SubmissionDtos.FileResponse;
import com.example.project_flow.admin.submission.SubmissionDtos.PhaseBoardRow;
import com.example.project_flow.admin.submission.SubmissionDtos.SubmissionDetail;
import com.example.project_flow.admin.submission.SubmissionDtos.SubmissionResponse;
import com.example.project_flow.admin.web.AdminWebConfig;
import com.example.project_flow.entity.Project;
import com.example.project_flow.entity.ProjectPhase;
import com.example.project_flow.entity.Task;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.ProjectPhaseRepository;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.service.References;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Submissions per schedule phase, with review. Reads projects, phases and tasks from the existing
 * tables but never writes to them.
 */
@Service
public class SubmissionService {

    private final SubmissionRepository submissions;
    private final SubmissionFileRepository files;
    private final SubmissionEventRepository events;
    private final SubmissionFileStorage storage;
    private final ProjectRepository projects;
    private final ProjectPhaseRepository phases;
    private final TaskRepository tasks;
    private final NotificationService notifications;
    private final Clock clock;

    public SubmissionService(SubmissionRepository submissions, SubmissionFileRepository files, SubmissionEventRepository events,
                             SubmissionFileStorage storage, ProjectRepository projects, ProjectPhaseRepository phases,
                             TaskRepository tasks, NotificationService notifications, Clock clock) {
        this.submissions = submissions;
        this.files = files;
        this.events = events;
        this.storage = storage;
        this.projects = projects;
        this.phases = phases;
        this.tasks = tasks;
        this.notifications = notifications;
        this.clock = clock;
    }

    public record Download(SubmissionFile file, Path path) {
    }

    /* ---------- Reading ---------- */

    @Transactional(readOnly = true)
    public List<SubmissionResponse> list(Long projectId, Long phaseId, SubmissionStatus status, String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<SubmissionResponse> all = toResponses(submissions.findAllByOrderBySubmittedAtDescIdDesc());
        return all.stream()
                .filter(s -> projectId == null || projectId.equals(s.projectId()))
                .filter(s -> phaseId == null || phaseId.equals(s.phaseId()))
                .filter(s -> status == null || status == s.status())
                .filter(s -> q.isEmpty() || contains(s.title(), q) || contains(s.submitterName(), q)
                        || contains(s.note(), q) || contains(s.taskTitle(), q))
                .toList();
    }

    @Transactional(readOnly = true)
    public SubmissionDetail detail(Long id) {
        Submission submission = require(id);
        return new SubmissionDetail(
                toResponses(List.of(submission)).getFirst(),
                files.findBySubmission_IdOrderByVersionDescIdAsc(id).stream().map(FileResponse::from).toList(),
                events.findBySubmission_IdOrderByCreatedAtAscIdAsc(id).stream().map(EventResponse::from).toList());
    }

    /** Every stored file with the submission it belongs to, newest first, for the admin file manager. */
    public record StoredFile(Long id, Long submissionId, String submissionTitle, SubmissionStatus status,
                             Long projectId, String projectName, Long phaseId, String phaseName,
                             String submitterName, Long submitterUserId, int version, String originalName,
                             long sizeBytes, LocalDateTime uploadedAt) {
    }

    @Transactional(readOnly = true)
    public List<StoredFile> allFiles() {
        Map<Long, SubmissionResponse> byId = toResponses(submissions.findAll()).stream()
                .collect(Collectors.toMap(SubmissionResponse::id, Function.identity()));
        return files.findAll().stream()
                .sorted(Comparator.comparing(SubmissionFile::getUploadedAt).reversed().thenComparing(SubmissionFile::getId, Comparator.reverseOrder()))
                .map(f -> {
                    SubmissionResponse s = byId.get(f.getSubmission().getId());
                    return new StoredFile(f.getId(), s.id(), s.title(), s.status(), s.projectId(), s.projectName(), s.phaseId(), s.phaseName(),
                            s.submitterName(), f.getSubmission().getSubmitterUserId(), f.getVersion(), f.getOriginalName(), f.getSizeBytes(), f.getUploadedAt());
                }).toList();
    }

    /* ---------- One member's own submissions ---------- */

    @Transactional(readOnly = true)
    public List<SubmissionResponse> listForUser(Long userId) {
        return toResponses(submissions.findAllByOrderBySubmittedAtDescIdDesc().stream()
                .filter(s -> userId.equals(s.getSubmitterUserId())).toList());
    }

    /** A submission the person handed in themselves; anything else looks like it does not exist. */
    @Transactional(readOnly = true)
    public Submission requireOwned(Long userId, Long id) {
        Submission submission = submissions.findById(id).orElse(null);
        if (submission == null || !userId.equals(submission.getSubmitterUserId())) {
            throw new NotFoundException("That submission is not in your workspace.");
        }
        return submission;
    }

    @Transactional(readOnly = true)
    public SubmissionDetail detailForUser(Long userId, Long id) {
        requireOwned(userId, id);
        return detail(id);
    }

    @Transactional(readOnly = true)
    public Download downloadForUser(Long userId, Long fileId) {
        SubmissionFile file = files.findById(fileId).orElseThrow(() -> new NotFoundException("That file no longer exists."));
        requireOwned(userId, file.getSubmission().getId());
        return download(fileId);
    }

    /** Per phase, the latest status of each person's submissions: who handed in, who did not. */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, SubmissionStatus>> statusByPhaseAndUser(Long projectId) {
        Map<Long, Map<Long, SubmissionStatus>> result = new HashMap<>();
        submissions.findAllByOrderBySubmittedAtDescIdDesc().stream()
                .filter(s -> projectId.equals(s.getProjectId()) && s.getSubmitterUserId() != null)
                .forEach(s -> result.computeIfAbsent(s.getPhaseId(), k -> new HashMap<>())
                        .merge(s.getSubmitterUserId(), s.getStatus(), SubmissionService::stronger));
        return result;
    }

    /** Approved beats pending beats rejected: one approved submission covers the phase. */
    private static SubmissionStatus stronger(SubmissionStatus a, SubmissionStatus b) {
        return rank(a) <= rank(b) ? a : b;
    }

    private static int rank(SubmissionStatus s) {
        return switch (s) { case APPROVED -> 0; case SUBMITTED -> 1; case REJECTED -> 2; };
    }

    @Transactional(readOnly = true)
    public DashboardSummary dashboard() {
        List<Submission> all = submissions.findAll();
        Map<SubmissionStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(Submission::getStatus, Collectors.counting()));
        long fileCount = 0;
        long totalBytes = 0;
        for (Object[] row : files.totalsBySubmission()) {
            fileCount += ((Number) row[1]).longValue();
            totalBytes += ((Number) row[2]).longValue();
        }
        List<SubmissionResponse> queue = toResponses(submissions.findByStatusOrderBySubmittedAtAscIdAsc(SubmissionStatus.SUBMITTED))
                .stream().limit(8).toList();
        List<EventResponse> recent = events.findTop12ByOrderByCreatedAtDescIdDesc().stream().map(EventResponse::from).toList();
        return new DashboardSummary(all.size(),
                byStatus.getOrDefault(SubmissionStatus.SUBMITTED, 0L),
                byStatus.getOrDefault(SubmissionStatus.APPROVED, 0L),
                byStatus.getOrDefault(SubmissionStatus.REJECTED, 0L),
                fileCount, totalBytes, storage.root().toString(), queue, recent, phaseBoard(all));
    }

    @Transactional(readOnly = true)
    public Download download(Long fileId) {
        SubmissionFile file = files.findById(fileId).orElseThrow(() -> new NotFoundException("That file no longer exists."));
        Path path = storage.path(file.getSubmission().getId(), file.getStoredName());
        if (!Files.isReadable(path)) {
            throw new NotFoundException("The file is missing from the upload folder.");
        }
        return new Download(file, path);
    }

    /* ---------- Changes ---------- */

    @Transactional
    public SubmissionResponse create(AppUser user, Long projectId, Long phaseId, Long taskId, String title, String note,
                                     String submitterName, List<MultipartFile> uploads) {
        if (projectId == null || phaseId == null) {
            throw new ConflictException("Choose the project and phase this submission is for.");
        }
        Project project = projects.findById(projectId).orElseThrow(() -> new NotFoundException("That project no longer exists."));
        ProjectPhase phase = phases.findById(phaseId).orElseThrow(() -> new NotFoundException("That phase no longer exists."));
        if (!Objects.equals(phase.getProjectId(), project.getId())) {
            throw new ConflictException("That phase belongs to a different project.");
        }
        validateTask(taskId, project.getId());
        List<MultipartFile> incoming = checkFiles(uploads);
        String who = References.clean(submitterName) == null ? user.getDisplayName() : limit(submitterName.trim(), 80, "submitter name");

        LocalDateTime now = LocalDateTime.now(clock);
        Submission submission = submissions.save(new Submission(project.getId(), phase.getId(), taskId,
                requireTitle(title), cleanNote(note), who, user.getId(), now));
        saveFiles(submission, incoming, now);
        events.save(new SubmissionEvent(submission, SubmissionEvent.Type.SUBMITTED, null, who, now));
        notifications.notifyAdmins("SUBMISSION_NEW", "New submission for " + phase.getName(),
                "\"" + submission.getTitle() + "\" · " + incoming.size() + (incoming.size() == 1 ? " file" : " files"),
                "#/submission?id=" + submission.getId());
        return toResponses(List.of(submission)).getFirst();
    }

    @Transactional
    public SubmissionResponse resubmit(AppUser user, Long id, String note, List<MultipartFile> uploads) {
        Submission submission = require(id);
        if (submission.getStatus() == SubmissionStatus.APPROVED) {
            throw new ConflictException("This submission is already approved. Reopen it first to hand in a new version.");
        }
        List<MultipartFile> incoming = checkFiles(uploads);
        LocalDateTime now = LocalDateTime.now(clock);
        submission.resubmit(cleanNote(note), now);
        saveFiles(submission, incoming, now);
        events.save(new SubmissionEvent(submission, SubmissionEvent.Type.RESUBMITTED, cleanNote(note), user.getDisplayName(), now));
        notifications.notifyAdmins("SUBMISSION_RESUBMITTED", "Resubmitted: " + submission.getTitle(),
                "Version " + submission.getCurrentVersion() + " is waiting for review.", "#/submission?id=" + id);
        return toResponses(List.of(submission)).getFirst();
    }

    @Transactional
    public SubmissionResponse review(AppUser reviewer, Long id, SubmissionStatus decision, String comment) {
        Submission submission = require(id);
        if (submission.getStatus() != SubmissionStatus.SUBMITTED) {
            throw new ConflictException("Only a submission waiting for review can be " + (decision == SubmissionStatus.APPROVED ? "approved." : "rejected."));
        }
        String text = References.clean(comment);
        if (decision == SubmissionStatus.REJECTED && text == null) {
            throw new ConflictException("Say why it was rejected, so it can be fixed.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        submission.review(decision, text == null ? null : limit(text, 2000, "comment"), reviewer.getDisplayName(), now);
        boolean approved = decision == SubmissionStatus.APPROVED;
        events.save(new SubmissionEvent(submission, approved ? SubmissionEvent.Type.APPROVED : SubmissionEvent.Type.REJECTED,
                submission.getReviewComment(), reviewer.getDisplayName(), now));
        String title = (approved ? "Approved: " : "Rejected: ") + submission.getTitle();
        String message = text == null ? "Reviewed by " + reviewer.getDisplayName() + "." : text;
        if (submission.getSubmitterUserId() != null && !submission.getSubmitterUserId().equals(reviewer.getId())) {
            notifications.notifyUser(submission.getSubmitterUserId(), approved ? "SUBMISSION_APPROVED" : "SUBMISSION_REJECTED",
                    title, approved ? message : "Reason: " + message, "#/submission?id=" + id);
        } else {
            notifications.notifyAdmins(approved ? "SUBMISSION_APPROVED" : "SUBMISSION_REJECTED", title, message, "#/submission?id=" + id);
        }
        return toResponses(List.of(submission)).getFirst();
    }

    @Transactional
    public SubmissionResponse reopen(AppUser user, Long id) {
        Submission submission = require(id);
        if (submission.getStatus() == SubmissionStatus.SUBMITTED) {
            throw new ConflictException("This submission is already waiting for review.");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        submission.reopen(now);
        events.save(new SubmissionEvent(submission, SubmissionEvent.Type.REOPENED, null, user.getDisplayName(), now));
        return toResponses(List.of(submission)).getFirst();
    }

    @Transactional
    public SubmissionResponse edit(AppUser user, Long id, SubmissionDtos.EditRequest request) {
        Submission submission = require(id);
        validateTask(request.taskId(), submission.getProjectId());
        LocalDateTime now = LocalDateTime.now(clock);
        submission.edit(request.taskId(), requireTitle(request.title()), cleanNote(request.note()), now);
        events.save(new SubmissionEvent(submission, SubmissionEvent.Type.EDITED, null, user.getDisplayName(), now));
        return toResponses(List.of(submission)).getFirst();
    }

    /** Removes the submission, its history and its files. The files are deleted only once the rows are gone. */
    @Transactional
    public void delete(Long id) {
        Submission submission = require(id);
        List<SubmissionFile> stored = files.findBySubmission_IdOrderByVersionDescIdAsc(id);
        events.deleteAll(events.findBySubmission_IdOrderByCreatedAtAscIdAsc(id));
        files.deleteAll(stored);
        submissions.delete(submission);
        List<String> names = stored.stream().map(SubmissionFile::getStoredName).toList();
        afterCommit(() -> {
            names.forEach(name -> storage.delete(id, name));
            storage.deleteFolderIfEmpty(id);
        });
    }

    /* ---------- Helpers ---------- */

    private void saveFiles(Submission submission, List<MultipartFile> incoming, LocalDateTime now) {
        List<String> written = new ArrayList<>();
        try {
            for (MultipartFile upload : incoming) {
                String storedName = storage.store(submission.getId(), upload);
                written.add(storedName);
                files.save(new SubmissionFile(submission, submission.getCurrentVersion(), originalName(upload),
                        storedName, limitOrNull(upload.getContentType(), 150), upload.getSize(), now));
            }
        } catch (RuntimeException e) {
            written.forEach(name -> storage.delete(submission.getId(), name));
            throw e;
        }
        // If the transaction fails later, the rows roll back, so drop the bytes too.
        Long submissionId = submission.getId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        written.forEach(name -> storage.delete(submissionId, name));
                    }
                }
            });
        }
    }

    private static List<MultipartFile> checkFiles(List<MultipartFile> uploads) {
        List<MultipartFile> incoming = uploads == null ? List.of()
                : uploads.stream().filter(f -> f != null && !f.isEmpty()).toList();
        if (incoming.isEmpty()) {
            throw new ConflictException("Attach at least one file.");
        }
        if (incoming.size() > AdminWebConfig.MAX_FILES_PER_UPLOAD) {
            throw new ConflictException("Attach up to " + AdminWebConfig.MAX_FILES_PER_UPLOAD + " files at a time.");
        }
        for (MultipartFile f : incoming) {
            if (f.getSize() > AdminWebConfig.MAX_FILE_BYTES) {
                throw new ConflictException("\"" + originalName(f) + "\" is larger than " + AdminWebConfig.MAX_FILE_BYTES / (1024 * 1024) + " MB.");
            }
        }
        return incoming;
    }

    private void validateTask(Long taskId, Long projectId) {
        if (taskId == null) {
            return;
        }
        Task task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("That task no longer exists."));
        if (task.getProject() != null && !Objects.equals(task.getProject().getId(), projectId)) {
            throw new ConflictException("That task belongs to a different project.");
        }
    }

    private List<SubmissionResponse> toResponses(List<Submission> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Set<Long> projectIds = new HashSet<>();
        Set<Long> phaseIds = new HashSet<>();
        Set<Long> taskIds = new HashSet<>();
        for (Submission s : list) {
            projectIds.add(s.getProjectId());
            phaseIds.add(s.getPhaseId());
            if (s.getTaskId() != null) {
                taskIds.add(s.getTaskId());
            }
        }
        Map<Long, Project> projectById = projects.findAllById(projectIds).stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<Long, ProjectPhase> phaseById = phases.findAllById(phaseIds).stream().collect(Collectors.toMap(ProjectPhase::getId, Function.identity()));
        Map<Long, Task> taskById = tasks.findAllById(taskIds).stream().collect(Collectors.toMap(Task::getId, Function.identity()));
        Map<Long, long[]> totals = new HashMap<>();
        for (Object[] row : files.totalsBySubmission()) {
            totals.put(((Number) row[0]).longValue(), new long[]{((Number) row[1]).longValue(), ((Number) row[2]).longValue()});
        }
        return list.stream().map(s -> {
            Project project = projectById.get(s.getProjectId());
            ProjectPhase phase = phaseById.get(s.getPhaseId());
            Task task = s.getTaskId() == null ? null : taskById.get(s.getTaskId());
            long[] t = totals.getOrDefault(s.getId(), new long[]{0, 0});
            boolean late = phase != null && s.getSubmittedAt().toLocalDate().isAfter(phase.getEndDate());
            return new SubmissionResponse(s.getId(),
                    s.getProjectId(), project == null ? null : project.getName(),
                    s.getPhaseId(), phase == null ? null : phase.getName(),
                    phase == null ? null : phase.getStartDate(), phase == null ? null : phase.getEndDate(),
                    s.getTaskId(), task == null ? null : task.getTitle(),
                    s.getTitle(), s.getNote(), s.getSubmitterName(), s.getSubmitterUserId(), s.getStatus(), s.getCurrentVersion(),
                    s.getReviewComment(), s.getReviewedBy(), s.getReviewedAt(),
                    s.getSubmittedAt(), s.getCreatedAt(), s.getUpdatedAt(), t[0], t[1], late);
        }).toList();
    }

    private List<PhaseBoardRow> phaseBoard(List<Submission> all) {
        Map<Long, List<Submission>> byPhase = all.stream().collect(Collectors.groupingBy(Submission::getPhaseId));
        Map<Long, String> projectNames = projects.findAll().stream().collect(Collectors.toMap(Project::getId, Project::getName));
        return phases.findAll().stream()
                .sorted(Comparator.comparing(ProjectPhase::getProjectId).thenComparing(ProjectPhase::getStartDate).thenComparing(ProjectPhase::getId))
                .map(phase -> {
                    List<Submission> list = byPhase.getOrDefault(phase.getId(), List.of());
                    int pending = count(list, SubmissionStatus.SUBMITTED);
                    int approved = count(list, SubmissionStatus.APPROVED);
                    int rejected = count(list, SubmissionStatus.REJECTED);
                    SubmissionStatus overall = approved > 0 ? SubmissionStatus.APPROVED
                            : pending > 0 ? SubmissionStatus.SUBMITTED
                            : rejected > 0 ? SubmissionStatus.REJECTED : null;
                    return new PhaseBoardRow(phase.getId(), phase.getProjectId(), projectNames.get(phase.getProjectId()),
                            phase.getName(), phase.getStartDate(), phase.getEndDate(), list.size(), pending, approved, rejected, overall);
                }).toList();
    }

    private static int count(List<Submission> list, SubmissionStatus status) {
        return (int) list.stream().filter(s -> s.getStatus() == status).count();
    }

    private Submission require(Long id) {
        return submissions.findById(id).orElseThrow(() -> new NotFoundException("That submission no longer exists."));
    }

    private static String requireTitle(String title) {
        String t = References.clean(title);
        if (t == null) {
            throw new ConflictException("Enter a title.");
        }
        return limit(t, 200, "title");
    }

    private static String cleanNote(String note) {
        String n = References.clean(note);
        return n == null ? null : limit(n, 5000, "note");
    }

    private static String limit(String value, int max, String what) {
        if (value.length() > max) {
            throw new ConflictException("Keep the " + what + " under " + max + " characters.");
        }
        return value;
    }

    private static String limitOrNull(String value, int max) {
        return value == null ? null : value.length() > max ? value.substring(0, max) : value;
    }

    /** Keeps only the file's own name: some browsers send a full local path. */
    private static String originalName(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "file";
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty()) {
            return "file";
        }
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private static boolean contains(String value, String q) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(q);
    }

    private static void afterCommit(Runnable action) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
