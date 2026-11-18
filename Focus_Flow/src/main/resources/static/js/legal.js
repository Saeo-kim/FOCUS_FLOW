"use strict";

/*
 * Legal & Compliance page (administrators): the Legal & Compliance Lead position, its five
 * specialist functions, the document reviews, specialist findings, and the Decision Assistant.
 * Everything goes through /api/admin/legal. Loaded after shell.js; admin.js routes "#/legal" here.
 */
window.Legal = (() => {
    const { call, DEPARTMENTS } = FF;
    const RISK_TAG = { HIGH: "red", MEDIUM: "amber", LOW: "blue", NONE: "gray" };
    const STATUS_LABEL = {
        OPEN: "Open", IN_REVIEW: "In review", NEEDS_CLARIFICATION: "Needs clarification",
        APPROVED: "Approved", REJECTED: "Rejected", CLOSED: "Closed",
    };
    const PRIORITY_TAG = { HIGH: "red", MEDIUM: "amber", LOW: "gray" };
    const DECISION = {
        APPROVE: ["Recommend approval", "green"],
        REJECT: ["Recommend rejection", "red"],
        CLARIFY: ["Request clarification", "amber"],
    };
    const state = { meta: null, selected: null, decision: null, busy: false };

    const date = (s) => (s ? new Date(s + "T00:00:00").toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : "—");
    const when = (s) => (s ? new Date(s).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) : "—");
    const riskOptions = (selected) => state.meta.riskLevels.map(r => h("option", { value: r, selected: r === selected ? true : null }, r.charAt(0) + r.slice(1).toLowerCase()));

    function positionCard(p) {
        return h("section", { class: "card legal-position" },
            h("div", { class: "legal-head" },
                h("div", null,
                    h("div", { class: "muted small" }, DEPARTMENTS[p.department] || p.department),
                    h("h2", null, p.title),
                    h("div", { class: "legal-korean" }, p.koreanTitle)),
                h("div", { class: "row tight" },
                    h("span", { class: "tag " + (p.status === "VACANT" ? "amber" : "green") }, p.status === "VACANT" ? "Vacant" : "Filled"))),
            h("p", { class: "legal-mission" }, p.mission),
            h("h3", { class: "ai-subhead" }, "Specialist responsibilities"),
            h("div", { class: "legal-functions" }, p.functions.map((f, i) => h("div", { class: "legal-function" },
                h("span", { class: "legal-num" }, String(i + 1).padStart(2, "0")),
                h("div", null, h("strong", null, f.title), h("div", { class: "muted small" }, f.description))))),
            h("p", { class: "muted small" }, "No one is assigned yet. The owner assigns this position; FOCUS FLOW does not fill it automatically."));
    }

    function stats(p) {
        return h("section", { class: "stats legal-stats" },
            stat(p.pendingReviews, "Pending reviews"),
            stat(p.highPriorityRisks, "High-priority risks"),
            stat(p.completedReviews, "Completed reviews"));
    }
    const stat = (n, label) => h("div", { class: "stat" }, h("div", { class: "stat-number" }, n), h("div", { class: "stat-label" }, label));

    function newReviewCard() {
        const category = h("select", { "aria-label": "Review category" }, state.meta.categories.map(c => h("option", { value: c.key }, c.label)));
        const title = h("input", { type: "text", maxlength: 200, required: true, "aria-label": "Review title", placeholder: "e.g. Cloud hosting service terms" });
        const ref = h("input", { type: "text", maxlength: 500, "aria-label": "Document reference", placeholder: "Link or where the document is kept" });
        const desc = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Description", placeholder: "What the document is and why it needs review" });
        const priority = h("select", { "aria-label": "Priority" }, ["HIGH", "MEDIUM", "LOW"].map(v => h("option", { value: v, selected: v === "MEDIUM" ? true : null }, v)));
        const deadline = h("input", { type: "date", "aria-label": "Deadline" });
        const save = h("button", { class: "btn primary", type: "button", onclick: async () => {
            if (!title.value.trim()) { toast("Give the review a title.", true); return; }
            if (state.busy) return;
            state.busy = true;
            try {
                const created = await call("/api/admin/legal/reviews", { method: "POST", body: {
                    category: category.value, title: title.value.trim(), documentRef: ref.value.trim() || null,
                    description: desc.value.trim() || null, priority: priority.value, deadline: deadline.value || null,
                } });
                toast("Review opened.");
                state.selected = created.id;
                refresh();
            } catch (error) { toast(error.message, true); }
            finally { state.busy = false; }
        } }, icon("plus", 16), "Open review");
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "New document review"),
                h("span", { class: "muted small" }, "Record the document before the organization accepts it")),
            h("div", { class: "ai-ask-grid" },
                h("label", { class: "field wide" }, "Document", title),
                h("label", { class: "field" }, "Category", category),
                h("label", { class: "field" }, "Priority", priority),
                h("label", { class: "field" }, "Reference", ref),
                h("label", { class: "field" }, "Deadline", deadline),
                h("label", { class: "field wide" }, "Description", desc)),
            h("div", { class: "row" }, save));
    }

    function reviewList(reviews) {
        if (!reviews.length) return h("div", { class: "empty" }, "No reviews yet. Open the first one above.");
        const openRow = (r) => { state.selected = r.id; state.decision = null; refresh(); };
        const row = (r) => h("tr", {
            class: "legal-row" + (state.selected === r.id ? " selected" : ""),
            tabindex: 0,
            onclick: () => openRow(r),
            onkeydown: e => { if (e.key === "Enter") openRow(r); },
        },
            h("td", null, h("strong", null, r.title)),
            h("td", null, r.categoryLabel),
            h("td", null, h("span", { class: "tag " + PRIORITY_TAG[r.priority] }, r.priority)),
            h("td", null, r.deadline ? h("span", { class: "tag " + (r.overdue ? "red" : "gray") }, (r.overdue ? "Overdue · " : "") + date(r.deadline)) : h("span", { class: "muted" }, "—")),
            h("td", null, STATUS_LABEL[r.status] || r.status));
        const head = h("thead", null, h("tr", null, ["Document", "Category", "Priority", "Deadline", "Status"].map(c => h("th", { scope: "col" }, c))));
        const body = h("tbody", null, reviews.map(row));
        return h("div", { class: "table-wrap" }, h("table", { class: "ai-table legal-table" }, head, body));
    }

    function detail(review) {
        const status = h("select", { "aria-label": "Review status" }, Object.entries(STATUS_LABEL).map(([v, l]) => h("option", { value: v, selected: v === review.status ? true : null }, l)));
        const findingsSummary = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Findings summary" }, review.findingsSummary || "");
        const action = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Recommended action" }, review.recommendedAction || "");
        const rows = state.meta && review.findings;
        const byKey = Object.fromEntries((review.findings || []).map(f => [f.functionKey, f]));
        const functionEditors = (state.position?.functions || []).map(fn => {
            const existing = byKey[fn.key] || {};
            const risk = h("select", { "aria-label": `Risk for ${fn.title}` }, riskOptions(existing.riskLevel || "NONE"));
            const finding = h("textarea", { rows: 2, maxlength: 4000, "aria-label": `Finding by ${fn.title}` }, existing.finding || "");
            const rec = h("textarea", { rows: 2, maxlength: 4000, "aria-label": `Recommendation by ${fn.title}` }, existing.recommendation || "");
            return { key: fn.key, node: h("div", { class: "legal-finding" },
                h("div", { class: "row between" }, h("strong", null, fn.title), h("label", { class: "field inline-field" }, "Risk", risk)),
                h("label", { class: "field" }, "Finding", finding),
                h("label", { class: "field" }, "Recommendation", rec)), risk, finding, rec };
        });
        const decisionBox = h("div", { class: "legal-decision" }, state.decision ? decisionView(state.decision) : h("p", { class: "muted small" }, "Run the Decision Assistant once the specialist reviews are recorded."));

        return h("section", { class: "card legal-detail" },
            h("div", { class: "section-heading" },
                h("h2", null, review.title),
                h("span", { class: "tag " + PRIORITY_TAG[review.priority] }, review.priority)),
            h("div", { class: "muted small" }, review.categoryLabel + (review.documentRef ? " · " : "") + (review.documentRef || "")),
            review.description && h("p", { class: "legal-desc" }, review.description),
            h("h3", { class: "ai-subhead" }, "Specialist findings"),
            functionEditors.map(e => e.node),
            h("label", { class: "field" }, "Findings summary", findingsSummary),
            h("label", { class: "field" }, "Recommended action", action),
            h("label", { class: "field" }, "Status", status),
            h("div", { class: "row" },
                h("button", { class: "btn", type: "button", onclick: async () => {
                    try {
                        await call(`/api/admin/legal/reviews/${review.id}/findings`, { method: "PUT", body: functionEditors.map(e => ({
                            functionKey: e.key, riskLevel: e.risk.value, finding: e.finding.value.trim() || null, recommendation: e.rec.value.trim() || null,
                        })) });
                        await call(`/api/admin/legal/reviews/${review.id}`, { method: "PUT", body: {
                            title: review.title, category: review.category, documentRef: review.documentRef, description: review.description,
                            priority: review.priority, deadline: review.deadline, status: status.value,
                            findingsSummary: findingsSummary.value.trim() || null, recommendedAction: action.value.trim() || null,
                        } });
                        toast("Review saved.");
                        state.decision = null;
                        refresh();
                    } catch (error) { toast(error.message, true); }
                } }, icon("check", 16), "Save review"),
                h("button", { class: "btn primary", type: "button", onclick: async () => {
                    try {
                        state.decision = await call(`/api/admin/legal/reviews/${review.id}/decision`);
                        refresh();
                    } catch (error) { toast(error.message, true); }
                } }, icon("activity", 16), "Run Decision Assistant")),
            decisionBox);
    }

    function decisionView(d) {
        const [label, tone] = DECISION[d.recommendation] || [d.recommendation, "gray"];
        const badges = h("div", { class: "row tight" },
            h("span", { class: "tag " + tone }, label),
            h("span", { class: "tag gray" }, `${d.specialistsReviewed} of ${d.specialistsTotal} reviewed`),
            h("span", { class: "tag " + RISK_TAG[d.highestRisk] }, `Highest risk: ${d.highestRisk}`));
        const summary = h("p", { class: "ai-summary" }, d.executiveSummary);
        const high = d.professionalReviewRequired
            ? h("p", { class: "warn small" }, "High risk found: have a qualified lawyer review this before any decision.")
            : null;
        const conflicts = d.conflicts.length
            ? h("div", null, h("strong", { class: "small" }, "Conflicting opinions"),
                h("ul", { class: "ai-list" }, d.conflicts.map(c => h("li", null, c))))
            : null;
        const riskItems = d.prioritizedRisks.map(x => {
            const tag = h("span", { class: "tag " + RISK_TAG[x.riskLevel] }, x.riskLevel);
            const name = h("strong", null, x.functionTitle);
            return h("li", null, tag, " ", name, x.finding ? " — " + x.finding : "");
        });
        const risks = d.prioritizedRisks.length
            ? h("div", null, h("strong", { class: "small" }, "Risks, most serious first"), h("ul", { class: "ai-list" }, riskItems))
            : null;
        const disclaimer = h("p", { class: "muted small" }, d.disclaimer);
        return h("div", { class: "legal-decision-body" }, badges, summary, high, conflicts, risks, disclaimer);
    }

    async function view(params = new URLSearchParams()) {
        if (!state.meta) state.meta = await call("/api/admin/legal/meta");
        const [positions, reviews] = await Promise.all([
            call("/api/admin/legal/positions"),
            call("/api/admin/legal/reviews"),
        ]);
        state.position = positions.find(p => p.key === "legal-compliance-lead") || positions[0];
        if (params.get("review")) state.selected = Number(params.get("review"));
        const selected = reviews.find(r => r.id === state.selected) || null;
        if (selected && selected.id !== state.decisionFor) { state.decision = null; state.decisionFor = selected.id; }
        return h("div", { class: "stack" },
            state.position ? positionCard(state.position) : h("div", { class: "card empty" }, "The Legal & Compliance position is not registered yet."),
            state.position && stats(state.position),
            newReviewCard(),
            h("section", { class: "card" },
                h("div", { class: "section-heading" }, h("h2", null, "Document reviews"),
                    h("span", { class: "muted small" }, `${reviews.length} recorded · reviews are closed by status, never deleted`)),
                reviewList(reviews)),
            selected ? detail(selected) : null);
    }

    return { view };
})();
