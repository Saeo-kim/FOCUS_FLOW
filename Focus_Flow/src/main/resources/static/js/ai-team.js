"use strict";

/*
 * AI Team page (administrators): the AI teammates and their responsibilities, whether Owner's
 * Office is connected, the work sent to them with each report, and a form for new requests.
 * Everything goes through the admin API. Loaded after shell.js; admin.js routes "#/ai-team" here.
 */
window.AiTeam = (() => {
    const { call, DEPARTMENTS } = FF;

    const KIND_LABELS = {
        monthly_report: "Monthly financial report",
        financial_analysis: "Business profit & loss analysis",
        cash_flow: "Cash flow & runway check",
        bookkeeping: "Bookkeeping status",
        tax_summary: "Tax preparation summary",
        portfolio: "Investment portfolio status",
        planning: "Financial plan & recommendations",
        risk_audit: "Financial risk review",
    };
    const STATUS = {
        QUEUED: ["Waiting to send", "amber"],
        SENT: ["Accepted", "blue"],
        IN_PROGRESS: ["In progress", "blue"],
        COMPLETED: ["Report ready", "green"],
        FAILED: ["Failed", "red"],
    };
    const SEVERITY = { high: ["High", "red"], medium: ["Medium", "amber"], low: ["Low", "gray"], info: ["Info", "gray"] };

    const state = { busy: false };

    const when = (s) => (s ? new Date(s).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) : "—");

    function money(value, unit) {
        if (value === null || value === undefined) return h("span", { class: "muted" }, "No data");
        if (typeof value === "string") return value || "—";
        if (unit === "%") return `${value}%`;
        if (unit === "원") return `${Math.round(value).toLocaleString("ko-KR")}원`;
        return `${Number(value).toLocaleString("en-US")}${unit ? " " + unit : ""}`;
    }

    function connection(member) {
        if (!member.active) return ["Deactivated", "gray"];
        const s = member.status || {};
        if (s.reachable) return ["Connected", "green"];
        if (s.configured) return ["Not reachable", "red"];
        return ["Not connected", "amber"];
    }

    function teamCard(member) {
        const [label, tone] = connection(member);
        const s = member.status || {};
        const note = !s.configured
            ? "Set OWNERS_OFFICE_TOKEN to connect Owner's Office. Until then nothing is sent to it."
            : s.reachable
                ? `${s.openRequests} request${s.openRequests === 1 ? "" : "s"} still open.`
                : "Start Owner's Office on this PC. Requests wait and are sent automatically.";
        return h("article", { class: "ai-card" },
            h("div", { class: "ai-card-head" },
                FF.avatar(member, 44),
                h("div", { class: "ai-card-title" },
                    h("strong", null, member.displayName),
                    h("div", { class: "muted small" }, member.title),
                    h("div", { class: "row tight" },
                        h("span", { class: "tag ai" }, "AI seat"),
                        h("span", { class: "tag gray" }, DEPARTMENTS[member.department] || member.department),
                        h("span", { class: "tag " + tone }, label)))),
            h("div", { class: "muted small" }, note),
            h("h3", { class: "ai-subhead" }, "Responsibilities"),
            h("ul", { class: "ai-duties" }, member.responsibilities.map(r => h("li", null, r))));
    }

    function askCard(team, projects) {
        const cfo = team.find(m => m.key === "cfo");
        const kind = h("select", { "aria-label": "Report type" },
            h("option", { value: "" }, "Let the CFO choose from the request"),
            Object.entries(KIND_LABELS).map(([value, text]) => h("option", { value }, text)));
        const title = h("input", { type: "text", maxlength: 200, required: true, "aria-label": "Request title", placeholder: "e.g. Cash runway for the next quarter" });
        const details = h("textarea", { rows: 3, maxlength: 4000, "aria-label": "Details", placeholder: "Period, context or questions for the CFO (optional)" });
        const project = h("select", { "aria-label": "Project for the task" },
            h("option", { value: "" }, "No project"),
            projects.map(p => h("option", { value: p.id }, p.name)));
        const send = h("button", { class: "btn primary", type: "button", disabled: cfo && cfo.active ? null : true }, icon("right", 16), "Send to CFO");
        send.addEventListener("click", async () => {
            if (!title.value.trim()) {
                toast("Give the request a title.", true);
                title.focus();
                return;
            }
            if (state.busy) return;
            state.busy = true;
            send.disabled = true;
            try {
                const created = await call("/api/admin/ai/requests", {
                    method: "POST",
                    body: {
                        agentKey: "cfo",
                        kind: kind.value || null,
                        title: title.value.trim(),
                        instructions: details.value.trim(),
                        projectId: project.value ? Number(project.value) : null,
                    },
                });
                toast("Sent to the CFO. Its report will appear in the work history.");
                location.hash = `#/ai-team?request=${created.id}`;
            } catch (error) {
                toast(error.message, true);
            } finally {
                state.busy = false;
                send.disabled = false;
            }
        });
        return h("section", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Ask the CFO"),
                h("span", { class: "muted small" }, "Creates a task assigned to the CFO")),
            h("div", { class: "ai-ask-grid" },
                h("label", { class: "field wide" }, "Request", title),
                h("label", { class: "field" }, "Report type", kind),
                h("label", { class: "field" }, "Project", project),
                h("label", { class: "field wide" }, "Details", details)),
            h("div", { class: "row" }, send),
            h("p", { class: "muted small" }, "You can also assign any task to the CFO, or mention @CFO in a task comment. The CFO works from Owner's Office data. Results are reports to review, not tax or investment advice."));
    }

    function table(headers, rows, numericCols = []) {
        return h("div", { class: "table-wrap" }, h("table", { class: "ai-table" },
            h("thead", null, h("tr", null, headers.map(x => h("th", { scope: "col" }, x)))),
            h("tbody", null, rows.map(cells => h("tr", null, cells.map((c, i) => h("td", { class: numericCols.includes(i) ? "num" : null }, c)))))));
    }

    function reportBody(r) {
        const parts = [];
        if (r.taskId) parts.push(h("p", { class: "small" }, "Task: ", h("a", { href: `#/task?id=${r.taskId}` }, r.taskTitle || "Open task")));
        if (r.status === "FAILED") parts.push(h("p", { class: "small warn" }, `The CFO could not finish this. ${r.lastError || ""}`));
        else if (r.status === "QUEUED") parts.push(h("p", { class: "small warn" }, `Not sent yet (${r.attempts} attempt${r.attempts === 1 ? "" : "s"}). It is retried automatically. ${r.lastError || ""}`));
        const res = r.result;
        if (!res) {
            parts.push(h("p", { class: "muted small" }, "The report appears here when the CFO finishes."));
            return parts;
        }
        if (res.includes_demo) parts.push(h("p", { class: "small warn" }, "This report includes DEMO data from Owner's Office. Turn demo data off there for real figures."));
        if (r.summary) parts.push(h("p", { class: "ai-summary" }, r.summary));
        if (res.figures?.length) {
            parts.push(table(["Measure", "Value", "Basis"], res.figures.map(f => [
                f.label, money(f.value, f.unit), h("span", { class: "muted" }, f.basis || ""),
            ]), [1]));
        }
        if (res.findings?.length) {
            parts.push(h("div", null, h("strong", { class: "small" }, "Findings"),
                h("ul", { class: "ai-list" }, res.findings.map(f => h("li", null,
                    h("span", { class: "tag " + (SEVERITY[f.severity]?.[1] || "gray") }, SEVERITY[f.severity]?.[0] || f.severity), " ",
                    h("strong", null, f.title),
                    f.detail && h("div", { class: "muted" }, f.detail),
                    f.recommendation && h("div", null, "→ " + f.recommendation))))));
        }
        if (res.recommendations?.length) {
            parts.push(h("div", null, h("strong", { class: "small" }, "Recommendations"),
                h("ul", { class: "ai-list" }, res.recommendations.map(x => h("li", null, x)))));
        }
        if (res.limitations?.length) {
            parts.push(h("div", null, h("strong", { class: "small" }, "Limits of this report"),
                h("ul", { class: "ai-list muted" }, res.limitations.map(x => h("li", null, x)))));
        }
        if (res.breakdown?.length) {
            parts.push(table(["Business", "Revenue", "Business cost", "Profit"], res.breakdown.map(b => [
                b.name, money(b.revenue, "원"), money(b.cost, "원"), money(b.profit, "원"),
            ]), [1, 2, 3]));
        }
        return parts;
    }

    function requestRow(r, focusId) {
        const [label, tone] = STATUS[r.status] || [r.status, "gray"];
        return h("details", { class: "ai-req", id: `req-${r.id}`, open: focusId === r.id ? true : null },
            h("summary", { class: "ai-req-row" },
                h("span", { class: "ai-req-when muted small" }, when(r.createdAt)),
                h("span", { class: "ai-req-title" }, r.title),
                h("span", { class: "ai-req-kind muted small" }, KIND_LABELS[r.kind] || "Chosen by the CFO"),
                h("span", { class: "tag " + tone }, label)),
            h("div", { class: "ai-req-body" }, reportBody(r)));
    }

    async function view(params = new URLSearchParams()) {
        const focus = Number(params.get("request")) || null;
        const [team, requests, projects] = await Promise.all([
            call("/api/admin/ai/team"),
            call("/api/admin/ai/requests?agent=cfo"),
            call("/api/projects"),
        ]);
        const sync = h("button", {
            class: "btn", type: "button",
            onclick: async () => {
                try {
                    const result = await call("/api/admin/ai/sync", { method: "POST" });
                    toast(`Synced with Owner's Office: ${result.sent} sent, ${result.updated} updated.`);
                    refresh();
                } catch (error) {
                    toast(error.message, true);
                }
            },
        }, icon("activity", 16), "Sync now");

        return h("div", { class: "stack" },
            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    h("div", null,
                        h("h2", null, "AI Team"),
                        h("div", { class: "muted" }, "AI teammates with defined roles. They take assigned work and report back to the task.")),
                    sync)),
            h("div", { class: "ai-grid" }, team.map(teamCard)),
            askCard(team, projects),
            h("section", { class: "card" },
                h("div", { class: "section-heading" }, h("h2", null, "Work history"),
                    h("span", { class: "muted small" }, `${requests.length} request${requests.length === 1 ? "" : "s"}`)),
                requests.length
                    ? h("div", { class: "ai-history" }, requests.map(r => requestRow(r, focus)))
                    : h("div", { class: "empty" }, "No work sent to the CFO yet.")));
    }

    return { view };
})();
