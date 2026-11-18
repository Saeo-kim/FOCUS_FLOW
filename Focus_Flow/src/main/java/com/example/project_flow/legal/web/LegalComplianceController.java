package com.example.project_flow.legal.web;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.web.UserSession;
import com.example.project_flow.legal.LegalCatalog;
import com.example.project_flow.legal.LegalComplianceService;
import com.example.project_flow.legal.LegalComplianceService.DecisionView;
import com.example.project_flow.legal.LegalComplianceService.FindingRequest;
import com.example.project_flow.legal.LegalComplianceService.PositionView;
import com.example.project_flow.legal.LegalComplianceService.ReviewRequest;
import com.example.project_flow.legal.LegalComplianceService.ReviewView;
import com.example.project_flow.legal.LegalComplianceService.Summary;
import com.example.project_flow.legal.ReviewCategory;
import com.example.project_flow.legal.ReviewStatus;
import com.example.project_flow.legal.RiskLevel;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Legal and compliance reviews. Administrators only (enforced by AuthInterceptor for /api/admin/**). */
@RestController
@RequestMapping("/api/admin/legal")
public class LegalComplianceController {

    public record Meta(List<Map<String, String>> categories, List<String> statuses, List<String> riskLevels) {
    }

    private final LegalComplianceService service;

    public LegalComplianceController(LegalComplianceService service) {
        this.service = service;
    }

    @GetMapping("/meta")
    public Meta meta() {
        return new Meta(
                Arrays.stream(ReviewCategory.values()).map(c -> Map.of("key", c.name(), "label", c.label())).toList(),
                Arrays.stream(ReviewStatus.values()).map(Enum::name).toList(),
                Arrays.stream(RiskLevel.values()).map(Enum::name).toList());
    }

    @GetMapping("/positions")
    public List<PositionView> positions() {
        return service.positions();
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam(defaultValue = LegalCatalog.LEAD) String position) {
        return service.summary(position);
    }

    @GetMapping("/reviews")
    public List<ReviewView> reviews(@RequestParam(defaultValue = LegalCatalog.LEAD) String position,
                                    @RequestParam(required = false) String status) {
        return service.reviews(position, status);
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewView create(@RequestParam(defaultValue = LegalCatalog.LEAD) String position,
                             @RequestBody ReviewRequest body, HttpServletRequest request) {
        AppUser actor = UserSession.user(request);
        return service.create(actor, position, body);
    }

    @PutMapping("/reviews/{id}")
    public ReviewView update(@PathVariable Long id, @RequestBody ReviewRequest body, HttpServletRequest request) {
        return service.update(UserSession.user(request), id, body);
    }

    @PutMapping("/reviews/{id}/findings")
    public ReviewView findings(@PathVariable Long id, @RequestBody List<FindingRequest> body, HttpServletRequest request) {
        return service.saveFindings(UserSession.user(request), id, body);
    }

    @GetMapping("/reviews/{id}/decision")
    public DecisionView decision(@PathVariable Long id) {
        return service.consolidate(id);
    }
}
