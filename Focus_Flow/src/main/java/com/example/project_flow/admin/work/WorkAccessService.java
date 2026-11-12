package com.example.project_flow.admin.work;

import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.admin.notification.NotificationService;
import com.example.project_flow.entity.Task;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.repository.WorkStepRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides what a person may reach. A member reaches the tasks assigned to them and the projects
 * they belong to (being assigned a task in a project makes them a member of it). Every member API
 * goes through these checks, so changing an id in a URL never reveals someone else's work.
 */
@Service
public class WorkAccessService {

    private final TaskAssignmentRepository assignments;
    private final ProjectMemberRepository members;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final WorkStepRepository steps;
    private final AccountService accounts;
    private final NotificationService notifications;
    private final Clock clock;

    public WorkAccessService(TaskAssignmentRepository assignments, ProjectMemberRepository members, TaskRepository tasks,
                             ProjectRepository projects, WorkStepRepository steps, AccountService accounts,
                             NotificationService notifications, Clock clock) {
        this.assignments = assignments;
        this.members = members;
        this.tasks = tasks;
        this.projects = projects;
        this.steps = steps;
        this.accounts = accounts;
        this.notifications = notifications;
        this.clock = clock;
    }

    public record Assignment(Long taskId, Long userId, String userName, LocalDateTime assignedAt, String assignedBy) {
    }

    /* ---------- Scope of one person ---------- */

    @Transactional(readOnly = true)
    public Set<Long> taskIds(Long userId) {
        return assignments.findByUserId(userId).stream().map(TaskAssignment::getTaskId).collect(Collectors.toSet());
    }

    /** Projects the person belongs to, plus the projects of their assigned tasks. */
    @Transactional(readOnly = true)
    public Set<Long> projectIds(Long userId) {
        Set<Long> ids = members.findByUserId(userId).stream().map(ProjectMember::getProjectId).collect(Collectors.toCollection(HashSet::new));
        tasks.findAllById(taskIds(userId)).forEach(t -> {
            if (t.getProject() != null) {
                ids.add(t.getProject().getId());
            }
        });
        return ids;
    }

    /** 404 rather than 403, so probing ids does not reveal which tasks exist. */
    public void requireTask(Long userId, Long taskId) {
        if (taskId == null || !assignments.findByTaskId(taskId).map(a -> a.getUserId().equals(userId)).orElse(false)) {
            throw new NotFoundException("That task is not in your workspace.");
        }
    }

    public void requireProject(Long userId, Long projectId) {
        if (projectId == null || !projectIds(userId).contains(projectId)) {
            throw new NotFoundException("That project is not in your workspace.");
        }
    }

    /** The step's task must be assigned to this person. Returns the task id. */
    @Transactional(readOnly = true)
    public Long requireStep(Long userId, Long stepId) {
        Long taskId = steps.findById(stepId).map(s -> s.getTask().getId())
                .orElseThrow(() -> new NotFoundException("That step is not in your workspace."));
        requireTask(userId, taskId);
        return taskId;
    }

    /* ---------- Administration ---------- */

    @Transactional(readOnly = true)
    public List<Assignment> allAssignments() {
        Map<Long, String> names = names();
        return assignments.findAll().stream()
                .map(a -> new Assignment(a.getTaskId(), a.getUserId(), names.get(a.getUserId()), a.getAssignedAt(), a.getAssignedBy()))
                .toList();
    }

    /** Assigns a task to one person, or clears it with a null user. The person joins the task's project. */
    @Transactional
    public Assignment assign(AppUser actor, Long taskId, Long userId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> new NotFoundException("That task no longer exists."));
        TaskAssignment current = assignments.findByTaskId(taskId).orElse(null);
        LocalDateTime now = LocalDateTime.now(clock);
        if (userId == null) {
            if (current != null) {
                assignments.delete(current);
            }
            return new Assignment(taskId, null, null, null, null);
        }
        AppUser person = accounts.require(userId);
        if (!person.isActive()) {
            throw new ConflictException("That account is deactivated. Reactivate it before assigning work.");
        }
        boolean changed = current == null || !current.getUserId().equals(userId);
        if (current == null) {
            current = assignments.save(new TaskAssignment(taskId, userId, now, actor.getDisplayName()));
        } else if (changed) {
            current.reassign(userId, now, actor.getDisplayName());
        }
        if (task.getProject() != null) {
            addMember(task.getProject().getId(), userId);
        }
        if (changed && !Objects.equals(userId, actor.getId())) {
            notifications.notifyUser(userId, "TASK_ASSIGNED", "New task: " + task.getTitle(),
                    "Assigned by " + actor.getDisplayName() + (task.getDueDate() != null ? " · due " + task.getDueDate() : ""),
                    "#/task?id=" + taskId);
        }
        return new Assignment(taskId, userId, person.getDisplayName(), current.getAssignedAt(), current.getAssignedBy());
    }

    @Transactional(readOnly = true)
    public List<Long> projectMemberIds(Long projectId) {
        return members.findByProjectId(projectId).stream().map(ProjectMember::getUserId).distinct().toList();
    }

    /** Replaces a project's team. People who still hold tasks in the project stay reachable through them. */
    @Transactional
    public List<Long> setProjectMembers(Long projectId, List<Long> userIds) {
        if (!projects.existsById(projectId)) {
            throw new NotFoundException("That project no longer exists.");
        }
        Set<Long> wanted = new HashSet<>(userIds == null ? List.of() : userIds);
        for (Long id : wanted) {
            accounts.require(id);
        }
        List<ProjectMember> current = members.findByProjectId(projectId);
        current.stream().filter(m -> !wanted.contains(m.getUserId())).forEach(members::delete);
        Set<Long> have = current.stream().map(ProjectMember::getUserId).collect(Collectors.toSet());
        wanted.stream().filter(id -> !have.contains(id)).forEach(id -> addMember(projectId, id));
        return projectMemberIds(projectId);
    }

    /** Everyone on a project: listed members and anyone holding a task in it. */
    @Transactional(readOnly = true)
    public Set<Long> peopleOnProject(Long projectId) {
        Set<Long> ids = new HashSet<>(projectMemberIds(projectId));
        Set<Long> projectTasks = tasks.findAll().stream()
                .filter(t -> t.getProject() != null && projectId.equals(t.getProject().getId()))
                .map(Task::getId).collect(Collectors.toSet());
        assignments.findAll().stream().filter(a -> projectTasks.contains(a.getTaskId())).forEach(a -> ids.add(a.getUserId()));
        return ids;
    }

    private void addMember(Long projectId, Long userId) {
        if (!members.existsByProjectIdAndUserId(projectId, userId)) {
            members.save(new ProjectMember(projectId, userId, LocalDateTime.now(clock)));
        }
    }

    private Map<Long, String> names() {
        Map<Long, String> names = new HashMap<>();
        accounts.listUsers().forEach(u -> names.put(u.id(), u.displayName()));
        return names;
    }

    public static boolean isAdmin(AppUser user) {
        return user.getRole() == UserRole.ADMIN;
    }
}
