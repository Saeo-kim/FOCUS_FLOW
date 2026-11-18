"use strict";

/*
 * AI Governance & Information Security page (administrators). Every action calls /api/admin/security,
 * which enforces the rules in the server. Buttons shown only to the Owner are also refused by the server
 * for anyone else. Loaded after shell.js; admin.js routes "#/security" here.
 */
window.Security = (() => {
    const { call, DEPARTMENTS } = FF;
    const LEVELS = ["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"];
    const CATEGORIES = ["GENERAL", "FINANCIAL", "CONTRACT", "CUSTOMER_PERSONAL", "EMPLOYEE_PERSONAL", "BUSINESS_PLAN", "CREDENTIAL", "SOURCE_CODE", "INTELLECTUAL_PROPERTY"];
    const SEVERITIES = ["LOW", "MEDIUM", "HIGH", "CRITICAL"];
    const DECISION_TAG = { ALLOWED: "green", ALLOWED_REDACTED: "blue", PENDING_APPROVAL: "amber", BLOCKED: "red" };
    const SEVERITY_TAG = { LOW: "gray", MEDIUM: "amber", HIGH: "red", CRITICAL: "red" };
    const state = { owner: null, me: null, people: [], items: [], seats: [] };

    const when = (s) => (s ? new Date(s).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) : "—");
    const opts = (values, selected) => values.map(v => h("option", { value: v, selected: v === selected ? true : null }, v.replaceAll("_", " ").toLowerCase()));
    const stat = (n, label) => h("div", { class: "stat" }, h("div", { class: "stat-number" }, String(n)), h("div", { class: "stat-label" }, label));
    const btn = (label, onclick, primary = false, disabled = false) => h("button", { class: "btn" + (primary ? " primary" : ""), type: "button", disabled: disabled ? true : null, onclick }, label);
    async function act(fn, message) {
        try { await fn(); if (message) toast(message); refresh(); }
        catch (error) { toast(error.message, true); }
    }

    function summaryCard(s) {
        const integrity = s.logIntegrity.intact
            ? h("span", { class: "tag green" }, `Audit log intact · ${s.logIntegrity.rows} records`)
            : h("span", { class: "tag red" }, `Audit log BROKEN at #${s.logIntegrity.brokenAtSeq}`);
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Security status"), integrity),
            h("section", { class: "stats sec-stats" },
                stat(s.openViolations, "AI access violations (30 days)"),
                stat(s.pendingApprovals, "Pending approvals"),
                stat(s.openCriticalIncidents, "Critical incidents open"),
                stat(s.openFindings, "Open audit findings"),
                stat(s.pendingComplianceRisks, "Compliance risks (legal)")),
            h("p", { class: "muted small" }, "Security expenses: " + s.securityExpenses),
            Object.keys(s.accountabilityByDepartment).length
                ? h("div", null, h("strong", { class: "small" }, "Accountability by department: "),
                    Object.entries(s.accountabilityByDepartment).map(([d, n]) => h("span", { class: "tag gray" }, `${d}: ${n}`, " ")))
                : h("p", { class: "muted small" }, "No open incidents or findings are assigned to a department."));
    }

    function positionsCard(positions) {
        const holders = state.people.filter(p => p.role === "ADMIN" && p.kind !== "AI" && p.active);
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Executive security positions"),
                h("span", { class: "muted small" }, state.owner && state.owner.youAreOwner ? "You assign these as Owner" : "Only the Owner assigns these")),
            h("div", { class: "sec-grid" }, positions.map(p => {
                const select = h("select", { "aria-label": `Holder of ${p.title}`, disabled: state.owner && state.owner.youAreOwner ? null : true },
                    h("option", { value: "" }, "— vacant —"),
                    holders.map(u => h("option", { value: u.id, selected: u.id === p.holderId ? true : null }, u.displayName)));
                return h("article", { class: "ai-card" },
                    h("strong", null, p.title),
                    h("div", { class: "muted small" }, p.mission),
                    h("div", { class: "row tight" }, h("span", { class: "tag " + (p.status === "VACANT" ? "amber" : "green") }, p.status === "VACANT" ? "Vacant" : "Filled: " + p.holderName)),
                    state.owner && state.owner.youAreOwner ? h("div", { class: "row" }, select,
                        btn("Save", () => act(() => call(`/api/admin/security/positions/${p.key}`, { method: "PUT", body: { userId: select.value ? Number(select.value) : null } }), "Position updated.")))
                        : null);
            })));
    }

    function itemsCard() {
        const title = h("input", { type: "text", maxlength: 200, "aria-label": "Information title", placeholder: "e.g. Q4 pricing model" });
        const category = h("select", { "aria-label": "Category" }, opts(CATEGORIES, "GENERAL"));
        const level = h("select", { "aria-label": "Classification level" }, opts(LEVELS, "INTERNAL"));
        const content = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Content", placeholder: "Content. Leave empty for RESTRICTED: it is never stored." });
        const rows = state.items.map(i => h("tr", null,
            h("td", null, h("strong", null, i.title), h("div", { class: "muted small" }, i.category.replaceAll("_", " ").toLowerCase())),
            h("td", null, h("span", { class: "tag " + ({ PUBLIC: "green", INTERNAL: "blue", CONFIDENTIAL: "amber", RESTRICTED: "red" })[i.level] }, i.level)),
            h("td", null, i.level === "RESTRICTED" ? h("span", { class: "muted small" }, "Content not stored") : (i.content || "—")),
            h("td", null, h("select", { "aria-label": "Change level" }, opts(LEVELS, i.level),
                h("option", { value: "" }, "")), btn("Set", (e) => {
                    const sel = e.target.closest("td").querySelector("select");
                    return act(() => call(`/api/admin/security/items/${i.id}/level`, { method: "PUT", body: { level: sel.value } }), "Classification updated.");
                }))));
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Information classification"),
                h("span", { class: "muted small" }, "PUBLIC · INTERNAL · CONFIDENTIAL · RESTRICTED")),
            state.items.length ? h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
                h("thead", null, h("tr", null, ["Information", "Level", "Content", "Change"].map(c => h("th", { scope: "col" }, c)))),
                h("tbody", null, rows))) : h("div", { class: "empty" }, "No classified information yet."),
            h("h3", { class: "ai-subhead" }, "Classify new information"),
            h("div", { class: "sec-form" },
                h("label", { class: "field" }, "Title", title),
                h("label", { class: "field" }, "Category", category),
                h("label", { class: "field" }, "Level", level),
                h("label", { class: "field wide" }, "Content", content)),
            h("div", { class: "row" }, btn("Classify", () => act(() => call("/api/admin/security/items", { method: "POST", body: {
                title: title.value.trim(), category: category.value, level: level.value, content: content.value.trim() || null,
            } }), "Information classified."), true)));
    }

    function gatewayCard() {
        const actor = h("select", { "aria-label": "Requesting AI seat or person" }, state.seats.map(u => h("option", { value: u.id }, `${u.displayName}${u.kind === "AI" ? " (AI seat)" : ""}`)));
        const item = h("select", { "aria-label": "Information" }, state.items.map(i => h("option", { value: i.id }, `${i.title} · ${i.level}`)));
        const dest = h("select", { "aria-label": "Destination" }, opts(["INTERNAL_AI_TOOL", "EXTERNAL_AI_PROVIDER"], "INTERNAL_AI_TOOL"));
        const purpose = h("input", { type: "text", maxlength: 300, "aria-label": "Purpose", placeholder: "Why this access is needed" });
        const content = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Outbound content to check", placeholder: "Optional: text to check before it leaves (defaults to the stored content)" });
        const result = h("div", { class: "sec-result", "aria-live": "polite" }, h("p", { class: "muted small" }, "Run a check to see the decision."));
        const run = btn("Check access", async () => {
            try {
                const r = await call("/api/admin/security/evaluate", { method: "POST", body: {
                    actorUserId: Number(actor.value), itemId: Number(item.value), destination: dest.value,
                    purpose: purpose.value.trim() || null, content: content.value.trim() || null,
                } });
                result.replaceChildren(
                    h("div", { class: "row tight" }, h("span", { class: "tag " + DECISION_TAG[r.decision] }, r.decision.replaceAll("_", " ")),
                        h("span", { class: "muted small" }, `Log #${r.logSeq}`)),
                    h("p", { class: "small" }, r.reason),
                    r.findings.length ? h("p", { class: "muted small" }, "Found: " + r.findings.join(", ")) : null,
                    r.releasedContent != null
                        ? h("div", null, h("strong", { class: "small" }, "Released (after redaction if needed)"), h("pre", { class: "sec-pre" }, r.releasedContent))
                        : h("p", { class: "small warn" }, "Nothing was released."));
                refresh();
            } catch (error) { toast(error.message, true); }
        }, true);
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "AI access check"),
                h("span", { class: "muted small" }, "Every decision is written to the audit log")),
            h("div", { class: "sec-form" },
                h("label", { class: "field" }, "Who or which AI seat", actor),
                h("label", { class: "field" }, "Information", item),
                h("label", { class: "field" }, "Destination", dest),
                h("label", { class: "field" }, "Purpose", purpose),
                h("label", { class: "field wide" }, "Content to check", content)),
            h("div", { class: "row" }, run), result);
    }

    function requestsCard(requests) {
        const owner = state.owner && state.owner.youAreOwner;
        const pending = requests.filter(r => r.status === "PENDING");
        const rows = requests.slice(0, 50).map(r => {
            const reason = h("input", { type: "text", maxlength: 500, "aria-label": "Decision reason", placeholder: "Reason (10+ characters)" });
            return h("tr", null,
                h("td", null, h("strong", null, r.actorName), h("div", { class: "muted small" }, `for "${r.itemTitle}" (${r.itemLevel})`)),
                h("td", null, r.purpose || "—"),
                h("td", null, h("span", { class: "tag " + (r.status === "PENDING" ? "amber" : r.status === "APPROVED" ? "green" : "gray") }, r.status)),
                h("td", null, r.status === "PENDING" && owner
                    ? h("div", { class: "row tight" }, reason,
                        btn("Approve", () => act(() => call(`/api/admin/security/requests/${r.id}/decision`, { method: "POST", body: { approve: true, reason: reason.value } }), "Approved for one use."), true),
                        btn("Deny", () => act(() => call(`/api/admin/security/requests/${r.id}/decision`, { method: "POST", body: { approve: false, reason: reason.value } }), "Denied."))) 
                    : (r.decidedBy ? `${r.decidedBy}: ${r.decisionReason || ""}` : "—")));
        });
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Approval requests"),
                h("span", { class: "muted small" }, `${pending.length} waiting for the Owner`)),
            requests.length ? h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
                h("thead", null, h("tr", null, ["Request", "Purpose", "Status", "Decision"].map(c => h("th", { scope: "col" }, c)))),
                h("tbody", null, rows))) : h("div", { class: "empty" }, "No approval requests."));
    }

    function grantsCard(grants) {
        const owner = state.owner && state.owner.youAreOwner;
        const actor = h("select", { "aria-label": "Person or AI seat" }, state.seats.map(u => h("option", { value: u.id }, `${u.displayName}${u.kind === "AI" ? " (AI seat)" : ""}`)));
        const level = h("select", { "aria-label": "Highest level" }, opts(LEVELS.slice(0, 3), "INTERNAL"));
        const just = h("input", { type: "text", maxlength: 1000, "aria-label": "Justification", placeholder: "Business justification (required for RESTRICTED)" });
        const expires = h("input", { type: "date", "aria-label": "Expires on" });
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Access grants"),
                h("span", { class: "muted small" }, owner ? "Only you can grant or revoke" : "Read only: only the Owner grants access")),
            grants.length ? h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
                h("thead", null, h("tr", null, ["Who", "Up to", "Justification", "Status", ""].map(c => h("th", { scope: "col" }, c)))),
                h("tbody", null, grants.map(g => h("tr", null,
                    h("td", null, g.actorName), h("td", null, g.maxLevel), h("td", null, g.justification || "—"),
                    h("td", null, h("span", { class: "tag " + (g.active ? "green" : "gray") }, g.active ? "Active" : "Ended")),
                    h("td", null, owner && g.active ? btn("Revoke", () => act(() => call(`/api/admin/security/grants/${g.id}/revoke`, { method: "POST" }), "Grant revoked.")) : ""))))))
                : h("div", { class: "empty" }, "No grants yet."),
            owner ? h("div", null,
                h("h3", { class: "ai-subhead" }, "Grant access"),
                h("div", { class: "sec-form" },
                    h("label", { class: "field" }, "Who", actor), h("label", { class: "field" }, "Up to", level),
                    h("label", { class: "field" }, "Expires", expires), h("label", { class: "field wide" }, "Justification", just)),
                h("p", { class: "muted small" }, "AI seats cannot be granted RESTRICTED. Confidential access for an AI seat still needs a request approved for each use."),
                h("div", { class: "row" }, btn("Grant", () => act(() => call("/api/admin/security/grants", { method: "POST", body: {
                    actorUserId: Number(actor.value), maxLevel: level.value, justification: just.value.trim() || null, expiresOn: expires.value || null,
                } }), "Access granted."), true))) : null);
    }

    function logCard(log, check) {
        const verify = btn("Verify chain", async () => {
            const r = await call("/api/admin/security/log/verify");
            toast(r.intact ? `Chain intact: ${r.rows} records.` : `Chain BROKEN at #${r.brokenAtSeq}.`, !r.intact);
        });
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "AI activity log"),
                h("span", { class: "muted small" }, "Append-only and hash-chained · the database refuses edits")),
            h("div", { class: "row" }, verify),
            log.length ? h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
                h("thead", null, h("tr", null, ["#", "When", "Who", "Information", "Destination", "Decision", "Reason"].map(c => h("th", { scope: "col" }, c)))),
                h("tbody", null, log.map(l => h("tr", null,
                    h("td", null, String(l.seq)), h("td", null, when(l.createdAt)),
                    h("td", null, `${l.actorName} (${l.actorKind === "AI" ? "AI" : "person"})`, h("div", { class: "muted small" }, "asked by " + (l.requestedBy || "—"))),
                    h("td", null, `${l.itemTitle} · ${l.itemLevel}`), h("td", null, l.destination.replaceAll("_", " ").toLowerCase()),
                    h("td", null, h("span", { class: "tag " + DECISION_TAG[l.decision] }, l.decision.replaceAll("_", " "))),
                    h("td", { class: "small" }, l.reason)))))) : h("div", { class: "empty" }, "No AI access decisions yet."));
    }

    function incidentRow(i, owner) {
        const resolve = i.status !== "RESOLVED"
            ? btn("Resolve", () => act(() => call(`/api/admin/security/incidents/${i.id}/status`, { method: "PUT", body: { status: "RESOLVED" } }), "Incident resolved."))
            : null;
        const ack = owner && i.escalatedToOwner && !i.ownerAcknowledgedBy
            ? btn("Acknowledge", () => act(() => call(`/api/admin/security/incidents/${i.id}/acknowledge`, { method: "POST" }), "Acknowledged."))
            : null;
        const escalation = !i.escalatedToOwner ? "—"
            : i.ownerAcknowledgedBy ? h("span", { class: "tag green" }, "Acknowledged by Owner")
            : h("span", { class: "tag red" }, "Escalated: awaiting Owner");
        return h("tr", null,
            h("td", null, h("strong", null, i.title), h("div", { class: "muted small" }, i.evidence)),
            h("td", null, h("span", { class: "tag " + SEVERITY_TAG[i.severity] }, i.severity)),
            h("td", null, `${i.responsibleDepartment} · ${i.responsibleRole}`),
            h("td", null, i.discoveredOn),
            h("td", null, i.status.replaceAll("_", " ").toLowerCase(), resolve ? h("div", null, resolve) : null),
            h("td", null, escalation, ack ? h("div", null, ack) : null));
    }

    function incidentsCard(incidents) {
        const owner = state.owner && state.owner.youAreOwner;
        const title = h("input", { type: "text", maxlength: 200, "aria-label": "Incident title" });
        const sev = h("select", { "aria-label": "Severity" }, opts(SEVERITIES, "MEDIUM"));
        const dept = h("input", { type: "text", maxlength: 80, "aria-label": "Responsible department", value: "Information Security & Audit" });
        const role = h("input", { type: "text", maxlength: 120, "aria-label": "Responsible person or AI role", placeholder: "e.g. CISO, or a named person" });
        const evidence = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Evidence", placeholder: "Evidence (required)" });
        const action = h("input", { type: "text", maxlength: 4000, "aria-label": "Recommended corrective action", placeholder: "Corrective action" });
        const table = incidents.length ? h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
            h("thead", null, h("tr", null, ["Incident", "Severity", "Responsible", "Discovered", "Status", "Owner"].map(c => h("th", { scope: "col" }, c)))),
            h("tbody", null, incidents.map(i => incidentRow(i, owner))))) : h("div", { class: "empty" }, "No incidents recorded.");
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Security incidents"),
                h("span", { class: "muted small" }, "Critical and high incidents escalate to the Owner")),
            table,
            h("h3", { class: "ai-subhead" }, "Record an incident"),
            h("div", { class: "sec-form" },
                h("label", { class: "field wide" }, "Title", title), h("label", { class: "field" }, "Severity", sev),
                h("label", { class: "field" }, "Responsible department", dept), h("label", { class: "field" }, "Responsible person or AI role", role),
                h("label", { class: "field wide" }, "Evidence", evidence), h("label", { class: "field wide" }, "Corrective action", action)),
            h("div", { class: "row" }, btn("Record incident", () => act(() => call("/api/admin/security/incidents", { method: "POST", body: {
                title: title.value.trim(), severity: sev.value, department: dept.value.trim(), role: role.value.trim(),
                evidence: evidence.value.trim(), recommendedAction: action.value.trim() || null,
            } }), "Incident recorded."), true)));
    }

    function findingRow(f, owner) {
        const closed = f.status === "VERIFIED_CLOSED";
        const actions = closed ? null : h("div", { class: "row tight" },
            btn("In remediation", () => act(() => call(`/api/admin/security/findings/${f.id}/status`, { method: "PUT", body: { status: "IN_REMEDIATION" } }), "Set to in remediation.")),
            btn("Close", () => act(() => call(`/api/admin/security/findings/${f.id}/status`, { method: "PUT", body: { status: "VERIFIED_CLOSED" } }), "Finding closed.")));
        const ack = owner && f.escalatedToOwner && !f.ownerAcknowledgedBy
            ? btn("Acknowledge", () => act(() => call(`/api/admin/security/findings/${f.id}/acknowledge`, { method: "POST" }), "Acknowledged."))
            : null;
        const escalation = !f.escalatedToOwner ? "—" : (f.ownerAcknowledgedBy ? "Acknowledged" : "Escalated");
        return h("tr", null,
            h("td", null, h("strong", null, f.title), h("div", { class: "muted small" }, `${f.subject || "—"} · ${f.responsibleRole}`)),
            h("td", null, h("span", { class: "tag " + SEVERITY_TAG[f.severity] }, f.severity)),
            h("td", null, f.auditorName, h("div", { class: "muted small" }, f.source.replaceAll("_", " ").toLowerCase())),
            h("td", null, f.status.replaceAll("_", " ").toLowerCase(), actions ? h("div", null, actions) : null),
            h("td", { class: "mono small" }, (f.evidenceHash || "").slice(0, 12) + "…"),
            h("td", null, escalation, ack ? h("div", null, ack) : null));
    }

    function findingsCard(findings, positions) {
        const owner = state.owner && state.owner.youAreOwner;
        const source = h("select", { "aria-label": "Auditor" }, opts(["IT_SECURITY_AUDITOR", "EXTERNAL_SECURITY_AUDITOR"], "IT_SECURITY_AUDITOR"));
        const subject = h("select", { "aria-label": "Subject position" }, h("option", { value: "" }, "— none —"), positions.map(p => h("option", { value: p.key }, p.title)));
        const title = h("input", { type: "text", maxlength: 200, "aria-label": "Finding title" });
        const sev = h("select", { "aria-label": "Severity" }, opts(SEVERITIES, "MEDIUM"));
        const role = h("input", { type: "text", maxlength: 120, "aria-label": "Responsible role" });
        const evidence = h("textarea", { rows: 2, maxlength: 4000, "aria-label": "Evidence", placeholder: "Evidence the finding rests on (fixed once recorded)" });
        const action = h("input", { type: "text", maxlength: 4000, "aria-label": "Recommended action" });
        const table = findings.length ? h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
            h("thead", null, h("tr", null, ["Finding", "Severity", "Auditor", "Status", "Evidence hash", "Owner"].map(c => h("th", { scope: "col" }, c)))),
            h("tbody", null, findings.map(f => findingRow(f, owner))))) : h("div", { class: "empty" }, "No audit findings yet.");
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Independent audit findings"),
                h("span", { class: "muted small" }, "Evidence cannot be edited after recording")),
            table,
            h("h3", { class: "ai-subhead" }, "Record a finding (auditor positions only)"),
            h("div", { class: "sec-form" },
                h("label", { class: "field" }, "Auditor", source), h("label", { class: "field" }, "Subject", subject),
                h("label", { class: "field wide" }, "Title", title), h("label", { class: "field" }, "Severity", sev),
                h("label", { class: "field" }, "Responsible role", role),
                h("label", { class: "field wide" }, "Evidence", evidence), h("label", { class: "field wide" }, "Recommended action", action)),
            h("div", { class: "row" }, btn("Record finding", () => act(() => call("/api/admin/security/findings", { method: "POST", body: {
                source: source.value, subjectKey: subject.value || null, title: title.value.trim(), severity: sev.value,
                department: "Information Security & Audit", role: role.value.trim(),
                evidence: evidence.value.trim(), recommendedAction: action.value.trim() || null,
            } }), "Finding recorded."), true)));
    }

    function policiesCard(policies) {
        const owner = state.owner && state.owner.youAreOwner;
        const key = h("input", { type: "text", maxlength: 60, "aria-label": "Policy key", placeholder: "e.g. ai-data-handling" });
        const title = h("input", { type: "text", maxlength: 200, "aria-label": "Policy title" });
        const body = h("textarea", { rows: 3, maxlength: 4000, "aria-label": "Policy text", placeholder: "The rule, in plain language" });
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Security policies"),
                h("span", { class: "muted small" }, "Drafted by the CISO; only the Owner approves; authors cannot approve their own")),
            policies.length ? h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
                h("thead", null, h("tr", null, ["Policy", "Version", "Status", "Author", "Approved by", ""].map(c => h("th", { scope: "col" }, c)))),
                h("tbody", null, policies.map(p => h("tr", null,
                    h("td", null, h("strong", null, p.title), h("div", { class: "muted small" }, p.body)),
                    h("td", null, "v" + p.version), h("td", null, h("span", { class: "tag " + (p.status === "APPROVED" ? "green" : p.status === "DRAFT" ? "amber" : "gray") }, p.status)),
                    h("td", null, p.authoredBy), h("td", null, p.approvedBy || "—"),
                    h("td", null, owner && p.status === "DRAFT" ? btn("Approve", () => act(() => call(`/api/admin/security/policies/${p.id}/approve`, { method: "POST" }), "Policy approved."), true) : ""))))))
                : h("div", { class: "empty" }, "No policies yet."),
            h("h3", { class: "ai-subhead" }, "Draft a policy"),
            h("div", { class: "sec-form" }, h("label", { class: "field" }, "Key", key), h("label", { class: "field" }, "Title", title), h("label", { class: "field wide" }, "Text", body)),
            h("div", { class: "row" }, btn("Save draft", () => act(() => call("/api/admin/security/policies", { method: "POST", body: { key: key.value.trim(), title: title.value.trim(), body: body.value.trim() } }), "Draft saved for Owner approval."), true)));
    }

    /** Shown to administrators who hold neither the Owner nor the CISO role: the security data is not theirs to see. */
    function notAuthorized(owner) {
        return h("section", { class: "card" },
            h("h2", null, "AI Governance & Information Security"),
            h("p", { class: "muted" }, "Security controls are visible to the Owner and the CISO only. Administrator access alone does not grant them."),
            h("p", { class: "small" }, owner && owner.userId
                ? `The current Owner is ${owner.userName}.`
                : "No Owner is appointed yet. Appointing the Owner is an explicit act on the Legal & Compliance page."));
    }

    async function view() {
        let owner;
        try {
            owner = await call("/api/admin/legal/owner");
        } catch (error) {
            return h("div", { class: "card empty" }, error.message);
        }
        let data;
        try {
            data = await loadAll();
        } catch (error) {
            if (error.status === 403) return notAuthorized(owner);
            return h("div", { class: "card empty" }, error.message);
        }
        return render(owner, data);
    }

    function loadAll() {
        return Promise.all([
            FF.ready(), call("/api/admin/legal/owner"), call("/api/admin/security/summary"), call("/api/admin/security/positions"),
            call("/api/admin/users"), call("/api/admin/security/items"), call("/api/admin/security/requests"),
            call("/api/admin/security/grants"), call("/api/admin/security/log?limit=100"), call("/api/admin/security/incidents"),
            call("/api/admin/security/findings"), call("/api/admin/security/policies"),
        ]).then(([me, owner, summary, positions, people, items, requests, grants, log, incidents, findings, policies]) =>
            ({ me, owner, summary, positions, people, items, requests, grants, log, incidents, findings, policies }));
    }

    function render(owner, d) {
        const { me, summary, positions, people, items, requests, grants, log, incidents, findings, policies } = d;
        state.me = me; state.owner = owner; state.people = people; state.items = items;
        state.seats = people.filter(u => u.active);
        return h("div", { class: "stack sec-page" },
            h("section", { class: "card" }, h("h2", null, "AI Governance & Information Security"),
                h("p", { class: "muted" }, "AI use is allowed by default. Access to company information is not: every request is classified, checked, redacted where needed, and logged. The Owner holds final authority over business risk.")),
            summaryCard(summary),
            positionsCard(positions),
            itemsCard(),
            gatewayCard(),
            requestsCard(requests),
            grantsCard(grants),
            logCard(log),
            incidentsCard(incidents),
            findingsCard(findings, positions),
            policiesCard(policies));
    }

    return { view };
})();
