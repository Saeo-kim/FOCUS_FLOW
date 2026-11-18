"use strict";

/* Project management pages. Shares the helpers and dialogs defined in app.js. */

const WORK_STATUS = {
    NOT_STARTED: ["Not Started", "gray"],
    IN_PROGRESS: ["In Progress", "blue"],
    BLOCKED: ["Blocked", "red"],
    REVIEW: ["Review", "amber"],
    COMPLETED: ["Completed", "green"],
};

const CHECK_STATUS = { NOT_RUN: ["Not Run", "gray"], PASSED: ["Passed", "green"], FAILED: ["Failed", "red"] };

const DEADLINE_CATEGORY = {
    MILESTONE: "Milestone", DELIVERABLE: "Deliverable", WEEKLY_REPORT: "Weekly Report", FINAL_REPORT: "Final Report",
    JAR: "JAR", AI_LOG: "AI Code Log", DEPLOYMENT: "Deployment", PRESENTATION: "Presentation",
};

const EVIDENCE_TYPE = {
    REPOSITORY: "GitHub Repository", COMMIT: "GitHub Commit", PULL_REQUEST: "Pull Request", CODE_PATH: "Code Path",
    SCREENSHOT: "Screenshot", TEST_RESULT: "Test Result", BUILD_RESULT: "Build Result", DEPLOY_URL: "Deploy URL",
    DOCUMENT: "Document", OTHER: "Other",
};

const options = map => Object.entries(map).map(([v, l]) => [v, Array.isArray(l) ? l[0] : l]);
const PRIORITY_OPTIONS = [["HIGH", "High"], ["MEDIUM", "Medium"], ["LOW", "Low"]];

const statusTag = (map, key) => h("span", { class: "tag " + map[key][1] }, map[key][0]);
const yesNo = (ok, yes, no) => h("span", { class: "tag " + (ok ? "green" : "gray") }, ok ? yes : no);

function daysUntil(date) {
    return Math.round((parseDate(date) - parseDate(todayIso())) / 86400000);
}

const dayLabel = date => { const n = daysUntil(date); return n === 0 ? "Today" : n > 0 ? `D-${n}` : `D+${-n}`; };

/** Multi-line text where only http(s) lines become links, so a stored value can never run script. */
function rich(value) {
    return String(value).split("\n").map(line => /^https?:\/\/\S+$/.test(line.trim())
        ? h("div", null, h("a", { href: line.trim(), target: "_blank", rel: "noopener noreferrer" }, line.trim()))
        : h("div", null, line || " "));
}

function detailList(rows) {
    return h("dl", { class: "details" }, rows.filter(r => r.group || (r[1] != null && r[1] !== "")).map(r => r.group
        ? h("div", { class: "detail-group" }, r.group)
        : h("div", { class: "detail-row" }, h("dt", null, r[0]), h("dd", null, r[1] instanceof Node ? r[1] : rich(r[1])))));
}

/* ---------- Record definitions: one dialog and one card builder serve every record type ---------- */

const text = (k, label, extra = {}) => ({ k, label, type: "text", ...extra });
const area = (k, label) => ({ k, label, type: "textarea", full: true });
const date = (k, label, extra = {}) => ({ k, label, type: "date", ...extra });
const pick = (k, label, opts) => ({ k, label, type: "select", opts });
const check = (k, label) => ({ k, label, type: "check" });
const taskField = { k: "taskId", label: "Related task", type: "task" };
const respField = { k: "responsibilityId", label: "Responsibility", type: "resp" };

const RECORDS = {
    phases: {
        path: "/api/phases", noun: "phase",
        fields: [text("name", "Phase name", { required: true }), date("startDate", "Start date", { required: true }), date("endDate", "End date", { required: true }), area("items", "Work items (one per line)")],
    },
    roles: {
        path: "/api/team-roles", noun: "team role",
        fields: [text("roleName", "Role", { required: true }), check("mine", "This is my role"), area("duties", "Duties")],
    },
    responsibilities: {
        path: "/api/responsibilities", noun: "responsibility",
        defaults: { priority: "MEDIUM", status: "NOT_STARTED" },
        fields: [text("title", "Title", { required: true, full: true }), area("description", "Description"), text("owner", "Owner"), pick("priority", "Importance", PRIORITY_OPTIONS), date("startDate", "Start date"), date("dueDate", "Deadline"), pick("status", "Status", options(WORK_STATUS)), area("deliverables", "Deliverables")],
    },
    deadlines: {
        path: "/api/deadlines", noun: "deadline",
        defaults: { category: "MILESTONE", scope: "PERSONAL" },
        fields: [text("title", "Title", { required: true, full: true }), pick("category", "Category", options(DEADLINE_CATEGORY)), pick("scope", "Scope", [["PERSONAL", "Personal"], ["TEAM", "Team"]]), date("dueDate", "Due date", { required: true }), text("owner", "Lead"), area("personalAction", "What I have to do"), area("note", "Note"), check("done", "Submitted / done")],
    },
    requirements: {
        path: "/api/requirements", noun: "requirement",
        fields: [text("title", "Requirement", { required: true, full: true }), area("detail", "Details"), text("relatedFeature", "Related feature"), respField, taskField, check("completed", "Completed"), check("tested", "Tested"), check("verified", "Verified by running it"), area("verificationResult", "Verification result"), area("evidenceNote", "Evidence")],
    },
    errors: {
        path: "/api/error-logs", noun: "error",
        defaults: () => ({ occurredOn: todayIso(), status: "OPEN" }),
        fields: [text("title", "Error title", { required: true, full: true }), date("occurredOn", "Date", { required: true }), taskField, pick("status", "Status", [["OPEN", "Open"], ["RESOLVED", "Resolved"]]), area("situation", "What happened"), area("cause", "Cause"), area("attempts", "What I tried"), area("solution", "Final solution"), area("changedFiles", "Changed files"), area("verification", "How it was verified"), area("result", "Final result"), area("links", "Links and screenshots (one per line)")],
    },
    ai: {
        path: "/api/ai-verifications", noun: "AI verification",
        defaults: { testStatus: "NOT_RUN", buildStatus: "NOT_RUN" },
        fields: [text("title", "Work title", { required: true, full: true }), text("aiTool", "AI tool"), taskField, area("requestText", "What was requested"), area("aiActions", "What the AI did"), area("changedFiles", "Changed files"), area("testItems", "Test items"), pick("testStatus", "Test status", options(CHECK_STATUS)), area("testResult", "Test result"), pick("buildStatus", "Build status", options(CHECK_STATUS)), area("buildResult", "Build result"), area("humanReview", "What I personally checked"), area("followUp", "Follow-up changes"), check("verified", "Verified"), check("approved", "Final approval")],
    },
    daily: {
        path: "/api/daily-reports", noun: "daily report",
        defaults: () => ({ reportDate: todayIso() }),
        fields: [date("reportDate", "Date", { required: true }), area("goal", "Today's goal"), area("actualWork", "What I actually did"), area("problemsSolved", "Problems solved"), area("incompleteReason", "Why anything is unfinished"), area("nextWork", "Next work"), area("evidenceNote", "Work evidence")],
    },
    evidence: {
        path: "/api/evidence", noun: "evidence",
        defaults: { type: "OTHER" },
        fields: [text("title", "Title", { required: true, full: true }), pick("type", "Type", options(EVIDENCE_TYPE)), taskField, text("location", "Link or file path", { required: true, full: true }), respField, area("note", "Note")],
    },
    steps: {
        path: "/api/steps", noun: "step", noProject: true,
        createPath: ctx => `/api/tasks/${ctx.taskId}/steps`,
        defaults: { status: "NOT_STARTED", progress: 0 },
        fields: [text("title", "Step title", { required: true, full: true }), area("description", "Details"), pick("status", "Status", options(WORK_STATUS)), { k: "progress", label: "Progress (%)", type: "number", min: 0, max: 100 }, date("startDate", "Start date"), date("completedDate", "Completed date"), area("result", "Work result"), area("errorNote", "Errors"), text("link", "Related link", { full: true }), area("memo", "Memo")],
    },
    templates: {
        path: "/api/workflow-templates", noun: "template", noProject: true,
        fields: [text("name", "Template name", { required: true, full: true }), area("description", "Description"), { k: "steps", label: "Steps (one per line)", type: "lines", full: true, required: true }],
    },
};

function fieldInput(field, value, ctx) {
    const required = field.required || undefined;
    switch (field.type) {
        case "textarea": return h("textarea", { value: value ?? "" });
        case "lines": return h("textarea", { rows: 9, required, value: (value || []).join("\n") });
        case "date": return h("input", { type: "date", required, value: value ?? "" });
        case "number": return h("input", { type: "number", min: field.min, max: field.max, value: value ?? "" });
        case "check": return h("input", { type: "checkbox", checked: Boolean(value) });
        case "select": return h("select", { value: value ?? field.opts[0][0] }, field.opts.map(([v, l]) => h("option", { value: v }, l)));
        case "task": return h("select", { value: value ?? "" }, h("option", { value: "" }, "None"), (ctx.tasks || []).map(t => h("option", { value: t.id }, t.title)));
        case "resp": return h("select", { value: value ?? "" }, h("option", { value: "" }, "None"), (ctx.responsibilities || []).map(r => h("option", { value: r.id }, r.title)));
        default: return h("input", { type: "text", required, value: value ?? "" });
    }
}

function fieldValue(field, input) {
    switch (field.type) {
        case "check": return input.checked;
        case "number": return input.value === "" ? null : Number(input.value);
        case "date": return input.value || null;
        case "task": case "resp": return input.value ? Number(input.value) : null;
        case "lines": return input.value.split("\n").map(s => s.trim()).filter(Boolean);
        default: return input.value;
    }
}

function bodyFrom(cfg, rec, ctx) {
    const body = cfg.noProject ? {} : { projectId: ctx.project?.id ?? rec.projectId };
    for (const field of cfg.fields) body[field.k] = rec[field.k] ?? null;
    return body;
}

function openRecordDialog(cfg, record, ctx, presets = {}) {
    const start = typeof cfg.defaults === "function" ? cfg.defaults() : (cfg.defaults || {});
    const values = record || { ...start, ...presets };
    const inputs = new Map();

    const fields = cfg.fields.map(field => {
        const input = fieldInput(field, values[field.k], ctx);
        inputs.set(field, input);
        return field.type === "check"
            ? h("label", { class: "field check-field" }, input, field.label)
            : h("label", { class: "field" + (field.full ? " full" : "") }, field.label, input);
    });

    openModal(h("form", {
        onsubmit: async event => {
            event.preventDefault();
            const body = cfg.noProject ? {} : { projectId: ctx.project.id };
            for (const [field, input] of inputs) body[field.k] = fieldValue(field, input);
            const path = record ? `${cfg.path}/${record.id}` : (cfg.createPath ? cfg.createPath(ctx) : cfg.path);
            const saved = await act(() => api(path, { method: record ? "PUT" : "POST", body }), `Saved ${cfg.noun}.`);
            if (saved) closeModal();
        },
    },
        h("h2", null, `${record ? "Edit" : "New"} ${cfg.noun}`),
        h("div", { class: "form-grid" }, fields),
        h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Save"))));
}

function deleteRecord(cfg, record, label) {
    if (confirm(`Delete "${label}"?`)) {
        act(() => api(`${cfg.path}/${record.id}`, { method: "DELETE" }), `Deleted ${cfg.noun}.`);
    }
}

function recordActions(cfg, rec, ctx, label) {
    return h("div", { class: "task-actions" },
        h("button", { class: "icon-btn", type: "button", "aria-label": `Edit ${cfg.noun}`, onclick: () => openRecordDialog(cfg, rec, ctx) }, icon("pencil", 16)),
        h("button", { class: "icon-btn danger", type: "button", "aria-label": `Delete ${cfg.noun}`, onclick: () => deleteRecord(cfg, rec, label) }, icon("trash", 16)));
}

const openRecords = new Set();

/** Collapsed by default so a long log stays scannable; stays open across re-renders. */
function recordCard(cfg, rec, ctx, { title, tags = [], rows = [], extra }) {
    const key = `${cfg.noun}-${rec.id}`;
    return h("details", {
        class: "rec", open: openRecords.has(key),
        ontoggle: event => { if (event.target.open) openRecords.add(key); else openRecords.delete(key); },
    },
        h("summary", null,
            h("div", { class: "rec-title" }, title),
            h("div", { class: "task-meta" }, tags)),
        h("div", { class: "rec-body" },
            detailList(rows),
            extra,
            h("div", { class: "rec-foot" }, recordActions(cfg, rec, ctx, title))));
}

const addButton = (label, onclick) => h("button", { class: "btn primary", type: "button", onclick }, icon("plus", 16), label);

function sectionHead(titleText, ...right) {
    return h("div", { class: "section-heading" }, h("h2", null, titleText), h("div", { class: "row" }, right));
}

const taskLinkRow = rec => rec.taskId ? h("a", { href: `#/task?id=${rec.taskId}` }, rec.taskTitle) : null;

/* ---------- Upcoming deadlines (Overview) ---------- */

async function upcomingDeadlines(tasks) {
    const [deadlines, responsibilities] = await Promise.all([api("/api/deadlines"), api("/api/responsibilities")]);
    const items = [
        ...deadlines.filter(d => !d.done).map(d => ({
            date: d.dueDate, title: d.title, personal: d.scope === "PERSONAL", href: `#/project?id=${d.projectId}&tab=deadlines`,
        })),
        ...responsibilities.filter(r => r.dueDate && r.status !== "COMPLETED").map(r => ({
            date: r.dueDate, title: r.title, personal: true, href: `#/project?id=${r.projectId}&tab=responsibilities`,
        })),
        ...tasks.filter(t => t.dueDate && !t.done).map(t => ({
            date: t.dueDate, title: t.title, personal: true, href: `#/task?id=${t.id}`,
        })),
    ];
    return items
        .filter(item => { const n = daysUntil(item.date); return n >= 0 && n <= 14; })
        .sort((a, b) => a.date.localeCompare(b.date) || Number(a.personal) - Number(b.personal))
        .slice(0, 8);
}

function upcomingDeadlinesCard(items) {
    return h("section", { class: "card block-gap" },
        h("div", { class: "section-heading" }, h("h2", null, "Upcoming Deadlines"), h("span", { class: "tag" }, "NEXT 14 DAYS")),
        items.length
            ? h("div", { class: "deadline-list" }, items.map(item => h("a", { class: "deadline-row", href: item.href },
                h("span", { class: "tag " + (daysUntil(item.date) <= 3 ? "red" : "gray") }, dayLabel(item.date)),
                h("span", { class: "deadline-title" }, item.title),
                h("span", { class: "muted deadline-date" }, fmtDate(item.date)),
                h("span", { class: "tag " + (item.personal ? "" : "teal") }, item.personal ? "Personal" : "Team"))))
            : h("div", { class: "empty" }, "Nothing due in the next two weeks."));
}

/* ---------- Task Details ---------- */

const taskDetailState = { lastId: null };

function saveStep(step, patch) {
    const body = { ...bodyFrom(RECORDS.steps, step, {}), ...patch };
    return act(() => api(`/api/steps/${step.id}`, { method: "PUT", body }));
}

function stepCard(step, index, total, ctx) {
    const cfg = RECORDS.steps;
    const rows = [
        ["Details", step.description], ["Started", step.startDate && fmtDate(step.startDate)],
        ["Completed", step.completedDate && fmtDate(step.completedDate)], ["Result", step.result],
        ["Errors", step.errorNote], ["Link", step.link], ["Memo", step.memo],
    ];
    const hasDetails = rows.some(r => r[1]);
    const statusSelect = h("select", {
        class: "step-status", "aria-label": `Status of ${step.title}`, value: step.status,
        onchange: event => saveStep(step, { status: event.target.value }),
    }, options(WORK_STATUS).map(([v, l]) => h("option", { value: v }, l)));

    return h("div", { class: "step" + (step.status === "COMPLETED" ? " done" : "") },
        h("div", { class: "step-main" },
            h("span", { class: "step-num" }, index + 1),
            h("div", { class: "task-info" },
                h("div", { class: "task-name" }, step.title),
                h("div", { class: "task-meta" },
                    statusTag(WORK_STATUS, step.status),
                    step.status !== "NOT_STARTED" && h("span", { class: "tag gray" }, `${step.progress}%`))),
            h("div", { class: "step-tools" },
                statusSelect,
                h("div", { class: "task-actions" },
                    h("button", { class: "icon-btn", type: "button", "aria-label": "Move step up", disabled: index === 0, onclick: () => act(() => api(`/api/steps/${step.id}/move`, { method: "POST", body: { direction: -1 } })) }, icon("up", 16)),
                    h("button", { class: "icon-btn", type: "button", "aria-label": "Move step down", disabled: index === total - 1, onclick: () => act(() => api(`/api/steps/${step.id}/move`, { method: "POST", body: { direction: 1 } })) }, icon("down", 16)),
                    h("button", { class: "icon-btn", type: "button", "aria-label": "Edit step", onclick: () => openRecordDialog(cfg, step, ctx) }, icon("pencil", 16)),
                    h("button", { class: "icon-btn danger", type: "button", "aria-label": "Delete step", onclick: () => deleteRecord(cfg, step, step.title) }, icon("trash", 16))))),
        hasDetails && detailList(rows));
}

async function viewTaskDetails(params) {
    const [tasks, templates] = await Promise.all([api("/api/tasks"), api("/api/workflow-templates")]);
    if (!tasks.length) return h("div", { class: "card empty" }, "Create a task first.");

    const wanted = Number(params.get("id")) || taskDetailState.lastId;
    const inOrder = sortTasks(tasks);
    const task = tasks.find(t => t.id === wanted) || inOrder.find(t => !t.done && t.projectId) || inOrder[0];
    taskDetailState.lastId = task.id;

    const steps = await api(`/api/tasks/${task.id}/steps`);
    const ctx = { taskId: task.id };
    const related = task.projectId ? await loadTaskRecords(task.projectId, task.id) : null;

    const stepTitle = h("input", { id: "stepTitle", type: "text", maxlength: 200, required: true, placeholder: "Add a work step...", "aria-label": "New work step" });
    const templateSelect = h("select", { "aria-label": "Workflow template" }, templates.map(t => h("option", { value: t.id }, t.name)));

    return h("div", { class: "stack" },
        h("label", { class: "field picker" }, "Task",
            h("select", { value: task.id, onchange: e => { location.hash = `#/task?id=${e.target.value}`; } },
                inOrder.map(t => h("option", { value: t.id }, (t.done ? "[Done] " : "") + t.title)))),

        h("div", { class: "card" },
            h("div", { class: "section-heading" },
                h("h2", { class: "detail-title" }, task.title),
                h("button", { class: "btn", type: "button", onclick: () => openTaskDialog(task) }, icon("pencil", 16), "Edit")),
            h("div", { class: "task-meta" },
                statusTag(WORK_STATUS, task.status),
                h("span", { class: "tag " + PRIORITY_TAG[task.priority] }, `${task.priority} PRIORITY`),
                task.dueDate && h("span", { class: "tag " + (isOverdue(task) ? "red" : "blue") }, `Due ${fmtDate(task.dueDate)}`),
                task.scheduledDate && h("span", { class: "tag gray" }, `Scheduled ${fmtDate(task.scheduledDate)}`),
                task.projectName && h("a", { class: "tag", href: `#/project?id=${task.projectId}` }, task.projectName),
                task.responsibilityTitle && h("span", { class: "tag teal" }, task.responsibilityTitle)),
            task.description && h("p", { class: "muted", style: "margin-top:12px;white-space:pre-wrap" }, task.description),
            h("div", { class: "progress-caption" },
                h("span", null, task.stepCount ? `${task.completedStepCount} of ${task.stepCount} steps completed` : "No work steps yet"),
                h("span", null, `${task.progress}%`)),
            progressBar(task.progress),
            h("label", { class: "field", style: "margin-top:14px;max-width:240px" }, "Task status",
                h("select", {
                    value: task.status, "aria-label": "Task status",
                    onchange: e => act(() => api(`/api/tasks/${task.id}/status`, { method: "PATCH", body: { status: e.target.value } })).then(ok => { if (!ok) refresh(); }),
                }, options(WORK_STATUS).map(([v, l]) => h("option", { value: v }, l))))),

        h("div", { class: "card" },
            h("div", { class: "section-heading" }, h("h2", null, "Work Sequence"),
                templates.length > 0 && h("div", { class: "row template-apply" }, templateSelect,
                    h("button", {
                        class: "btn", type: "button",
                        onclick: () => act(() => api(`/api/tasks/${task.id}/apply-template/${templateSelect.value}`, { method: "POST" }), "Template steps added."),
                    }, "Apply"))),
            steps.length
                ? h("div", { class: "task-list" }, steps.map((s, i) => stepCard(s, i, steps.length, ctx)))
                : h("div", { class: "empty" }, "Break this task into steps, or apply a template."),
            h("form", {
                class: "step-add",
                onsubmit: async event => {
                    event.preventDefault();
                    const title = stepTitle.value.trim();
                    if (!title) return;
                    const ok = await act(() => api(`/api/tasks/${task.id}/steps`, { method: "POST", body: { title, status: "NOT_STARTED" } }), "Step added.");
                    if (ok) document.getElementById("stepTitle")?.focus();
                },
            }, stepTitle, h("button", { class: "btn primary", type: "submit" }, icon("plus", 16), "Add Work Step"))),

        renderDiscussion(task.id, { base: location.pathname.startsWith("/member") ? "member" : "admin" }),
        related && relatedRecordsCard(task, related));
}

async function loadTaskRecords(projectId, taskId) {
    const q = `?projectId=${projectId}`;
    const [project, tasks, responsibilities, requirements, errors, ai, evidence] = await Promise.all([
        api(`/api/projects/${projectId}`), api("/api/tasks"), api(`/api/responsibilities${q}`),
        api(`/api/requirements${q}`), api(`/api/error-logs${q}`), api(`/api/ai-verifications${q}`), api(`/api/evidence${q}`),
    ]);
    const mine = list => list.filter(r => r.taskId === taskId);
    return {
        ctx: { project, tasks: tasks.filter(t => t.projectId === projectId), responsibilities },
        requirements: mine(requirements), errors: mine(errors), ai: mine(ai), evidence: mine(evidence),
    };
}

function relatedRecordsCard(task, data) {
    const { ctx } = data;
    const presets = { taskId: task.id };
    const cards = [
        ...data.requirements.map(r => requirementCard(r, ctx)),
        ...data.errors.map(r => errorCard(r, ctx)),
        ...data.ai.map(r => aiCard(r, ctx)),
        ...data.evidence.map(r => evidenceCard(r, ctx)),
    ];
    return h("div", { class: "card" },
        h("div", { class: "section-heading" }, h("h2", null, "Linked Records"),
            h("div", { class: "row" },
                h("button", { class: "btn", type: "button", onclick: () => openRecordDialog(RECORDS.evidence, null, ctx, presets) }, "Add evidence"),
                h("button", { class: "btn", type: "button", onclick: () => openRecordDialog(RECORDS.errors, null, ctx, presets) }, "Log error"),
                h("button", { class: "btn", type: "button", onclick: () => openRecordDialog(RECORDS.ai, null, ctx, presets) }, "AI check"))),
        cards.length ? h("div", { class: "rec-list" }, cards) : h("div", { class: "empty" }, "No requirements, errors, AI checks or evidence linked to this task."));
}

/* ---------- Record cards ---------- */

function requirementCard(r, ctx) {
    return recordCard(RECORDS.requirements, r, ctx, {
        title: r.title,
        tags: [yesNo(r.completed, "Completed", "Not completed"), yesNo(r.tested, "Tested", "Not tested"), yesNo(r.verified, "Verified", "Not verified")],
        rows: [["Details", r.detail], ["Feature", r.relatedFeature], ["Responsibility", r.responsibilityTitle], ["Task", taskLinkRow(r)], ["Verification result", r.verificationResult], ["Evidence", r.evidenceNote]],
    });
}

function errorCard(e, ctx) {
    const note = h("input", { type: "text", maxlength: 1000, required: true, placeholder: "Add what happened next...", "aria-label": "Timeline note" });
    return recordCard(RECORDS.errors, e, ctx, {
        title: e.title,
        tags: [h("span", { class: "tag " + (e.status === "RESOLVED" ? "green" : "red") }, e.status === "RESOLVED" ? "Resolved" : "Open"), h("span", { class: "tag gray" }, fmtDate(e.occurredOn))],
        rows: [["Task", taskLinkRow(e)], ["What happened", e.situation], ["Cause", e.cause], ["What I tried", e.attempts], ["Final solution", e.solution], ["Changed files", e.changedFiles], ["Verified by", e.verification], ["Final result", e.result], ["Links", e.links]],
        extra: h("div", { class: "timeline" },
            h("div", { class: "detail-group" }, "Timeline"),
            e.events.map(ev => h("div", { class: "timeline-row" },
                h("span", { class: "log-time" }, `${fmtDate(ev.happenedAt.slice(0, 10))} ${fmtTime(ev.happenedAt)}`),
                h("span", { class: "log-body" }, ev.note))),
            h("form", {
                class: "step-add",
                onsubmit: event => {
                    event.preventDefault();
                    act(() => api(`/api/error-logs/${e.id}/events`, { method: "POST", body: { note: note.value } }));
                },
            }, note, h("button", { class: "btn", type: "submit" }, "Add"))),
    });
}

function aiCard(a, ctx) {
    return recordCard(RECORDS.ai, a, ctx, {
        title: a.title,
        tags: [a.aiTool && h("span", { class: "tag" }, a.aiTool), h("span", { class: "tag " + CHECK_STATUS[a.testStatus][1] }, `Tests: ${CHECK_STATUS[a.testStatus][0]}`), h("span", { class: "tag " + CHECK_STATUS[a.buildStatus][1] }, `Build: ${CHECK_STATUS[a.buildStatus][0]}`), yesNo(a.verified, "Verified", "Not verified"), yesNo(a.approved, "Approved", "Not approved")],
        rows: [
            { group: "From the AI" }, ["Requested", a.requestText], ["AI did", a.aiActions], ["Changed files", a.changedFiles],
            { group: "Measured results" }, ["Test items", a.testItems], ["Test result", a.testResult], ["Build result", a.buildResult],
            { group: "Confirmed by me" }, ["What I checked", a.humanReview], ["Follow-up", a.followUp], ["Task", taskLinkRow(a)],
        ],
    });
}

function evidenceCard(e, ctx) {
    return recordCard(RECORDS.evidence, e, ctx, {
        title: e.title,
        tags: [h("span", { class: "tag blue" }, EVIDENCE_TYPE[e.type]), e.taskTitle && h("span", { class: "tag gray" }, e.taskTitle)],
        rows: [["Location", e.location], ["Note", e.note], ["Task", taskLinkRow(e)], ["Responsibility", e.responsibilityTitle]],
    });
}

function dailyCard(d, ctx) {
    const list = (items, empty) => items.length ? h("ul", { class: "plain" }, items.map(i => h("li", null, i))) : empty;
    return recordCard(RECORDS.daily, d, ctx, {
        title: fmtDateRow(d.reportDate),
        tags: [h("span", { class: "tag green" }, `${d.completedTasks.length} completed`), d.errors.length > 0 && h("span", { class: "tag red" }, `${d.errors.length} errors`)],
        rows: [
            ["Goal", d.goal], ["What I did", d.actualWork],
            ["Completed tasks", list(d.completedTasks, null)], ["In progress now", list(d.tasksInProgress, null)], ["Errors logged", list(d.errors, null)],
            ["Problems solved", d.problemsSolved], ["Unfinished because", d.incompleteReason], ["Next", d.nextWork], ["Evidence", d.evidenceNote],
        ],
    });
}

/* ---------- Project Details ---------- */

const PROJECT_TABS = [
    ["info", "Overview"], ["schedule", "Schedule"], ["responsibilities", "My Responsibilities"], ["deadlines", "Deadlines"],
    ["requirements", "Requirements"], ["errors", "Errors & Solutions"], ["ai", "AI Verification"], ["daily", "Daily Reports"], ["evidence", "Evidence"],
];

const projectState = { lastId: null, deadlineFilter: "all" };

function phaseState(phase) {
    const today = todayIso();
    if (phase.endDate < today) return ["Past", "gray"];
    return phase.startDate <= today ? ["Current", "green"] : ["Upcoming", "blue"];
}

function currentPhaseLabel(phases) {
    const today = todayIso();
    const current = phases.find(p => p.startDate <= today && today <= p.endDate);
    if (current) return current.name;
    const next = phases.find(p => p.startDate > today);
    return next ? `Before ${next.name}` : phases.length ? "Finished" : "—";
}

async function viewProjectDetails(params) {
    const projects = await api("/api/projects");
    if (!projects.length) return h("div", { class: "card empty" }, "Create a project first.");

    const wanted = Number(params.get("id")) || projectState.lastId;
    const project = projects.find(p => p.id === wanted) || projects.find(p => p.status === "ACTIVE") || projects[0];
    projectState.lastId = project.id;
    const tab = PROJECT_TABS.some(([k]) => k === params.get("tab")) ? params.get("tab") : "info";

    const q = `?projectId=${project.id}`;
    const [allTasks, responsibilities] = await Promise.all([api("/api/tasks"), api(`/api/responsibilities${q}`)]);
    const ctx = { project, tasks: allTasks.filter(t => t.projectId === project.id), responsibilities };
    const content = await PROJECT_TAB_VIEWS[tab](project, ctx, q);

    return h("div", { class: "stack" },
        projects.length > 1 && h("label", { class: "field picker" }, "Project",
            h("select", { value: project.id, onchange: e => { location.hash = `#/project?id=${e.target.value}`; } },
                projects.map(p => h("option", { value: p.id }, p.name)))),
        h("div", { class: "project-banner" },
            h("h1", null, project.name),
            h("div", { class: "task-meta" },
                h("span", { class: "tag " + PROJECT_STATUS[project.status][1] }, PROJECT_STATUS[project.status][0]),
                project.teamName && h("span", { class: "tag teal" }, project.teamName),
                project.projectType && h("span", { class: "tag gray" }, project.projectType))),
        h("nav", { class: "tabs", "aria-label": "Project sections" }, PROJECT_TABS.map(([key, label]) =>
            h("a", { class: "tab", href: `#/project?id=${project.id}&tab=${key}`, "aria-current": key === tab ? "page" : null }, label))),
        content);
}

const PROJECT_TAB_VIEWS = {
    async info(project, ctx, q) {
        const [phases, deadlines, roles] = await Promise.all([api(`/api/phases${q}`), api(`/api/deadlines${q}`), api(`/api/team-roles${q}`)]);
        const next = deadlines.find(d => !d.done && d.dueDate >= todayIso());
        const presentation = deadlines.find(d => d.category === "PRESENTATION");
        const percent = pct(project.completedTaskCount, project.taskCount);
        const dateRange = (a, b) => a && b ? `${a} ~ ${b}` : a || b || null;

        return h("div", { class: "stack" },
            h("div", { class: "metric-grid" },
                metric(`${percent}%`, "MY TASK PROGRESS", `${project.completedTaskCount} of ${project.taskCount} tasks done`),
                metric(currentPhaseLabel(phases), "CURRENT PHASE"),
                metric(next ? dayLabel(next.dueDate) : "—", "NEXT DEADLINE", next?.title),
                metric(presentation ? fmtDate(presentation.dueDate) : "—", "FINAL PRESENTATION", presentation ? dayLabel(presentation.dueDate) : null)),
            h("div", { class: "card" },
                sectionHead("Project Information", h("button", { class: "btn", type: "button", onclick: () => openProjectDialog(project) }, icon("pencil", 16), "Edit")),
                detailList([
                    ["Purpose", project.purpose],
                    ["Description", project.description], ["Type", project.projectType],
                    ["Team", [project.teamName, project.teamNumber != null ? `No. ${project.teamNumber}` : null].filter(Boolean).join(" · ")],
                    ["Project period", dateRange(project.startDate, project.dueDate)],
                    ["Development period", dateRange(project.devStartDate, project.devEndDate)],
                    ["Tech stack", project.techStack], ["Service features", project.serviceFeatures],
                ])),
            h("div", { class: "card" },
                sectionHead("Team Roles", addButton("Add", () => openRecordDialog(RECORDS.roles, null, ctx))),
                h("p", { class: "muted", style: "margin-bottom:12px" }, "Reference only. Other members' work is never added to your tasks."),
                roles.map(role => h("div", { class: "role-row" + (role.mine ? " mine" : "") },
                    h("div", { class: "task-info" },
                        h("div", { class: "task-name" }, role.roleName, " ", role.mine && h("span", { class: "tag" }, "Me")),
                        h("div", { class: "muted" }, role.duties)),
                    recordActions(RECORDS.roles, role, ctx, role.roleName)))));
    },

    async schedule(project, ctx, q) {
        const [phases, deadlines] = await Promise.all([api(`/api/phases${q}`), api(`/api/deadlines${q}`)]);
        const presentation = deadlines.find(d => d.category === "PRESENTATION");
        return h("div", { class: "card" },
            sectionHead("Team Schedule", addButton("Add phase", () => openRecordDialog(RECORDS.phases, null, ctx))),
            h("p", { class: "muted", style: "margin-bottom:12px" }, "The team's overall plan, for reference."),
            phases.length ? phases.map(phase => {
                const [label, color] = phaseState(phase);
                return h("div", { class: "phase-row" + (label === "Past" ? " past" : "") },
                    h("div", { class: "task-info" },
                        h("div", { class: "row" }, h("strong", null, phase.name), h("span", { class: "tag " + color }, label), h("span", { class: "muted" }, `${fmtDate(phase.startDate)} – ${fmtDate(phase.endDate)}`)),
                        phase.items && h("div", { class: "muted phase-items" }, phase.items.split("\n").join(" · "))),
                    recordActions(RECORDS.phases, phase, ctx, phase.name));
            }) : h("div", { class: "empty" }, "No schedule yet."),
            presentation && h("div", { class: "phase-row" },
                h("div", { class: "task-info" },
                    h("div", { class: "row" }, h("strong", null, presentation.title), h("span", { class: "tag red" }, dayLabel(presentation.dueDate)), h("span", { class: "muted" }, fmtDate(presentation.dueDate))))));
    },

    async responsibilities(project, ctx) {
        return h("div", { class: "stack" },
            h("div", { class: "section-heading" },
                h("p", { class: "muted" }, "Only the work you personally own."),
                addButton("Add responsibility", () => openRecordDialog(RECORDS.responsibilities, null, ctx, { owner: "" }))),
            ctx.responsibilities.length ? ctx.responsibilities.map(r => {
                const tasks = sortTasks(ctx.tasks.filter(t => t.responsibilityId === r.id));
                return h("div", { class: "card" },
                    h("div", { class: "section-heading" },
                        h("h2", { class: "detail-title" }, r.title),
                        recordActions(RECORDS.responsibilities, r, ctx, r.title)),
                    h("div", { class: "task-meta" },
                        statusTag(WORK_STATUS, r.status),
                        h("span", { class: "tag " + PRIORITY_TAG[r.priority] }, `${r.priority} IMPORTANCE`),
                        r.owner && h("span", { class: "tag gray" }, r.owner),
                        r.dueDate && h("span", { class: "tag blue" }, `Due ${fmtDate(r.dueDate)} · ${dayLabel(r.dueDate)}`)),
                    r.description && h("p", { class: "muted", style: "margin-top:10px" }, r.description),
                    h("div", { class: "progress-caption" }, h("span", null, `${r.completedTaskCount} of ${r.taskCount} tasks done`), h("span", null, `${r.progress}%`)),
                    progressBar(r.progress),
                    r.deliverables && detailList([["Deliverables", r.deliverables]]),
                    h("details", { class: "subtasks" },
                        h("summary", null, `Tasks (${tasks.length})`),
                        h("div", { class: "task-list" }, tasks.map(taskRow)),
                        h("button", {
                            class: "btn", type: "button", style: "margin-top:10px",
                            onclick: () => openTaskDialog(null, { projectId: project.id, responsibilityId: r.id }),
                        }, icon("plus", 16), "Add task")));
            }) : h("div", { class: "card empty" }, "No responsibilities yet."));
    },

    async deadlines(project, ctx, q) {
        const deadlines = await api(`/api/deadlines${q}`);
        const today = todayIso();
        const filter = projectState.deadlineFilter;
        const shown = deadlines.filter(d => filter === "all" || d.scope === filter);
        const upcoming = shown.filter(d => d.dueDate >= today);
        const past = shown.filter(d => d.dueDate < today);

        const row = d => {
            const box = h("input", {
                type: "checkbox", checked: d.done, "aria-label": `Mark ${d.title} as done`,
                onchange: event => act(() => api(`/api/deadlines/${d.id}`, { method: "PUT", body: { ...bodyFrom(RECORDS.deadlines, d, ctx), done: event.target.checked } })),
            });
            return h("div", { class: "task" + (d.done ? " done" : "") },
                box,
                h("div", { class: "task-info" },
                    h("div", { class: "task-name" }, d.title),
                    h("div", { class: "task-meta" },
                        h("span", { class: "tag " + (d.scope === "TEAM" ? "teal" : "") }, d.scope === "TEAM" ? "Team" : "Personal"),
                        h("span", { class: "tag gray" }, DEADLINE_CATEGORY[d.category]),
                        h("span", { class: "tag " + (d.dueDate < today ? "gray" : daysUntil(d.dueDate) <= 3 ? "red" : "blue") }, `${fmtDate(d.dueDate)} · ${d.dueDate < today ? "Past" : dayLabel(d.dueDate)}`),
                        d.owner && h("span", { class: "tag gray" }, d.owner)),
                    d.personalAction && h("div", { class: "muted", style: "margin-top:6px" }, `My part: ${d.personalAction}`),
                    d.note && h("div", { class: "muted" }, d.note)),
                recordActions(RECORDS.deadlines, d, ctx, d.title));
        };

        return h("div", { class: "card" },
            sectionHead("Deadline Tracker", addButton("Add", () => openRecordDialog(RECORDS.deadlines, null, ctx))),
            h("div", { class: "filters", style: "margin-bottom:14px" }, [["all", "All"], ["PERSONAL", "Personal"], ["TEAM", "Team"]].map(([key, label]) => h("button", {
                class: "filter", type: "button", "aria-pressed": String(filter === key),
                onclick: () => { projectState.deadlineFilter = key; refresh(); },
            }, label))),
            upcoming.length ? h("div", { class: "task-list" }, upcoming.map(row)) : h("div", { class: "empty" }, "No upcoming deadlines."),
            past.length > 0 && h("details", { class: "subtasks" },
                h("summary", null, `Past deadlines (${past.length})`),
                h("div", { class: "task-list" }, past.map(row))));
    },

    async requirements(project, ctx, q) {
        const items = await api(`/api/requirements${q}`);
        const count = key => items.filter(r => r[key]).length;
        return recordTab("Requirement Checklist", "Add", RECORDS.requirements, ctx, items, requirementCard, "No requirements yet.",
            items.length > 0 && h("div", { class: "task-meta", style: "margin-bottom:12px" },
                h("span", { class: "tag gray" }, `${count("completed")}/${items.length} completed`),
                h("span", { class: "tag gray" }, `${count("tested")}/${items.length} tested`),
                h("span", { class: "tag green" }, `${count("verified")}/${items.length} verified`)));
    },

    async errors(project, ctx, q) {
        return recordTab("Error & Solution Log", "Log error", RECORDS.errors, ctx, await api(`/api/error-logs${q}`), errorCard, "No errors logged.");
    },

    async ai(project, ctx, q) {
        return recordTab("AI Code Verification", "Add", RECORDS.ai, ctx, await api(`/api/ai-verifications${q}`), aiCard, "No AI work recorded yet.",
            h("p", { class: "muted", style: "margin-bottom:12px" }, "AI output stays unverified until tests and the build pass and you record what you checked."));
    },

    async daily(project, ctx, q) {
        return recordTab("Daily Reports", "Add", RECORDS.daily, ctx, await api(`/api/daily-reports${q}`), dailyCard, "No daily reports yet.");
    },

    async evidence(project, ctx, q) {
        return recordTab("Evidence & Links", "Add", RECORDS.evidence, ctx, await api(`/api/evidence${q}`), evidenceCard, "No evidence linked yet.");
    },
};

function metric(value, label, sub) {
    return h("div", { class: "stat" },
        h("div", { class: "stat-number metric-value" }, value),
        h("div", { class: "stat-label" }, label),
        sub && h("div", { class: "muted metric-sub" }, sub));
}

function recordTab(titleText, addLabel, cfg, ctx, items, card, emptyText, intro) {
    return h("div", { class: "card" },
        sectionHead(titleText, addButton(addLabel, () => openRecordDialog(cfg, null, ctx))),
        intro,
        items.length ? h("div", { class: "rec-list" }, items.map(item => card(item, ctx))) : h("div", { class: "empty" }, emptyText));
}

/* ---------- Workflow Templates ---------- */

async function viewTemplates() {
    const templates = await api("/api/workflow-templates");
    const cfg = RECORDS.templates;
    return h("div", null,
        h("div", { class: "section-heading" },
            h("p", { class: "muted" }, "Reusable step lists. Apply one from a task's Work Sequence."),
            addButton("New template", () => openRecordDialog(cfg, null, {}))),
        templates.length
            ? h("div", { class: "project-grid" }, templates.map(t => h("div", { class: "card" },
                h("div", { class: "project-head" }, h("h3", null, t.name), recordActions(cfg, t, {}, t.name)),
                t.description && h("p", { class: "muted" }, t.description),
                h("ol", { class: "template-steps" }, t.steps.map(s => h("li", null, s))))))
            : h("div", { class: "card empty" }, "No templates yet."));
}

/* ---------- Reports ---------- */

function mondayOf(iso) {
    const d = parseDate(iso);
    d.setDate(d.getDate() - ((d.getDay() + 6) % 7));
    return isoDate(d);
}

const addDays = (iso, n) => { const d = parseDate(iso); d.setDate(d.getDate() + n); return isoDate(d); };

const reportState = { projectId: null, week: mondayOf(todayIso()) };

function weeklyText(project, week, reports) {
    const sunday = addDays(week, 6);
    const inWeek = reports.filter(r => r.reportDate >= week && r.reportDate <= sunday).sort((a, b) => a.reportDate.localeCompare(b.reportDate));
    const lines = [`Weekly Report - ${project.name}`, `${week} ~ ${sunday}`, `Days reported: ${inWeek.length}`, ""];
    const section = (titleText, values) => {
        const filled = values.filter(Boolean);
        if (filled.length) lines.push(`[${titleText}]`, ...filled, "");
    };
    const perDay = key => inWeek.map(r => r[key] ? `${r.reportDate}: ${r[key]}` : null);
    const unique = key => [...new Set(inWeek.flatMap(r => r[key]))].map(v => `- ${v}`);

    section("Goals", perDay("goal"));
    section("Work done", perDay("actualWork"));
    section("Completed tasks", unique("completedTasks"));
    section("Errors logged", unique("errors"));
    section("Problems solved", perDay("problemsSolved"));
    section("Unfinished and why", perDay("incompleteReason"));
    section("Next work", perDay("nextWork"));
    section("Evidence", perDay("evidenceNote"));
    return lines.join("\n");
}

async function viewReports() {
    const projects = await api("/api/projects");
    if (!projects.length) return h("div", { class: "card empty" }, "Create a project first.");
    const project = projects.find(p => p.id === reportState.projectId) || projects.find(p => p.status === "ACTIVE") || projects[0];
    reportState.projectId = project.id;

    const q = `?projectId=${project.id}`;
    const [tasks, responsibilities, reports] = await Promise.all([api("/api/tasks"), api(`/api/responsibilities${q}`), api(`/api/daily-reports${q}`)]);
    const ctx = { project, tasks: tasks.filter(t => t.projectId === project.id), responsibilities };
    const todayReport = reports.find(r => r.reportDate === todayIso());
    const week = reportState.week;

    return h("div", { class: "stack" },
        projects.length > 1 && h("label", { class: "field picker" }, "Project",
            h("select", { value: project.id, onchange: e => { reportState.projectId = Number(e.target.value); refresh(); } },
                projects.map(p => h("option", { value: p.id }, p.name)))),
        h("div", { class: "card" },
            sectionHead("Daily Work Reports", h("button", {
                class: "btn primary", type: "button",
                onclick: () => openRecordDialog(RECORDS.daily, todayReport || null, ctx),
            }, icon("plus", 16), todayReport ? "Edit today's report" : "Write today's report")),
            reports.length ? h("div", { class: "rec-list" }, reports.map(r => dailyCard(r, ctx))) : h("div", { class: "empty" }, "No daily reports yet.")),
        h("div", { class: "card" },
            h("div", { class: "section-heading" },
                h("h2", null, "Weekly Summary"),
                monthNav(`${fmtDate(week)} – ${fmtDate(addDays(week, 6))}`, delta => { reportState.week = addDays(week, delta * 7); refresh(); })),
            h("p", { class: "muted", style: "margin-bottom:10px" }, "Built from your daily reports. Select and copy into the weekly report."),
            h("textarea", { class: "weekly-text", readonly: true, rows: 14, "aria-label": "Weekly summary", value: weeklyText(project, week, reports) })));
}
