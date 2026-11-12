package com.example.project_flow.admin.web;

import com.example.project_flow.admin.account.AccountDtos.UserResponse;
import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.submission.SubmissionService;
import com.example.project_flow.admin.submission.SubmissionService.StoredFile;
import com.example.project_flow.admin.submission.SubmissionStatus;
import com.example.project_flow.admin.work.WorkAccessService;
import com.example.project_flow.dto.RecordDtos.PhaseResponse;
import com.example.project_flow.service.PhaseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Read-only views across people and phases, for the admin workspace. */
@RestController
@RequestMapping("/api/admin")
public class InsightsController {

    private final SubmissionService submissions;
    private final WorkAccessService access;
    private final AccountService accounts;
    private final PhaseService phases;

    public InsightsController(SubmissionService submissions, WorkAccessService access, AccountService accounts, PhaseService phases) {
        this.submissions = submissions;
        this.access = access;
        this.accounts = accounts;
        this.phases = phases;
    }

    /**
     * Who has submitted for each phase of a project and who has not. {@code cells} maps a phase id to
     * {user id → best status}; a person missing from a phase has not submitted for it.
     */
    public record Tracker(Long projectId, List<PhaseResponse> phases, List<UserResponse> people,
                          Map<Long, Map<Long, SubmissionStatus>> cells) {
    }

    @GetMapping("/tracker")
    public Tracker tracker(@RequestParam Long projectId) {
        Set<Long> ids = access.peopleOnProject(projectId);
        Map<Long, Map<Long, SubmissionStatus>> cells = submissions.statusByPhaseAndUser(projectId);
        cells.values().forEach(byUser -> ids.addAll(byUser.keySet())); // anyone who handed something in
        List<UserResponse> people = accounts.listUsers().stream().filter(u -> ids.contains(u.id()))
                .sorted(Comparator.comparing(UserResponse::displayName)).toList();
        return new Tracker(projectId,
                phases.list(projectId).stream().sorted(Comparator.comparing(PhaseResponse::startDate)).toList(),
                people, cells);
    }

    @GetMapping("/files")
    public List<StoredFile> files() {
        return submissions.allFiles();
    }
}
