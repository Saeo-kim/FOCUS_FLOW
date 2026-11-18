package com.example.project_flow.security.web;

import com.example.project_flow.security.SecurityService;
import com.example.project_flow.security.SecurityService.FindingView;
import com.example.project_flow.security.SecurityService.IncidentView;
import com.example.project_flow.security.SecurityService.Summary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * Read-only security status for Owner's Office. Authenticated by FOCUS_FLOW's security service token
 * (SECURITY_INTEGRATION_TOKEN). Unset means every request is refused with 401. Exempt from the session
 * check only for these handlers, which check the token themselves.
 */
@RestController
@RequestMapping("/api/integration/owners-office/security")
public class SecurityIntegrationController {

    private final SecurityService service;
    private final byte[] expected;

    public SecurityIntegrationController(SecurityService service,
                                         @Value("${focusflow.security-integration-token:}") String token) {
        this.service = service;
        this.expected = token == null || token.isBlank() ? null : token.trim().getBytes(StandardCharsets.UTF_8);
    }

    @GetMapping("/summary")
    public Summary summary(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authorize(authorization);
        return service.buildSummary();
    }

    @GetMapping("/incidents")
    public List<IncidentView> incidents(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authorize(authorization);
        return service.incidentsForIntegration();
    }

    @GetMapping("/findings")
    public List<FindingView> findings(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authorize(authorization);
        return service.findingsForIntegration();
    }

    private void authorize(String header) {
        String given = header != null && header.startsWith("Bearer ") ? header.substring(7).trim() : "";
        boolean valid = expected != null && MessageDigest.isEqual(given.getBytes(StandardCharsets.UTF_8), expected);
        if (!valid) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
    }
}
