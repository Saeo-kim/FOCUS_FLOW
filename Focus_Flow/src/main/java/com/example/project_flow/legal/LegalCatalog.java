package com.example.project_flow.legal;

import com.example.project_flow.admin.account.Department;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * The legal and compliance position and its five specialist functions, declared in one place.
 * The seeder registers them once. New functions or positions are added here, not in the database.
 */
@Component
public class LegalCatalog {

    public record Function(String key, String title, String description) {
    }

    public record Position(String key, String title, String koreanTitle, Department department, String mission,
                           List<Function> functions) {
    }

    public static final String LEAD = "legal-compliance-lead";

    private static final Position LEGAL_LEAD = new Position(
            LEAD,
            "Legal & Compliance Lead",
            "Chief legal and compliance officer",
            Department.LEGAL_COMPLIANCE,
            "Protect the owner and the organization from unfavorable agreements, hidden obligations, privacy risks, "
                    + "legal exposure, and regulatory compliance issues. Review agreements, consent forms, service terms, "
                    + "contracts, and privacy policies before the organization accepts them.",
            List.of(
                    new Function("legal-advisor", "Legal Advisor",
                            "Legal risk identification, liability and indemnification review, rights waiver analysis, legal interpretation and escalation."),
                    new Function("contract-reviewer", "Contract Reviewer",
                            "Contract terms, automatic renewal, payment obligations, cancellation and termination, penalties and hidden conditions."),
                    new Function("privacy-advisor", "Privacy Advisor",
                            "Personal information collection, third-party data sharing, international transfers, AI training and data use, retention and deletion."),
                    new Function("compliance-officer", "Compliance Officer",
                            "Regulatory requirements, consent procedures, required disclosures, internal compliance checks."),
                    new Function("consumer-advocate", "Consumer Advocate",
                            "Unfair contractual conditions, consumer disadvantages, restrictions on user rights, negotiation and clarification recommendations.")));

    public List<Position> all() {
        return List.of(LEGAL_LEAD);
    }

    public Optional<Position> byKey(String key) {
        return all().stream().filter(p -> p.key().equals(key)).findFirst();
    }

    /** Whether a function key belongs to the given position. */
    public boolean hasFunction(String positionKey, String functionKey) {
        return byKey(positionKey).map(p -> p.functions().stream().anyMatch(f -> f.key().equals(functionKey))).orElse(false);
    }
}
