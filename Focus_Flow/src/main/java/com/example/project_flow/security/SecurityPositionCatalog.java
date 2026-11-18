package com.example.project_flow.security;

import com.example.project_flow.admin.account.Department;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The executive security positions. Each is created vacant. The Owner assigns people, and the
 * independence rules in SecurityService apply to every assignment.
 */
@Component
public class SecurityPositionCatalog {

    public record Position(String key, String title, String mission) {
    }

    public static final String CISO = "ciso";
    public static final String IT_AUDITOR = "it-security-auditor";
    public static final String COMPLIANCE = "executive-compliance-officer";
    public static final String EXTERNAL_AUDITOR = "external-security-auditor";

    private static final List<Position> POSITIONS = List.of(
            new Position(CISO, "Chief Information Security Officer (CISO)",
                    "Set information security policy, define AI access permissions, classify company information, monitor sensitive data exposure, manage security incidents, and review AI-related security risk."),
            new Position(IT_AUDITOR, "IT Security Auditor",
                    "Independently inspect CISO policies, verify access control configuration, examine system and AI activity logs, test whether sensitive information is reachable without authorization, and report weaknesses directly to the Owner."),
            new Position(COMPLIANCE, "Compliance Officer",
                    "Review legal and regulatory compliance, privacy and personal data handling, contracts, consent documents and data processing agreements, identify potential violations, and escalate unresolved compliance risk to the Owner."),
            new Position(EXTERNAL_AUDITOR, "External Security Auditor",
                    "Independent external function: evaluate the internal security system, conduct assessments and penetration tests, check that internal auditors operate independently, and report critical vulnerabilities directly to the Owner. Not an employee with automatic access to company information."));

    public static final Department DEPARTMENT = Department.INFORMATION_SECURITY;

    public List<Position> all() {
        return POSITIONS;
    }

    public Optional<Position> byKey(String key) {
        return POSITIONS.stream().filter(p -> p.key().equals(key)).findFirst();
    }
}
