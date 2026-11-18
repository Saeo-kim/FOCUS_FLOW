package com.example.project_flow.legal;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.admin.account.Department;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.legal.LegalComplianceService.FindingRequest;
import com.example.project_flow.legal.LegalComplianceService.ReviewRequest;
import com.example.project_flow.legal.LegalComplianceService.ReviewView;
import com.example.project_flow.legal.LegalComplianceService.DecisionView;
import com.example.project_flow.legal.LegalComplianceService.Summary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class LegalComplianceTest {

    @Autowired LegalComplianceService service;
    @Autowired LegalPositionSeeder seeder;
    @Autowired OrgPositionRepository positions;
    @Autowired OrgPositionFunctionRepository functions;
    @Autowired AppUserRepository users;

    private static final List<String> ALL = List.of("legal-advisor", "contract-reviewer", "privacy-advisor",
            "compliance-officer", "consumer-advocate");

    private AppUser admin() {
        return users.save(new AppUser("legal-" + UUID.randomUUID().toString().substring(0, 8), "Owner", "not-used",
                UserRole.ADMIN, LocalDateTime.now()));
    }

    private ReviewView newReview(AppUser actor, String title, LocalDate deadline) {
        return service.create(actor, LegalCatalog.LEAD, new ReviewRequest("AGREEMENT_REVIEW", title,
                "https://example.test/doc", "test document", Priority.HIGH, deadline, null, null, null));
    }

    private static FindingRequest f(String key, RiskLevel risk) {
        return new FindingRequest(key, risk, risk + " finding", "recommendation for " + key);
    }

    private List<FindingRequest> all(RiskLevel risk) {
        List<FindingRequest> out = new ArrayList<>();
        ALL.forEach(k -> out.add(f(k, risk)));
        return out;
    }

    @Test
    void positionIsRegisteredOnceAndStaysVacant() {
        seeder.run(null);
        seeder.run(null);
        List<OrgPosition> found = positions.findAll().stream().filter(p -> p.getPositionKey().equals(LegalCatalog.LEAD)).toList();
        assertEquals(1, found.size(), "no duplicate position");
        OrgPosition lead = found.get(0);
        assertEquals("Legal & Compliance Lead", lead.getTitle());
        assertEquals("법률·준법 총괄 책임자", lead.getKoreanTitle());
        assertEquals(Department.LEGAL_COMPLIANCE, lead.getDepartment());
        assertEquals("VACANT", lead.getStatus());
        assertNull(lead.getAssigneeUserId(), "no person or agent is attached");
        assertEquals(5, functions.findByPositionKeyOrderBySortOrderAsc(LegalCatalog.LEAD).size());
        assertEquals(ALL, functions.findByPositionKeyOrderBySortOrderAsc(LegalCatalog.LEAD).stream()
                .map(OrgPositionFunction::getFunctionKey).toList());
    }

    @Test
    void reviewsCarryCategoryDeadlineAndOverdueFlag() {
        AppUser actor = admin();
        ReviewView r = newReview(actor, "서비스 약관 검토", LocalDate.now().minusDays(2));
        assertEquals("AGREEMENT_REVIEW", r.category());
        assertEquals("Agreement Review", r.categoryLabel());
        assertEquals("OPEN", r.status());
        assertTrue(r.overdue(), "past deadline and pending");
        assertNull(r.completedAt());
    }

    @Test
    void validationRejectsUnknownCategoryAndFunction() {
        AppUser actor = admin();
        assertThrows(ConflictException.class, () -> service.create(actor, LegalCatalog.LEAD,
                new ReviewRequest("NOT_A_CATEGORY", "x", null, null, Priority.LOW, null, null, null, null)));
        ReviewView r = newReview(actor, "validation", null);
        assertThrows(ConflictException.class, () -> service.saveFindings(actor, Long.valueOf(r.id()),
                List.of(f("astrologer", RiskLevel.LOW))));
        assertThrows(ConflictException.class, () -> service.create(actor, LegalCatalog.LEAD,
                new ReviewRequest("AGREEMENT_REVIEW", "  ", null, null, Priority.LOW, null, null, null, null)));
    }

    @Test
    void decisionAssistantApprovesOnlyWhenAllFiveAreLow() {
        AppUser actor = admin();
        ReviewView r = newReview(actor, "approve case", null);
        service.saveFindings(actor, r.id(), all(RiskLevel.LOW));
        DecisionView d = view(r.id());
        assertEquals("APPROVE", d.recommendation());
        assertEquals(5, d.specialistsReviewed());
        assertFalse(d.professionalReviewRequired());
        assertTrue(d.conflicts().isEmpty());
        assertTrue(d.executiveSummary().contains("Recommend approval"));
        assertTrue(d.disclaimer().contains("not legal advice"));
    }

    @Test
    void highRiskRecommendsRejectionConflictsAndProfessionalReview() {
        AppUser actor = admin();
        ReviewView r = newReview(actor, "reject case", null);
        service.saveFindings(actor, r.id(), List.of(
                f("legal-advisor", RiskLevel.HIGH), f("contract-reviewer", RiskLevel.NONE),
                f("privacy-advisor", RiskLevel.LOW), f("compliance-officer", RiskLevel.LOW),
                f("consumer-advocate", RiskLevel.MEDIUM)));
        DecisionView d = view(r.id());
        assertEquals("REJECT", d.recommendation());
        assertEquals(RiskLevel.HIGH, d.highestRisk());
        assertTrue(d.professionalReviewRequired());
        assertFalse(d.conflicts().isEmpty(), "a HIGH next to NONE/LOW is a conflicting opinion");
        assertEquals(RiskLevel.HIGH, d.prioritizedRisks().get(0).riskLevel(), "risks are listed most serious first");
        assertTrue(d.executiveSummary().contains("Professional legal review is required"));
    }

    @Test
    void mediumRiskOrMissingReviewsRequestClarification() {
        AppUser actor = admin();
        ReviewView empty = newReview(actor, "empty case", null);
        assertEquals("CLARIFY", view(empty.id()).recommendation());
        assertEquals(0, view(empty.id()).specialistsReviewed());

        ReviewView partial = newReview(actor, "partial case", null);
        service.saveFindings(actor, partial.id(), List.of(f("legal-advisor", RiskLevel.LOW)));
        DecisionView p = view(partial.id());
        assertEquals("CLARIFY", p.recommendation());
        assertTrue(p.executiveSummary().contains("4 specialist review(s) are still missing"));

        ReviewView medium = newReview(actor, "medium case", null);
        List<FindingRequest> items = new ArrayList<>(all(RiskLevel.LOW));
        items.set(0, f("legal-advisor", RiskLevel.MEDIUM));
        service.saveFindings(actor, medium.id(), items);
        assertEquals("CLARIFY", view(medium.id()).recommendation());
    }

    @Test
    void findingsAreUpdatedInPlaceNotDuplicated() {
        AppUser actor = admin();
        ReviewView r = newReview(actor, "update findings", null);
        service.saveFindings(actor, r.id(), List.of(f("privacy-advisor", RiskLevel.HIGH)));
        ReviewView again = service.saveFindings(actor, r.id(), List.of(f("privacy-advisor", RiskLevel.LOW)));
        assertEquals(1, again.findings().size());
        assertEquals(RiskLevel.LOW, again.findings().get(0).riskLevel());
    }

    @Test
    void completingAReviewSetsCompletedAtAndMovesItToCompletedCounts() {
        AppUser actor = admin();
        ReviewView r = newReview(actor, "to close", null);
        int pendingBefore = service.summary(LegalCatalog.LEAD).pendingReviews();
        int completedBefore = service.summary(LegalCatalog.LEAD).completedReviews();
        ReviewView done = service.update(actor, r.id(), new ReviewRequest(null, "to close", null, null, null, null,
                "APPROVED", "all clear", "sign as drafted"));
        assertEquals("APPROVED", done.status());
        assertNotNull(done.completedAt());
        assertEquals("sign as drafted", done.recommendedAction());
        Summary after = service.summary(LegalCatalog.LEAD);
        assertEquals(pendingBefore - 1, after.pendingReviews(), "closing a review removes it from pending");
        assertEquals(completedBefore + 1, after.completedReviews());
    }

    @Test
    void summaryCountsHighPriorityOpenRisks() {
        AppUser actor = admin();
        int before = service.summary(LegalCatalog.LEAD).highPriorityRisks();
        ReviewView r = newReview(actor, "risky open review", null);
        service.saveFindings(actor, r.id(), List.of(f("consumer-advocate", RiskLevel.HIGH)));
        Summary s = service.summary(LegalCatalog.LEAD);
        assertEquals(before + 1, s.highPriorityRisks());
        assertTrue(s.topRisks().stream().anyMatch(x -> x.reviewId().equals(r.id()) && x.riskLevel() == RiskLevel.HIGH));
    }

    @Test
    void positionsEndpointShowsCountsAndFunctions() {
        AppUser actor = admin();
        newReview(actor, "counted review", null);
        LegalComplianceService.PositionView p = service.positions().stream()
                .filter(v -> v.key().equals(LegalCatalog.LEAD)).findFirst().orElseThrow();
        assertEquals(5, p.functions().size());
        assertTrue(p.pendingReviews() >= 1);
        assertEquals("Legal & Compliance Lead", p.title());
    }

    private DecisionView view(Long id) {
        return service.consolidate(id);
    }
}
