package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.work.WorkAccessService;
import com.example.project_flow.admin.work.WorkAccessService.Assignment;
import com.example.project_flow.aiteam.AiRequestService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Who works on what: task owners and project teams, managed by an administrator. */
@RestController
@RequestMapping("/api/admin")
public class AssignmentController {

    private final WorkAccessService access;
    private final AiRequestService aiRequests;

    public AssignmentController(WorkAccessService access, AiRequestService aiRequests) {
        this.access = access;
        this.aiRequests = aiRequests;
    }

    public record AssigneeRequest(Long userId) {
    }

    public record MembersRequest(List<Long> userIds) {
    }

    @GetMapping("/assignments")
    public List<Assignment> assignments() {
        return access.allAssignments();
    }

    @PutMapping("/tasks/{taskId}/assignee")
    public Assignment assign(@PathVariable Long taskId, @RequestBody AssigneeRequest body, HttpServletRequest request) {
        AppUser actor = UserSession.user(request);
        Long userId = body == null ? null : body.userId();
        Assignment assignment = access.assign(actor, taskId, userId);
        // Handing a task to an AI teammate sends it to that teammate (unless it is already in flight).
        aiRequests.onAssigned(actor, taskId, userId);
        return assignment;
    }

    @GetMapping("/projects/{projectId}/members")
    public List<Long> members(@PathVariable Long projectId) {
        return access.projectMemberIds(projectId);
    }

    @PutMapping("/projects/{projectId}/members")
    public List<Long> setMembers(@PathVariable Long projectId, @RequestBody MembersRequest body) {
        return access.setProjectMembers(projectId, body == null ? List.of() : body.userIds());
    }
}
