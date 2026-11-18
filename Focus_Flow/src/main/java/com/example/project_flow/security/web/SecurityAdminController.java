package com.example.project_flow.security.web;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.web.UserSession;
import com.example.project_flow.security.AuditFinding;
import com.example.project_flow.security.DataCategory;
import com.example.project_flow.security.Destination;
import com.example.project_flow.security.Decision;
import com.example.project_flow.security.SecurityIncident;
import com.example.project_flow.security.SecurityLevel;
import com.example.project_flow.security.SecurityDefaults;
import com.example.project_flow.security.SecurityService;
import com.example.project_flow.security.SecurityService.AccessResult;
import com.example.project_flow.security.SecurityService.FindingView;
import com.example.project_flow.security.SecurityService.GrantView;
import com.example.project_flow.security.SecurityService.IncidentView;
import com.example.project_flow.security.SecurityService.ItemView;
import com.example.project_flow.security.SecurityService.LogView;
import com.example.project_flow.security.SecurityService.PolicyView;
import com.example.project_flow.security.SecurityService.PositionView;
import com.example.project_flow.security.SecurityService.RequestView;
import com.example.project_flow.security.SecurityService.Summary;
import com.example.project_flow.security.SecurityService.ChainCheck;
import com.example.project_flow.security.AiActivityLog;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * AI governance and information security. Administrators only (AuthInterceptor). Every rule is enforced
 * in SecurityService, so these endpoints add no authority of their own.
 */
@RestController
@RequestMapping("/api/admin/security")
public class SecurityAdminController {

    public record ItemRequest(String title, String category, String level, String content) {
    }
    public record LevelRequest(String level) {
    }
    public record EvaluateRequest(Long actorUserId, Long itemId, String destination, String purpose, String content) {
    }
    public record DecisionRequest(boolean approve, String reason) {
    }
    public record GrantRequest(Long actorUserId, String maxLevel, String justification, LocalDate expiresOn) {
    }
    public record IncidentRequest(String title, String severity, String department, String role, LocalDate discoveredOn,
                                  String evidence, String recommendedAction) {
    }
    public record StatusRequest(String status) {
    }
    public record FindingRequest(String source, String subjectKey, String title, String severity, String department,
                                 String role, LocalDate discoveredOn, String evidence, String recommendedAction) {
    }
    public record PolicyRequest(String key, String title, String body) {
    }
    public record AssignRequest(Long userId) {
    }

    private final SecurityService service;

    public SecurityAdminController(SecurityService service) {
        this.service = service;
    }

    private static AppUser me(HttpServletRequest r) {
        return UserSession.user(r);
    }

    /** The safe defaults in force, as recorded in the system. Read-only. */
    @GetMapping("/defaults")
    public java.util.Map<String, Object> defaults(HttpServletRequest r) {
        me(r);
        return java.util.Map.of("policyKey", SecurityDefaults.POLICY_KEY, "rules", SecurityDefaults.RULES);
    }

    @GetMapping("/summary")
    public Summary summary(HttpServletRequest r) {
        return service.summary(me(r));
    }

    /* ---------- positions ---------- */

    @GetMapping("/positions")
    public List<PositionView> positions(HttpServletRequest r) {
        me(r);
        return service.positions();
    }

    @PutMapping("/positions/{key}")
    public PositionView assign(@PathVariable String key, @RequestBody AssignRequest body, HttpServletRequest r) {
        return service.assignPosition(me(r), key, body == null ? null : body.userId());
    }

    /* ---------- classification ---------- */

    @GetMapping("/items")
    public List<ItemView> items(HttpServletRequest r) {
        return service.items(me(r));
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemView classify(@RequestBody ItemRequest body, HttpServletRequest r) {
        return service.classify(me(r), body.title(), parse(DataCategory.class, body.category(), "category"),
                parse(SecurityLevel.class, body.level(), "level"), body.content());
    }

    @PutMapping("/items/{id}/level")
    public ItemView reclassify(@PathVariable Long id, @RequestBody LevelRequest body, HttpServletRequest r) {
        return service.reclassify(me(r), id, parse(SecurityLevel.class, body.level(), "level"));
    }

    /* ---------- AI access gateway ---------- */

    @PostMapping("/evaluate")
    public AccessResult evaluate(@RequestBody EvaluateRequest body, HttpServletRequest r) {
        return service.evaluate(me(r), body.actorUserId(), body.itemId(),
                parse(Destination.class, body.destination(), "destination"), body.purpose(), body.content());
    }

    @GetMapping("/requests")
    public List<RequestView> requests(HttpServletRequest r) {
        return service.requests(me(r));
    }

    @PostMapping("/requests/{id}/decision")
    public RequestView decide(@PathVariable Long id, @RequestBody DecisionRequest body, HttpServletRequest r) {
        return service.decideRequest(me(r), id, body.approve(), body.reason());
    }

    @GetMapping("/grants")
    public List<GrantView> grants(HttpServletRequest r) {
        return service.grants(me(r));
    }

    @PostMapping("/grants")
    @ResponseStatus(HttpStatus.CREATED)
    public GrantView grant(@RequestBody GrantRequest body, HttpServletRequest r) {
        return service.grant(me(r), body.actorUserId(), parse(SecurityLevel.class, body.maxLevel(), "maxLevel"),
                body.justification(), body.expiresOn());
    }

    @PostMapping("/grants/{id}/revoke")
    public GrantView revoke(@PathVariable Long id, HttpServletRequest r) {
        return service.revokeGrant(me(r), id);
    }

    /* ---------- AI activity log ---------- */

    @GetMapping("/log")
    public List<LogView> log(@RequestParam(defaultValue = "100") int limit, HttpServletRequest r) {
        return service.log(me(r), limit);
    }

    @GetMapping("/log/verify")
    public ChainCheck verify(HttpServletRequest r) {
        me(r);
        return service.verifyChain();
    }

    /* ---------- incidents ---------- */

    @GetMapping("/incidents")
    public List<IncidentView> incidents(HttpServletRequest r) {
        return service.incidents(me(r));
    }

    @PostMapping("/incidents")
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentView record(@RequestBody IncidentRequest body, HttpServletRequest r) {
        return service.recordIncident(me(r), body.title(), parse(SecurityIncident.Severity.class, body.severity(), "severity"),
                body.department(), body.role(), body.discoveredOn(), body.evidence(), body.recommendedAction());
    }

    @PutMapping("/incidents/{id}/status")
    public IncidentView incidentStatus(@PathVariable Long id, @RequestBody StatusRequest body, HttpServletRequest r) {
        return service.setIncidentStatus(me(r), id, parse(SecurityIncident.Status.class, body.status(), "status"));
    }

    @PostMapping("/incidents/{id}/acknowledge")
    public IncidentView acknowledgeIncident(@PathVariable Long id, HttpServletRequest r) {
        return service.acknowledgeIncident(me(r), id);
    }

    /* ---------- audit findings ---------- */

    @GetMapping("/findings")
    public List<FindingView> findings(HttpServletRequest r) {
        return service.findings(me(r));
    }

    @PostMapping("/findings")
    @ResponseStatus(HttpStatus.CREATED)
    public FindingView record(@RequestBody FindingRequest body, HttpServletRequest r) {
        return service.recordFinding(me(r), parse(AuditFinding.Source.class, body.source(), "source"), body.subjectKey(),
                body.title(), parse(SecurityIncident.Severity.class, body.severity(), "severity"), body.department(),
                body.role(), body.discoveredOn(), body.evidence(), body.recommendedAction());
    }

    @PutMapping("/findings/{id}/status")
    public FindingView findingStatus(@PathVariable Long id, @RequestBody StatusRequest body, HttpServletRequest r) {
        return service.setFindingStatus(me(r), id, parse(AuditFinding.Status.class, body.status(), "status"));
    }

    @PostMapping("/findings/{id}/acknowledge")
    public FindingView acknowledgeFinding(@PathVariable Long id, HttpServletRequest r) {
        return service.acknowledgeFinding(me(r), id);
    }

    /* ---------- policies ---------- */

    @GetMapping("/policies")
    public List<PolicyView> policies(HttpServletRequest r) {
        return service.policies(me(r));
    }

    @PostMapping("/policies")
    @ResponseStatus(HttpStatus.CREATED)
    public PolicyView draft(@RequestBody PolicyRequest body, HttpServletRequest r) {
        return service.draftPolicy(me(r), body.key(), body.title(), body.body());
    }

    @PostMapping("/policies/{id}/approve")
    public PolicyView approve(@PathVariable Long id, HttpServletRequest r) {
        return service.approvePolicy(me(r), id);
    }

    /** Parses an enum from the request, refusing unknown values with a readable message. */
    private static <E extends Enum<E>> E parse(Class<E> type, String value, String field) {
        if (value == null) throw new com.example.project_flow.exception.ConflictException("Choose a " + field + ".");
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException e) {
            throw new com.example.project_flow.exception.ConflictException("Unknown " + field + ": " + value);
        }
    }
}
