package com.example.project_flow.admin.work;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.aiteam.AiMentions;
import com.example.project_flow.aiteam.AiRequestService;
import com.example.project_flow.entity.Task;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/** Discussion on a task. A comment that mentions @CFO also asks the CFO to answer. */
@Service
public class TaskCommentService {

    public static final int MAX_LENGTH = 2000;

    public record CommentRequest(String body) {
    }

    public record CommentView(Long id, Long taskId, String authorName, boolean fromAgent, String body,
                              Long aiRequestId, LocalDateTime createdAt) {
        static CommentView from(TaskComment c) {
            return new CommentView(c.getId(), c.getTaskId(), c.getAuthorName(), c.isFromAgent(), c.getBody(),
                    c.getAiRequestId(), c.getCreatedAt());
        }
    }

    private final TaskCommentRepository comments;
    private final TaskRepository tasks;
    private final AiRequestService ai;
    private final Clock clock;

    public TaskCommentService(TaskCommentRepository comments, TaskRepository tasks, AiRequestService ai, Clock clock) {
        this.comments = comments;
        this.tasks = tasks;
        this.ai = ai;
        this.clock = clock;
    }

    /** Callers check that the person may see the task before calling. */
    @Transactional(readOnly = true)
    public List<CommentView> list(Long taskId) {
        requireTask(taskId);
        return comments.findByTaskIdOrderByIdAsc(taskId).stream().map(CommentView::from).toList();
    }

    @Transactional
    public CommentView post(AppUser author, Long taskId, String body) {
        Task task = requireTask(taskId);
        String text = body == null ? "" : body.trim();
        if (text.isEmpty()) throw new ConflictException("Write something before posting.");
        if (text.length() > MAX_LENGTH) throw new ConflictException("Keep comments under " + MAX_LENGTH + " characters.");
        TaskComment comment = comments.save(new TaskComment(taskId, author.getId(), author.getDisplayName(), false, text,
                null, LocalDateTime.now(clock)));
        if (AiMentions.mentionsCfo(text)) {
            ai.onMention(author, comment, task.getTitle()).ifPresent(request -> comment.linkRequest(request.getId()));
        }
        return CommentView.from(comment);
    }

    private Task requireTask(Long taskId) {
        return tasks.findById(taskId).orElseThrow(() -> new NotFoundException("That task no longer exists."));
    }
}
