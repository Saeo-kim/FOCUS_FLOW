package com.example.project_flow.security;

/** Information classification, lowest to highest sensitivity. Order is meaningful: compare with compareTo. */
public enum SecurityLevel {
    /** Publicly available. AI access permitted, including external providers. */
    PUBLIC,
    /** Ordinary internal business information. Only authorized company AI tools; never external. */
    INTERNAL,
    /** Financial records, contracts, customer data, business plans. Explicit permission; no external AI. */
    CONFIDENTIAL,
    /** Credentials, payment secrets, critical IP. No direct AI access; never transmitted; Owner authorization. */
    RESTRICTED
}
