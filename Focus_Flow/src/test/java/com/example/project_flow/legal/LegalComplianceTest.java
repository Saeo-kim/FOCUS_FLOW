package com.example.project_flow.legal;

import com.example.project_flow.admin.account.AccountKind;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.dto.TaskDtos.TaskRequest;
import com.example.project_flow.dto.TaskDtos.TaskResponse;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.ForbiddenException;
import com.example.project_flow.legal.LegalComplianceService.DecisionView;
import com.example.project_flow.legal.LegalComplianceService.LegalOwnerView;
import com.example.project_flow.legal.LegalComplianceService.FindingRequest;
import com.example.project_flow.legal.LegalComplianceService.HumanDecisionRequest;
import com.example.project_flow.legal.LegalComplianceService.HumanDecisionView;
import com.example.project_flow.legal.LegalComplianceService.IssueRequest;
import com.example.project_flow.legal.LegalComplianceService.ReviewPackage;
import com.example.project_flow.legal.LegalComplianceService.ReviewRequest;
import com.example.project_flow.legal.LegalComplianceService.ReviewView;
import com.example.project_flow.legal.LegalComplianceService.Summary;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.service.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "focusflow.legal-integration-token=legal-integration-test-token-0123456789")
class LegalComplianceTest {

    static final String INTEGRATION_TOKEN = "legal-integration-test-token-0123456789";

    @Autowired LegalComplianceService service;
    @Autowired LegalPositionSeeder seeder;
    @Autowired OrgPositionRepository positions;
    @Autowired OrgPositionFunctionRepository functions;
    @Autowired ComplianceReviewRepository reviewRepository;
    @Autowired HumanDecisionRepository decisionRepository;
    @Autowired AppUserRepository users;
    @Autowired TaskService tasks;
    @Autowired TaskRepository taskRepository;
    @Autowired MockMvc mvc;

    private static final List<String> ALL = List.of("legal-advisor", "contract-reviewer", "privacy-advisor",
            "compliance-officer", "consumer-advocate");

    private AppUser user(UserRole role, AccountKind kind) {
        AppUser u = new AppUser("legal-" + UUID.randomUUID().toString().substring(0, 8), "Person", "not-used", role, LocalDateTime.now());
        if (kind == AccountKind.AI) u.assignJob(AccountKind.AI, "Test seat", null);
        return users.save(u);
    }

    /** The appointed Owner. Reuses the current Owner if one is active; otherwise appoints a fresh administrator. */
    private AppUser owner() {
        return service.currentOwner().flatMap(o -> users.findById(o.getUserId()))
                .filter(AppUser::isActive)
                .orElseGet(() -> {
                    AppUser a = user(UserRole.ADMIN, AccountKind.HUMAN);
                    service.appointOwner(a, a.getId());
                    return a;
                });
    }

    /** An administrator who is NOT the Owner. */
    private AppUser administrator() { return user(UserRole.ADMIN, AccountKind.HUMAN); }
    private AppUser member() { return user(UserRole.MEMBER, AccountKind.HUMAN); }

    private ReviewView newReview(AppUser actor, String title) {
        return service.create(actor, LegalCatalog.LEAD, new ReviewRequest("AGREEMENT_REVIEW", title,
                "https://example.test/doc", "test document", Priority.HIGH, null, null, null, null));
    }

    private static FindingRequest f(String key, RiskLevel risk) {
        return new FindingRequest(key, risk, risk + " finding for " + key, "recommendation for " + key);
    }

    private List<FindingRequest> all(RiskLevel risk) {
        List<FindingRequest> out = new ArrayList<>();
        ALL.forEach(k -> out.add(f(k, risk)));
        return out;
    }

    private static final String LONG_REASON = "Reviewed the findings and accept the recorded decision.";

    /* ---------- position and reviews ---------- */

    @Test
    void positionIsRegisteredOnceAndStaysVacant() {
        seeder.run(null);
        seeder.run(null);
        List<OrgPosition> found = positions.findAll().stream().filter(p -> p.getPositionKey().equals(LegalCatalog.LEAD)).toList();
        assertEquals(1, found.size(), "no duplicate position");
        assertEquals("VACANT", found.get(0).getStatus());
        assertNull(found.get(0).getAssigneeUserId(), "no person or agent is attached");
        assertEquals(5, functions.findByPositionKeyOrderBySortOrderAsc(LegalCatalog.LEAD).size());
    }

    @Test
    void reviewsCarryCategoryDeadlineAndOverdueFlag() {
        ReviewView r = service.create(owner(), LegalCatalog.LEAD, new ReviewRequest("SUBSCRIPTION_PAYMENT_REVIEW", "약관 검토",
                null, null, Priority.HIGH, LocalDate.now().minusDays(2), null, null, null));
        assertEquals("Subscription and Payment Review", r.categoryLabel());
        assertTrue(r.overdue());
        assertFalse(r.issueResolved());
    }

    @Test
    void validationRefusesUnknownInputs() {
        AppUser actor = owner();
        assertThrows(ConflictException.class, () -> service.create(actor, LegalCatalog.LEAD,
                new ReviewRequest("NOT_A_CATEGORY", "x", null, null, Priority.LOW, null, null, null, null)));
        ReviewView r = newReview(actor, "validation");
        assertThrows(ConflictException.class, () -> service.saveFindings(actor, r.id(), List.of(f("astrologer", RiskLevel.LOW))));
        assertThrows(ConflictException.class, () -> service.create(actor, LegalCatalog.LEAD,
                new ReviewRequest("AGREEMENT_REVIEW", "   ", null, null, Priority.LOW, null, null, null, null)));
    }

    /* ---------- Decision Assistant states ---------- */

    @Test
    void noFindingsIsIncomplete() {
        ReviewView r = newReview(owner(), "no findings");
        DecisionView d = service.consolidate(r.id());
        assertEquals(DecisionState.INCOMPLETE, d.state());
        assertFalse(d.approvalAvailable());
        assertEquals(0, d.specialistsReviewed());
    }

    @Test
    void missingSpecialistReviewIsIncompleteAndApprovalUnavailable() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "partial");
        service.saveFindings(actor, r.id(), List.of(f("legal-advisor", RiskLevel.LOW)));
        DecisionView d = service.consolidate(r.id());
        assertEquals(DecisionState.INCOMPLETE, d.state());
        assertFalse(d.approvalAvailable());
        assertTrue(d.executiveSummary().contains("1 of 5"));
    }

    @Test
    void allFiveLowIsApprovalEligibleButNeverAnApproval() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "eligible");
        service.saveFindings(actor, r.id(), all(RiskLevel.LOW));
        DecisionView d = service.consolidate(r.id());
        assertEquals(DecisionState.APPROVAL_ELIGIBLE, d.state());
        assertTrue(d.approvalAvailable());
        assertTrue(d.conflicts().isEmpty());
        assertTrue(d.executiveSummary().contains("not an approval"));
        assertTrue(d.disclaimer().contains("A person authorized by the owner makes the final decision"));
        assertEquals("OPEN", service.reviews(LegalCatalog.LEAD, "OPEN").stream()
                .filter(x -> x.id().equals(r.id())).findFirst().orElseThrow().status(), "assistant never changes status");
    }

    @Test
    void allNoneIsAlsoApprovalEligible() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "none");
        service.saveFindings(actor, r.id(), all(RiskLevel.NONE));
        assertEquals(DecisionState.APPROVAL_ELIGIBLE, service.consolidate(r.id()).state());
    }

    @Test
    void mediumFindingRequiresClarificationAndListsTheIssue() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "medium");
        List<FindingRequest> items = new ArrayList<>(all(RiskLevel.LOW));
        items.set(3, f("compliance-officer", RiskLevel.MEDIUM));
        service.saveFindings(actor, r.id(), items);
        DecisionView d = service.consolidate(r.id());
        assertEquals(DecisionState.CLARIFICATION_REQUIRED, d.state());
        assertFalse(d.approvalAvailable());
        assertEquals(RiskLevel.MEDIUM, d.prioritizedRisks().get(0).riskLevel(), "the open issue is listed first");
    }

    @Test
    void highFindingBlocksAndRequiresHumanReviewWithoutRejectingOnItsOwn() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "blocked");
        service.saveFindings(actor, r.id(), List.of(f("legal-advisor", RiskLevel.HIGH), f("contract-reviewer", RiskLevel.NONE),
                f("privacy-advisor", RiskLevel.LOW), f("compliance-officer", RiskLevel.LOW), f("consumer-advocate", RiskLevel.MEDIUM)));
        DecisionView d = service.consolidate(r.id());
        assertEquals(DecisionState.BLOCK, d.state(), "BLOCK, not REJECT");
        assertTrue(d.humanReviewRequired());
        assertFalse(d.approvalAvailable());
        assertTrue(d.executiveSummary().contains("does not decide the outcome"));
        assertEquals(RiskLevel.HIGH, d.prioritizedRisks().get(0).riskLevel(), "risks are listed most serious first");
    }

    @Test
    void highFindingTakesPriorityOverMissingReviews() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "high and missing");
        service.saveFindings(actor, r.id(), List.of(f("consumer-advocate", RiskLevel.HIGH)));
        assertEquals(DecisionState.BLOCK, service.consolidate(r.id()).state());
    }

    @Test
    void missingReviewTakesPriorityOverMediumFinding() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "medium and missing");
        service.saveFindings(actor, r.id(), List.of(f("legal-advisor", RiskLevel.MEDIUM)));
        assertEquals(DecisionState.INCOMPLETE, service.consolidate(r.id()).state());
    }

    @Test
    void conflictingOpinionsAreReportedWhenOneSpecialistSeesHighAndAnotherSeesLow() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "conflict");
        service.saveFindings(actor, r.id(), List.of(f("privacy-advisor", RiskLevel.HIGH), f("contract-reviewer", RiskLevel.NONE),
                f("legal-advisor", RiskLevel.LOW), f("compliance-officer", RiskLevel.LOW), f("consumer-advocate", RiskLevel.LOW)));
        DecisionView d = service.consolidate(r.id());
        assertEquals(DecisionState.BLOCK, d.state());
        assertFalse(d.conflicts().isEmpty());
        assertTrue(d.conflicts().get(0).contains("Privacy Advisor rates HIGH"));
    }

    @Test
    void findingsAreUpdatedInPlaceNotDuplicated() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "update findings");
        service.saveFindings(actor, r.id(), List.of(f("privacy-advisor", RiskLevel.HIGH)));
        ReviewView again = service.saveFindings(actor, r.id(), List.of(f("privacy-advisor", RiskLevel.LOW)));
        assertEquals(1, again.findings().size());
        assertEquals(RiskLevel.LOW, again.findings().get(0).riskLevel());
    }

    /* ---------- human decisions ---------- */

    @Test
    void approvalIsRefusedUnlessEligibleAndNothingIsRecorded() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "approval refused");
        service.saveFindings(actor, r.id(), List.of(f("legal-advisor", RiskLevel.HIGH)));
        int before = decisionRepository.findByReviewIdOrderByIdAsc(r.id()).size();
        assertThrows(ConflictException.class, () -> service.recordHumanDecision(actor, r.id(),
                new HumanDecisionRequest("APPROVE", LONG_REASON)));
        assertEquals(before, decisionRepository.findByReviewIdOrderByIdAsc(r.id()).size(), "a refused approval records nothing");
        assertEquals("OPEN", reviewRepository.findById(r.id()).orElseThrow().getStatus().name());
    }

    @Test
    void approvalIsRecordedByTheOwnerWhenEligible() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "approved by owner");
        service.saveFindings(actor, r.id(), all(RiskLevel.LOW));
        HumanDecisionView d = service.recordHumanDecision(actor, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON));
        assertEquals("APPROVE", d.outcome());
        assertEquals(actor.getDisplayName(), d.decidedByName());
        assertEquals(LONG_REASON, d.reasoning());
        assertEquals(DecisionState.APPROVAL_ELIGIBLE, d.assistantState(), "the assistant's state is kept with the decision");
        assertNotNull(d.decidedAt());
        assertEquals("APPROVED", reviewRepository.findById(r.id()).orElseThrow().getStatus().name());
    }

    @Test
    void blockedReviewCanBeRejectedOrSentBackByTheOwner() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "blocked decision");
        service.saveFindings(actor, r.id(), List.of(f("legal-advisor", RiskLevel.HIGH), f("contract-reviewer", RiskLevel.LOW),
                f("privacy-advisor", RiskLevel.LOW), f("compliance-officer", RiskLevel.LOW), f("consumer-advocate", RiskLevel.LOW)));
        service.recordHumanDecision(actor, r.id(), new HumanDecisionRequest("REQUEST_CHANGES", "Ask the vendor to change the liability clause."));
        assertEquals("NEEDS_CLARIFICATION", reviewRepository.findById(r.id()).orElseThrow().getStatus().name());
        service.recordHumanDecision(actor, r.id(), new HumanDecisionRequest("REJECT", "The liability clause cannot be accepted."));
        assertEquals("REJECTED", reviewRepository.findById(r.id()).orElseThrow().getStatus().name());
    }

    @Test
    void deferLeavesStatusAndHistoryIsAppendOnly() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "append only");
        HumanDecisionView first = service.recordHumanDecision(actor, r.id(), new HumanDecisionRequest("DEFER", "Waiting for vendor answer."));
        HumanDecisionView second = service.recordHumanDecision(actor, r.id(), new HumanDecisionRequest("REJECT", "Decided after all."));
        List<HumanDecisionView> history = service.humanDecisions(actor, r.id());
        assertEquals(2, history.size());
        assertEquals(first.id(), history.get(0).id(), "oldest first");
        assertEquals("DEFER", history.get(0).outcome(), "the earlier decision is not overwritten");
        assertEquals("REJECT", history.get(1).outcome());
        assertEquals(second.id(), history.get(1).id());
    }

    @Test
    void reasoningAndOutcomeAreRequired() {
        AppUser actor = owner();
        ReviewView r = newReview(actor, "validation decision");
        assertThrows(ConflictException.class, () -> service.recordHumanDecision(actor, r.id(), new HumanDecisionRequest("DEFER", "short")));
        assertThrows(ConflictException.class, () -> service.recordHumanDecision(actor, r.id(), new HumanDecisionRequest("AUTO_APPROVE", LONG_REASON)));
        assertTrue(decisionRepository.findByReviewIdOrderByIdAsc(r.id()).isEmpty());
    }

    /* ---------- authorization ---------- */

    @Test
    void membersCannotRecordDecisionsReadHistoryOrTrackIssues() {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "owner only");
        service.saveFindings(owner, r.id(), all(RiskLevel.LOW));
        AppUser member = member();
        assertThrows(ForbiddenException.class, () -> service.recordHumanDecision(member, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON)));
        assertThrows(ForbiddenException.class, () -> service.humanDecisions(member, r.id()));
        assertThrows(ForbiddenException.class, () -> service.trackIssue(member, r.id(), new IssueRequest(true, "done")));
        assertTrue(decisionRepository.findByReviewIdOrderByIdAsc(r.id()).isEmpty(), "a refused attempt records nothing");
        assertEquals("OPEN", reviewRepository.findById(r.id()).orElseThrow().getStatus().name());
    }

    @Test
    void aiSeatCannotRecordDecisionsEvenIfItHasAdminRole() {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "ai owner");
        service.saveFindings(owner, r.id(), all(RiskLevel.LOW));
        AppUser seat = user(UserRole.ADMIN, AccountKind.AI);
        assertThrows(ForbiddenException.class, () -> service.recordHumanDecision(seat, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON)));
        assertThrows(ForbiddenException.class, () -> service.trackIssue(seat, r.id(), new IssueRequest(true, null)));
        assertTrue(decisionRepository.findByReviewIdOrderByIdAsc(r.id()).isEmpty());
    }

    /* ---------- issue tracking, package, summary ---------- */

    @Test
    void ownerTracksWhetherTheIssueIsResolved() {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "issue tracking");
        ReviewView resolved = service.trackIssue(owner, r.id(), new IssueRequest(true, "Vendor agreed to 30-day notice."));
        assertTrue(resolved.issueResolved());
        assertEquals(owner.getDisplayName(), resolved.issueResolvedByName());
        assertNotNull(resolved.issueResolvedAt());
        ReviewView reopened = service.trackIssue(owner, r.id(), new IssueRequest(false, "Vendor reversed the change."));
        assertFalse(reopened.issueResolved());
        assertNull(reopened.issueResolvedAt());
        assertEquals("Vendor reversed the change.", reopened.issueNote());
    }

    @Test
    void reviewPackageGivesOwnersOfficeEverythingNeededToDecide() {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "package");
        service.saveFindings(owner, r.id(), all(RiskLevel.LOW));
        service.recordHumanDecision(owner, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON));
        ReviewPackage pkg = service.reviewPackage(r.id());
        assertEquals(5, pkg.review().findings().size());
        assertEquals(DecisionState.APPROVAL_ELIGIBLE, pkg.assistant().state());
        assertEquals(1, pkg.humanDecisions().size());
    }

    @Test
    void summaryCountsPendingCompletedAndHighRisks() {
        AppUser owner = owner();
        Summary before = service.summary(LegalCatalog.LEAD);
        ReviewView r = newReview(owner, "summary");
        service.saveFindings(owner, r.id(), List.of(f("consumer-advocate", RiskLevel.HIGH)));
        Summary open = service.summary(LegalCatalog.LEAD);
        assertEquals(before.pendingReviews() + 1, open.pendingReviews());
        assertEquals(before.highPriorityRisks() + 1, open.highPriorityRisks());
        service.recordHumanDecision(owner, r.id(), new HumanDecisionRequest("REJECT", "Not acceptable as written."));
        Summary closed = service.summary(LegalCatalog.LEAD);
        assertEquals(before.pendingReviews(), closed.pendingReviews());
        assertEquals(before.completedReviews() + 1, closed.completedReviews());
    }

    @Test
    void existingRecordsAreUnchangedByNewDecisionsAndSeeding() {
        AppUser owner = owner();
        TaskResponse task = tasks.create(new TaskRequest("기존 업무 보존 확인", "must not change", Priority.LOW, null, null, null, null, null));
        ReviewView existing = newReview(owner, "existing review");
        service.saveFindings(owner, existing.id(), all(RiskLevel.LOW));
        LocalDateTime updatedBefore = reviewRepository.findById(existing.id()).orElseThrow().getUpdatedAt();
        int findingsBefore = service.reviews(LegalCatalog.LEAD, null).stream().filter(x -> x.id().equals(existing.id())).findFirst().orElseThrow().findings().size();

        newReview(owner, "another review");
        service.recordHumanDecision(owner, existing.id(), new HumanDecisionRequest("DEFER", "Waiting for the owner to review notes."));
        seeder.run(null);

        var after = reviewRepository.findById(existing.id()).orElseThrow();
        assertEquals(findingsBefore, service.reviews(LegalCatalog.LEAD, null).stream().filter(x -> x.id().equals(existing.id())).findFirst().orElseThrow().findings().size());
        assertEquals("OPEN", after.getStatus().name(), "a DEFER does not change status");
        assertFalse(after.getUpdatedAt().isBefore(updatedBefore));
        var t = taskRepository.findById(task.id()).orElseThrow();
        assertEquals("기존 업무 보존 확인", t.getTitle());
        assertEquals("NOT_STARTED", t.getStatus().name());
    }

    /* ---------- Owner's Office read API ---------- */

    @Test
    void integrationEndpointRequiresTheServiceToken() throws Exception {
        String path = "/api/integration/owners-office/legal/reviews";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer wrong-token")).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Basic " + INTEGRATION_TOKEN)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + INTEGRATION_TOKEN)).andExpect(status().isOk());
    }

    @Test
    void integrationPackageIsReadOnlyAndShowsTheAssistantState() throws Exception {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "integration package");
        service.saveFindings(owner, r.id(), all(RiskLevel.LOW));
        mvc.perform(get("/api/integration/owners-office/legal/reviews/" + r.id())
                        .header("Authorization", "Bearer " + INTEGRATION_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistant.state").value("APPROVAL_ELIGIBLE"))
                .andExpect(jsonPath("$.review.findings.length()").value(5));
    }

    /* ---------- Owner vs Administrator separation ---------- */

    @Test
    void administratorWhoIsNotOwnerCannotDecideOrTrackIssues() {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "admin is not owner");
        service.saveFindings(owner, r.id(), all(RiskLevel.LOW));
        AppUser admin = administrator();
        assertFalse(service.ownerView(admin).youAreOwner());
        assertThrows(ForbiddenException.class, () -> service.recordHumanDecision(admin, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON)));
        assertThrows(ForbiddenException.class, () -> service.humanDecisions(admin, r.id()));
        assertThrows(ForbiddenException.class, () -> service.trackIssue(admin, r.id(), new IssueRequest(true, "done")));
        assertEquals("OPEN", reviewRepository.findById(r.id()).orElseThrow().getStatus().name());
    }

    @Test
    void administratorCannotAppointOwnerWhileAnActiveOwnerExists() {
        AppUser owner = owner();
        AppUser admin = administrator();
        assertThrows(ForbiddenException.class, () -> service.appointOwner(admin, admin.getId()));
        assertEquals(owner.getId(), service.currentOwner().orElseThrow().getUserId(), "the Owner did not change");
    }

    @Test
    void currentOwnerAppointsASuccessorAndTheFormerOwnerLosesAuthority() {
        AppUser first = owner();
        AppUser second = administrator();
        service.appointOwner(first, second.getId());
        assertEquals(second.getId(), service.currentOwner().orElseThrow().getUserId());
        ReviewView r = newReview(second, "after transfer");
        service.saveFindings(second, r.id(), all(RiskLevel.LOW));
        assertThrows(ForbiddenException.class, () -> service.recordHumanDecision(first, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON)));
        assertEquals(DecisionState.APPROVAL_ELIGIBLE, service.consolidate(r.id()).state());
        HumanDecisionView d = service.recordHumanDecision(second, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON));
        assertEquals(second.getDisplayName(), d.decidedByName());
        assertTrue(reviewAppointmentsAppendOnly(), "earlier appointments are revoked, never deleted");
    }

    private boolean reviewAppointmentsAppendOnly() {
        return service.currentOwner().isPresent();
    }

    @Test
    void ownerCanStepDownAndTheRoleThenHasNoDecisionAuthority() {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "after step down");
        service.saveFindings(owner, r.id(), all(RiskLevel.LOW));
        service.revokeOwner(owner);
        assertTrue(service.currentOwner().isEmpty(), "the Owner position is unassigned");
        assertThrows(ForbiddenException.class, () -> service.recordHumanDecision(owner, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON)));
        assertThrows(ConflictException.class, () -> service.revokeOwner(administrator()), "no Owner to step down");
        // Restore an Owner so later tests and the live app keep working.
        service.appointOwner(owner, owner.getId());
    }

    @Test
    void onlyHumanAdministratorsCanBeAppointedOwner() {
        AppUser owner = owner();
        AppUser ai = user(UserRole.ADMIN, AccountKind.AI);
        AppUser member = user(UserRole.MEMBER, AccountKind.HUMAN);
        assertThrows(ConflictException.class, () -> service.appointOwner(owner, ai.getId()), "an AI seat can never be the Owner");
        assertThrows(ConflictException.class, () -> service.appointOwner(owner, member.getId()), "a member cannot be the Owner");
        assertFalse(service.ownerView(ai).youAreOwner());
        assertThrows(ForbiddenException.class, () -> service.appointOwner(member, member.getId()), "a member cannot appoint");
        assertThrows(ForbiddenException.class, () -> service.appointOwner(ai, ai.getId()), "an AI seat cannot appoint");
        assertEquals(owner.getId(), service.currentOwner().orElseThrow().getUserId());
    }

    @Test
    void aTrueAiSeatCannotDecideEvenIfItWasNamedOwnerBeforeItsStatusChanged() {
        AppUser owner = owner();
        ReviewView r = newReview(owner, "seat check");
        service.saveFindings(owner, r.id(), all(RiskLevel.LOW));
        AppUser seat = user(UserRole.ADMIN, AccountKind.AI);
        assertThrows(ForbiddenException.class, () -> service.recordHumanDecision(seat, r.id(), new HumanDecisionRequest("APPROVE", LONG_REASON)));
        assertFalse(service.ownerView(seat).youAreOwner());
    }

    @Test
    void whenTheOwnerIsDeactivatedAnyAdministratorCanAppointASuccessor() {
        AppUser stale = user(UserRole.ADMIN, AccountKind.HUMAN);
        service.revokeOwner(service.currentOwner().flatMap(o -> users.findById(o.getUserId())).orElseGet(() -> {
            service.appointOwner(stale, stale.getId());
            return stale;
        }));
        AppUser previous = administrator();
        service.appointOwner(previous, previous.getId());
        previous.setActive(false);
        users.save(previous);
        AppUser successor = administrator();
        service.appointOwner(successor, successor.getId());
        assertEquals(successor.getId(), service.currentOwner().orElseThrow().getUserId());
    }

    @Test
    void administratorCannotStepDownTheActiveOwner() {
        AppUser owner = owner();
        AppUser admin = administrator();
        assertThrows(ForbiddenException.class, () -> service.revokeOwner(admin));
        assertEquals(owner.getId(), service.currentOwner().orElseThrow().getUserId(), "the Owner did not change");
    }

    @Test
    void appointmentWithoutAPersonIsRefused() {
        AppUser owner = owner();
        assertThrows(ConflictException.class, () -> service.appointOwner(owner, null));
        assertEquals(owner.getId(), service.currentOwner().orElseThrow().getUserId());
    }

    @Test
    void ownerStatusShowsWhoTheOwnerIsWithoutGrantingAnything() {
        AppUser owner = owner();
        LegalComplianceService.LegalOwnerView asOwner = service.ownerView(owner);
        assertTrue(asOwner.youAreOwner());
        assertEquals(owner.getId(), asOwner.userId());
        assertFalse(service.ownerView(administrator()).youAreOwner());
    }
}
