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
                h("label", { class: "field" }, "Phase", phase),
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

    /* ---------- Company Overview ---------- */

    /** Tasks with their owner attached, from the existing task API and the assignment table. */
    async function loadWork() {
        const [tasks, assignments, users] = await Promise.all([api("/api/tasks"), adminApi("/api/admin/assignments"), adminApi("/api/admin/users")]);
        const byTask = new Map(assignments.map(a => [a.taskId, a]));
        const people = new Map(users.map(u => [u.id, u]));
        return { tasks: tasks.map(t => ({ ...t, assignee: byTask.get(t.id) ? people.get(byTask.get(t.id).userId) || null : null })), users, people };
    }

    async function viewAdmin() {
        const [dash, projects, work, legal] = await Promise.all([adminApi("/api/admin/dashboard"), api("/api/projects"), loadWork(), adminApi("/api/admin/legal/summary")]);
        const user = await FF.ready();
        const { tasks, users } = work;
        const open = tasks.filter(t => !t.done);
        const inProgress = open.filter(t => t.status === "IN_PROGRESS" || t.status === "REVIEW");
        const overdue = sortTasks(open.filter(isOverdue));
        const unassigned = open.filter(t => !t.assignee);
        const done = tasks.filter(t => t.done).length;
        const members = users.filter(u => u.role === "MEMBER");

        // Who has not submitted for the phase running now, per project.
        const today = todayIso();
        const currentPhases = dash.phases.filter(p => p.startDate <= today && today <= p.endDate);
        const trackers = await Promise.all([...new Set(currentPhases.map(p => p.projectId))].map(id => adminApi(`/api/admin/tracker?projectId=${id}`)));
        const missing = [];
        for (const phase of currentPhases) {
            const t = trackers.find(x => x.projectId === phase.projectId);
            const cells = t?.cells[phase.phaseId] || {};
            for (const person of t?.people || []) if (person.role === "MEMBER" && person.active && !cells[person.id]) missing.push({ phase, person });
        }

        const phasesByProject = new Map();
        for (const row of dash.phases) {
            if (!phasesByProject.has(row.projectId)) phasesByProject.set(row.projectId, { name: row.projectName, rows: [] });
            phasesByProject.get(row.projectId).rows.push(row);
        }
        const covered = dash.phases.filter(r => r.overallStatus === "APPROVED").length;

        const load = members.map(m => {
            const mine = tasks.filter(t => t.assignee?.id === m.id);
            return { m, open: mine.filter(t => !t.done).length, overdue: mine.filter(isOverdue).length, active: mine.filter(t => !t.done && t.status === "IN_PROGRESS").length, done: mine.filter(t => t.done).length };
        });

        return h("div", null,
            h("section", { class: "welcome" },
                h("div", { class: "eyebrow" }, "COMPANY OVERVIEW"),
                h("h1", null, `Hello, ${user.displayName}.`),
                h("p", { class: "subtitle" }, "Who is working on what, what is late, and what is waiting for your review.")),

            h("div", { class: "detail-group overview-group" }, "Work"),
            h("section", { class: "metric-grid" },
                metric(String(inProgress.length), "IN PROGRESS", `${open.length} open tasks in total`),
                metric(String(overdue.length), "OVERDUE", overdue.length ? "Past their deadline" : "Nothing late"),
                metric(String(unassigned.length), "UNASSIGNED", unassigned.length ? "Open tasks without an owner" : "Every open task has an owner"),
                metric(`${done}/${tasks.length}`, "COMPLETED", `${pct(done, tasks.length)}% of all tasks`)),
            h("div", { class: "detail-group overview-group" }, "Submissions"),
            h("section", { class: "metric-grid" },
                metric(String(dash.pending), "AWAITING REVIEW", dash.pending ? "Waiting for your decision" : "Nothing waiting"),
                metric(String(dash.rejected), "REJECTED", "Waiting for a new version"),
                metric(String(dash.approved), "APPROVED", `${covered} of ${dash.phases.length} phases approved`),
                metric(String(missing.length), "NOT SUBMITTED", currentPhases.length ? `For ${currentPhases.map(p => p.phaseName).join(", ")}` : "No phase running now")),
            h("div", { class: "detail-group overview-group" }, "Legal & Compliance"),
            h("section", { class: "metric-grid" },
                metric(String(legal.pendingReviews), "PENDING REVIEWS", legal.pendingReviews ? "Documents waiting for a decision" : "Nothing waiting"),
                metric(String(legal.highPriorityRisks), "HIGH-PRIORITY RISKS", legal.highPriorityRisks ? "Open reviews with a HIGH finding" : "No high risks open"),
                metric(String(legal.completedReviews), "COMPLETED", legal.overdueReviews ? `${legal.overdueReviews} past deadline` : "Reviews closed"),
                h("a", { class: "metric legal-link", href: "#/legal" }, h("div", { class: "metric-value" }, "Open"), h("div", { class: "metric-label" }, "LEGAL & COMPLIANCE"), h("div", { class: "muted small" }, "Review documents before accepting"))),

            h("section", { class: "two-col" },
                h("div", { class: "card" },
                    sectionHead("Needs Attention", h("a", { href: "#/assignments" }, "Task Assignment")),
                    overdue.length || unassigned.length || missing.length || dash.rejected
                        ? h("div", { class: "attention-stack" },
                            overdue.slice(0, 5).map(t => h("a", { class: "attn-row", href: `#/task?id=${t.id}` },
                                h("span", { class: "tag red" }, `Overdue ${dayLabel(t.dueDate)}`),
                                h("span", { class: "attn-title" }, t.title),
                                h("span", { class: "muted" }, t.assignee ? t.assignee.displayName : "Unassigned"))),
                            missing.slice(0, 5).map(({ phase, person }) => h("a", { class: "attn-row", href: `#/tracker?project=${phase.projectId}` },
                                h("span", { class: "tag amber" }, `${phase.phaseName} · not submitted`),
                                h("span", { class: "attn-title" }, person.displayName),
                                h("span", { class: "muted" }, `ends ${fmtDate(phase.endDate)}`))),
                            unassigned.length > 0 && h("a", { class: "attn-row", href: "#/assignments?filter=unassigned" },
                                h("span", { class: "tag gray" }, "Unassigned"),
                                h("span", { class: "attn-title" }, `${unassigned.length} open ${unassigned.length === 1 ? "task has" : "tasks have"} no owner`),
                                h("span", { class: "muted" }, "Assign →")),
                            dash.rejected > 0 && h("a", { class: "attn-row", href: "#/submissions?status=REJECTED" },
                                h("span", { class: "tag red" }, "Rejected"),
                                h("span", { class: "attn-title" }, `${dash.rejected} ${dash.rejected === 1 ? "submission is" : "submissions are"} waiting for a new version`),
                                h("span", { class: "muted" }, "View →")))
                        : h("div", { class: "empty" }, "Nothing needs your attention right now.")),
                h("div", { class: "card" },
                    sectionHead("Review Queue", h("a", { href: "#/submissions?status=SUBMITTED" }, "View all")),
                    dash.reviewQueue.length
                        ? h("div", { class: "task-list" }, dash.reviewQueue.map(submissionRow))
                        : h("div", { class: "empty" }, "No submissions are waiting for review."))),

            h("section", { class: "two-col" },
                h("div", { class: "card" },
                    sectionHead("Team Workload", h("a", { href: "#/members" }, "Members")),
                    load.length
                        ? h("div", { class: "table-wrap" }, h("table", { class: "compact" },
                            h("thead", null, h("tr", null, ["Member", "Open", "In progress", "Overdue", "Done"].map(c => h("th", { scope: "col" }, c)))),
                            h("tbody", null, load.map(({ m, open: o, active, overdue: late, done: d }) => h("tr", null,
                                h("td", null, h("a", { href: `#/assignments?member=${m.id}` }, m.displayName), !m.active && h("span", { class: "tag gray", style: "margin-left:6px" }, "Deactivated")),
                                h("td", null, o), h("td", null, active),
                                h("td", null, late ? h("span", { class: "tag red" }, late) : "0"),
                                h("td", null, d))))))
                        : h("div", { class: "empty" }, "No members yet. ", h("a", { href: "#/members" }, "Invite your team"), ".")),
                h("div", { class: "card" },
                    sectionHead("Submissions by Phase", h("a", { href: "#/tracker" }, "Submission Tracker")),
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
                    sectionHead("Workspace Snapshot", h("a", { href: "#/projects" }, "Projects")),
                    h("div", { class: "stats snapshot" },
                        h("div", { class: "stat" }, h("div", { class: "stat-number" }, projects.length), h("div", { class: "stat-label" }, "PROJECTS")),
                        h("div", { class: "stat" }, h("div", { class: "stat-number" }, `${members.filter(m => m.active).length}`), h("div", { class: "stat-label" }, "ACTIVE MEMBERS"))),
                    progressBar(pct(done, tasks.length)),
                    h("p", { class: "muted snapshot-note" }, `${done} of ${tasks.length} tasks done. Uploaded files are kept in `,
                        h("code", null, dash.storageFolder), "."))));
    }

    /* ---------- Members ---------- */

    function accountState(u) {
        if (!u.active) return ["Deactivated", "gray"];
        if (!u.passwordSet) return [u.codePending ? "Invitation pending" : "Not set up", "amber"];
        if (u.codePending) return ["Reset pending", "amber"];
        return ["Active", "green"];
    }

    /** Shows a one-time code once, with what to tell the person. */
    function showCode(title, issued) {
        const code = h("code", { class: "invite-code" }, issued.code);
        openModal(h("div", null,
            h("h2", null, title),
            h("p", { class: "muted" }, `Give this code to ${issued.user.displayName} (@${issued.user.username}). It works once and expires on ${fmtDate(issued.expiresAt.slice(0, 10))}. It won't be shown again.`),
            h("div", { class: "invite-box" }, code,
                h("button", {
                    class: "btn", type: "button",
                    onclick: () => navigator.clipboard?.writeText(issued.code).then(() => toast("Code copied."), () => toast("Copy it by hand.", true)),
                }, "Copy")),
            h("ol", { class: "plain invite-steps" },
                h("li", null, "Open ", h("strong", null, `${location.origin}/login`)),
                h("li", null, "Choose ", h("strong", null, "I have an invitation code"), " and enter the code"),
                h("li", null, "Pick a password — they are signed straight into their workspace")),
            h("div", { class: "modal-actions" }, h("button", { class: "btn primary", type: "button", onclick: () => { closeModal(); refresh(); } }, "Done"))));
    }

    /**
     * Seat type, department and title for the invite and edit dialogs. The title suggestions follow
     * the chosen department, and any other title can be typed. Returns [fields, read()].
     */
    function jobFields(u = {}) {
        const kind = h("select", null,
            h("option", { value: "HUMAN" }, "Person"),
            h("option", { value: "AI" }, "AI teammate — a seat you operate now and connect to an AI later"));
        kind.value = u.kind || "HUMAN";
        const department = h("select", null, h("option", { value: "" }, "— None —"),
            Object.entries(FF.DEPARTMENTS).map(([value, label]) => h("option", { value }, label)));
        department.value = u.department || "";
        const listId = "title-options-" + Math.random().toString(36).slice(2);
        const options = h("datalist", { id: listId });
        const title = h("input", { type: "text", maxlength: 60, value: u.title || "", list: listId, placeholder: "e.g. Exam Coach" });
        const fill = () => options.replaceChildren(...(FF.TITLES[department.value] || Object.values(FF.TITLES).flat()).map(t => h("option", { value: t })));
        department.addEventListener("change", fill);
        fill();
        // An AI teammate's responsibilities come from the AI team catalog, so only people get the field here.
        const isAi = u.kind === "AI";
        let duties = null;
        let dutiesField = null;
        if (!isAi) {
            duties = h("textarea", { rows: 5, maxlength: 2000, "aria-label": "Main responsibilities", placeholder: "One responsibility per line" });
            duties.value = u.responsibilities || "";
            const suggest = h("button", {
                class: "btn", type: "button",
                onclick: () => {
                    const found = suggestionsFor(title.value);
                    if (!found) { toast("No suggestions for this title yet. Type the responsibilities, one per line.", true); return; }
                    duties.value = found.join("\n");
                },
            }, "Suggest from title");
            dutiesField = h("div", { class: "field full" },
                h("span", null, "Main responsibilities"),
                duties,
                h("div", { class: "row between" }, h("span", { class: "muted hint" }, "What this person is accountable for. One item per line."), suggest));
        }
        const aiNote = isAi && h("p", { class: "muted small full", style: "grid-column: 1 / -1; margin: 0" },
            "This AI teammate's responsibilities come from the AI team catalog and are not edited here.");
        return [[
            h("label", { class: "field full" }, "Seat", kind),
            h("label", { class: "field" }, "Department", department),
            h("label", { class: "field" }, "Title", title, options),
            dutiesField,
            aiNote,
        ].filter(Boolean), () => ({
            kind: kind.value,
            department: department.value || null,
            title: title.value.trim() || null,
            // Left out for AI seats, so the server keeps their current value.
            ...(duties ? { responsibilities: duties.value } : {}),
        })];
    }

    /** Starting points for "Main responsibilities", matched by part of the job title. The admin edits them per person. */
    const SUGGESTED_RESPONSIBILITIES = {
        "Knowledge Manager": [
            "Maintain project documentation",
            "Record development decisions and progress",
            "Organize technical knowledge",
            "Track bugs and proposed improvements",
            "Review whether documentation matches the actual code",
            "Prepare completed documentation for README publication",
        ],
    };

    function suggestionsFor(title) {
        const text = (title || "").toLowerCase();
        const key = Object.keys(SUGGESTED_RESPONSIBILITIES).find(k => text.includes(k.toLowerCase()));
        return key ? SUGGESTED_RESPONSIBILITIES[key] : null;
    }

    function openInviteDialog() {
        const displayName = h("input", { type: "text", required: true, maxlength: 60 });
        const username = h("input", { type: "text", required: true, maxlength: 40, pattern: "[A-Za-z0-9._\\-]{3,40}", placeholder: "e.g. jiyoung.park" });
        const email = h("input", { type: "email", maxlength: 120, placeholder: "optional" });
        const role = h("select", { value: "MEMBER" }, h("option", { value: "MEMBER" }, "Member — does assigned work"), h("option", { value: "ADMIN" }, "Administrator — manages everything"));
        const [job, readJob] = jobFields();
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                try {
                    const issued = await adminApi("/api/admin/users", { method: "POST", body: { displayName: displayName.value, username: username.value, email: email.value || null, role: role.value, ...readJob() } });
                    // The member is saved now, so the list shows them even if the code dialog is dismissed another way.
                    refresh();
                    showCode("Invitation created", issued);
                } catch (error) { toast(error.message, true); }
            },
        },
            h("h2", null, "Invite a member"),
            h("div", { class: "form-grid" },
                h("label", { class: "field full" }, "Name", displayName),
                h("label", { class: "field" }, "Username", username),
                h("label", { class: "field" }, "Email", email),
                h("label", { class: "field full" }, "Role (what they may do)", role),
                ...job),
            h("p", { class: "muted small" }, "The invitation code is shown only once, right after you create it. Copy it before closing."),
            h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Create invitation"))));
    }

    function openMemberDialog(u, me) {
        const displayName = h("input", { type: "text", required: true, maxlength: 60, value: u.displayName });
        const email = h("input", { type: "email", maxlength: 120, value: u.email || "" });
        const active = h("input", { type: "checkbox", disabled: u.id === me.id });
        active.checked = u.active;
        const [job, readJob] = jobFields(u);
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const ok = await adminAct(() => adminApi(`/api/admin/users/${u.id}`, { method: "PUT", body: { displayName: displayName.value, email: email.value || null, active: active.checked, ...readJob() } }), "Member updated.");
                if (ok) closeModal();
            },
        },
            h("h2", null, `Edit ${u.displayName}`),
            h("div", { class: "form-grid" },
                h("label", { class: "field full" }, "Name", displayName),
                h("label", { class: "field full" }, "Email", email),
                ...job,
                h("label", { class: "field full check-field" }, active, u.id === me.id ? "Active (you can't deactivate yourself)" : "Active — can sign in")),
            h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Save"))));
    }

    /**
     * Member profile: what this person does and what they are responsible for right now. Uses the
     * data the Members page already loads, plus the activity feed and, for AI seats, the AI team
     * catalog and their work requests.
     */
    async function openMemberProfile(u, { tasks, projects, memberships, me }) {
        let team = [];
        let requests = [];
        let activity = [];
        try {
            [team, requests, activity] = await Promise.all([
                u.kind === "AI" ? adminApi("/api/admin/ai/team") : [],
                u.kind === "AI" ? adminApi("/api/admin/ai/requests?agent=cfo") : [],
                api("/api/activity?limit=200"),
            ]);
        } catch (error) { toast(error.message, true); return; }

        const seat = team.find(m => m.username === u.username || m.displayName === u.displayName);
        const duties = u.kind === "AI"
            ? (seat?.responsibilities || [])
            : (u.responsibilities || "").split("\n").map(s => s.trim()).filter(Boolean);
        const assigned = tasks.filter(t => t.assignee?.id === u.id);
        const open = assigned.filter(t => !t.done).sort((a, b) => (a.dueDate || "9999") < (b.dueDate || "9999") ? -1 : 1);
        const late = open.filter(isOverdue).length;
        const finished = assigned.length - open.length;
        const projectNames = projects.filter(p => memberships.get(p.id)?.includes(u.id) || assigned.some(t => t.projectId === p.id)).map(p => p.name);
        const projectName = id => projects.find(p => p.id === id)?.name;
        const mentions = activity.filter(a => a.message.includes(`(@${u.username})`) || a.message.includes(u.displayName)).slice(0, 8);
        const [state, tone] = accountState(u);
        const row = (label, value) => h("div", { class: "detail-row" }, h("dt", null, label), h("dd", null, value));
        const section = (title, body) => h("section", { class: "profile-section" }, h("h3", { class: "detail-group" }, title), body);
        const list = (items, empty) => items.length ? h("ul", { class: "profile-list" }, items) : h("p", { class: "muted small" }, empty);

        openModal(h("div", { class: "profile" },
            h("div", { class: "profile-head" },
                FF.avatar(u, 56),
                h("div", { class: "min0" },
                    h("h2", { class: "profile-name" }, u.displayName),
                    h("div", { class: "muted" }, `@${u.username}${u.email ? " · " + u.email : ""}`),
                    h("div", { class: "row tight" }, ...FF.roleTags(u), h("span", { class: "tag " + tone }, state),
                        u.department && h("span", { class: "tag gray" }, FF.DEPARTMENTS[u.department]),
                        u.title && h("span", { class: "tag gray" }, u.title)))),
            h("div", { class: "profile-grid" },
                section(u.kind === "AI" ? "Responsibilities (AI seat)" : "Main responsibilities",
                    list(duties.map(d => h("li", null, d)),
                        "No responsibilities recorded yet. Use Edit to add them, or Suggest from title for a starting list.")),
                section("Current status", h("div", null,
                    row("Seat", u.kind === "AI" ? "AI teammate" : "Person"),
                    row("Open tasks", `${open.length}${late ? ` (${late} overdue)` : ""}`),
                    row("Finished tasks", String(finished)),
                    row("Projects", projectNames.join(", ") || "—"),
                    row("Last sign-in", u.lastLoginAt ? ago(u.lastLoginAt) : "Never")))),
            section("Assigned work", open.length
                ? h("ul", { class: "profile-list" }, open.slice(0, 12).map(t => h("li", null,
                    h("a", { href: `#/task?id=${t.id}`, onclick: closeModal }, t.title), " ",
                    h("span", { class: "tag " + (isOverdue(t) ? "red" : "gray") }, isOverdue(t) ? "Overdue" : (t.status || "Open").replace(/_/g, " ").toLowerCase()),
                    t.dueDate && h("span", { class: "muted small" }, ` · due ${fmtDate(t.dueDate)}`),
                    projectName(t.projectId) && h("span", { class: "muted small" }, ` · ${projectName(t.projectId)}`))))
                : h("p", { class: "muted small" }, "No open tasks assigned.")),
            u.kind === "AI" && section("Work requests", list(requests.slice(0, 6).map(r => h("li", null, r.title,
                h("span", { class: "muted small" }, ` · ${(r.status || "").toLowerCase()}`))), "No requests sent yet.")),
            section("Recent activity", list(mentions.map(a => h("li", null, a.message,
                h("span", { class: "muted small" }, ` · ${ago(a.createdAt)}`))), "No recent activity mentions this person.")),
            h("div", { class: "modal-actions" },
                h("button", { class: "btn", type: "button", onclick: closeModal }, "Close"),
                h("button", { class: "btn primary", type: "button", onclick: () => { closeModal(); openMemberDialog(u, me); } }, icon("pencil", 16), "Edit"))),
        { wide: true });
    }

    async function openProjectsDialog(u, projects) {
        let current;
        try {
            current = await Promise.all(projects.map(p => adminApi(`/api/admin/projects/${p.id}/members`).then(ids => [p.id, ids])));
        } catch (error) { toast(error.message, true); return; }
        const memberOf = new Map(current);
        const boxes = projects.map(p => {
            const box = h("input", { type: "checkbox", value: p.id });
            box.checked = memberOf.get(p.id).includes(u.id);
            return [p, box];
        });
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const ok = await adminAct(async () => {
                    for (const [p, box] of boxes) {
                        const ids = memberOf.get(p.id);
                        const has = ids.includes(u.id);
                        if (box.checked !== has) {
                            await adminApi(`/api/admin/projects/${p.id}/members`, { method: "PUT", body: { userIds: box.checked ? [...ids, u.id] : ids.filter(id => id !== u.id) } });
                        }
                    }
                }, "Projects updated.");
                if (ok) closeModal();
            },
        },
            h("h2", null, `${u.displayName}'s projects`),
            h("p", { class: "muted dialog-sub" }, "Members see the projects they belong to. Assigning a task also adds them to its project."),
            projects.length ? h("div", { class: "stack" }, boxes.map(([p, box]) => h("label", { class: "field check-field" }, box, p.name)))
                : h("div", { class: "empty" }, "No projects yet."),
            h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Save"))));
    }

    async function viewMembers() {
        const [work, projects, subs, me] = await Promise.all([loadWork(), api("/api/projects"), adminApi("/api/admin/submissions"), FF.ready()]);
        const { tasks, users } = work;
        const memberships = new Map(await Promise.all(projects.map(p => adminApi(`/api/admin/projects/${p.id}/members`).then(ids => [p.id, ids]))));
        const projectNames = u => projects.filter(p => memberships.get(p.id).includes(u.id) || tasks.some(t => t.projectId === p.id && t.assignee?.id === u.id)).map(p => p.name);
        const active = users.filter(u => u.active).length;

        return h("div", { class: "card" },
            h("div", { class: "section-heading" },
                h("div", { class: "muted" }, `${users.length} ${users.length === 1 ? "person" : "people"} · ${active} active`),
                h("button", { class: "btn primary", type: "button", onclick: openInviteDialog }, icon("plus", 16), "Invite member")),
            h("div", { class: "table-wrap" }, h("table", { class: "members-table" },
                h("thead", null, h("tr", null, ["Name", "Role", "Status", "Projects", "Tasks", "Submissions", "Last sign-in", ""].map(c => h("th", { scope: "col" }, c)))),
                h("tbody", null, users.map(u => {
                    const mine = tasks.filter(t => t.assignee?.id === u.id);
                    const mySubs = subs.filter(s => s.submitterUserId === u.id);
                    const [state, tag] = accountState(u);
                    return h("tr", null,
                        h("td", null, h("div", { class: "person" },
                            FF.avatar(u, 30),
                            h("div", null, h("button", { class: "name-link", type: "button", onclick: () => openMemberProfile(u, { tasks, projects, memberships, me }) }, u.displayName), u.id === me.id && h("span", { class: "muted" }, " (you)"),
                                h("div", { class: "muted" }, "@" + u.username + (u.email ? ` · ${u.email}` : "")),
                                FF.jobLine(u) && h("div", { class: "muted small" }, FF.jobLine(u))))),
                        h("td", null, h("div", { class: "row tight" }, ...FF.roleTags(u))),
                        h("td", null, h("span", { class: "tag " + tag }, state)),
                        h("td", { class: "wrap" }, projectNames(u).join(", ") || h("span", { class: "muted" }, "—")),
                        h("td", null, mine.length
                            ? h("a", { href: `#/assignments?member=${u.id}` }, `${mine.filter(t => !t.done).length} open`, mine.some(isOverdue) && h("span", { class: "tag red", style: "margin-left:6px" }, `${mine.filter(isOverdue).length} late`))
                            : h("span", { class: "muted" }, "—")),
                        h("td", null, mySubs.length ? ["SUBMITTED", "REJECTED", "APPROVED"].filter(s => mySubs.some(x => x.status === s))
                            .map(s => h("span", { class: "tag " + FF.SUB_STATUS[s][1], style: "margin-right:4px" }, `${mySubs.filter(x => x.status === s).length} ${FF.SUB_STATUS[s][0].toLowerCase()}`))
                            : h("span", { class: "muted" }, "—")),
                        h("td", null, u.lastLoginAt ? ago(u.lastLoginAt) : h("span", { class: "muted" }, "Never")),
                        h("td", null, h("div", { class: "task-actions" },
                            h("button", { class: "icon-btn", type: "button", title: "Profile", "aria-label": `Profile of ${u.displayName}`, onclick: () => openMemberProfile(u, { tasks, projects, memberships, me }) }, icon("user", 16)),
                            h("button", { class: "icon-btn", type: "button", title: "Edit", "aria-label": `Edit ${u.displayName}`, onclick: () => openMemberDialog(u, me) }, icon("pencil", 16)),
                            h("button", { class: "icon-btn", type: "button", title: "Projects", "aria-label": `Projects of ${u.displayName}`, onclick: () => openProjectsDialog(u, projects) }, icon("folder", 16)),
                            u.id !== me.id && h("button", {
                                class: "icon-btn", type: "button", title: "Reset access", "aria-label": `Reset access for ${u.displayName}`,
                                onclick: async () => {
                                    if (!confirm(`Reset access for ${u.displayName}? Their current password stops working until they use the new code.`)) return;
                                    try { showCode("New access code", await adminApi(`/api/admin/users/${u.id}/reset-access`, { method: "POST" })); } catch (e) { toast(e.message, true); }
                                },
                            }, icon("key", 16)))));
                })))));
    }

    /* ---------- Task Assignment ---------- */

    const assignState = { filter: "open" };
    const ASSIGN_FILTERS = [
        ["open", "Open", t => !t.done],
        ["unassigned", "Unassigned", t => !t.done && !t.assignee],
        ["progress", "In progress", t => !t.done && (t.status === "IN_PROGRESS" || t.status === "REVIEW")],
        ["overdue", "Overdue", isOverdue],
        ["done", "Done", t => t.done],
        ["all", "All", () => true],
    ];

    async function viewAssignments(params) {
        if (params.get("filter")) {
            assignState.filter = ASSIGN_FILTERS.some(([k]) => k === params.get("filter")) ? params.get("filter") : "open";
            params.delete("filter");
            history.replaceState(null, "", "#/assignments" + (params.toString() ? "?" + params : ""));
        }
        const [work, projects] = await Promise.all([loadWork(), api("/api/projects")]);
        const { tasks, users } = work;
        const projectId = params.get("project") || "";
        const memberId = params.get("member") || "";
        const scoped = tasks.filter(t => (!projectId || String(t.projectId) === projectId) && (!memberId || String(t.assignee?.id) === memberId));
        const test = ASSIGN_FILTERS.find(([k]) => k === assignState.filter)[2];
        const visible = sortTasks(scoped.filter(test));
        const people = users.filter(u => u.active);
        const go = (key, value) => { const p = new URLSearchParams(params); value ? p.set(key, value) : p.delete(key); location.hash = "#/assignments" + (p.toString() ? "?" + p : ""); };

        return h("div", { class: "card" },
            h("div", { class: "toolbar" },
                h("div", { class: "filters" }, ASSIGN_FILTERS.map(([key, label, match]) => h("button", {
                    class: "filter", type: "button", "aria-pressed": String(assignState.filter === key),
                    onclick: () => { assignState.filter = key; refresh(); },
                }, `${label} (${scoped.filter(match).length})`)))),
            h("div", { class: "toolbar" },
                h("select", { "aria-label": "Filter by project", value: projectId, onchange: e => go("project", e.target.value) },
                    h("option", { value: "" }, "All projects"), projects.map(p => h("option", { value: p.id }, p.name))),
                h("select", { "aria-label": "Filter by member", value: memberId, onchange: e => go("member", e.target.value) },
                    h("option", { value: "" }, "Everyone"), users.map(u => h("option", { value: u.id }, u.displayName))),
                h("button", { class: "btn", type: "button", onclick: () => openTaskDialog(null, projectId ? { projectId: Number(projectId) } : {}) }, icon("plus", 16), "New task")),
            visible.length
                ? h("div", { class: "table-wrap" }, h("table", { class: "assign-table" },
                    h("thead", null, h("tr", null, ["Task", "Project", "Priority", "Status", "Deadline", "Steps", "Responsible"].map(c => h("th", { scope: "col" }, c)))),
                    h("tbody", null, visible.map(t => {
                        const select = h("select", {
                            class: "assignee-select" + (t.assignee ? "" : " empty"), "aria-label": `Responsible for ${t.title}`,
                            value: t.assignee ? String(t.assignee.id) : "",
                            onchange: () => adminAct(() => adminApi(`/api/admin/tasks/${t.id}/assignee`, { method: "PUT", body: { userId: select.value ? Number(select.value) : null } }),
                                select.value ? `Assigned to ${select.selectedOptions[0].textContent}.` : "Unassigned."),
                        }, h("option", { value: "" }, "— Unassigned —"),
                            people.map(u => h("option", { value: u.id }, u.displayName + (u.role === "ADMIN" ? " (admin)" : ""))),
                            t.assignee && !t.assignee.active && h("option", { value: t.assignee.id }, `${t.assignee.displayName} (deactivated)`));
                        return h("tr", { class: isOverdue(t) ? "row-late" : null },
                            h("td", { class: "wrap" }, h("a", { href: `#/task?id=${t.id}` }, t.title)),
                            h("td", { class: "wrap" }, t.projectName || h("span", { class: "muted" }, "—")),
                            h("td", null, h("span", { class: "tag " + PRIORITY_TAG[t.priority] }, t.priority)),
                            h("td", null, t.done ? h("span", { class: "tag green" }, "Done") : statusTag(WORK_STATUS, t.status || "NOT_STARTED")),
                            h("td", null, t.dueDate ? h("span", { class: "tag " + (isOverdue(t) ? "red" : "gray") }, `${fmtDate(t.dueDate)} · ${dayLabel(t.dueDate)}`) : h("span", { class: "muted" }, "—")),
                            h("td", null, t.stepCount ? `${t.completedStepCount}/${t.stepCount}` : h("span", { class: "muted" }, "—")),
                            h("td", null, select));
                    }))))
                : h("div", { class: "empty" }, "No tasks match this filter."));
    }

    /* ---------- Submission Tracker ---------- */

    async function viewTracker(params) {
        const projects = await api("/api/projects");
        if (!projects.length) return h("div", { class: "card empty" }, "Create a project first.");
        const projectId = params.get("project") || String(projects[0].id);
        const t = await adminApi(`/api/admin/tracker?projectId=${projectId}`);
        const people = t.people.filter(p => p.role === "MEMBER" || Object.values(t.cells).some(c => c[p.id]));
        const today = todayIso();
        const cell = (phase, person) => {
            const status = t.cells[phase.id]?.[person.id];
            if (status) return h("a", { href: `#/submissions?phase=${phase.id}&q=${encodeURIComponent(person.displayName)}` }, subTag(status));
            const due = phase.startDate <= today;
            return h("span", { class: "tag " + (phase.endDate < today ? "red" : due ? "amber" : "gray") }, phase.endDate < today ? "Missing" : due ? "Not yet" : "—");
        };
        return h("div", { class: "card" },
            h("div", { class: "toolbar" },
                h("select", { "aria-label": "Project", value: projectId, onchange: e => { location.hash = `#/tracker?project=${e.target.value}`; } },
                    projects.map(p => h("option", { value: p.id }, p.name))),
                h("span", { class: "muted" }, "Who has handed in work for each phase. Members appear here once they are on the project.")),
            !t.phases.length ? h("div", { class: "empty" }, "This project has no schedule phases yet.")
                : !people.length ? h("div", { class: "empty" }, "Nobody is on this project yet. Add members or assign tasks first.")
                    : h("div", { class: "table-wrap" }, h("table", { class: "tracker-table" },
                        h("thead", null, h("tr", null, h("th", { scope: "col" }, "Member"), t.phases.map(ph => {
                            const [state, tag] = phaseState(ph);
                            return h("th", { scope: "col" }, h("div", null, ph.name), h("div", { class: "muted th-sub" }, `${fmtDate(ph.startDate)} – ${fmtDate(ph.endDate)}`), h("span", { class: "tag " + tag }, state));
                        }))),
                        h("tbody", null,
                            people.map(person => h("tr", null,
                                h("td", null, h("strong", null, person.displayName), person.role === "ADMIN" && h("span", { class: "muted" }, " (admin)")),
                                t.phases.map(ph => h("td", null, cell(ph, person))))),
                            h("tr", { class: "tracker-total" }, h("td", null, "Submitted"),
                                t.phases.map(ph => h("td", null, `${people.filter(p => t.cells[ph.id]?.[p.id]).length} / ${people.length}`)))))),
            h("div", { class: "legend" }, subTag("APPROVED"), subTag("SUBMITTED"), subTag("REJECTED"),
                h("span", { class: "tag amber" }, "Not yet (phase running)"), h("span", { class: "tag red" }, "Missing (phase ended)")));
    }

    /* ---------- Files ---------- */

    const fileState = { q: "" };

    async function viewFiles(params) {
        const [files, projects] = await Promise.all([adminApi("/api/admin/files"), api("/api/projects")]);
        const projectId = params.get("project") || "";
        const q = fileState.q.toLowerCase();
        const visible = files.filter(f => (!projectId || String(f.projectId) === projectId)
            && (!q || [f.originalName, f.submissionTitle, f.submitterName, f.phaseName].some(v => v && v.toLowerCase().includes(q))));
        const search = h("input", { type: "search", placeholder: "Search file, submission or person…", value: fileState.q, "aria-label": "Search files" });
        return h("div", { class: "card" },
            h("div", { class: "toolbar" },
                h("select", { "aria-label": "Project", value: projectId, onchange: e => { location.hash = e.target.value ? `#/files?project=${e.target.value}` : "#/files"; } },
                    h("option", { value: "" }, "All projects"), projects.map(p => h("option", { value: p.id }, p.name))),
                h("form", { class: "search-form", onsubmit: e => { e.preventDefault(); fileState.q = search.value.trim(); refresh(); } }, search),
                h("span", { class: "muted" }, `${visible.length} ${visible.length === 1 ? "file" : "files"} · ${fmtBytes(visible.reduce((n, f) => n + f.sizeBytes, 0))}`)),
            visible.length
                ? h("div", { class: "table-wrap" }, h("table", { class: "files-table" },
                    h("thead", null, h("tr", null, ["File", "Submission", "Phase", "Submitted by", "Version", "Size", "Uploaded"].map(c => h("th", { scope: "col" }, c)))),
                    h("tbody", null, visible.map(f => h("tr", null,
                        h("td", { class: "wrap" }, h("a", { class: "file-link", href: `/api/admin/submission-files/${f.id}`, download: f.originalName }, icon("download", 14), f.originalName)),
                        h("td", { class: "wrap" }, h("a", { href: `#/submission?id=${f.submissionId}` }, f.submissionTitle), " ", subTag(f.status)),
                        h("td", null, f.phaseName || "—"),
                        h("td", null, f.submitterName),
                        h("td", null, `v${f.version}`),
                        h("td", null, fmtBytes(f.sizeBytes)),
                        h("td", null, FF.fmtStamp(f.uploadedAt)))))))
                : h("div", { class: "empty" }, files.length ? "No files match." : "No files have been uploaded yet."));
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
        if (params.has("q")) {
            subState.q = params.get("q");
            params.delete("q");
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
        admin: ["Company Overview", "grid", viewAdmin],
        members: ["Members", "users", viewMembers],
        assignments: ["Task Assignment", "target", viewAssignments],
        submissions: ["Review Queue", "upload", viewSubmissions],
        submission: ["Submission Details", "file", viewSubmission],
        tracker: ["Submission Tracker", "check", viewTracker],
        files: ["Files", "file", viewFiles],
        "admin-account": ["Account", "user", () => FF.viewAccount()],
        "ai-team": ["AI Team", "briefcase", params => window.AiTeam.view(params)],
        legal: ["Legal & Compliance", "check", params => window.Legal.view(params)],
        security: ["AI Security", "activity", () => window.Security.view()],
    };
    Object.assign(ROUTES, ADMIN_ROUTES);
    // The original pages stay as they are; in a company workspace these two read better under clearer names.
    ROUTES["my-work"][0] = "All Tasks";
    ROUTES.overview[0] = "Workspace Overview";

    FF.buildNav([
        ["MANAGEMENT", [["admin", "Company Overview", "grid"], ["members", "Members", "users"], ["assignments", "Task Assignment", "target"], ["ai-team", "AI Team", "briefcase"], ["legal", "Legal & Compliance", "check"], ["security", "AI Security", "activity"]]],
        ["REVIEW", [["submissions", "Review Queue", "upload"], ["tracker", "Submission Tracker", "check"], ["files", "Files", "file"]]],
        ["PROJECTS & WORK", [["projects", "Projects", "folder"], ["project", "Project Details", "briefcase"], ["my-work", "All Tasks", "work"],
            ["task", "Task Details", "list"], ["calendar", "Work Calendar", "calendar"], ["templates", "Workflow Templates", "layers"], ["reports", "Reports", "file"]]],
        ["OPERATIONS", [["overview", "Workspace Overview", "overview"], ["attendance", "Attendance", "clock"], ["activity", "Activity Log", "activity"], ["settings", "Settings", "settings"]]],
    ]);

    // Opening the admin workspace with no page chosen lands on the company overview.
    if (!location.hash || location.hash === "#" || location.hash === "#/") history.replaceState(null, "", "#/admin");

    FF.mountHeader({
        role: "ADMIN",
        links: [["#/admin", "grid", "Company Overview"], ["#/submissions", "upload", "Review Queue"], ["#/members", "users", "Members"], ["#/admin-account", "user", "Account settings"]],
    });
})();
