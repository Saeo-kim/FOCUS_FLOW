package com.example.project_flow.admin.web;

import com.example.project_flow.admin.work.WorkAccessService;
import com.example.project_flow.admin.work.WorkAccessService.Assignment;
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

    public AssignmentController(WorkAccessService access) {
        this.access = access;
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
        return access.assign(UserSession.user(request), taskId, body == null ? null : body.userId());
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
