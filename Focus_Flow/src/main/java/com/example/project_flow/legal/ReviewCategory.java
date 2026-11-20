package com.example.project_flow.legal;

/** The kinds of review the legal and compliance function handles. */
public enum ReviewCategory {
    AGREEMENT_REVIEW("Agreement Review"),
    TERMS_OF_SERVICE_REVIEW("Terms of Service Review"),
    PRIVACY_POLICY_REVIEW("Privacy Policy Review"),
    CONTRACT_RISK_ASSESSMENT("Contract Risk Assessment"),
    SUBSCRIPTION_PAYMENT_REVIEW("Subscription and Payment Review"),
    DATA_PROTECTION_REVIEW("Data Protection Review"),
    REGULATORY_COMPLIANCE("Regulatory Compliance"),
    LEGAL_RISK_MONITORING("Legal Risk Monitoring");

    private final String label;

    ReviewCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
