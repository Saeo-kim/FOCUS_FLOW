package com.example.project_flow.legal;

import java.util.List;

/**
 * The seam for CONSENT GUARD. CONSENT GUARD is a separate product, not part of this app, and nothing
 * is connected to it today. A future integration implements this interface and provides the documents
 * (agreements, consent forms, policies) that need legal and compliance review. Without an implementation,
 * the default below returns nothing, so the review list is unaffected.
 */
public interface ReviewSubjectSource {

    /** A document that a product wants reviewed. Only references are exchanged, never the product's data. */
    record Subject(String productKey, String name, String documentRef) {
    }

    List<Subject> pendingSubjects();
}
