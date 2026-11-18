package com.example.project_flow.legal.web;

import com.example.project_flow.legal.LegalComplianceService;
import com.example.project_flow.legal.LegalComplianceService.ReviewPackage;
import com.example.project_flow.legal.LegalComplianceService.ReviewView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Read-only access for Owner's Office. It sees reviews, the five specialist findings, the Decision
 * Assistant's readiness and the human decisions recorded so far. It cannot record a decision: only
 * the owner, signed in to FOCUS FLOW, can. Authenticated by a service token (FOCUS_FLOW_LEGAL_TOKEN),
 * which is disabled when unset.
 */
@RestController
@RequestMapping("/api/integration/owners-office/legal")
public class LegalIntegrationController {

    private final LegalComplianceService service;
    private final byte[] expected;

    public LegalIntegrationController(LegalComplianceService service,
                                      @Value("${focusflow.legal-integration-token:}") String token) {
        this.service = service;
        this.expected = token == null || token.isBlank() ? null : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/reviews")
    public List<ReviewView> reviews(@RequestHeader(value = "Authorization", required = false) String authorization,
                                    @RequestParam(defaultValue = "legal-compliance-lead") String position,
                                    @RequestParam(required = false) String status) {
        authorize(authorization);
        return service.reviews(position, status);
    }

    @GetMapping("/reviews/{id}")
    public ReviewPackage review(@RequestHeader(value = "Authorization", required = false) String authorization,
                                @PathVariable Long id) {
        authorize(authorization);
        return service.reviewPackage(id);
    }

    private void authorize(String header) {
        if (expected == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "The Legal integration is not enabled.");
        }
        String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        if (!MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
    }
}
