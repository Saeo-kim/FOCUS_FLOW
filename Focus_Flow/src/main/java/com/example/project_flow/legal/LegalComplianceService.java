package com.example.project_flow.legal;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.ForbiddenException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.service.ActivityLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Legal and compliance reviews: records of documents, the five specialist findings on each, and
 * the Decision Assistant that consolidates them. It supports the owner's decision; it is not legal advice.
 */
@Service
public class LegalComplianceService {

    public static final String DISCLAIMER = "Decision support only. This is not legal advice, and it does not decide anything. "
            + "A person authorized by the owner makes the final decision.";
    private static final int MIN_REASONING = 10;
    private static final int SPECIALIST_COUNT = 5;
    private static final int MAX_TEXT = 4000;

    public record FunctionView(String key, String title, String description) {
    }

    public record PositionView(String key, String title, String koreanTitle, String department, String mission,
                               String status, List<FunctionView> functions, int pendingReviews,
                               int highPriorityRisks, int completedReviews) {
    }

    public record FindingView(String functionKey, String functionTitle, RiskLevel riskLevel, String finding, String recommendation) {
    }

    public record ReviewView(Long id, String category, String categoryLabel, String title, String documentRef, String description,
                             Priority priority, LocalDate deadline, String status, boolean overdue, String findingsSummary,
                             String recommendedAction, List<FindingView> findings, LocalDateTime createdAt,
                             LocalDateTime updatedAt, LocalDateTime completedAt, boolean issueResolved,
                             LocalDateTime issueResolvedAt, String issueResolvedByName, String issueNote) {
    }

    public record RiskItem(Long reviewId, String reviewTitle, String functionTitle, RiskLevel riskLevel, String finding) {
    }

    public record Summary(int pendingReviews, int completedReviews, int overdueReviews, int highPriorityRisks,
                          List<RiskItem> topRisks) {
    }

    public record DecisionView(Long reviewId, DecisionState state, boolean approvalAvailable, int specialistsReviewed,
                               int specialistsTotal, RiskLevel highestRisk, boolean humanReviewRequired,
                               List<String> conflicts, List<RiskItem> prioritizedRisks, String executiveSummary,
                               String disclaimer) {
    }

    public record HumanDecisionRequest(String outcome, String reasoning) {
    }

    public record HumanDecisionView(Long id, Long reviewId, String outcome, String reasoning, String decidedByName,
                                    DecisionState assistantState, LocalDateTime decidedAt) {
    }

    public record IssueRequest(boolean resolved, String note) {
    }

    /** Everything Owner's Office needs to show one review, for a human to decide. Read-only. */
    public record ReviewPackage(ReviewView review, DecisionView assistant, List<HumanDecisionView> humanDecisions) {
    }

    public record ReviewRequest(String category, String title, String documentRef, String description, Priority priority,
                                LocalDate deadline, String status, String findingsSummary, String recommendedAction) {
    }

    public record FindingRequest(String functionKey, RiskLevel riskLevel, String finding, String recommendation) {
    }

    private final LegalCatalog catalog;
    private final OrgPositionRepository positions;
    private final OrgPositionFunctionRepository functions;
    private final ComplianceReviewRepository reviews;
    private final ComplianceFindingRepository findings;
    private final HumanDecisionRepository decisions;
    private final ActivityLogService activity;
    private final Clock clock;

    public LegalComplianceService(LegalCatalog catalog, OrgPositionRepository positions, OrgPositionFunctionRepository functions,
                                  ComplianceReviewRepository reviews, ComplianceFindingRepository findings,
                                  HumanDecisionRepository decisions, ActivityLogService activity, Clock clock) {
        this.catalog = catalog;
        this.positions = positions;
        this.functions = functions;
        this.reviews = reviews;
        this.findings = findings;
        this.decisions = decisions;
        this.activity = activity;
        this.clock = clock;
    }

    /* ---------- Position ---------- */

    @Transactional(readOnly = true)
    public List<PositionView> positions() {
        LocalDate today = LocalDate.now(clock);
        return positions.findAll().stream().sorted(Comparator.comparing(OrgPosition::getId)).map(p -> {
            List<ComplianceReview> mine = reviews.findAllByOrderByIdDesc().stream()
                    .filter(r -> r.getPositionKey().equals(p.getPositionKey())).toList();
            Map<Long, List<ComplianceFinding>> byReview = findingsByReview(mine);
            return new PositionView(p.getPositionKey(), p.getTitle(), p.getKoreanTitle(), p.getDepartment().name(),
                    p.getMission(), p.getStatus(),
                    functions.findByPositionKeyOrderBySortOrderAsc(p.getPositionKey()).stream()
                            .map(f -> new FunctionView(f.getFunctionKey(), f.getTitle(), f.getDescription())).toList(),
                    (int) mine.stream().filter(r -> ReviewStatus.PENDING.contains(r.getStatus())).count(),
                    (int) mine.stream().filter(r -> ReviewStatus.PENDING.contains(r.getStatus()))
                            .flatMap(r -> byReview.getOrDefault(r.getId(), List.of()).stream())
                            .filter(f -> f.getRiskLevel() == RiskLevel.HIGH).count(),
                    (int) mine.stream().filter(r -> ReviewStatus.COMPLETED.contains(r.getStatus())).count());
        }).toList();
    }

    /* ---------- Summary for dashboards ---------- */

    @Transactional(readOnly = true)
    public Summary summary(String positionKey) {
        LocalDate today = LocalDate.now(clock);
        List<ComplianceReview> all = reviews.findAllByOrderByIdDesc().stream()
                .filter(r -> r.getPositionKey().equals(positionKey)).toList();
        List<ComplianceReview> pending = all.stream().filter(r -> ReviewStatus.PENDING.contains(r.getStatus())).toList();
        Map<Long, List<ComplianceFinding>> byReview = findingsByReview(pending);
        List<RiskItem> risks = pending.stream()
                .flatMap(r -> byReview.getOrDefault(r.getId(), List.of()).stream()
                        .filter(f -> f.getRiskLevel().compareTo(RiskLevel.MEDIUM) >= 0)
                        .map(f -> new RiskItem(r.getId(), r.getTitle(), functionTitle(positionKey, f.getFunctionKey()),
                                f.getRiskLevel(), f.getFinding())))
                .sorted(Comparator.comparing(RiskItem::riskLevel).reversed())
                .toList();
        return new Summary(pending.size(),
                (int) all.stream().filter(r -> ReviewStatus.COMPLETED.contains(r.getStatus())).count(),
                (int) pending.stream().filter(r -> r.getDeadline() != null && r.getDeadline().isBefore(today)).count(),
                (int) risks.stream().filter(r -> r.riskLevel() == RiskLevel.HIGH).count(),
                risks.stream().limit(5).toList());
    }

    /* ---------- Reviews ---------- */

    @Transactional(readOnly = true)
    public List<ReviewView> reviews(String positionKey, String status) {
        LocalDate today = LocalDate.now(clock);
        List<ComplianceReview> list = reviews.findAllByOrderByIdDesc().stream()
                .filter(r -> r.getPositionKey().equals(positionKey))
                .filter(r -> status == null || status.isBlank() || r.getStatus().name().equals(status))
                .toList();
        Map<Long, List<ComplianceFinding>> byReview = findingsByReview(list);
        return list.stream().map(r -> view(r, byReview.getOrDefault(r.getId(), List.of()), today)).toList();
    }

    @Transactional
    public ReviewView create(AppUser actor, String positionKey, ReviewRequest q) {
        requirePosition(positionKey);
        ReviewCategory category = category(q.category());
        String title = required(q.title(), "Enter a title for the review.", 200);
        LocalDateTime now = now();
        ComplianceReview review = new ComplianceReview(positionKey, category, title, clip(q.documentRef(), 500),
                clip(q.description(), MAX_TEXT), q.priority() == null ? Priority.MEDIUM : q.priority(), q.deadline(),
                actor.getId(), now);
        if (q.status() != null) review.changeStatus(status(q.status()), now);
        reviews.save(review);
        activity.record("LEGAL_REVIEW", actor.getDisplayName() + " opened a " + category.label() + " review: \"" + title + "\"");
        return view(review, List.of(), LocalDate.now(clock));
    }

    @Transactional
    public ReviewView update(AppUser actor, Long id, ReviewRequest q) {
        ComplianceReview review = require(id);
        LocalDateTime now = now();
        ReviewCategory category = q.category() == null ? review.getCategory() : category(q.category());
        review.update(category, required(q.title(), "Enter a title for the review.", 200), clip(q.documentRef(), 500),
                clip(q.description(), MAX_TEXT), q.priority() == null ? review.getPriority() : q.priority(),
                q.deadline(), clip(q.findingsSummary(), MAX_TEXT), clip(q.recommendedAction(), MAX_TEXT), now);
        if (q.status() != null && status(q.status()) != review.getStatus()) {
            ReviewStatus next = status(q.status());
            review.changeStatus(next, now);
            activity.record("LEGAL_REVIEW", actor.getDisplayName() + " set \"" + review.getTitle() + "\" to " + next);
        } else {
            activity.record("LEGAL_REVIEW", actor.getDisplayName() + " updated \"" + review.getTitle() + "\"");
        }
        return view(review, findings.findByReviewIdOrderByIdAsc(id), LocalDate.now(clock));
    }

    /** Records what each specialist found. Functions not in the list keep their previous finding. */
    @Transactional
    public ReviewView saveFindings(AppUser actor, Long id, List<FindingRequest> items) {
        ComplianceReview review = require(id);
        LocalDateTime now = now();
        for (FindingRequest item : items == null ? List.<FindingRequest>of() : items) {
            if (!catalog.hasFunction(review.getPositionKey(), item.functionKey())) {
                throw new ConflictException("Unknown specialist function: " + item.functionKey());
            }
            if (item.riskLevel() == null) throw new ConflictException("Choose a risk level for each finding.");
            String finding = clip(item.finding(), MAX_TEXT);
            String recommendation = clip(item.recommendation(), MAX_TEXT);
            Optional<ComplianceFinding> existing = findings.findByReviewIdAndFunctionKey(id, item.functionKey());
            if (existing.isPresent()) {
                existing.get().update(item.riskLevel(), finding, recommendation, now);
            } else {
                findings.save(new ComplianceFinding(id, item.functionKey(), item.riskLevel(), finding, recommendation, now));
            }
        }
        review.touch(now);
        activity.record("LEGAL_REVIEW", actor.getDisplayName() + " recorded specialist findings on \"" + review.getTitle() + "\"");
        return view(review, findings.findByReviewIdOrderByIdAsc(id), LocalDate.now(clock));
    }

    /* ---------- Decision Assistant ---------- */

    /**
     * Readiness of a review. Rules, in order:
     * any HIGH finding -> BLOCK (human review first, approval unavailable);
     * otherwise a missing specialist review, or none at all -> INCOMPLETE (approval unavailable);
     * otherwise a MEDIUM finding -> CLARIFICATION_REQUIRED (approval unavailable until answered);
     * otherwise all five complete with LOW or NONE -> APPROVAL_ELIGIBLE (eligibility only).
     * It never records a decision.
     */
    @Transactional(readOnly = true)
    public DecisionView consolidate(Long id) {
        ComplianceReview review = require(id);
        List<ComplianceFinding> reviewed = findings.findByReviewIdOrderByIdAsc(id).stream()
                .filter(f -> catalog.hasFunction(review.getPositionKey(), f.getFunctionKey())).toList();
        RiskLevel highest = reviewed.stream().map(ComplianceFinding::getRiskLevel).max(Comparator.naturalOrder()).orElse(RiskLevel.NONE);
        boolean anyHigh = highest == RiskLevel.HIGH;

        DecisionState state;
        if (anyHigh) {
            state = DecisionState.BLOCK;
        } else if (reviewed.size() < SPECIALIST_COUNT) {
            state = DecisionState.INCOMPLETE;
        } else if (highest == RiskLevel.MEDIUM) {
            state = DecisionState.CLARIFICATION_REQUIRED;
        } else {
            state = DecisionState.APPROVAL_ELIGIBLE;
        }

        List<RiskItem> prioritized = reviewed.stream()
                .sorted(Comparator.comparing(ComplianceFinding::getRiskLevel).reversed())
                .map(f -> new RiskItem(id, review.getTitle(), functionTitle(review.getPositionKey(), f.getFunctionKey()),
                        f.getRiskLevel(), f.getFinding()))
                .toList();

        List<String> conflicts = new ArrayList<>();
        boolean anyLow = reviewed.stream().anyMatch(f -> f.getRiskLevel().compareTo(RiskLevel.LOW) <= 0);
        if (anyHigh && anyLow) {
            for (ComplianceFinding high : reviewed.stream().filter(f -> f.getRiskLevel() == RiskLevel.HIGH).toList()) {
                for (ComplianceFinding low : reviewed.stream().filter(f -> f.getRiskLevel().compareTo(RiskLevel.LOW) <= 0).toList()) {
                    conflicts.add(functionTitle(review.getPositionKey(), high.getFunctionKey()) + " rates HIGH, while "
                            + functionTitle(review.getPositionKey(), low.getFunctionKey()) + " rates " + low.getRiskLevel()
                            + ". Resolve the disagreement before deciding.");
                }
            }
        }

        String summary = executiveSummary(review, state, reviewed.size(), highest, conflicts.size());
        return new DecisionView(id, state, state == DecisionState.APPROVAL_ELIGIBLE, reviewed.size(), SPECIALIST_COUNT,
                highest, anyHigh, conflicts, prioritized, summary, DISCLAIMER);
    }

    private String executiveSummary(ComplianceReview review, DecisionState state, int reviewed, RiskLevel highest, int conflicts) {
        String head = switch (state) {
            case BLOCK -> "Blocked: a HIGH-risk finding needs human review before anything else. This does not decide the outcome.";
            case INCOMPLETE -> reviewed == 0
                    ? "Incomplete: no specialist findings are recorded yet. Approval is unavailable."
                    : "Incomplete: " + reviewed + " of " + SPECIALIST_COUNT + " specialist reviews are recorded. Approval is unavailable until all five are complete.";
            case CLARIFICATION_REQUIRED -> "Clarification required: the highest risk is MEDIUM. Resolve the open issues listed below. Approval is unavailable until they are answered.";
            case APPROVAL_ELIGIBLE -> "Approval eligible: all five reviews are complete and show only LOW or NONE risk. This is eligibility only, not an approval; an authorized person must still decide.";
        };
        return review.getTitle() + ". " + head + " Highest risk: " + highest + "."
                + (conflicts > 0 ? " " + conflicts + " conflicting opinion(s) to resolve." : "");
    }

    /* ---------- Owner-only actions ---------- */

    /** Only the owner (an administrator who is a person, not an AI seat) may record a final decision or close an issue. */
    private static void requireOwner(AppUser actor) {
        if (actor.isAi() || actor.getRole() != UserRole.ADMIN) {
            throw new ForbiddenException("Only the owner can record a final decision on a legal review.");
        }
    }

    /**
     * Records a human decision. Append-only. Approval is refused unless the Decision Assistant
     * reports APPROVAL_ELIGIBLE at this moment. The reasoning is required.
     */
    @Transactional
    public HumanDecisionView recordHumanDecision(AppUser actor, Long reviewId, HumanDecisionRequest q) {
        requireOwner(actor);
        ComplianceReview review = require(reviewId);
        HumanOutcome outcome = outcome(q.outcome());
        String reasoning = q.reasoning() == null ? "" : q.reasoning().trim();
        if (reasoning.length() < MIN_REASONING) {
            throw new ConflictException("Write your reasoning (at least " + MIN_REASONING + " characters) so the decision can be reviewed later.");
        }
        if (reasoning.length() > MAX_TEXT) reasoning = reasoning.substring(0, MAX_TEXT);
        DecisionState state = consolidate(reviewId).state();
        if (outcome == HumanOutcome.APPROVE && state != DecisionState.APPROVAL_ELIGIBLE) {
            throw new ConflictException("Approval is unavailable while the review is " + state
                    + ". Resolve the findings first, or record a different decision.");
        }
        LocalDateTime now = now();
        HumanDecision decision = decisions.save(new HumanDecision(reviewId, outcome, reasoning, actor.getId(),
                actor.getDisplayName(), state, now));
        ReviewStatus next = switch (outcome) {
            case APPROVE -> ReviewStatus.APPROVED;
            case REJECT -> ReviewStatus.REJECTED;
            case REQUEST_CHANGES -> ReviewStatus.NEEDS_CLARIFICATION;
            case DEFER -> review.getStatus();
        };
        if (next != review.getStatus()) review.changeStatus(next, now); else review.touch(now);
        activity.record("LEGAL_DECISION", actor.getDisplayName() + " recorded " + outcome + " on \"" + review.getTitle()
                + "\" (assistant: " + state + ")");
        return humanView(decision);
    }

    @Transactional(readOnly = true)
    public List<HumanDecisionView> humanDecisions(AppUser actor, Long reviewId) {
        requireOwner(actor);
        require(reviewId);
        return decisions.findByReviewIdOrderByIdAsc(reviewId).stream().map(this::humanView).toList();
    }

    /** Marks whether the issue behind a review is resolved. Owner only. */
    @Transactional
    public ReviewView trackIssue(AppUser actor, Long reviewId, IssueRequest q) {
        requireOwner(actor);
        ComplianceReview review = require(reviewId);
        String note = clip(q.note(), MAX_TEXT);
        review.trackIssue(q.resolved(), actor.getDisplayName(), note, now());
        activity.record("LEGAL_ISSUE", actor.getDisplayName() + (q.resolved() ? " marked resolved: " : " reopened: ") + "\"" + review.getTitle() + "\"");
        return view(review, findings.findByReviewIdOrderByIdAsc(reviewId), LocalDate.now(clock));
    }

    /** Everything needed to decide one review, for Owner's Office. Read-only. */
    @Transactional(readOnly = true)
    public ReviewPackage reviewPackage(Long reviewId) {
        ComplianceReview review = require(reviewId);
        return new ReviewPackage(view(review, findings.findByReviewIdOrderByIdAsc(reviewId), LocalDate.now(clock)),
                consolidate(reviewId),
                decisions.findByReviewIdOrderByIdAsc(reviewId).stream().map(this::humanView).toList());
    }

    private HumanDecisionView humanView(HumanDecision d) {
        return new HumanDecisionView(d.getId(), d.getReviewId(), d.getOutcome().name(), d.getReasoning(),
                d.getDecidedByName(), d.getAssistantState(), d.getDecidedAt());
    }

    private static HumanOutcome outcome(String value) {
        try {
            return HumanOutcome.valueOf(value);
        } catch (RuntimeException e) {
            throw new ConflictException("Choose APPROVE, REJECT, REQUEST_CHANGES or DEFER.");
        }
    }

    /* ---------- helpers ---------- */

    private ReviewView view(ComplianceReview r, List<ComplianceFinding> rows, LocalDate today) {
        List<FindingView> out = rows.stream()
                .map(f -> new FindingView(f.getFunctionKey(), functionTitle(r.getPositionKey(), f.getFunctionKey()),
                        f.getRiskLevel(), f.getFinding(), f.getRecommendation()))
                .toList();
        boolean overdue = r.getDeadline() != null && r.getDeadline().isBefore(today) && ReviewStatus.PENDING.contains(r.getStatus());
        return new ReviewView(r.getId(), r.getCategory().name(), r.getCategory().label(), r.getTitle(), r.getDocumentRef(),
                r.getDescription(), r.getPriority(), r.getDeadline(), r.getStatus().name(), overdue, r.getFindingsSummary(),
                r.getRecommendedAction(), out, r.getCreatedAt(), r.getUpdatedAt(), r.getCompletedAt(),
                r.isIssueResolved(), r.getIssueResolvedAt(), r.getIssueResolvedByName(), r.getIssueNote());
    }

    private Map<Long, List<ComplianceFinding>> findingsByReview(List<ComplianceReview> list) {
        Map<Long, List<ComplianceFinding>> out = new LinkedHashMap<>();
        if (list.isEmpty()) return out;
        for (ComplianceFinding f : findings.findByReviewIdIn(list.stream().map(ComplianceReview::getId).toList())) {
            out.computeIfAbsent(f.getReviewId(), k -> new ArrayList<>()).add(f);
        }
        return out;
    }

    private String functionTitle(String positionKey, String functionKey) {
        return functions.findByPositionKeyAndFunctionKey(positionKey, functionKey).map(OrgPositionFunction::getTitle).orElse(functionKey);
    }

    private void requirePosition(String positionKey) {
        if (catalog.byKey(positionKey).isEmpty()) throw new NotFoundException("That position does not exist.");
    }

    private ComplianceReview require(Long id) {
        return reviews.findById(id).orElseThrow(() -> new NotFoundException("That review no longer exists."));
    }

    private static ReviewCategory category(String value) {
        try {
            return ReviewCategory.valueOf(value);
        } catch (RuntimeException e) {
            throw new ConflictException("Choose a review category.");
        }
    }

    private static ReviewStatus status(String value) {
        try {
            return ReviewStatus.valueOf(value);
        } catch (RuntimeException e) {
            throw new ConflictException("Choose a valid status.");
        }
    }

    private static String required(String value, String message, int max) {
        if (value == null || value.isBlank()) throw new ConflictException(message);
        String t = value.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private static String clip(String value, int max) {
        if (value == null || value.isBlank()) return null;
        String t = value.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
