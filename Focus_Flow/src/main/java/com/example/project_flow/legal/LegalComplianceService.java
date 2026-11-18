package com.example.project_flow.legal;

import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.exception.ConflictException;
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

    public static final String DISCLAIMER = "Decision support only. This is not legal advice and does not replace review by a qualified lawyer.";
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
                             LocalDateTime updatedAt, LocalDateTime completedAt) {
    }

    public record RiskItem(Long reviewId, String reviewTitle, String functionTitle, RiskLevel riskLevel, String finding) {
    }

    public record Summary(int pendingReviews, int completedReviews, int overdueReviews, int highPriorityRisks,
                          List<RiskItem> topRisks) {
    }

    public record DecisionView(Long reviewId, String recommendation, int specialistsReviewed, int specialistsTotal,
                               RiskLevel highestRisk, boolean professionalReviewRequired, List<String> conflicts,
                               List<RiskItem> prioritizedRisks, String executiveSummary, String disclaimer) {
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
    private final ActivityLogService activity;
    private final Clock clock;

    public LegalComplianceService(LegalCatalog catalog, OrgPositionRepository positions, OrgPositionFunctionRepository functions,
                                  ComplianceReviewRepository reviews, ComplianceFindingRepository findings,
                                  ActivityLogService activity, Clock clock) {
        this.catalog = catalog;
        this.positions = positions;
        this.functions = functions;
        this.reviews = reviews;
        this.findings = findings;
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

    @Transactional(readOnly = true)
    public DecisionView consolidate(Long id) {
        ComplianceReview review = require(id);
        List<ComplianceFinding> rows = findings.findByReviewIdOrderByIdAsc(id);
        List<ComplianceFinding> reviewed = rows.stream()
                .filter(f -> catalog.hasFunction(review.getPositionKey(), f.getFunctionKey())).toList();
        RiskLevel highest = reviewed.stream().map(ComplianceFinding::getRiskLevel).max(Comparator.naturalOrder()).orElse(RiskLevel.NONE);

        List<RiskItem> prioritized = reviewed.stream()
                .sorted(Comparator.comparing(ComplianceFinding::getRiskLevel).reversed())
                .map(f -> new RiskItem(id, review.getTitle(), functionTitle(review.getPositionKey(), f.getFunctionKey()),
                        f.getRiskLevel(), f.getFinding()))
                .toList();

        List<String> conflicts = new ArrayList<>();
        boolean anyHigh = highest == RiskLevel.HIGH;
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

        String recommendation;
        if (reviewed.isEmpty()) {
            recommendation = "CLARIFY";
        } else if (anyHigh) {
            recommendation = "REJECT";
        } else if (reviewed.size() < SPECIALIST_COUNT || highest == RiskLevel.MEDIUM) {
            recommendation = "CLARIFY";
        } else {
            recommendation = "APPROVE";
        }
        String summary = executiveSummary(review, recommendation, reviewed.size(), highest, conflicts.size());
        return new DecisionView(id, recommendation, reviewed.size(), SPECIALIST_COUNT, highest, anyHigh, conflicts,
                prioritized, summary, DISCLAIMER);
    }

    private String executiveSummary(ComplianceReview review, String recommendation, int reviewed, RiskLevel highest, int conflicts) {
        String head = switch (recommendation) {
            case "APPROVE" -> "Recommend approval: all " + SPECIALIST_COUNT + " specialist reviews are complete and show only low risk.";
            case "REJECT" -> "Recommend rejecting as written, or asking for changes to the high-risk clauses before accepting.";
            default -> reviewed == 0
                    ? "Recommend requesting clarification: no specialist findings are recorded yet."
                    : "Recommend requesting clarification: " + (reviewed < SPECIALIST_COUNT
                    ? (SPECIALIST_COUNT - reviewed) + " specialist review(s) are still missing."
                    : "the highest risk is MEDIUM and needs an answer from the other party.");
        };
        return review.getTitle() + ". " + head + " Highest risk: " + highest + "."
                + (conflicts > 0 ? " " + conflicts + " conflicting opinion(s) to resolve." : "")
                + (highest == RiskLevel.HIGH ? " Professional legal review is required before any decision." : "");
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
                r.getRecommendedAction(), out, r.getCreatedAt(), r.getUpdatedAt(), r.getCompletedAt());
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
