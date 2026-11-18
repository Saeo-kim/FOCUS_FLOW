package com.example.project_flow.security;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.ForbiddenException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.legal.LegalComplianceService;
import com.example.project_flow.legal.LegalCatalog;
import com.example.project_flow.legal.OrgPosition;
import com.example.project_flow.legal.OrgPositionRepository;
import com.example.project_flow.service.ActivityLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AI governance and information security. Every rule that protects company information lives here,
 * so it holds whatever the screen shows. Owner, CISO, auditors and AI seats are checked by identity
 * against the position assignments and the Owner appointment, never by the role label alone.
 */
@Service
public class SecurityService {

    public static final String DEPARTMENT_NAME = "Information Security & Audit";
    private static final int MIN_JUSTIFICATION = 20;
    private static final int MIN_REASON = 10;
    private static final int MAX_TEXT = 4000;

    /* ---------- views ---------- */

    public record ItemView(Long id, String title, String category, String level, String content, String fingerprint,
                           String classifiedBy, LocalDateTime updatedAt) {
    }

    public record AccessResult(String decision, String reason, List<String> findings, String releasedContent,
                               Long requestId, Long logSeq) {
    }

    public record GrantView(Long id, Long actorUserId, String actorName, String maxLevel, String justification,
                            String grantedBy, LocalDateTime grantedAt, LocalDateTime expiresAt, LocalDateTime revokedAt,
                            boolean active) {
    }

    public record RequestView(Long id, String actorName, String itemTitle, String itemLevel, String destination,
                              String purpose, String requestedBy, String status, String decidedBy, String decisionReason,
                              LocalDateTime requestedAt, LocalDateTime decidedAt) {
    }

    public record LogView(Long seq, LocalDateTime createdAt, String actorName, String actorKind, String requestedBy,
                          String itemTitle, String itemLevel, String destination, String decision, String reason,
                          String findings, String prevHash, String hash) {
    }

    public record ChainCheck(boolean intact, long rows, Long brokenAtSeq) {
    }

    public record IncidentView(Long id, String title, String severity, String status, String responsibleDepartment,
                               String responsibleRole, LocalDate discoveredOn, String evidence, String recommendedAction,
                               boolean escalatedToOwner, LocalDateTime ownerAcknowledgedAt, String ownerAcknowledgedBy,
                               String recordedBy, LocalDateTime createdAt) {
    }

    public record FindingView(Long id, String title, String source, String subject, String severity, String status,
                              String responsibleDepartment, String responsibleRole, LocalDate discoveredOn, String evidence,
                              String evidenceHash, String recommendedAction, boolean escalatedToOwner,
                              LocalDateTime ownerAcknowledgedAt, String ownerAcknowledgedBy, String auditor,
                              LocalDateTime createdAt) {
    }

    public record PolicyView(Long id, String policyKey, int version, String title, String body, String status,
                             String authoredBy, String approvedBy, LocalDateTime approvedAt, LocalDateTime createdAt) {
    }

    public record PositionView(String key, String title, String mission, Long holderId, String holderName, String status) {
    }

    public record Summary(int openViolations, int blockedAttempts, int redactedReleases, long pendingApprovals,
                          long openCriticalIncidents, long openHighIncidents, long openFindings, long criticalFindings,
                          long pendingComplianceRisks, List<FindingView> unresolvedVulnerabilities,
                          Map<String, Long> accountabilityByDepartment, List<PositionView> positions,
                          ChainCheck logIntegrity, String securityExpenses) {
    }

    private final ClassifiedItemRepository items;
    private final AiAccessGrantRepository grants;
    private final AccessRequestRepository requests;
    private final AiActivityLogRepository logs;
    private final SecurityIncidentRepository incidents;
    private final AuditFindingRepository findings;
    private final SecurityPolicyRepository policies;
    private final SecurityPositionCatalog positionCatalog;
    private final OrgPositionRepository orgPositions;
    private final AppUserRepository users;
    private final SensitiveDataDetector detector;
    private final LegalComplianceService legal;
    private final ActivityLogService activity;
    private final Clock clock;

    public SecurityService(ClassifiedItemRepository items, AiAccessGrantRepository grants, AccessRequestRepository requests,
                           AiActivityLogRepository logs, SecurityIncidentRepository incidents, AuditFindingRepository findings,
                           SecurityPolicyRepository policies, SecurityPositionCatalog positionCatalog,
                           OrgPositionRepository orgPositions, AppUserRepository users, SensitiveDataDetector detector,
                           LegalComplianceService legal, ActivityLogService activity, Clock clock) {
        this.items = items;
        this.grants = grants;
        this.requests = requests;
        this.logs = logs;
        this.incidents = incidents;
        this.findings = findings;
        this.policies = policies;
        this.positionCatalog = positionCatalog;
        this.orgPositions = orgPositions;
        this.users = users;
        this.detector = detector;
        this.legal = legal;
        this.activity = activity;
        this.clock = clock;
    }

    /* ---------- identity and authority ---------- */

    private Optional<Long> holderOf(String positionKey) {
        return orgPositions.findByPositionKey(positionKey).map(OrgPosition::getAssigneeUserId);
    }

    private boolean holds(AppUser actor, String positionKey) {
        return !actor.isAi() && holderOf(positionKey).filter(actor.getId()::equals).isPresent();
    }

    public boolean isOwner(AppUser actor) {
        return legal.isOwner(actor);
    }

    /** The Owner, or the person currently holding the CISO position. Both may classify and set policy. */
    private boolean isSecurityAuthority(AppUser actor) {
        return isOwner(actor) || holds(actor, SecurityPositionCatalog.CISO);
    }

    private void requireOwner(AppUser actor) {
        if (!isOwner(actor)) throw new ForbiddenException("Only the Owner can do this.");
    }

    private void requireSecurityAuthority(AppUser actor) {
        if (!isSecurityAuthority(actor)) {
            throw new ForbiddenException("Only the Owner or the CISO can do this.");
        }
    }

    private void requireAuditor(AppUser actor, AuditFinding.Source source) {
        String key = source == AuditFinding.Source.IT_SECURITY_AUDITOR
                ? SecurityPositionCatalog.IT_AUDITOR : SecurityPositionCatalog.EXTERNAL_AUDITOR;
        if (!holds(actor, key)) {
            throw new ForbiddenException("Only the holder of this auditor position can record findings from it.");
        }
    }

    /* ---------- classification ---------- */

    @Transactional
    public ItemView classify(AppUser actor, String title, DataCategory category, SecurityLevel level, String content) {
        requireSecurityAuthority(actor);
        String name = requireText(title, "Give the information a title.", 200);
        if (category == null || level == null) throw new ConflictException("Choose a category and a level.");
        if (category == DataCategory.CREDENTIAL && level != SecurityLevel.RESTRICTED) {
            throw new ConflictException("Credentials are always RESTRICTED.");
        }
        String body = content == null ? "" : content;
        if (!body.isBlank() && SensitiveDataDetector.containsCredential(detector.scan(body))) {
            throw new ConflictException("This text contains a credential. Store the credential only as RESTRICTED, without its content.");
        }
        String stored = level == SecurityLevel.RESTRICTED ? null : (body.isBlank() ? null : body);
        if (stored != null && stored.length() > MAX_TEXT) throw new ConflictException("Keep content under " + MAX_TEXT + " characters.");
        ClassifiedItem item = items.save(new ClassifiedItem(name, category, level, stored, fingerprint(body),
                actor.getDisplayName(), now()));
        activity.record("SECURITY_CLASSIFY", actor.getDisplayName() + " classified \"" + name + "\" as " + level);
        return itemView(item);
    }

    /** Raising a level is open to the security authority. Lowering it is the Owner's decision only. */
    @Transactional
    public ItemView reclassify(AppUser actor, Long itemId, SecurityLevel next) {
        requireSecurityAuthority(actor);
        ClassifiedItem item = requireItem(itemId);
        if (next == null) throw new ConflictException("Choose a level.");
        if (next.compareTo(item.getLevel()) < 0) requireOwner(actor);
        if (item.getCategory() == DataCategory.CREDENTIAL && next != SecurityLevel.RESTRICTED) {
            throw new ConflictException("Credentials stay RESTRICTED.");
        }
        item.reclassify(next, actor.getDisplayName(), now());
        if (next == SecurityLevel.RESTRICTED) {
            item.stripContentForRestricted();
        }
        activity.record("SECURITY_RECLASSIFY", actor.getDisplayName() + " set \"" + item.getTitle() + "\" to " + next);
        return itemView(item);
    }

    @Transactional(readOnly = true)
    public List<ItemView> items(AppUser viewer) {
        requireSecurityAuthority(viewer);
        return items.findAllByOrderByIdAsc().stream().map(this::itemView).toList();
    }

    /* ---------- the access gateway ---------- */

    /**
     * Decides whether actor may send an item to a destination, in this order:
     * 1. credentials are never transmitted (BLOCKED, CRITICAL incident);
     * 2. INTERNAL or higher never goes to an external provider (BLOCKED, incident);
     * 3. the actor must hold a grant for the level; AI seats need an Owner-approved request for
     *    CONFIDENTIAL or higher, consumed by this use (PENDING_APPROVAL otherwise);
     * 4. personal data is redacted before release (ALLOWED_REDACTED).
     * Every decision, including a block, is written to the hash-chained AI activity log.
     */
    @Transactional
    public AccessResult evaluate(AppUser requester, Long actorId, Long itemId, Destination destination, String purpose,
                                 String contentOverride) {
        AppUser actor = users.findById(actorId).orElseThrow(() -> new NotFoundException("That account no longer exists."));
        ClassifiedItem item = requireItem(itemId);
        if (destination == null) throw new ConflictException("Choose a destination.");
        String content = contentOverride != null ? contentOverride
                : (item.getContent() == null ? "" : item.getContent());
        List<SensitiveDataDetector.Finding> found = detector.scan(content);
        List<String> foundSummary = found.stream().map(f -> f.kind() + ":" + f.type() + "x" + f.count()).toList();
        SecurityLevel level = item.getLevel();

        if (!actor.isActive()) {
            return finish(requester, actor, item, destination, Decision.BLOCKED, "The account is deactivated.", foundSummary, null, null);
        }
        if (SensitiveDataDetector.containsCredential(found)) {
            Long seq = finish(requester, actor, item, destination, Decision.BLOCKED,
                    "A credential was found in the content. Credentials are never transmitted.", foundSummary, null, null).logSeq();
            incident("Credential found in outbound AI content", SecurityIncident.Severity.CRITICAL,
                    "Credential in AI-bound content, log #" + seq + ", actor " + actor.getDisplayName(), requester.getDisplayName());
            return new AccessResult(Decision.BLOCKED.name(), "A credential was found in the content. Credentials are never transmitted.",
                    foundSummary, null, null, seq);
        }
        if (destination == Destination.EXTERNAL_AI_PROVIDER && level.compareTo(SecurityLevel.PUBLIC) > 0) {
            String reason = level + " information may not be sent to an external AI provider.";
            SecurityIncident.Severity sev = level == SecurityLevel.RESTRICTED ? SecurityIncident.Severity.CRITICAL
                    : level == SecurityLevel.CONFIDENTIAL ? SecurityIncident.Severity.HIGH : SecurityIncident.Severity.MEDIUM;
            AccessResult blocked = finish(requester, actor, item, destination, Decision.BLOCKED, reason, foundSummary, null, null);
            incident("Attempted external transmission of " + level + " information", sev,
                    "\"" + item.getTitle() + "\" to external AI, log #" + blocked.logSeq(), requester.getDisplayName());
            return blocked;
        }
        // Authorization
        if (level != SecurityLevel.PUBLIC) {
            Optional<AiAccessGrant> grant = activeGrant(actor);
            boolean levelGranted = grant.map(g -> g.getMaxLevel().compareTo(level) >= 0).orElse(false);
            if (actor.isAi()) {
                if (!levelGranted) {
                    AccessResult blocked = finish(requester, actor, item, destination, Decision.BLOCKED,
                            "This AI seat has no grant for " + level + " information.", foundSummary, null, null);
                    incident("AI seat without grant requested " + level + " information", SecurityIncident.Severity.MEDIUM,
                            "\"" + item.getTitle() + "\", seat " + actor.getDisplayName() + ", log #" + blocked.logSeq(), requester.getDisplayName());
                    return blocked;
                }
                if (level.compareTo(SecurityLevel.INTERNAL) > 0) {
                    Optional<AccessRequest> approved = requests.findFirstByActorUserIdAndItemIdAndStatusOrderByIdAsc(
                            actor.getId(), item.getId(), AccessRequest.Status.APPROVED);
                    if (approved.isEmpty()) {
                        AccessRequest pending = requests.findFirstByActorUserIdAndItemIdAndStatusOrderByIdAsc(
                                actor.getId(), item.getId(), AccessRequest.Status.PENDING).orElseGet(() -> requests.save(new AccessRequest(
                                actor.getId(), actor.getDisplayName(), item.getId(), item.getTitle(), level, destination,
                                purpose == null ? "" : purpose, requester.getId(), requester.getDisplayName(), now())));
                        AccessResult waiting = finish(requester, actor, item, destination, Decision.PENDING_APPROVAL,
                                "Waiting for the Owner's approval (request #" + pending.getId() + ").", foundSummary, pending.getId(), null);
                        return waiting;
                    }
                    approved.get().consume(now());
                }
            } else if (level.compareTo(SecurityLevel.INTERNAL) > 0 && !levelGranted) {
                AccessResult blocked = finish(requester, actor, item, destination, Decision.BLOCKED,
                        "No grant covers " + level + " information for this person.", foundSummary, null, null);
                incident("Unauthorized access attempt to " + level + " information", SecurityIncident.Severity.MEDIUM,
                        "\"" + item.getTitle() + "\", person " + actor.getDisplayName() + ", log #" + blocked.logSeq(), requester.getDisplayName());
                return blocked;
            }
        }
        // Release, with personal data redacted
        if (SensitiveDataDetector.containsPersonalData(found)) {
            String redacted = detector.redact(content);
            return finish(requester, actor, item, destination, Decision.ALLOWED_REDACTED,
                    "Personal data redacted before release.", foundSummary, null, redacted);
        }
        return finish(requester, actor, item, destination, Decision.ALLOWED, "Authorized.", foundSummary, null, content);
    }

    private Optional<AiAccessGrant> activeGrant(AppUser actor) {
        LocalDateTime now = now();
        return grants.findByActorUserIdOrderByIdDesc(actor.getId()).stream()
                .filter(g -> g.isActiveAt(now))
                .max((a, b) -> a.getMaxLevel().compareTo(b.getMaxLevel()));
    }

    private AccessResult finish(AppUser requester, AppUser actor, ClassifiedItem item, Destination destination,
                                Decision decision, String reason, List<String> findings, Long requestId, String released) {
        AiActivityLog entry = appendLog(actor, requester, item, destination, decision, reason, String.join("; ", findings));
        String out = decision == Decision.ALLOWED || decision == Decision.ALLOWED_REDACTED ? released : null;
        return new AccessResult(decision.name(), reason, findings, out, requestId, entry.getSeq());
    }

    /* ---------- approvals and grants ---------- */

    @Transactional(readOnly = true)
    public List<RequestView> requests(AppUser viewer) {
        requireSecurityAuthority(viewer);
        return requests.findAllByOrderByIdDesc().stream().map(this::requestView).toList();
    }

    /** Only the Owner decides. The requester and the AI seat being granted access can never decide. */
    @Transactional
    public RequestView decideRequest(AppUser owner, Long requestId, boolean approve, String reason) {
        requireOwner(owner);
        AccessRequest request = requests.findById(requestId).orElseThrow(() -> new NotFoundException("That request no longer exists."));
        if (request.getStatus() != AccessRequest.Status.PENDING) throw new ConflictException("This request is already decided.");
        if (owner.getId().equals(request.getRequestedByUserId()) || owner.getId().equals(request.getActorUserId())) {
            throw new ForbiddenException("Nobody can approve a request they made or one that grants their own access.");
        }
        String why = requireText(reason, "Write the reason for your decision (at least " + MIN_REASON + " characters).", MAX_TEXT);
        if (why.length() < MIN_REASON) throw new ConflictException("Write the reason for your decision (at least " + MIN_REASON + " characters).");
        request.decide(approve ? AccessRequest.Status.APPROVED : AccessRequest.Status.DENIED, owner.getId(), owner.getDisplayName(), why, now());
        activity.record("SECURITY_APPROVAL", owner.getDisplayName() + (approve ? " approved" : " denied") + " access request #" + requestId);
        return requestView(request);
    }

    @Transactional
    public GrantView grant(AppUser owner, Long actorUserId, SecurityLevel maxLevel, String justification, LocalDate expiresOn) {
        requireOwner(owner);
        AppUser actor = users.findById(actorUserId).orElseThrow(() -> new NotFoundException("That account no longer exists."));
        if (!actor.isActive()) throw new ConflictException("The account is deactivated.");
        if (maxLevel == null) throw new ConflictException("Choose the highest level to grant.");
        if (actor.isAi() && maxLevel == SecurityLevel.RESTRICTED) {
            throw new ConflictException("AI seats cannot be granted RESTRICTED access. Use an Owner-approved request instead.");
        }
        String why = justification == null ? "" : justification.trim();
        if (maxLevel == SecurityLevel.RESTRICTED && why.length() < MIN_JUSTIFICATION) {
            throw new ConflictException("RESTRICTED access needs a business justification of at least " + MIN_JUSTIFICATION + " characters.");
        }
        LocalDateTime expires = expiresOn == null ? null : expiresOn.atTime(23, 59);
        AiAccessGrant g = grants.save(new AiAccessGrant(actor.getId(), actor.getDisplayName(), maxLevel,
                why.isEmpty() ? null : why, owner.getDisplayName(), now(), expires));
        activity.record("SECURITY_GRANT", owner.getDisplayName() + " granted " + actor.getDisplayName() + " up to " + maxLevel);
        return grantView(g);
    }

    @Transactional
    public GrantView revokeGrant(AppUser owner, Long grantId) {
        requireOwner(owner);
        AiAccessGrant g = grants.findById(grantId).orElseThrow(() -> new NotFoundException("That grant no longer exists."));
        if (g.getRevokedAt() == null) g.revoke(now());
        activity.record("SECURITY_GRANT", owner.getDisplayName() + " revoked a grant for " + g.getActorName());
        return grantView(g);
    }

    @Transactional(readOnly = true)
    public List<GrantView> grants(AppUser viewer) {
        requireSecurityAuthority(viewer);
        return grants.findAllByOrderByIdDesc().stream().map(this::grantView).toList();
    }

    /* ---------- AI activity log ---------- */

    private synchronized AiActivityLog appendLog(AppUser actor, AppUser requester, ClassifiedItem item, Destination destination,
                                                 Decision decision, String reason, String findings) {
        Optional<AiActivityLog> last = logs.findFirstByOrderBySeqDesc();
        long seq = last.map(l -> l.getSeq() + 1).orElse(1L);
        String prev = last.map(AiActivityLog::getHash).orElse(HexFormat.of().formatHex(new byte[32]));
        // Whole seconds only: MySQL DATETIME rounds fractions, so the hashed time must already be exact.
        LocalDateTime at = now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        String kind = actor.isAi() ? "AI" : "PERSON";
        String canonical = String.join("|", prev, String.valueOf(seq), String.valueOf(actor.getId()), String.valueOf(item.getId()),
                item.getLevel().name(), destination.name(), decision.name(), reason, findings, at.toString(),
                requester.getDisplayName(), kind, actor.getDisplayName(), item.getTitle());
        String hash = sha256(canonical);
        return logs.save(new AiActivityLog(seq, prev, hash, actor.getId(), actor.getDisplayName(), kind,
                requester.getDisplayName(), item.getId(), item.getTitle(), item.getLevel(), destination, decision, reason,
                findings, at));
    }

    /** Recomputes every hash from the first row. Any edit, insertion or removal breaks the chain. */
    @Transactional(readOnly = true)
    public ChainCheck verifyChain() {
        String prev = HexFormat.of().formatHex(new byte[32]);
        long rows = 0;
        for (AiActivityLog l : logs.findAllByOrderBySeqAsc()) {
            rows++;
            if (!l.getPrevHash().equals(prev)) return new ChainCheck(false, rows, l.getSeq());
            AppUser actor = users.findById(l.getActorUserId()).orElse(null);
            String actorName = l.getActorName();
            String canonical = String.join("|", l.getPrevHash(), String.valueOf(l.getSeq()), String.valueOf(l.getActorUserId()),
                    String.valueOf(l.getItemId()), l.getItemLevel().name(), l.getDestination().name(), l.getDecision().name(),
                    l.getReason(), l.getFindings(), l.getCreatedAt().toString(), l.getRequestedByName(), l.getActorKind(),
                    actorName, l.getItemTitle());
            if (!sha256(canonical).equals(l.getHash())) return new ChainCheck(false, rows, l.getSeq());
            prev = l.getHash();
        }
        return new ChainCheck(true, rows, null);
    }

    @Transactional(readOnly = true)
    public List<LogView> log(AppUser viewer, int limit) {
        requireSecurityAuthority(viewer);
        return logs.findTop200ByOrderBySeqDesc().stream().limit(Math.max(1, Math.min(limit, 200))).map(this::logView).toList();
    }

    /* ---------- incidents ---------- */

    /** Incidents raised automatically by the gateway: owned by the Information Security & Audit department. */
    private SecurityIncident incident(String title, SecurityIncident.Severity sev, String evidence, String recordedBy) {
        return incidents.save(new SecurityIncident(title, sev, DEPARTMENT_NAME, "CISO", LocalDate.now(clock), evidence,
                "Review the access decision and the policy that governs this information.", recordedBy, now()));
    }

    @Transactional
    public IncidentView recordIncident(AppUser actor, String title, SecurityIncident.Severity severity, String department,
                                       String role, LocalDate discovered, String evidence, String action) {
        requireSecurityAuthority(actor);
        SecurityIncident i = incidents.save(new SecurityIncident(
                requireText(title, "Give the incident a title.", 200), requireSeverity(severity),
                requireText(department, "Name the responsible department.", 80),
                requireText(role, "Name the responsible person or AI role.", 120),
                discovered == null ? LocalDate.now(clock) : discovered,
                requireText(evidence, "Record the evidence.", MAX_TEXT),
                action == null ? "" : action.trim(), actor.getDisplayName(), now()));
        return incidentView(i);
    }

    @Transactional
    public IncidentView setIncidentStatus(AppUser actor, Long id, SecurityIncident.Status status) {
        requireSecurityAuthority(actor);
        SecurityIncident i = incidents.findById(id).orElseThrow(() -> new NotFoundException("That incident no longer exists."));
        if (status == SecurityIncident.Status.RESOLVED && i.getSeverity() == SecurityIncident.Severity.CRITICAL && i.getOwnerAcknowledgedAt() == null) {
            throw new ConflictException("The Owner must acknowledge a critical incident before it can be resolved.");
        }
        i.changeStatus(status, now());
        return incidentView(i);
    }

    @Transactional
    public IncidentView acknowledgeIncident(AppUser owner, Long id) {
        requireOwner(owner);
        SecurityIncident i = incidents.findById(id).orElseThrow(() -> new NotFoundException("That incident no longer exists."));
        i.acknowledgeByOwner(owner.getDisplayName(), now());
        return incidentView(i);
    }

    @Transactional(readOnly = true)
    public List<IncidentView> incidents(AppUser viewer) {
        requireSecurityAuthority(viewer);
        return incidents.findAllByOrderByIdDesc().stream().map(this::incidentView).toList();
    }

    /* ---------- independent audit findings ---------- */

    @Transactional
    public FindingView recordFinding(AppUser auditor, AuditFinding.Source source, String subjectKey, String title,
                                     SecurityIncident.Severity severity, String department, String role, LocalDate discovered,
                                     String evidence, String action) {
        requireAuditor(auditor, source);
        if (subjectKey != null && holds(auditor, subjectKey)) {
            throw new ForbiddenException("An auditor cannot audit the position they hold.");
        }
        String body = requireText(evidence, "Record the evidence the finding rests on.", MAX_TEXT);
        AuditFinding f = findings.save(new AuditFinding(requireText(title, "Give the finding a title.", 200), source,
                subjectKey == null ? "" : subjectKey, requireSeverity(severity),
                requireText(department, "Name the responsible department.", 80),
                requireText(role, "Name the responsible person or AI role.", 120),
                discovered == null ? LocalDate.now(clock) : discovered, body, sha256(body),
                action == null ? "" : action.trim(), auditor.getId(), auditor.getDisplayName(), now()));
        activity.record("SECURITY_FINDING", auditor.getDisplayName() + " recorded finding \"" + f.getTitle() + "\"");
        return findingView(f);
    }

    /**
     * Status moves: IN_REMEDIATION may be set by the security authority. VERIFIED_CLOSED is set only by
     * the Owner or by the auditor who recorded the finding, and never by the CISO, who cannot approve
     * an audit of their own function.
     */
    @Transactional
    public FindingView setFindingStatus(AppUser actor, Long id, AuditFinding.Status status) {
        AuditFinding f = findings.findById(id).orElseThrow(() -> new NotFoundException("That finding no longer exists."));
        if (status == AuditFinding.Status.IN_REMEDIATION) {
            requireSecurityAuthority(actor);
        } else {
            boolean auditorOfFinding = actor.getId().equals(f.getAuditorUserId());
            if (holds(actor, SecurityPositionCatalog.CISO) && !isOwner(actor)) {
                throw new ForbiddenException("The CISO cannot close an audit finding about their own function.");
            }
            if (!isOwner(actor) && !auditorOfFinding) {
                throw new ForbiddenException("Only the Owner or the auditor who recorded this finding can close it.");
            }
        }
        f.changeStatus(status, actor.getDisplayName(), now());
        return findingView(f);
    }

    @Transactional
    public FindingView acknowledgeFinding(AppUser owner, Long id) {
        requireOwner(owner);
        AuditFinding f = findings.findById(id).orElseThrow(() -> new NotFoundException("That finding no longer exists."));
        f.acknowledgeByOwner(owner.getDisplayName(), now());
        return findingView(f);
    }

    @Transactional(readOnly = true)
    public List<FindingView> findings(AppUser viewer) {
        requireSecurityAuthority(viewer);
        return findings.findAllByOrderByIdDesc().stream().map(this::findingView).toList();
    }

    /* ---------- policies ---------- */

    @Transactional
    public PolicyView draftPolicy(AppUser actor, String key, String title, String body) {
        requireSecurityAuthority(actor);
        String k = requireText(key, "Give the policy a key, such as ai-data-handling.", 60);
        int version = policies.findByPolicyKeyOrderByVersionDesc(k).stream().findFirst().map(p -> p.getVersion() + 1).orElse(1);
        SecurityPolicy p = policies.save(new SecurityPolicy(k, version, requireText(title, "Give the policy a title.", 200),
                requireText(body, "Write the policy text.", MAX_TEXT), actor.getId(), actor.getDisplayName(), now()));
        return policyView(p);
    }

    /**
     * Only the Owner approves a policy, and the author cannot approve their own. This keeps the CISO from
     * certifying their own policy. Approving supersedes the previous approved version of the same key.
     */
    @Transactional
    public PolicyView approvePolicy(AppUser owner, Long id) {
        requireOwner(owner);
        SecurityPolicy p = policies.findById(id).orElseThrow(() -> new NotFoundException("That policy no longer exists."));
        if (p.getStatus() != SecurityPolicy.Status.DRAFT) throw new ConflictException("Only a draft can be approved.");
        if (owner.getId().equals(p.getAuthoredByUserId())) {
            throw new ForbiddenException("The author of a policy cannot approve it.");
        }
        policies.findByPolicyKeyOrderByVersionDesc(p.getPolicyKey()).stream()
                .filter(old -> old.getStatus() == SecurityPolicy.Status.APPROVED)
                .forEach(SecurityPolicy::supersede);
        p.approve(owner.getId(), owner.getDisplayName(), now());
        activity.record("SECURITY_POLICY", owner.getDisplayName() + " approved policy \"" + p.getTitle() + "\" v" + p.getVersion());
        return policyView(p);
    }

    @Transactional(readOnly = true)
    public List<PolicyView> policies(AppUser viewer) {
        requireSecurityAuthority(viewer);
        return policies.findAllByOrderByIdDesc().stream().map(this::policyView).toList();
    }

    /* ---------- executive positions ---------- */

    /**
     * Only the Owner assigns executive positions. Independence rules: the IT Security Auditor cannot be the
     * CISO; the CISO cannot be the IT Security Auditor; the External Security Auditor cannot be the CISO,
     * the IT Security Auditor, the Owner, or an AI seat.
     */
    @Transactional
    public PositionView assignPosition(AppUser owner, String key, Long userId) {
        requireOwner(owner);
        SecurityPositionCatalog.Position def = positionCatalog.byKey(key)
                .orElseThrow(() -> new NotFoundException("That position does not exist."));
        OrgPosition pos = orgPositions.findByPositionKey(key).orElseThrow(() -> new NotFoundException("That position is not registered."));
        if (userId == null) {
            pos.assign(null);
            return positionView(def, pos);
        }
        AppUser person = users.findById(userId).orElseThrow(() -> new NotFoundException("That account no longer exists."));
        if (person.isAi()) throw new ConflictException("An AI seat cannot hold an executive security position.");
        if (!person.isActive()) throw new ConflictException("The account is deactivated.");
        if (person.getRole() != UserRole.ADMIN) throw new ConflictException("Executive security positions need administrator access.");
        Long ciso = holderOf(SecurityPositionCatalog.CISO).orElse(null);
        Long itAuditor = holderOf(SecurityPositionCatalog.IT_AUDITOR).orElse(null);
        Long ownerId = legal.currentOwner().map(o -> o.getUserId()).orElse(null);
        if (key.equals(SecurityPositionCatalog.IT_AUDITOR) && userId.equals(ciso)) {
            throw new ConflictException("The IT Security Auditor cannot be the CISO they audit.");
        }
        if (key.equals(SecurityPositionCatalog.CISO) && userId.equals(itAuditor)) {
            throw new ConflictException("The CISO cannot also be the IT Security Auditor.");
        }
        if (key.equals(SecurityPositionCatalog.EXTERNAL_AUDITOR)) {
            if (userId.equals(ciso) || userId.equals(itAuditor) || userId.equals(ownerId)) {
                throw new ConflictException("The External Security Auditor must be independent of the CISO, the IT Security Auditor and the Owner.");
            }
        }
        pos.assign(userId);
        activity.record("SECURITY_POSITION", owner.getDisplayName() + " assigned " + person.getDisplayName() + " to " + def.title());
        return positionView(def, pos);
    }

    @Transactional(readOnly = true)
    public List<PositionView> positions() {
        return positionCatalog.all().stream().map(def -> positionView(def,
                orgPositions.findByPositionKey(def.key()).orElse(null))).toList();
    }

    /* ---------- Owner summary ---------- */

    @Transactional(readOnly = true)
    public Summary summary(AppUser viewer) {
        requireSecurityAuthority(viewer);
        return buildSummary();
    }

    /** Owner-level summary, also served to Owner's Office. */
    @Transactional(readOnly = true)
    public Summary buildSummary() {
        LocalDateTime since = now().minusDays(30);
        List<AiActivityLog> recent = logs.findAllByOrderBySeqAsc().stream().filter(l -> l.getCreatedAt().isAfter(since)).toList();
        int blocked = (int) recent.stream().filter(l -> l.getDecision() == Decision.BLOCKED).count();
        int redacted = (int) recent.stream().filter(l -> l.getDecision() == Decision.ALLOWED_REDACTED).count();
        List<SecurityIncident> openIncidents = incidents.findAllByOrderByIdDesc().stream()
                .filter(i -> i.getStatus() != SecurityIncident.Status.RESOLVED).toList();
        List<AuditFinding> openFindings = findings.findAllByOrderByIdDesc().stream()
                .filter(f -> f.getStatus() != AuditFinding.Status.VERIFIED_CLOSED).toList();
        Map<String, Long> accountability = new LinkedHashMap<>();
        openIncidents.forEach(i -> accountability.merge(i.getResponsibleDepartment(), 1L, Long::sum));
        openFindings.forEach(f -> accountability.merge(f.getResponsibleDepartment(), 1L, Long::sum));
        long pendingRisks = legal.summary(LegalCatalog.LEAD).highPriorityRisks();
        return new Summary(blocked + redacted, blocked, redacted, requests.countByStatus(AccessRequest.Status.PENDING),
                openIncidents.stream().filter(i -> i.getSeverity() == SecurityIncident.Severity.CRITICAL).count(),
                openIncidents.stream().filter(i -> i.getSeverity() == SecurityIncident.Severity.HIGH).count(),
                openFindings.size(),
                openFindings.stream().filter(f -> f.getSeverity() == SecurityIncident.Severity.CRITICAL).count(),
                pendingRisks,
                openFindings.stream().map(this::findingView).limit(20).toList(),
                accountability,
                positions(),
                verifyChain(),
                "Not tracked yet: no security expense records exist in this system.");
    }

    /** Integration reads: the same data, without an administrator session. Callers authenticate first. */
    @Transactional(readOnly = true)
    public List<IncidentView> incidentsForIntegration() {
        return incidents.findAllByOrderByIdDesc().stream().map(this::incidentView).toList();
    }

    @Transactional(readOnly = true)
    public List<FindingView> findingsForIntegration() {
        return findings.findAllByOrderByIdDesc().stream().map(this::findingView).toList();
    }

    /* ---------- helpers ---------- */

    private ClassifiedItem requireItem(Long id) {
        return items.findById(id).orElseThrow(() -> new NotFoundException("That information no longer exists."));
    }

    private static String requireText(String value, String message, int max) {
        if (value == null || value.isBlank()) throw new ConflictException(message);
        String t = value.trim();
        if (t.length() > max) throw new ConflictException("Keep this under " + max + " characters.");
        return t;
    }

    private static SecurityIncident.Severity requireSeverity(SecurityIncident.Severity s) {
        if (s == null) throw new ConflictException("Choose a severity.");
        return s;
    }

    private static String fingerprint(String text) {
        return text == null || text.isEmpty() ? null : sha256(text);
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private ItemView itemView(ClassifiedItem i) {
        return new ItemView(i.getId(), i.getTitle(), i.getCategory().name(), i.getLevel().name(),
                i.getLevel() == SecurityLevel.RESTRICTED ? null : i.getContent(), i.getFingerprint(),
                i.getClassifiedByName(), i.getUpdatedAt());
    }

    private GrantView grantView(AiAccessGrant g) {
        return new GrantView(g.getId(), g.getActorUserId(), g.getActorName(), g.getMaxLevel().name(), g.getJustification(),
                g.getGrantedByName(), g.getGrantedAt(), g.getExpiresAt(), g.getRevokedAt(), g.isActiveAt(now()));
    }

    private RequestView requestView(AccessRequest r) {
        return new RequestView(r.getId(), r.getActorName(), r.getItemTitle(), r.getItemLevel().name(), r.getDestination().name(),
                r.getPurpose(), r.getRequestedByName(), r.getStatus().name(), r.getDecidedByName(), r.getDecisionReason(),
                r.getRequestedAt(), r.getDecidedAt());
    }

    private LogView logView(AiActivityLog l) {
        return new LogView(l.getSeq(), l.getCreatedAt(), l.getActorName(), l.getActorKind(), l.getRequestedByName(),
                l.getItemTitle(), l.getItemLevel().name(), l.getDestination().name(), l.getDecision().name(), l.getReason(),
                l.getFindings(), l.getPrevHash(), l.getHash());
    }

    private IncidentView incidentView(SecurityIncident i) {
        return new IncidentView(i.getId(), i.getTitle(), i.getSeverity().name(), i.getStatus().name(), i.getResponsibleDepartment(),
                i.getResponsibleRole(), i.getDiscoveredOn(), i.getEvidence(), i.getRecommendedAction(), i.isEscalatedToOwner(),
                i.getOwnerAcknowledgedAt(), i.getOwnerAcknowledgedByName(), i.getRecordedByName(), i.getCreatedAt());
    }

    private FindingView findingView(AuditFinding f) {
        return new FindingView(f.getId(), f.getTitle(), f.getSource().name(), f.getSubject(), f.getSeverity().name(),
                f.getStatus().name(), f.getResponsibleDepartment(), f.getResponsibleRole(), f.getDiscoveredOn(), f.getEvidence(),
                f.getEvidenceHash(), f.getRecommendedAction(), f.isEscalatedToOwner(), f.getOwnerAcknowledgedAt(),
                f.getOwnerAcknowledgedByName(), f.getAuditorName(), f.getCreatedAt());
    }

    private PolicyView policyView(SecurityPolicy p) {
        return new PolicyView(p.getId(), p.getPolicyKey(), p.getVersion(), p.getTitle(), p.getBody(), p.getStatus().name(),
                p.getAuthoredByName(), p.getApprovedByName(), p.getApprovedAt(), p.getCreatedAt());
    }

    private PositionView positionView(SecurityPositionCatalog.Position def, OrgPosition pos) {
        Long holder = pos == null ? null : pos.getAssigneeUserId();
        String name = holder == null ? null : users.findById(holder).map(AppUser::getDisplayName).orElse(null);
        return new PositionView(def.key(), def.title(), def.mission(), holder, name, holder == null ? "VACANT" : "FILLED");
    }
}
