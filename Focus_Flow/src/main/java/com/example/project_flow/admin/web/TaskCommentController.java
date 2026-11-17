package com.example.project_flow.admin.web;

import com.example.project_flow.admin.work.TaskCommentService;
import com.example.project_flow.admin.work.TaskCommentService.CommentRequest;
import com.example.project_flow.admin.work.TaskCommentService.CommentView;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Task discussion for administrators. Members use the same service through /api/member. */
@RestController
@RequestMapping("/api/admin/tasks/{taskId}/comments")
public class TaskCommentController {

    private final TaskCommentService comments;

    public TaskCommentController(TaskCommentService comments) {
        this.comments = comments;
    }

    @GetMapping
    public List<CommentView> list(@PathVariable Long taskId) {
        return comments.list(taskId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CommentView post(@PathVariable Long taskId, @RequestBody CommentRequest body, HttpServletRequest request) {
        return comments.post(UserSession.user(request), taskId, body == null ? null : body.body());
    }
}
