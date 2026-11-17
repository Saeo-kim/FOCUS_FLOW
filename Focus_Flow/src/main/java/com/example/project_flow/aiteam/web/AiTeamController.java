package com.example.project_flow.aiteam.web;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.web.UserSession;
import com.example.project_flow.aiteam.AiRequestService;
import com.example.project_flow.aiteam.AiRequestService.RequestView;
import com.example.project_flow.aiteam.AiRequestService.SyncResult;
import com.example.project_flow.aiteam.AiTeamService;
import com.example.project_flow.aiteam.AiTeamService.MemberView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** The AI team: its members, the work sent to them, and a way to ask for new work. Administrators only. */
@RestController
@RequestMapping("/api/admin/ai")
public class AiTeamController {

    public record AskRequest(
            String agentKey,
            @Size(max = 40, message = "Choose a report type.") String kind,
            @NotBlank(message = "Give the request a title.") @Size(max = 200, message = "Keep the title under 200 characters.") String title,
            @Size(max = 4000, message = "Keep the details under 4000 characters.") String instructions,
            Long projectId) {
    }

    private final AiTeamService team;
    private final AiRequestService requests;

    public AiTeamController(AiTeamService team, AiRequestService requests) {
        this.team = team;
        this.requests = requests;
    }

    @GetMapping("/team")
    public List<MemberView> team() {
        return team.members();
    }

    @GetMapping("/requests")
    public List<RequestView> requests(@RequestParam(defaultValue = "cfo") String agent) {
        return requests.list(agent);
    }

    @GetMapping("/requests/{id}")
    public RequestView request(@PathVariable Long id) {
        return requests.detail(id);
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public RequestView ask(@Valid @RequestBody AskRequest body, HttpServletRequest httpRequest) {
        AppUser actor = UserSession.user(httpRequest);
        String agent = body.agentKey() == null || body.agentKey().isBlank() ? "cfo" : body.agentKey();
        String kind = body.kind() == null || body.kind().isBlank() ? null : body.kind();
        var created = requests.askNew(actor, agent, kind, body.title().trim(),
                body.instructions() == null ? "" : body.instructions().trim(), body.projectId());
        return requests.detail(created.getId());
    }

    @PostMapping("/sync")
    public SyncResult sync() {
        return requests.sync();
    }
}
