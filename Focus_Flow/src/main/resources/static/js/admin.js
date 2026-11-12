"use strict";

/*
 * Admin edition of FOCUS FLOW, loaded only by admin-dashboard.html after app.js and workspace.js.
 * It adds routes, header actions and sidebar links; it never redefines anything from those files.
 * Everything lives inside this function so no name can collide with the shared globals.
 */
(() => {
    // Shared session, API, header and helpers come from shell.js (FF).
    const { call: adminApi, change: adminAct, me, fmtBytes, fmtStamp, ago, subTag, filePicker, fileList, historyList } = FF;
    const EVENT_LABEL = FF.EVENT_LABEL;

    /* ---------- Dialogs ---------- */

    function busy(button, label) {
        button.disabled = true;
        button.replaceChildren(label);
    }

    function currentPhaseId(phases) {
        const today = todayIso();
        const current = phases.find(p => p.startDate <= today && today <= p.endDate)
            || phases.find(p => p.startDate > today)
            || phases[phases.length - 1];
        return current ? String(current.id) : "";
    }

    async function openSubmitDialog(presets = {}) {
        let projects, phases, tasks;
        try {
            [projects, phases, tasks] = await Promise.all([api("/api/projects"), api("/api/phases"), api("/api/tasks")]);
        } catch (error) {
            toast(error.message, true);
            return;
        }
        if (!phases.length) {
            toast("Add a schedule phase to a project first (Project Details → Schedule).", true);
            return;
        }
        const projectIds = [...new Set(phases.map(p => p.projectId))];
        const usable = projects.filter(p => projectIds.includes(p.id));
        const startProject = String(presets.projectId ?? phases.find(p => String(p.id) === String(presets.phaseId))?.projectId ?? usable[0]?.id ?? "");

        const project = h("select", { required: true, value: startProject, onchange: () => fillPhases(currentPhaseId(phasesOf())) },
            usable.map(p => h("option", { value: p.id }, p.name)));
        const phase = h("select", { required: true, onchange: suggestTitle });
        const task = h("select");
        const title = h("input", { type: "text", required: true, maxlength: 200 });
        const submitter = h("input", { type: "text", maxlength: 80, value: me()?.displayName || "" });
        const note = h("textarea", { maxlength: 5000 });
        const picker = filePicker();
        const submit = h("button", { class: "btn primary", type: "submit" }, icon("upload", 16), "Submit");
        let titleTouched = false;
        title.addEventListener("input", () => { titleTouched = true; });

        const phasesOf = () => phases.filter(p => String(p.projectId) === project.value).sort((a, b) => a.startDate.localeCompare(b.startDate));
        function suggestTitle() {
            if (titleTouched) return;
            const p = phases.find(x => String(x.id) === phase.value);
            title.value = p ? `${p.name} submission` : "";
        }
        function fillPhases(selected) {
            phase.replaceChildren(...phasesOf().map(p => h("option", { value: p.id }, `${p.name} · ${fmtDate(p.startDate)} – ${fmtDate(p.endDate)}`)));
            phase.value = selected;
            task.replaceChildren(h("option", { value: "" }, "No related task"),
                ...sortTasks(tasks.filter(t => String(t.projectId) === project.value)).map(t => h("option", { value: t.id }, t.title)));
            task.value = presets.taskId != null ? String(presets.taskId) : "";
            suggestTitle();
        }
        fillPhases(presets.phaseId != null ? String(presets.phaseId) : currentPhaseId(phasesOf()));

        const form = h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const problem = picker.problem();
                if (problem) { toast(problem, true); return; }
                const data = new FormData();
                data.append("projectId", project.value);
                data.append("phaseId", phase.value);
                if (task.value) data.append("taskId", task.value);
                data.append("title", title.value);
                data.append("submitterName", submitter.value);
                data.append("note", note.value);
                [...picker.input.files].forEach(f => data.append("files", f, f.name));
                busy(submit, "Uploading…");
                let created;
                const ok = await adminAct(async () => { created = await adminApi("/api/admin/submissions", { method: "POST", form: data }); }, "Submitted.");
                if (ok) {
                    closeModal();
                    location.hash = `#/submission?id=${created.id}`;
                } else {
                    submit.disabled = false;
                    submit.replaceChildren(icon("upload", 16), "Submit");
                }
            },
        },
            h("h2", null, "New submission"),
            h("div", { class: "form-grid" },
                h("label", { class: "field" }, "Project", project),
                h("label", { class: "field" }, "Phase (회차)", phase),
                h("label", { class: "field full" }, "Title", title),
                h("label", { class: "field" }, "Submitted by", submitter),
                h("label", { class: "field" }, "Related task", task),
                h("label", { class: "field full" }, "Note", note),
                picker.field),
            h("div", { class: "modal-actions" }, cancelButton(), submit));
        openModal(form);
    }

    function openResubmitDialog(sub) {
        const note = h("textarea", { maxlength: 5000, placeholder: "What changed in this version?" });
        const picker = filePicker();
        const submit = h("button", { class: "btn primary", type: "submit" }, icon("upload", 16), `Submit version ${sub.currentVersion + 1}`);
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const problem = picker.problem();
                if (problem) { toast(problem, true); return; }
                const data = new FormData();
                data.append("note", note.value);
                [...picker.input.files].forEach(f => data.append("files", f, f.name));
                busy(submit, "Uploading…");
                if (await adminAct(() => adminApi(`/api/admin/submissions/${sub.id}/resubmit`, { method: "POST", form: data }), "Resubmitted for review.")) closeModal();
                else { submit.disabled = false; submit.replaceChildren(icon("upload", 16), `Submit version ${sub.currentVersion + 1}`); }
            },
        },
            h("h2", null, "Resubmit"),
            sub.reviewComment && h("div", { class: "review-note rejected" }, h("strong", null, "Rejection reason: "), sub.reviewComment),
            h("div", { class: "form-grid" }, h("label", { class: "field full" }, "Note", note), picker.field),
            h("div", { class: "modal-actions" }, cancelButton(), submit)));
    }

    function openReviewDialog(sub, decision) {
        const approving = decision === "approve";
        const comment = h("textarea", {
            maxlength: 2000, required: !approving,
            placeholder: approving ? "Optional feedback" : "What needs to be fixed?",
        });
        const submit = h("button", { class: "btn " + (approving ? "primary" : "danger-solid"), type: "submit" },
            icon(approving ? "check" : "x", 16), approving ? "Approve" : "Reject");
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                submit.disabled = true;
                const ok = await adminAct(() => adminApi(`/api/admin/submissions/${sub.id}/${decision}`, { method: "POST", body: { comment: comment.value } }),
                    approving ? "Approved." : "Rejected.");
                if (ok) closeModal(); else submit.disabled = false;
            },
        },
            h("h2", null, approving ? "Approve submission" : "Reject submission"),
            h("p", { class: "muted dialog-sub" }, `${sub.phaseName || "Removed phase"} · ${sub.title} · version ${sub.currentVersion}`),
            h("label", { class: "field" }, approving ? "Comment" : "Reason", comment),
            h("div", { class: "modal-actions" }, cancelButton(), submit)));
    }

    async function openEditDialog(sub) {
        let tasks;
        try { tasks = await api("/api/tasks"); } catch (error) { toast(error.message, true); return; }
        const title = h("input", { type: "text", required: true, maxlength: 200, value: sub.title });
        const note = h("textarea", { maxlength: 5000, value: sub.note || "" });
        const task = h("select", { value: sub.taskId != null ? String(sub.taskId) : "" },
            h("option", { value: "" }, "No related task"),
            sortTasks(tasks.filter(t => t.projectId === sub.projectId || t.id === sub.taskId)).map(t => h("option", { value: t.id }, t.title)));
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const body = { title: title.value, note: note.value, taskId: task.value ? Number(task.value) : null };
                if (await adminAct(() => adminApi(`/api/admin/submissions/${sub.id}`, { method: "PUT", body }), "Submission updated.")) closeModal();
            },
        },
            h("h2", null, "Edit submission"),
            h("div", { class: "form-grid" },
                h("label", { class: "field full" }, "Title", title),
                h("label", { class: "field full" }, "Related task", task),
                h("label", { class: "field full" }, "Note", note)),
            h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Save"))));
    }

    async function deleteSubmission(sub) {
        if (!confirm(`Delete "${sub.title}" and its ${sub.fileCount} uploaded ${sub.fileCount === 1 ? "file" : "files"}? This cannot be undone.`)) return;
        // act() re-renders afterwards; leaving a deleted submission's page first sends that render to the list.
        await adminAct(async () => {
            await adminApi(`/api/admin/submissions/${sub.id}`, { method: "DELETE" });
            if (location.hash.startsWith("#/submission?")) history.replaceState(null, "", "#/submissions");
        }, "Submission deleted.");
    }

    function reopenSubmission(sub) {
        const what = sub.status === "APPROVED" ? "approval" : "rejection";
        if (!confirm(`Undo the ${what} and put "${sub.title}" back in the review queue?`)) return;
        adminAct(() => adminApi(`/api/admin/submissions/${sub.id}/reopen`, { method: "POST" }), "Back in the review queue.");
    }

    const reviewButtons = sub => sub.status === "SUBMITTED" && [
        h("button", { class: "icon-btn ok", type: "button", "aria-label": `Approve "${sub.title}"`, title: "Approve", onclick: () => openReviewDialog(sub, "approve") }, icon("check", 16)),
        h("button", { class: "icon-btn danger", type: "button", "aria-label": `Reject "${sub.title}"`, title: "Reject", onclick: () => openReviewDialog(sub, "reject") }, icon("x", 16)),
    ];

    function submissionRow(sub) {
        return h("div", { class: "sub-row" },
            h("span", { class: "sub-icon" }, icon("file", 16)),
            h("div", { class: "task-info" },
                h("div", { class: "task-name" }, h("a", { class: "task-link", href: `#/submission?id=${sub.id}` }, sub.title)),
                h("div", { class: "task-meta" },
                    subTag(sub.status),
                    h("span", { class: "tag teal" }, sub.phaseName || "Removed phase"),
                    sub.currentVersion > 1 && h("span", { class: "tag gray" }, `v${sub.currentVersion}`),
                    h("span", { class: "tag gray" }, `${sub.fileCount} ${sub.fileCount === 1 ? "file" : "files"} · ${fmtBytes(sub.totalBytes)}`),
                    sub.late && h("span", { class: "tag red" }, "Late"),
                    sub.taskTitle && h("a", { class: "tag", href: `#/task?id=${sub.taskId}` }, sub.taskTitle)),
                h("div", { class: "muted sub-by" }, `${sub.submitterName} · ${fmtStamp(sub.submittedAt)}`)),
            h("div", { class: "task-actions" }, reviewButtons(sub)));
    }

    /* ---------- Admin Dashboard ---------- */

    async function viewAdmin() {
        const [dash, projects, tasks] = await Promise.all([adminApi("/api/admin/dashboard"), api("/api/projects"), api("/api/tasks")]);
        const user = await FF.ready();
        const done = tasks.filter(t => t.done).length;
        const phasesByProject = new Map();
        for (const row of dash.phases) {
            if (!phasesByProject.has(row.projectId)) phasesByProject.set(row.projectId, { name: row.projectName, rows: [] });
            phasesByProject.get(row.projectId).rows.push(row);
        }
        const covered = dash.phases.filter(r => r.overallStatus === "APPROVED").length;

        return h("div", null,
            h("section", { class: "welcome" },
                h("div", { class: "eyebrow" }, "ADMIN CONSOLE"),
                h("h1", null, `Hello, ${user.displayName}.`),
                h("p", { class: "subtitle" }, "Review what was handed in for each phase, and keep every round on track.")),

            h("section", { class: "metric-grid" },
                metric(String(dash.pending), "PENDING REVIEW", dash.pending ? "Waiting for your decision" : "Nothing waiting"),
                metric(String(dash.approved), "APPROVED", `${covered} of ${dash.phases.length} phases approved`),
                metric(String(dash.rejected), "REJECTED", "Waiting for a new version"),
                metric(String(dash.fileCount), "FILES STORED", fmtBytes(dash.totalBytes))),

            h("section", { class: "two-col" },
                h("div", { class: "card" },
                    sectionHead("Review Queue", h("a", { href: "#/submissions?status=SUBMITTED" }, "View all")),
                    dash.reviewQueue.length
                        ? h("div", { class: "task-list" }, dash.reviewQueue.map(submissionRow))
                        : h("div", { class: "empty" }, "No submissions are waiting for review.")),
                h("div", { class: "card" },
                    sectionHead("Submissions by Phase",
                        h("button", { class: "btn primary", type: "button", onclick: () => openSubmitDialog() }, icon("upload", 16), "New submission")),
                    phasesByProject.size
                        ? [...phasesByProject.values()].map(group => h("div", { class: "phase-group" },
                            phasesByProject.size > 1 && h("div", { class: "detail-group" }, group.name || "Removed project"),
                            group.rows.map(phaseBoardRow)))
                        : h("div", { class: "empty" }, "No schedule phases yet. Add them under Project Details → Schedule."))),

            h("section", { class: "two-col" },
                h("div", { class: "card" },
                    sectionHead("Recent Review Activity"),
                    dash.recentEvents.length
                        ? dash.recentEvents.map(e => h("div", { class: "log" },
                            h("span", { class: "log-icon" }, icon(e.type === "APPROVED" ? "check" : e.type === "REJECTED" ? "x" : "upload", 14)),
                            h("div", { class: "log-body" },
                                h("strong", null, e.actor), ` ${EVENT_LABEL[e.type]} `,
                                h("a", { href: `#/submission?id=${e.submissionId}` }, e.submissionTitle),
                                e.version > 1 && ` (v${e.version})`,
                                e.comment && h("div", { class: "muted" }, e.comment)),
                            h("span", { class: "log-time" }, ago(e.createdAt))))
                        : h("div", { class: "empty" }, "No review activity yet.")),
                h("div", { class: "card" },
                    sectionHead("Workspace Snapshot", h("a", { href: "#/overview" }, "Overview")),
                    h("div", { class: "stats snapshot" },
                        h("div", { class: "stat" }, h("div", { class: "stat-number" }, projects.length), h("div", { class: "stat-label" }, "PROJECTS")),
                        h("div", { class: "stat" }, h("div", { class: "stat-number" }, `${done}/${tasks.length}`), h("div", { class: "stat-label" }, "TASKS DONE"))),
                    progressBar(pct(done, tasks.length)),
                    h("p", { class: "muted snapshot-note" },
                        "Read from your existing projects and tasks; the admin pages never change them. Files are kept in ",
                        h("code", null, dash.storageFolder), "."))));
    }

    function phaseBoardRow(row) {
        const [stateLabel, stateTag] = phaseState(row);
        return h("div", { class: "phase-row board-row" + (stateLabel === "Past" && row.overallStatus === "APPROVED" ? " past" : "") },
            h("div", { class: "task-info" },
                h("div", { class: "row" },
                    h("a", { class: "task-link", href: `#/submissions?phase=${row.phaseId}` }, h("strong", null, row.phaseName)),
                    h("span", { class: "tag " + stateTag }, stateLabel),
                    subTag(row.overallStatus)),
                h("div", { class: "muted" },
                    `${fmtDate(row.startDate)} – ${fmtDate(row.endDate)}`,
                    row.total > 0 && ` · ${row.total} submitted`,
                    row.pending > 0 && ` · ${row.pending} pending`,
                    row.rejected > 0 && ` · ${row.rejected} rejected`)),
            h("button", {
                class: "icon-btn", type: "button", "aria-label": `New submission for ${row.phaseName}`, title: "Submit for this phase",
                onclick: () => openSubmitDialog({ projectId: row.projectId, phaseId: row.phaseId }),
            }, icon("upload", 16)));
    }

    /* ---------- Submissions list ---------- */

    const subState = { status: "all", q: "" };
    const STATUS_FILTERS = [["all", "All"], ["SUBMITTED", "Pending"], ["APPROVED", "Approved"], ["REJECTED", "Rejected"]];

    async function viewSubmissions(params) {
        if (params.get("status")) {
            subState.status = STATUS_FILTERS.some(([k]) => k === params.get("status")) ? params.get("status") : "all";
            params.delete("status");
            history.replaceState(null, "", "#/submissions" + (params.toString() ? "?" + params : ""));
        }
        const [projects, phases] = await Promise.all([api("/api/projects"), api("/api/phases")]);
        let phaseId = params.get("phase") || "";
        let projectId = params.get("project") || (phaseId ? String(phases.find(p => String(p.id) === phaseId)?.projectId ?? "") : "");
        const query = new URLSearchParams();
        if (projectId) query.set("projectId", projectId);
        if (phaseId) query.set("phaseId", phaseId);
        if (subState.q) query.set("q", subState.q);
        const all = await adminApi("/api/admin/submissions" + (query.toString() ? "?" + query : ""));
        const visible = subState.status === "all" ? all : all.filter(s => s.status === subState.status);

        const go = (project, phase) => {
            const p = new URLSearchParams();
            if (project) p.set("project", project);
            if (phase) p.set("phase", phase);
            location.hash = "#/submissions" + (p.toString() ? "?" + p : "");
        };
        const projectPhases = phases.filter(p => !projectId || String(p.projectId) === projectId).sort((a, b) => a.startDate.localeCompare(b.startDate));
        const search = h("input", { type: "search", placeholder: "Search title, note, person…", value: subState.q, "aria-label": "Search submissions" });

        return h("div", { class: "card" },
            h("div", { class: "toolbar" },
                h("div", { class: "filters" }, STATUS_FILTERS.map(([key, label]) => h("button", {
                    class: "filter", type: "button", "aria-pressed": String(subState.status === key),
                    onclick: () => { subState.status = key; refresh(); },
                }, `${label} (${key === "all" ? all.length : all.filter(s => s.status === key).length})`))),
                h("button", {
                    class: "btn primary", type: "button",
                    onclick: () => openSubmitDialog({ projectId: projectId || undefined, phaseId: phaseId || undefined }),
                }, icon("upload", 16), "New submission")),
            h("div", { class: "toolbar sub-filters" },
                h("select", { "aria-label": "Filter by project", value: projectId, onchange: e => go(e.target.value, "") },
                    h("option", { value: "" }, "All projects"), projects.map(p => h("option", { value: p.id }, p.name))),
                h("select", { "aria-label": "Filter by phase", value: phaseId, onchange: e => go(projectId, e.target.value) },
                    h("option", { value: "" }, "All phases"),
                    projectPhases.map(p => h("option", { value: p.id }, `${p.name} · ${fmtDate(p.startDate)} – ${fmtDate(p.endDate)}`))),
                h("form", { class: "search-form", onsubmit: e => { e.preventDefault(); subState.q = search.value.trim(); refresh(); } }, search)),
            visible.length
                ? h("div", { class: "task-list" }, visible.map(submissionRow))
                : h("div", { class: "empty" }, all.length ? "No submissions match this filter." : "Nothing has been submitted yet. Use New submission to upload the files for a phase."));
    }

    /* ---------- Submission details ---------- */

    async function viewSubmission(params) {
        const id = params.get("id");
        if (!id || !/^\d+$/.test(id)) {
            return h("div", { class: "card empty" }, "Choose a submission from the ", h("a", { href: "#/submissions" }, "Submissions"), " list.");
        }
        const { submission: sub, files, events } = await adminApi(`/api/admin/submissions/${id}`);

        const actions = [];
        if (sub.status === "SUBMITTED") {
            actions.push(h("button", { class: "btn primary", type: "button", onclick: () => openReviewDialog(sub, "approve") }, icon("check", 16), "Approve"));
            actions.push(h("button", { class: "btn danger", type: "button", onclick: () => openReviewDialog(sub, "reject") }, icon("x", 16), "Reject"));
        } else {
            if (sub.status === "REJECTED") actions.push(h("button", { class: "btn primary", type: "button", onclick: () => openResubmitDialog(sub) }, icon("upload", 16), "Resubmit"));
            actions.push(h("button", { class: "btn", type: "button", onclick: () => reopenSubmission(sub) }, icon("undo", 16), sub.status === "APPROVED" ? "Undo approval" : "Undo rejection"));
        }
        actions.push(h("button", { class: "icon-btn", type: "button", "aria-label": "Edit submission", title: "Edit", onclick: () => openEditDialog(sub) }, icon("pencil", 16)));
        actions.push(h("button", { class: "icon-btn danger", type: "button", "aria-label": "Delete submission", title: "Delete", onclick: () => deleteSubmission(sub) }, icon("trash", 16)));

        return h("div", { class: "stack" },
            h("div", null, h("a", { href: "#/submissions" }, "← All submissions")),
            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    h("div", null,
                        h("h2", { class: "detail-title" }, sub.title),
                        h("div", { class: "task-meta" },
                            subTag(sub.status),
                            h("span", { class: "tag teal" }, sub.phaseName || "Removed phase"),
                            h("span", { class: "tag gray" }, `Version ${sub.currentVersion}`),
                            sub.late && h("span", { class: "tag red" }, "Submitted after the phase ended"))),
                    h("div", { class: "row" }, actions)),
                sub.reviewComment && h("div", { class: "review-note " + (sub.status === "REJECTED" ? "rejected" : "approved") },
                    h("strong", null, sub.status === "REJECTED" ? "Rejection reason: " : "Review comment: "), sub.reviewComment),
                detailList([
                    ["Project", sub.projectName || "Removed project"],
                    ["Phase", sub.phaseName ? `${sub.phaseName} · ${fmtDate(sub.phaseStartDate)} – ${fmtDate(sub.phaseEndDate)}` : "This phase was removed from the schedule."],
                    ["Related task", sub.taskId ? (sub.taskTitle ? h("a", { href: `#/task?id=${sub.taskId}` }, sub.taskTitle) : "Removed task") : null],
                    ["Submitted by", sub.submitterName],
                    ["Submitted", fmtStamp(sub.submittedAt)],
                    ["Note", sub.note],
                    ["Reviewed", sub.reviewedAt ? `${sub.reviewedBy} · ${fmtStamp(sub.reviewedAt)}` : null],
                ])),

            h("div", { class: "two-col flush" },
                h("div", { class: "card" },
                    sectionHead("Files", h("span", { class: "muted" }, `${files.length} · ${fmtBytes(files.reduce((n, f) => n + f.sizeBytes, 0))}`)),
                    fileList(files, sub.currentVersion, f => `/api/admin/submission-files/${f.id}`)),
                h("div", { class: "card" },
                    sectionHead("History"),
                    historyList(events))));
    }

    /* ---------- Wiring ---------- */

    const ADMIN_ROUTES = {
        admin: ["Admin Dashboard", "shield", viewAdmin],
        submissions: ["Submissions", "upload", viewSubmissions],
        submission: ["Submission Details", "file", viewSubmission],
        "admin-account": ["Account", "user", () => FF.viewAccount()],
    };
    Object.assign(ROUTES, ADMIN_ROUTES);

    // app.js has already built the original sidebar links; the admin links go in their own section.
    const nav = document.getElementById("nav");
    nav.append(h("div", { class: "nav-section" }, "ADMIN"),
        ...["admin", "submissions", "admin-account"].map(route =>
            h("a", { class: "nav-link", href: `#/${route}`, "data-route": route }, icon(ADMIN_ROUTES[route][1]), ADMIN_ROUTES[route][0])));

    // Opening the admin workspace with no page chosen lands on the dashboard rather than the overview.
    if (!location.hash || location.hash === "#" || location.hash === "#/") history.replaceState(null, "", "#/admin");

    FF.mountHeader({
        role: "ADMIN",
        links: [["#/admin", "shield", "Admin Dashboard"], ["#/submissions", "upload", "Submissions"], ["#/admin-account", "user", "Account settings"]],
    });
})();
