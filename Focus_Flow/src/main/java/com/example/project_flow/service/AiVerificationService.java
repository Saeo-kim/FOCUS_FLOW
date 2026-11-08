package com.example.project_flow.service;

import com.example.project_flow.dto.RecordDtos.AiVerificationRequest;
import com.example.project_flow.dto.RecordDtos.AiVerificationResponse;
import com.example.project_flow.entity.AiVerification;
import com.example.project_flow.entity.Task;
import com.example.project_flow.repository.AiVerificationRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
public class AiVerificationService extends ProjectRecordService<AiVerification, AiVerificationRequest, AiVerificationResponse> {

    private final Clock clock;

    public AiVerificationService(AiVerificationRepository repository, References refs, ActivityLogService activity, Clock clock) {
        super(repository, refs, activity);
        this.clock = clock;
    }

    @Override
    protected AiVerification newEntity() {
        return new AiVerification(LocalDateTime.now(clock));
    }

    @Override
    protected void apply(AiVerification a, AiVerificationRequest q) {
        a.describe(q.title().trim(), refs.task(q.taskId(), a.getProject()), References.clean(q.aiTool()),
                References.clean(q.requestText()), References.clean(q.changedFiles()), References.clean(q.aiActions()),
                References.clean(q.testItems()), References.clean(q.testResult()), References.clean(q.buildResult()),
                References.clean(q.followUp()));
        a.review(q.testStatus(), q.buildStatus(), References.clean(q.humanReview()), q.verified(), q.approved());
    }

    @Override
    protected AiVerificationResponse toResponse(AiVerification a) {
        Task task = a.getTask();
        return new AiVerificationResponse(a.getId(), a.getProjectId(), a.getTitle(),
                task != null ? task.getId() : null, task != null ? task.getTitle() : null, a.getAiTool(), a.getRequestText(),
                a.getChangedFiles(), a.getAiActions(), a.getTestItems(), a.getTestResult(), a.getTestStatus(),
                a.getBuildResult(), a.getBuildStatus(), a.getHumanReview(), a.isVerified(), a.getFollowUp(),
                a.isApproved(), a.getCreatedAt());
    }

    @Override
    protected String noun() {
        return "AI verification";
    }

    @Override
    protected String titleOf(AiVerification a) {
        return a.getTitle();
    }

    @Override
    protected Sort sort() {
        return Sort.by(Sort.Direction.DESC, "createdAt", "id");
    }
}
