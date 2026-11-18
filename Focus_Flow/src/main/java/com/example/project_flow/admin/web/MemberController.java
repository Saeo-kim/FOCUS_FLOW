package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.submission.SubmissionDtos.SubmissionDetail;
import com.example.project_flow.admin.submission.SubmissionDtos.SubmissionResponse;
import com.example.project_flow.admin.work.TaskCommentService;
import com.example.project_flow.admin.work.TaskCommentService.CommentRequest;
import com.example.project_flow.admin.work.TaskCommentService.CommentView;
import com.example.project_flow.admin.submission.SubmissionService;
import com.example.project_flow.admin.submission.SubmissionStatus;
import com.example.project_flow.admin.work.WorkAccessService;
import com.example.project_flow.dto.CalendarEntryResponse;
import com.example.project_flow.dto.ProjectDtos.ProjectResponse;
import com.example.project_flow.dto.RecordDtos.DeadlineResponse;
import com.example.project_flow.dto.RecordDtos.PhaseResponse;
import com.example.project_flow.dto.RecordDtos.StepMoveRequest;
import com.example.project_flow.dto.RecordDtos.StepRequest;
import com.example.project_flow.dto.RecordDtos.StepResponse;
import com.example.project_flow.dto.TaskDtos.TaskDoneRequest;
import com.example.project_flow.dto.TaskDtos.TaskResponse;
import com.example.project_flow.dto.TaskDtos.TaskStatusRequest;
import com.example.project_flow.entity.DeadlineScope;
import com.example.project_flow.service.CalendarService;
import com.example.project_flow.service.DeadlineService;
import com.example.project_flow.service.PhaseService;
import com.example.project_flow.service.ProjectService;
import com.example.project_flow.service.TaskService;
import com.example.project_flow.service.WorkStepService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.YearMonth;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The MEMBER workspace API. Every call is scoped to the signed-in person: their assigned tasks,
 * the projects they belong to, and their own submissions. It reuses the existing task, work-step,
 * calendar, phase and deadline services, so members and administrators work on the same records.
 */
@RestController
@RequestMapping("/api/member")
public class MemberController {

    private static final Set<String> PROJECT_ENTRY_TYPES = Set.of("PROJECT_DUE", "TEAM_PHASE", "DEADLINE_TEAM");
    private static final Set<String> TASK_ENTRY_TYPES = Set.of("TASK_DUE", "TASK_SCHEDULED");

    private final WorkAccessService access;
    private final TaskService tasks;
    private final WorkStepService steps;
    private final ProjectService projects;
    private final PhaseService phases;
    private final DeadlineService deadlines;
    private final CalendarService calendar;
    private final SubmissionService submissions;
    private final TaskCommentService comments;

    public MemberController(WorkAccessService access, TaskService tasks, WorkStepService steps, ProjectService projects,
                            PhaseService phases, DeadlineService deadlines, CalendarService calendar,
                            SubmissionService submissions, TaskCommentService comments) {
        this.comments = comments;
        this.access = access;
        this.tasks = tasks;
        this.steps = steps;
        this.projects = projects;
        this.phases = phases;
        this.deadlines = deadlines;
        this.calendar = calendar;
        this.submissions = submissions;
    }

    public record MemberProject(ProjectResponse project, long myTasks, long myDone) {
    }

    public record ProjectDetail(ProjectResponse project, List<PhaseResponse> phases, List<DeadlineResponse> teamDeadlines,
                                List<TaskResponse> myTasks, Map<Long, SubmissionStatus> myPhaseStatus) {
    }

    public record TaskDetail(TaskResponse task, List<StepResponse> steps) {
    }

    /* ---------- Projects ---------- */

    @GetMapping("/projects")
    public List<MemberProject> projects(HttpServletRequest request) {
        Long me = me(request).getId();
        List<TaskResponse> mine = myTasks(me);
        return access.projectIds(me).stream()
                .map(id -> {
                    try { return projects.find(id); } catch (RuntimeException gone) { return null; }
                })
                .filter(p -> p != null)
                .sorted(Comparator.comparing(ProjectResponse::name))
                .map(p -> new MemberProject(p,
                        mine.stream().filter(t -> p.id().equals(t.projectId())).count(),
                        mine.stream().filter(t -> p.id().equals(t.projectId()) && t.done()).count()))
                .toList();
    }

    @GetMapping("/projects/{id}")
    public ProjectDetail project(@PathVariable Long id, HttpServletRequest request) {
        Long me = me(request).getId();
        access.requireProject(me, id);
        Map<Long, SubmissionStatus> myStatus = new HashMap<>();
        submissions.statusByPhaseAndUser(id).forEach((phaseId, byUser) -> {
            if (byUser.containsKey(me)) myStatus.put(phaseId, byUser.get(me));
        });
        return new ProjectDetail(projects.find(id),
                phases.list(id).stream().sorted(Comparator.comparing(PhaseResponse::startDate)).toList(),
                deadlines.list(id).stream().filter(d -> d.scope() == DeadlineScope.TEAM).toList(),
                myTasks(me).stream().filter(t -> id.equals(t.projectId())).toList(),
                myStatus);
    }

    /** Phases of the member's projects, for choosing where a submission belongs. */
    @GetMapping("/phases")
    public List<PhaseResponse> phases(HttpServletRequest request) {
        Set<Long> mine = access.projectIds(me(request).getId());
        return phases.list(null).stream().filter(p -> mine.contains(p.projectId()))
                .sorted(Comparator.comparing(PhaseResponse::startDate)).toList();
    }

    /* ---------- Tasks and work steps ---------- */

    @GetMapping("/tasks")
    public List<TaskResponse> tasks(HttpServletRequest request) {
        return myTasks(me(request).getId());
    }

    @GetMapping("/tasks/{id}")
    public TaskDetail task(@PathVariable Long id, HttpServletRequest request) {
        access.requireTask(me(request).getId(), id);
        return new TaskDetail(tasks.find(id), steps.list(id));
    }

    @PatchMapping("/tasks/{id}/status")
    public TaskResponse setStatus(@PathVariable Long id, @Valid @RequestBody TaskStatusRequest body, HttpServletRequest request) {
        access.requireTask(me(request).getId(), id);
        return tasks.setStatus(id, body.status());
    }

    @PatchMapping("/tasks/{id}/done")
    public TaskResponse setDone(@PathVariable Long id, @RequestBody TaskDoneRequest body, HttpServletRequest request) {
        access.requireTask(me(request).getId(), id);
        return tasks.setDone(id, body.done());
    }

    /** Discussion on one of the member's tasks. Mentioning @CFO asks the AI teammate to answer. */
    @GetMapping("/tasks/{id}/comments")
    public List<CommentView> comments(@PathVariable Long id, HttpServletRequest request) {
        access.requireTask(me(request).getId(), id);
        return comments.list(id);
    }

    @PostMapping("/tasks/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView postComment(@PathVariable Long id, @RequestBody CommentRequest body, HttpServletRequest request) {
        AppUser user = me(request);
        access.requireTask(user.getId(), id);
        return comments.post(user, id, body == null ? null : body.body());
    }

    @PostMapping("/tasks/{id}/steps")
    @ResponseStatus(HttpStatus.CREATED)
    public StepResponse addStep(@PathVariable Long id, @Valid @RequestBody StepRequest body, HttpServletRequest request) {
        access.requireTask(me(request).getId(), id);
        return steps.add(id, body);
    }

    @PutMapping("/steps/{id}")
    public StepResponse updateStep(@PathVariable Long id, @Valid @RequestBody StepRequest body, HttpServletRequest request) {
        access.requireStep(me(request).getId(), id);
        return steps.update(id, body);
    }

    @PostMapping("/steps/{id}/move")
    public List<StepResponse> moveStep(@PathVariable Long id, @RequestBody StepMoveRequest body, HttpServletRequest request) {
        access.requireStep(me(request).getId(), id);
        return steps.move(id, body.direction());
    }

    @DeleteMapping("/steps/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteStep(@PathVariable Long id, HttpServletRequest request) {
        access.requireStep(me(request).getId(), id);
        steps.delete(id);
    }

    /* ---------- Calendar ---------- */

    /** The shared calendar, narrowed to the member's tasks and their projects' team dates. */
    @GetMapping("/calendar")
    public List<CalendarEntryResponse> calendar(@RequestParam YearMonth month, HttpServletRequest request) {
        Long me = me(request).getId();
        Set<Long> taskIds = access.taskIds(me);
        Set<Long> projectIds = access.projectIds(me);
        return calendar.month(month).stream()
                .filter(e -> TASK_ENTRY_TYPES.contains(e.type()) ? taskIds.contains(e.taskId())
                        : PROJECT_ENTRY_TYPES.contains(e.type()) && projectIds.contains(e.projectId()))
                .toList();
    }

    /* ---------- Submissions ---------- */

    @GetMapping("/submissions")
    public List<SubmissionResponse> submissions(HttpServletRequest request) {
        return submissions.listForUser(me(request).getId());
    }

    @GetMapping("/submissions/{id}")
    public SubmissionDetail submission(@PathVariable Long id, HttpServletRequest request) {
        return submissions.detailForUser(me(request).getId(), id);
    }

    @PostMapping(value = "/submissions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionResponse submit(@RequestParam(required = false) Long projectId,
                                     @RequestParam(required = false) Long phaseId,
                                     @RequestParam(required = false) Long taskId,
                                     @RequestParam(required = false) String title,
                                     @RequestParam(required = false) String note,
                                     @RequestPart(name = "files", required = false) List<MultipartFile> files,
                                     HttpServletRequest request) {
        AppUser user = me(request);
        access.requireProject(user.getId(), projectId);
        if (taskId != null) {
            access.requireTask(user.getId(), taskId);
        }
        // A member always submits under their own name.
        return submissions.create(user, projectId, phaseId, taskId, title, note, null, files);
    }

    @PostMapping(value = "/submissions/{id}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SubmissionResponse resubmit(@PathVariable Long id,
                                       @RequestParam(required = false) String note,
                                       @RequestPart(name = "files", required = false) List<MultipartFile> files,
                                       HttpServletRequest request) {
        AppUser user = me(request);
        submissions.requireOwned(user.getId(), id);
        return submissions.resubmit(user, id, note, files);
    }

    @GetMapping("/submission-files/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable Long fileId, HttpServletRequest request) {
        return SubmissionController.asDownload(submissions.downloadForUser(me(request).getId(), fileId));
    }

    /* ---------- Helpers ---------- */

    private List<TaskResponse> myTasks(Long userId) {
        Set<Long> ids = access.taskIds(userId);
        return tasks.findAll().stream().filter(t -> ids.contains(t.id())).toList();
    }

    private static AppUser me(HttpServletRequest request) {
        return UserSession.user(request);
    }
}
