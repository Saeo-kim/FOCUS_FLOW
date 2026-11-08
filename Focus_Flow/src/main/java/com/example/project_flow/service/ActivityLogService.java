package com.example.project_flow.service;

import com.example.project_flow.dto.ActivityResponse;
import com.example.project_flow.entity.ActivityLog;
import com.example.project_flow.repository.ActivityLogRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ActivityLogService {

    private static final int MAX_LIMIT = 200;

    private final ActivityLogRepository repository;
    private final Clock clock;

    public ActivityLogService(ActivityLogRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /** Joins the caller's transaction so a log row exists only if the change itself committed. */
    @Transactional
    public void record(String type, String message) {
        repository.save(new ActivityLog(type, message, LocalDateTime.now(clock)));
    }

    @Transactional(readOnly = true)
    public List<ActivityResponse> recent(int limit) {
        int size = Math.clamp(limit, 1, MAX_LIMIT);
        return repository.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, size)).stream()
                .map(ActivityResponse::from)
                .toList();
    }
}
