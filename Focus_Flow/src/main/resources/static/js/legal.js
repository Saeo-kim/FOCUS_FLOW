"use strict";

/*
 * Legal & Compliance page (administrators). Shows the Legal & Compliance Lead position, its five
 * specialist functions, document reviews, findings, the Decision Assistant's readiness state, and
 * the owner's human decision panel. The assistant never decides: only the owner records a decision.
 * Loaded after shell.js; admin.js routes "#/legal" here.
 */
window.Legal = (() => {
    const { call, DEPARTMENTS } = FF;
    const RISK_TAG = { HIGH: "red", MEDIUM: "amber", LOW: "blue", NONE: "gray" };
    const STATUS_LABEL = {
        OPEN: "Open", IN_REVIEW: "In review", NEEDS_CLARIFICATION: "Needs clarification",
        APPROVED: "Approved", REJECTED: "Rejected", CLOSED: "Closed",
    };
    const PRIORITY_TAG = { HIGH: "red", MEDIUM: "amber", LOW: "gray" };
    // Readiness labels. None of these is a decision.
    const STATE = {
        BLOCK: ["Blocked: human review required", "red", "A HIGH-risk finding needs a person to review it. Approval is not available."],
        INCOMPLETE: ["Incomplete: approval unavailable", "gray", "Not all five specialist reviews are recorded."],
        CLARIFICATION_REQUIRED: ["Clarification required", "amber", "A MEDIUM-risk issue needs an answer. Approval is not available yet."],
        APPROVAL_ELIGIBLE: ["Approval eligible (not an approval)", "green", "All five reviews are complete with LOW or NONE risk. Only the owner can decide."],
    };
    const OUTCOME = {
        APPROVE: "Approve", REJECT: "Reject", REQUEST_CHANGES: "Request changes", DEFER: "Defer (no decision yet)",
    };
    const state = { meta: null, position: null, selected: null, decisionFor: null, decision: null, history: [], busy: false, owner: null, me: null };

    const date = (s) => (s ? new Date(s + "T00:00:00").toLocaleDateString("en-US", { month: "short", day: "numeric", year: "numeric" }) : "—");
    const when = (s) => (s ? new Date(s).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) : "—");
    const stat = (n, label) => h("div", { class: "stat" }, h("div", { class: "stat-number" }, n), h("div", { class: "stat-label" }, label));
    const riskOptions = (selected) => state.meta.riskLevels.map(r => h("option", { value: r, selected: r === selected ? true : null }, r.charAt(0) + r.slice(1).toLowerCase()));

    function positionCard(p) {
        return h("section", { class: "card legal-position" },
            h("div", { class: "legal-head" },
                h("div", null,
                    h("div", { class: "muted small" }, DEPARTMENTS[p.department] || p.department),
                    h("h2", null, p.title),
                    h("div", { class: "legal-korean" }, p.koreanTitle)),
                h("span", { class: "tag " + (p.status === "VACANT" ? "amber" : "green") }, p.status === "VACANT" ? "Vacant" : "Filled")),
            h("p", { class: "legal-mission" }, p.mission),
            h("h3", { class: "ai-subhead" }, "Specialist responsibilities"),
            h("div", { class: "legal-functions" }, p.functions.map((f, i) => h("div", { class: "legal-function" },
                h("span", { class: "legal-num" }, String(i + 1).padStart(2, "0")),
                h("div", null, h("strong", null, f.title), h("div", { class: "muted small" }, f.description))))),
            h("p", { class: "muted small" }, "No one is assigned yet. The owner assigns this position; FOCUS FLOW does not fill it automatically."));
    }

    /** Who holds final decision authority. Administrators can see this; only the Owner can decide. */
    function ownerCard() {
        const o = state.owner;
        const me = state.me;
        const isMeAdmin = me && me.role === "ADMIN" && me.kind !== "AI";
        const head = o && o.userId
            ? h("p", { class: "ai-summary" }, h("strong", null, o.userName), " is the Owner", o.appointedAt ? ` since ${when(o.appointedAt)}` : "", ".")
            : h("p", { class: "ai-summary" }, "The Owner is not appointed. No final decision can be recorded until someone is appointed.");
        const controls = [];
        if (o && o.youAreOwner) {
            controls.push(h("p", { class: "muted small" }, "You are the Owner. You alone record final decisions here."));
            controls.push(h("button", { class: "btn", type: "button", onclick: async () => {
                if (!window.confirm("Step down as Owner? The position becomes unassigned and no one can record decisions until someone is appointed.")) return;
                try { await call("/api/admin/legal/owner/revoke", { method: "POST" }); toast("You stepped down as Owner."); refresh(); }
                catch (error) { toast(error.message, true); }
            } }, "Step down as Owner"));
        } else if (!o || !o.userId) {
            if (isMeAdmin) {
                controls.push(h("p", { class: "muted small" }, "Appointing the Owner is an explicit act recorded with your name. Administrator access alone does not make anyone the Owner."));
                controls.push(h("button", { class: "btn primary", type: "button", onclick: async () => {
                    if (!window.confirm("Appoint yourself as the Owner of legal decisions? This is recorded with your name.")) return;
                    try { await call("/api/admin/legal/owner", { method: "PUT", body: { userId: me.id } }); toast("You are now the Owner."); refresh(); }
                    catch (error) { toast(error.message, true); }
                } }, icon("check", 16), "Appoint myself as Owner"));
            }
        } else {
            controls.push(h("p", { class: "muted small" }, "Only the current Owner can appoint a successor or step down."));
        }
        return h("section", { class: "card legal-owner" },
            h("div", { class: "section-heading" }, h("h2", null, "Owner of legal decisions"),
                h("span", { class: "muted small" }, "Final decisions belong to the Owner alone")),
            head, ...controls);
    }

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
        const openRow = (r) => { state.selected = r.id; refresh(); };
        const row = (r) => h("tr", {
            class: "legal-row" + (state.selected === r.id ? " selected" : ""), tabindex: 0,
            onclick: () => openRow(r),
            onkeydown: e => { if (e.key === "Enter") openRow(r); },
        },
            h("td", null, h("strong", null, r.title)),
            h("td", null, r.categoryLabel),
            h("td", null, h("span", { class: "tag " + PRIORITY_TAG[r.priority] }, r.priority)),
            h("td", null, r.deadline ? h("span", { class: "tag " + (r.overdue ? "red" : "gray") }, (r.overdue ? "Overdue · " : "") + date(r.deadline)) : h("span", { class: "muted" }, "—")),
            h("td", null, STATUS_LABEL[r.status] || r.status),
            h("td", null, r.issueResolved ? h("span", { class: "tag green" }, "Resolved") : h("span", { class: "tag gray" }, "Open issue")));
        const head = h("thead", null, h("tr", null, ["Document", "Category", "Priority", "Deadline", "Status", "Issue"].map(c => h("th", { scope: "col" }, c))));
        return h("div", { class: "table-wrap" }, h("table", { class: "ai-table legal-table" }, head, h("tbody", null, reviews.map(row))));
    }

    function assistantPanel(d) {
        const [label, tone, help] = STATE[d.state] || [d.state, "gray", ""];
        const tags = h("div", { class: "row tight" },
            h("span", { class: "tag " + tone }, label),
            h("span", { class: "tag gray" }, `${d.specialistsReviewed} of ${d.specialistsTotal} reviewed`),
            h("span", { class: "tag " + RISK_TAG[d.highestRisk] }, `Highest risk: ${d.highestRisk}`));
        const conflicts = d.conflicts.length
            ? h("div", null, h("strong", { class: "small" }, "Conflicting opinions"), h("ul", { class: "ai-list" }, d.conflicts.map(c => h("li", null, c))))
            : null;
        const riskItems = d.prioritizedRisks.map(x => {
            const tag = h("span", { class: "tag " + RISK_TAG[x.riskLevel] }, x.riskLevel);
            const name = h("strong", null, x.functionTitle);
            return h("li", null, tag, " ", name, x.finding ? " — " + x.finding : "");
        });
        const risks = d.prioritizedRisks.length
            ? h("div", null, h("strong", { class: "small" }, "Risks, most serious first"), h("ul", { class: "ai-list" }, riskItems))
            : null;
        return h("div", { class: "legal-decision-body" }, tags,
            h("p", { class: "muted small" }, help),
            h("p", { class: "ai-summary" }, d.executiveSummary),
            conflicts, risks,
            h("p", { class: "muted small" }, d.disclaimer));
    }

    function historyList(items) {
        if (!items.length) return h("p", { class: "muted small" }, "No final decision recorded yet.");
        return h("ol", { class: "ai-list legal-history" }, items.map(x => h("li", null,
            h("strong", null, OUTCOME[x.outcome] || x.outcome), " by ", x.decidedByName, " · ", when(x.decidedAt),
            h("div", { class: "muted small" }, "Assistant said: " + (STATE[x.assistantState]?.[0] || x.assistantState)),
            h("div", { class: "legal-reason" }, x.reasoning))));
    }

    function humanPanel(review, decision) {
        const outcome = h("select", { "aria-label": "Final decision" },
            Object.entries(OUTCOME).map(([v, l]) => h("option", {
                value: v,
                disabled: v === "APPROVE" && !decision.approvalAvailable ? true : null,
            }, v === "APPROVE" && !decision.approvalAvailable ? `${l} (unavailable: ${decision.state.toLowerCase().replaceAll("_", " ")})` : l)));
        const reasoning = h("textarea", { rows: 3, maxlength: 2000, "aria-label": "Reasoning", placeholder: "Why you decided this. At least 10 characters. Kept with the record." });
        const record = h("button", { class: "btn primary", type: "button", onclick: async () => {
            if (reasoning.value.trim().length < 10) { toast("Write your reasoning (at least 10 characters).", true); reasoning.focus(); return; }
            if (!window.confirm(`Record "${OUTCOME[outcome.value]}" for "${review.title}"? This is your decision and is kept on record.`)) return;
            try {
                await call(`/api/admin/legal/reviews/${review.id}/human-decisions`, { method: "POST", body: { outcome: outcome.value, reasoning: reasoning.value.trim() } });
                toast("Decision recorded.");
                refresh();
            } catch (error) { toast(error.message, true); }
        } }, icon("check", 16), "Record my decision");
        return h("section", { class: "legal-human" },
            h("h3", { class: "ai-subhead" }, "Your final decision"),
            h("p", { class: "muted small" }, "The assistant only recommends. Nothing is approved, rejected or signed until you record it here."),
            h("label", { class: "field" }, "Decision", outcome),
            h("label", { class: "field" }, "Reasoning", reasoning),
            h("div", { class: "row" }, record),
            h("h3", { class: "ai-subhead" }, "Decision record"),
            historyList(state.history));
    }

    function issuePanel(review) {
        const resolved = h("input", { type: "checkbox", id: "legal-issue-resolved", checked: review.issueResolved ? true : null });
        const note = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Issue note", placeholder: "What was done, or what is still open" }, review.issueNote || "");
        const isOwner = state.owner && state.owner.youAreOwner;
        if (!isOwner) {
            return h("section", { class: "legal-issue" },
                h("h3", { class: "ai-subhead" }, "Issue status"),
                h("p", { class: "muted small" }, review.issueResolved
                    ? `Resolved${review.issueResolvedByName ? " by " + review.issueResolvedByName : ""}.`
                    : "Open. Only the Owner can mark the issue resolved."));
        }
        const save = h("button", { class: "btn", type: "button", onclick: async () => {
            try {
                await call(`/api/admin/legal/reviews/${review.id}/issue`, { method: "PUT", body: { resolved: resolved.checked, note: note.value.trim() || null } });
                toast(resolved.checked ? "Issue marked resolved." : "Issue kept open.");
                refresh();
            } catch (error) { toast(error.message, true); }
        } }, icon("check", 16), "Save issue status");
        return h("section", { class: "legal-issue" },
            h("h3", { class: "ai-subhead" }, "Issue status"),
            h("label", { class: "field inline-field" }, resolved, " The issue behind this review is resolved"),
            review.issueResolvedByName ? h("p", { class: "muted small" }, `Last marked resolved by ${review.issueResolvedByName} · ${when(review.issueResolvedAt)}`) : null,
            h("label", { class: "field" }, "Note", note),
            h("div", { class: "row" }, save));
    }

    function detail(review) {
        const byKey = Object.fromEntries((review.findings || []).map(f => [f.functionKey, f]));
        const editors = (state.position?.functions || []).map(fn => {
            const existing = byKey[fn.key] || {};
            const risk = h("select", { "aria-label": `Risk for ${fn.title}` }, riskOptions(existing.riskLevel || "NONE"));
            const finding = h("textarea", { rows: 2, maxlength: 4000, "aria-label": `Finding by ${fn.title}` }, existing.finding || "");
            const rec = h("textarea", { rows: 2, maxlength: 4000, "aria-label": `Recommendation by ${fn.title}` }, existing.recommendation || "");
            return { key: fn.key, risk, finding, rec, node: h("div", { class: "legal-finding" },
                h("div", { class: "row between" }, h("strong", null, fn.title), h("label", { class: "field inline-field" }, "Risk", risk)),
                h("label", { class: "field" }, "Finding", finding),
                h("label", { class: "field" }, "Recommendation", rec)) };
        });
        const status = h("select", { "aria-label": "Review status" }, Object.entries(STATUS_LABEL).map(([v, l]) => h("option", { value: v, selected: v === review.status ? true : null }, l)));
        const findingsSummary = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Findings summary" }, review.findingsSummary || "");
        const action = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Recommended action" }, review.recommendedAction || "");
        const saveDraft = h("button", { class: "btn", type: "button", onclick: async () => {
            try {
                await call(`/api/admin/legal/reviews/${review.id}/findings`, { method: "PUT", body: editors.map(e => ({
                    functionKey: e.key, riskLevel: e.risk.value, finding: e.finding.value.trim() || null, recommendation: e.rec.value.trim() || null,
                })) });
                await call(`/api/admin/legal/reviews/${review.id}`, { method: "PUT", body: {
                    title: review.title, category: review.category, documentRef: review.documentRef, description: review.description,
                    priority: review.priority, deadline: review.deadline, status: status.value,
                    findingsSummary: findingsSummary.value.trim() || null, recommendedAction: action.value.trim() || null,
                } });
                toast("Review saved.");
                refresh();
            } catch (error) { toast(error.message, true); }
        } }, icon("check", 16), "Save findings and status");

        return h("section", { class: "card legal-detail" },
            h("div", { class: "section-heading" }, h("h2", null, review.title), h("span", { class: "tag " + PRIORITY_TAG[review.priority] }, review.priority)),
            h("div", { class: "muted small" }, review.categoryLabel + (review.documentRef ? " · " + review.documentRef : "")),
            review.description && h("p", { class: "legal-desc" }, review.description),
            h("h2", { class: "ai-subhead" }, "Decision Assistant (recommendation only)"),
            state.decision && state.decisionFor === review.id ? assistantPanel(state.decision) : h("p", { class: "muted small" }, "Loading…"),
            h("h3", { class: "ai-subhead" }, "Specialist findings"),
            editors.map(e => e.node),
            h("label", { class: "field" }, "Findings summary", findingsSummary),
            h("label", { class: "field" }, "Recommended action", action),
            h("label", { class: "field" }, "Review status", status),
            h("div", { class: "row" }, saveDraft),
            issuePanel(review),
            state.decision && state.decisionFor === review.id
                ? (state.owner && state.owner.youAreOwner
                    ? humanPanel(review, state.decision)
                    : h("section", { class: "legal-human" }, h("h3", { class: "ai-subhead" }, "Final decision"),
                        h("p", { class: "muted small" }, "Only the appointed Owner can record a final decision. You can review the assistant's findings above.")))
                : null);
    }

    async function loadSelected(review) {
        if (!review) return;
        if (state.decisionFor === review.id) return;
        state.decisionFor = review.id;
        state.decision = await call(`/api/admin/legal/reviews/${review.id}/decision`);
        state.history = state.owner && state.owner.youAreOwner
            ? await call(`/api/admin/legal/reviews/${review.id}/human-decisions`)
            : [];
    }

    async function view(params = new URLSearchParams()) {
        if (!state.meta) state.meta = await call("/api/admin/legal/meta");
        state.me = await FF.ready();
        state.owner = await call("/api/admin/legal/owner");
        const [positions, reviews] = await Promise.all([call("/api/admin/legal/positions"), call("/api/admin/legal/reviews")]);
        state.position = positions.find(p => p.key === "legal-compliance-lead") || positions[0] || null;
        if (params.get("review")) state.selected = Number(params.get("review"));
        const selected = reviews.find(r => r.id === state.selected) || null;
        if (!selected) { state.decisionFor = null; state.decision = null; state.history = []; }
        await loadSelected(selected);
        const p = state.position;
        return h("div", { class: "stack" },
            p ? positionCard(p) : h("div", { class: "card empty" }, "The Legal & Compliance position is not registered yet."),
            ownerCard(),
            p && h("section", { class: "stats legal-stats" },
                stat(p.pendingReviews, "Pending reviews"),
                stat(p.highPriorityRisks, "High-priority risks"),
                stat(p.completedReviews, "Completed reviews")),
            newReviewCard(),
            h("section", { class: "card" },
                h("div", { class: "section-heading" }, h("h2", null, "Document reviews"),
                    h("span", { class: "muted small" }, `${reviews.length} recorded · closed by status, never deleted`)),
                reviewList(reviews)),
            selected ? detail(selected) : null);
    }

    return { view };
})();
