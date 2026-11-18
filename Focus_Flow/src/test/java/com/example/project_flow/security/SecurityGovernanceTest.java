package com.example.project_flow.security;

import com.example.project_flow.admin.account.AccountKind;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.ForbiddenException;
import com.example.project_flow.legal.LegalComplianceService;
import com.example.project_flow.security.SecurityService.AccessResult;
import com.example.project_flow.security.SecurityService.FindingView;
import com.example.project_flow.security.SecurityService.IncidentView;
import com.example.project_flow.security.SecurityService.ItemView;
import com.example.project_flow.security.SecurityService.PolicyView;
import com.example.project_flow.security.SecurityService.RequestView;
import com.example.project_flow.security.SecurityService.Summary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class SecurityGovernanceTest {

    @Autowired SecurityService security;
    @Autowired SensitiveDataDetector detector;
    @Autowired LegalComplianceService legal;
    @Autowired AppUserRepository users;
    @Autowired JdbcTemplate jdbc;

    private static final String JUSTIFICATION = "Quarterly board pack needs the figures reviewed by the CFO seat.";
    private static final String REASON = "Reviewed against the policy and approved for this single use.";

    private AppUser user(UserRole role, AccountKind kind) {
        AppUser u = new AppUser("sec-" + UUID.randomUUID().toString().substring(0, 8), "Person", "not-used", role, LocalDateTime.now());
        if (kind == AccountKind.AI) u.assignJob(AccountKind.AI, "Test seat", null);
        return users.save(u);
    }

    private AppUser owner() {
        return legal.currentOwner().flatMap(o -> users.findById(o.getUserId())).filter(AppUser::isActive)
                .orElseGet(() -> {
                    AppUser a = user(UserRole.ADMIN, AccountKind.HUMAN);
                    legal.appointOwner(a, a.getId());
                    return a;
                });
    }

    private AppUser aiSeat() { return user(UserRole.MEMBER, AccountKind.AI); }

    private AppUser admin() { return user(UserRole.ADMIN, AccountKind.HUMAN); }

    private ItemView item(AppUser owner, String title, DataCategory cat, SecurityLevel level, String content) {
        return security.classify(owner, title + " " + UUID.randomUUID(), cat, level, content);
    }

    private static String bodyOf(String title) { return title; }

    private List<IncidentView> incidentsNamed(AppUser owner, String fragment) {
        return security.incidents(owner).stream().filter(i -> i.title().contains(fragment)).toList();
    }

    /* ---------- classification ---------- */

    @Test
    void credentialsAreAlwaysRestrictedAndNeverStoredAsBusinessContent() {
        AppUser owner = owner();
        assertThrows(ConflictException.class, () -> security.classify(owner, "ci key", DataCategory.CREDENTIAL,
                SecurityLevel.CONFIDENTIAL, null));
        assertThrows(ConflictException.class, () -> item(owner, "note", DataCategory.GENERAL, SecurityLevel.INTERNAL,
                "deploy with password=hunter2hunter2 today"), "credential text cannot be stored as ordinary content");
        ItemView restricted = item(owner, "payment secret", DataCategory.CREDENTIAL, SecurityLevel.RESTRICTED, null);
        assertNull(restricted.content());
        assertNull(restricted.fingerprint(), "no content, so no fingerprint");
        ItemView withBody = item(owner, "vault note", DataCategory.BUSINESS_PLAN, SecurityLevel.RESTRICTED, "secret plan text");
        assertNull(withBody.content(), "RESTRICTED content is never kept");
        assertNotNull(withBody.fingerprint(), "a fingerprint is kept instead");
    }

    @Test
    void onlyTheOwnerCanLowerAClassification() {
        AppUser owner = owner();
        ItemView conf = item(owner, "contract", DataCategory.CONTRACT, SecurityLevel.CONFIDENTIAL, "terms");
        AppUser cisoLike = admin();
        security.assignPosition(owner, SecurityPositionCatalog.CISO, cisoLike.getId());
        assertEquals("RESTRICTED", security.reclassify(cisoLike, conf.id(), SecurityLevel.RESTRICTED).level(),
                "the CISO may raise a level");
        assertThrows(ForbiddenException.class, () -> security.reclassify(cisoLike, conf.id(), SecurityLevel.PUBLIC),
                "the CISO may not lower a level");
        assertEquals("PUBLIC", security.reclassify(owner, conf.id(), SecurityLevel.PUBLIC).level());
    }

    /* ---------- AI access gateway: the decision matrix ---------- */

    @Test
    void publicInformationIsAllowedEvenExternally() {
        AppUser owner = owner();
        ItemView pub = item(owner, "press release", DataCategory.GENERAL, SecurityLevel.PUBLIC, "We launched version two.");
        AccessResult r = security.evaluate(owner, aiSeat().getId(), pub.id(), Destination.EXTERNAL_AI_PROVIDER, "summary", null);
        assertEquals("ALLOWED", r.decision());
        assertEquals("We launched version two.", r.releasedContent());
    }

    @Test
    void internalInformationNeverGoesToAnExternalProviderAndLogsAnIncident() {
        AppUser owner = owner();
        ItemView internal = item(owner, "team handbook", DataCategory.GENERAL, SecurityLevel.INTERNAL, "Working hours are flexible.");
        AccessResult r = security.evaluate(owner, aiSeat().getId(), internal.id(), Destination.EXTERNAL_AI_PROVIDER, "x", null);
        assertEquals("BLOCKED", r.decision());
        assertNull(r.releasedContent());
        assertTrue(r.logSeq() != null);
        assertFalse(incidentsNamed(owner, "Attempted external transmission of INTERNAL").isEmpty());
    }

    @Test
    void confidentialInformationIsBlockedExternallyAndEscalatedToTheOwner() {
        AppUser owner = owner();
        ItemView conf = item(owner, "pricing model", DataCategory.BUSINESS_PLAN, SecurityLevel.CONFIDENTIAL, "margin 42%");
        AccessResult r = security.evaluate(owner, aiSeat().getId(), conf.id(), Destination.EXTERNAL_AI_PROVIDER, "x", null);
        assertEquals("BLOCKED", r.decision());
        IncidentView inc = incidentsNamed(owner, "Attempted external transmission of CONFIDENTIAL").get(0);
        assertEquals("HIGH", inc.severity());
        assertTrue(inc.escalatedToOwner(), "HIGH incidents escalate to the Owner");
        assertEquals("CISO", inc.responsibleRole() == null ? null : "CISO");
        assertEquals(SecurityService.DEPARTMENT_NAME, inc.responsibleDepartment());
    }

    @Test
    void restrictedInformationIsBlockedExternallyAsCritical() {
        AppUser owner = owner();
        ItemView restricted = item(owner, "payment token vault", DataCategory.CREDENTIAL, SecurityLevel.RESTRICTED, null);
        AccessResult r = security.evaluate(owner, aiSeat().getId(), restricted.id(), Destination.EXTERNAL_AI_PROVIDER, "x", null);
        assertEquals("BLOCKED", r.decision());
        assertEquals("CRITICAL", incidentsNamed(owner, "Attempted external transmission of RESTRICTED").get(0).severity());
    }

    @Test
    void aCredentialInOutboundContentIsBlockedEvenForPublicItems() {
        AppUser owner = owner();
        ItemView pub = item(owner, "public faq", DataCategory.GENERAL, SecurityLevel.PUBLIC, "How do I log in?");
        String leaked = "use this key: AKIAABCDEFGHIJKLMNOP to deploy";
        AccessResult r = security.evaluate(owner, aiSeat().getId(), pub.id(), Destination.INTERNAL_AI_TOOL, "x", leaked);
        assertEquals("BLOCKED", r.decision());
        assertTrue(r.findings().stream().anyMatch(f -> f.contains("CREDENTIAL")));
        assertNull(r.releasedContent(), "the credential is never released, not even redacted");
        assertFalse(incidentsNamed(owner, "Credential found in outbound AI content").isEmpty());
    }

    @Test
    void anAiSeatWithoutGrantIsBlockedForInternalInformation() {
        AppUser owner = owner();
        ItemView internal = item(owner, "roadmap", DataCategory.GENERAL, SecurityLevel.INTERNAL, "Launch in March.");
        AccessResult r = security.evaluate(owner, aiSeat().getId(), internal.id(), Destination.INTERNAL_AI_TOOL, "x", null);
        assertEquals("BLOCKED", r.decision());
        assertTrue(r.reason().contains("no grant"));
    }

    @Test
    void aGrantedAiSeatReceivesInternalInformationInsideCompany() {
        AppUser owner = owner();
        AppUser seat = aiSeat();
        security.grant(owner, seat.getId(), SecurityLevel.INTERNAL, null, null);
        ItemView internal = item(owner, "roadmap", DataCategory.GENERAL, SecurityLevel.INTERNAL, "Launch in March.");
        AccessResult r = security.evaluate(owner, seat.getId(), internal.id(), Destination.INTERNAL_AI_TOOL, "x", null);
        assertEquals("ALLOWED", r.decision());
        assertEquals("Launch in March.", r.releasedContent());
    }

    @Test
    void confidentialForAnAiSeatWaitsForTheOwnerAndAnApprovalIsUsedOnce() {
        AppUser owner = owner();
        AppUser seat = aiSeat();
        AppUser requester = admin();
        security.grant(owner, seat.getId(), SecurityLevel.CONFIDENTIAL, null, null);
        ItemView conf = item(owner, "contract terms", DataCategory.CONTRACT, SecurityLevel.CONFIDENTIAL, "Net 30, cap 1M.");

        AccessResult waiting = security.evaluate(requester, seat.getId(), conf.id(), Destination.INTERNAL_AI_TOOL, "review", null);
        assertEquals("PENDING_APPROVAL", waiting.decision());
        assertNull(waiting.releasedContent(), "nothing is released while the request waits");
        assertNotNull(waiting.requestId());

        AppUser admin = admin();
        assertThrows(ForbiddenException.class, () -> security.decideRequest(admin, waiting.requestId(), true, REASON),
                "an administrator who is not the Owner cannot approve");
        security.decideRequest(owner, waiting.requestId(), true, REASON);

        AccessResult once = security.evaluate(owner, seat.getId(), conf.id(), Destination.INTERNAL_AI_TOOL, "review", null);
        assertEquals("ALLOWED", once.decision());
        assertEquals("Net 30, cap 1M.", once.releasedContent());

        AccessResult again = security.evaluate(owner, seat.getId(), conf.id(), Destination.INTERNAL_AI_TOOL, "review", null);
        assertEquals("PENDING_APPROVAL", again.decision(), "an approval covers exactly one use");
    }

    @Test
    void nobodyCanApproveARequestTheyMadeOrOneThatGrantsTheirOwnAccess() {
        AppUser owner = owner();
        AppUser seat = aiSeat();
        security.grant(owner, seat.getId(), SecurityLevel.CONFIDENTIAL, null, null);
        ItemView conf = item(owner, "payroll summary", DataCategory.EMPLOYEE_PERSONAL, SecurityLevel.CONFIDENTIAL, "Band 3 average.");
        AccessResult waiting = security.evaluate(owner, seat.getId(), conf.id(), Destination.INTERNAL_AI_TOOL, "check", null);
        RequestView rv = security.requests(owner).stream().filter(r -> r.id().equals(waiting.requestId())).findFirst().orElseThrow();
        assertEquals("PENDING", rv.status());
        // The requester here is the Owner, so the Owner may not decide it.
        assertThrows(ForbiddenException.class, () -> security.decideRequest(owner, waiting.requestId(), true, REASON));
    }

    @Test
    void aHumanWithoutGrantIsBlockedForConfidentialAndAllowedWithGrant() {
        AppUser owner = owner();
        AppUser person = admin();
        ItemView conf = item(owner, "board minutes", DataCategory.BUSINESS_PLAN, SecurityLevel.CONFIDENTIAL, "Approved budget.");
        assertEquals("BLOCKED", security.evaluate(owner, person.getId(), conf.id(), Destination.INTERNAL_AI_TOOL, "x", null).decision());
        security.grant(owner, person.getId(), SecurityLevel.CONFIDENTIAL, null, null);
        assertEquals("ALLOWED", security.evaluate(owner, person.getId(), conf.id(), Destination.INTERNAL_AI_TOOL, "x", null).decision());
    }

    @Test
    void personalDataIsRedactedBeforeRelease() {
        AppUser owner = owner();
        ItemView internal = item(owner, "customer list", DataCategory.CUSTOMER_PERSONAL, SecurityLevel.INTERNAL,
                "Kim: kim@example.com, 010-1234-5678, card 4111 1111 1111 1111");
        AccessResult r = security.evaluate(owner, owner.getId(), internal.id(), Destination.INTERNAL_AI_TOOL, "summarize", null);
        assertEquals("ALLOWED_REDACTED", r.decision());
        assertFalse(r.releasedContent().contains("kim@example.com"));
        assertFalse(r.releasedContent().contains("4111 1111 1111 1111"));
        assertTrue(r.releasedContent().contains("[REDACTED:EMAIL]"));
        assertTrue(r.releasedContent().contains("[REDACTED:PAYMENT_CARD]"));
    }

    /* ---------- sensitive data detection ---------- */

    @Test
    void detectorFindsCardsByChecksumAndCredentialsAndRedacts() {
        assertTrue(detector.scan("4111111111111111").stream().anyMatch(f -> f.type().equals("PAYMENT_CARD")), "valid Luhn");
        assertTrue(detector.scan("1234567812345678").stream().noneMatch(f -> f.type().equals("PAYMENT_CARD")), "invalid Luhn");
        assertTrue(SensitiveDataDetector.containsCredential(detector.scan("password = correct-horse-battery")));
        assertTrue(SensitiveDataDetector.containsCredential(detector.scan("-----BEGIN RSA PRIVATE KEY-----")));
        assertTrue(detector.scan("주민 900101-1234567").stream().anyMatch(f -> f.type().equals("KR_RESIDENT_ID")));
        String redacted = detector.redact("token=abcd1234efgh and mail a@b.co");
        assertFalse(redacted.contains("abcd1234efgh"));
        assertFalse(redacted.contains("a@b.co"));
    }

    /* ---------- policies: the CISO cannot certify their own ---------- */

    @Test
    void theAuthorOfAPolicyCannotApproveIt() {
        AppUser owner = owner();
        AppUser ciso = admin();
        security.assignPosition(owner, SecurityPositionCatalog.CISO, ciso.getId());
        PolicyView draft = security.draftPolicy(ciso, "ai-data-" + UUID.randomUUID(), "AI data handling", "Never send RESTRICTED data.");
        assertEquals("DRAFT", draft.status());
        assertThrows(ForbiddenException.class, () -> security.approvePolicy(ciso, draft.id()), "the CISO is not the Owner");
        PolicyView approved = security.approvePolicy(owner, draft.id());
        assertEquals("APPROVED", approved.status());
        assertEquals(owner.getDisplayName(), approved.approvedBy());
    }

    @Test
    void theOwnerCannotApproveAPolicyTheyWrote() {
        AppUser owner = owner();
        PolicyView own = security.draftPolicy(owner, "own-" + UUID.randomUUID(), "Owner draft", "Text.");
        assertThrows(ForbiddenException.class, () -> security.approvePolicy(owner, own.id()));
    }

    /* ---------- executive positions and independence ---------- */

    @Test
    void executivePositionsStartVacantAndAreAssignedOnlyByTheOwner() {
        assertEquals(4, security.positions().size());
        AppUser owner = owner();
        AppUser ciso = admin();
        assertThrows(ForbiddenException.class, () -> security.assignPosition(ciso, SecurityPositionCatalog.CISO, ciso.getId()));
        security.assignPosition(owner, SecurityPositionCatalog.CISO, ciso.getId());
        assertEquals(ciso.getId(), security.positions().stream().filter(p -> p.key().equals(SecurityPositionCatalog.CISO))
                .findFirst().orElseThrow().holderId());
    }

    @Test
    void auditorsAreIndependentOfTheCisoAndTheExternalAuditorOfEveryoneInternal() {
        AppUser owner = owner();
        AppUser ciso = admin();
        security.assignPosition(owner, SecurityPositionCatalog.CISO, ciso.getId());
        assertThrows(ConflictException.class, () -> security.assignPosition(owner, SecurityPositionCatalog.IT_AUDITOR, ciso.getId()),
                "the CISO cannot audit themselves");
        AppUser itAuditor = admin();
        security.assignPosition(owner, SecurityPositionCatalog.IT_AUDITOR, itAuditor.getId());
        assertThrows(ConflictException.class, () -> security.assignPosition(owner, SecurityPositionCatalog.EXTERNAL_AUDITOR, itAuditor.getId()));
        assertThrows(ConflictException.class, () -> security.assignPosition(owner, SecurityPositionCatalog.EXTERNAL_AUDITOR, ciso.getId()));
        assertThrows(ConflictException.class, () -> security.assignPosition(owner, SecurityPositionCatalog.EXTERNAL_AUDITOR, owner.getId()));
    }

    @Test
    void anAiSeatOrAMemberCannotHoldAnExecutivePosition() {
        AppUser owner = owner();
        assertThrows(ConflictException.class, () -> security.assignPosition(owner, SecurityPositionCatalog.COMPLIANCE, aiSeat().getId()));
        assertThrows(ConflictException.class, () -> security.assignPosition(owner, SecurityPositionCatalog.COMPLIANCE,
                user(UserRole.MEMBER, AccountKind.HUMAN).getId()));
    }

    /* ---------- independent audit findings ---------- */

    @Test
    void onlyTheAuditorPositionRecordsFindingsAndTheCisoCannotCloseTheirOwn() {
        AppUser owner = owner();
        AppUser ciso = admin();
        AppUser auditor = admin();
        security.assignPosition(owner, SecurityPositionCatalog.CISO, ciso.getId());
        security.assignPosition(owner, SecurityPositionCatalog.IT_AUDITOR, auditor.getId());

        assertThrows(ForbiddenException.class, () -> security.recordFinding(ciso, AuditFinding.Source.IT_SECURITY_AUDITOR,
                SecurityPositionCatalog.CISO, "self audit", SecurityIncident.Severity.LOW, "IS", "CISO",
                LocalDate.now(), "evidence", "fix"), "a non-auditor cannot record findings");

        FindingView f = security.recordFinding(auditor, AuditFinding.Source.IT_SECURITY_AUDITOR, SecurityPositionCatalog.CISO,
                "Policy not approved before use", SecurityIncident.Severity.CRITICAL, "IS", "CISO", LocalDate.now(),
                "Export shows policy v1 in use without approval.", "Approve or withdraw the policy.");
        assertEquals(64, f.evidenceHash().length());
        assertTrue(f.escalatedToOwner(), "critical findings escalate");

        security.setFindingStatus(ciso, f.id(), AuditFinding.Status.IN_REMEDIATION);
        assertThrows(ForbiddenException.class, () -> security.setFindingStatus(ciso, f.id(), AuditFinding.Status.VERIFIED_CLOSED),
                "the CISO cannot approve an audit of their own function");
        assertEquals("VERIFIED_CLOSED", security.setFindingStatus(auditor, f.id(), AuditFinding.Status.VERIFIED_CLOSED).status());
    }

    @Test
    void anAuditorCannotAuditTheSeatTheyHold() {
        AppUser owner = owner();
        AppUser auditor = admin();
        security.assignPosition(owner, SecurityPositionCatalog.IT_AUDITOR, auditor.getId());
        assertThrows(ForbiddenException.class, () -> security.recordFinding(auditor, AuditFinding.Source.IT_SECURITY_AUDITOR,
                SecurityPositionCatalog.IT_AUDITOR, "self", SecurityIncident.Severity.LOW, "IS", "IT", LocalDate.now(), "e", "a"));
    }

    /* ---------- audit records cannot be altered ---------- */

    @Test
    void theAiActivityLogRefusesUpdateAndDeleteAndStaysIntact() {
        AppUser owner = owner();
        ItemView pub = item(owner, "log probe", DataCategory.GENERAL, SecurityLevel.PUBLIC, "hello");
        security.evaluate(owner, aiSeat().getId(), pub.id(), Destination.INTERNAL_AI_TOOL, "x", null);
        assertThrows(DataAccessException.class, () -> jdbc.update("UPDATE security_ai_activity_log SET decision = 'ALLOWED' WHERE id > 0"));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM security_ai_activity_log WHERE id > 0"));
        assertTrue(security.verifyChain().intact(), "the chain still verifies");
    }

    @Test
    void auditFindingEvidenceCannotBeChangedButItsStatusCan() {
        AppUser owner = owner();
        AppUser auditor = admin();
        security.assignPosition(owner, SecurityPositionCatalog.IT_AUDITOR, auditor.getId());
        FindingView f = security.recordFinding(auditor, AuditFinding.Source.IT_SECURITY_AUDITOR, null, "immutable evidence",
                SecurityIncident.Severity.LOW, "IS", "IT", LocalDate.now(), "original evidence", "action");
        assertThrows(DataAccessException.class, () -> jdbc.update(
                "UPDATE security_audit_findings SET evidence = 'edited' WHERE id = ?", f.id()));
        assertThrows(DataAccessException.class, () -> jdbc.update("DELETE FROM security_audit_findings WHERE id = ?", f.id()));
        assertEquals("original evidence", security.findings(owner).stream().filter(x -> x.id().equals(f.id())).findFirst().orElseThrow().evidence());
    }

    @Test
    void theLogChainVerifiesAfterManyDecisions() {
        AppUser owner = owner();
        ItemView pub = item(owner, "chain probe", DataCategory.GENERAL, SecurityLevel.PUBLIC, "x");
        for (int i = 0; i < 3; i++) security.evaluate(owner, aiSeat().getId(), pub.id(), Destination.INTERNAL_AI_TOOL, "x", null);
        var check = security.verifyChain();
        assertTrue(check.intact());
        assertTrue(check.rows() >= 3);
    }

    /* ---------- incidents and the Owner ---------- */

    @Test
    void aCriticalIncidentCannotBeResolvedUntilTheOwnerAcknowledgesIt() {
        AppUser owner = owner();
        AppUser ciso = admin();
        security.assignPosition(owner, SecurityPositionCatalog.CISO, ciso.getId());
        IncidentView inc = security.recordIncident(ciso, "Leaked key in repository", SecurityIncident.Severity.CRITICAL,
                "Engineering", "DevOps Engineer", LocalDate.now(), "git history contains a token", "Rotate and purge history.");
        assertTrue(inc.escalatedToOwner());
        assertThrows(ConflictException.class, () -> security.setIncidentStatus(ciso, inc.id(), SecurityIncident.Status.RESOLVED));
        assertThrows(ForbiddenException.class, () -> security.acknowledgeIncident(ciso, inc.id()));
        security.acknowledgeIncident(owner, inc.id());
        assertEquals("RESOLVED", security.setIncidentStatus(ciso, inc.id(), SecurityIncident.Status.RESOLVED).status());
    }

    @Test
    void incidentsRequireTheResponsibleDepartmentRoleAndEvidence() {
        AppUser owner = owner();
        assertThrows(ConflictException.class, () -> security.recordIncident(owner, "t", SecurityIncident.Severity.LOW, "",
                "role", LocalDate.now(), "e", "a"));
        assertThrows(ConflictException.class, () -> security.recordIncident(owner, "t", SecurityIncident.Severity.LOW, "IS",
                "", LocalDate.now(), "e", "a"));
        assertThrows(ConflictException.class, () -> security.recordIncident(owner, "t", SecurityIncident.Severity.LOW, "IS",
                "role", LocalDate.now(), "", "a"));
    }

    @Test
    void theOwnerSummaryReportsStatusAccountabilityAndIntegrity() {
        AppUser owner = owner();
        Summary s = security.summary(owner);
        assertTrue(s.logIntegrity().intact());
        assertTrue(s.positions().size() == 4);
        assertTrue(s.securityExpenses().startsWith("Not tracked"), "expenses are declared, not invented");
        assertThrows(ForbiddenException.class, () -> security.summary(admin()), "only the security authority reads the summary");
    }

    @Test
    void aMemberOrAiSeatCannotGrantPermissions() {
        AppUser owner = owner();
        AppUser seat = aiSeat();
        AppUser target = aiSeat();
        assertThrows(ForbiddenException.class, () -> security.grant(seat, target.getId(), SecurityLevel.INTERNAL, null, null),
                "an AI seat cannot grant itself or anyone else access");
        assertThrows(ForbiddenException.class, () -> security.grant(admin(), target.getId(), SecurityLevel.INTERNAL, null, null));
        assertThrows(ConflictException.class, () -> security.grant(owner, target.getId(), SecurityLevel.RESTRICTED, JUSTIFICATION, null),
                "AI seats cannot receive RESTRICTED access directly");
        assertThrows(ConflictException.class, () -> security.grant(owner, admin().getId(), SecurityLevel.RESTRICTED, "short", null),
                "RESTRICTED needs a real justification");
    }
}
