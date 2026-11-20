"use strict";

/*
 * MEMBER workspace (member.html), loaded after app.js, workspace.js and shell.js.
 * Built around one idea: help a member focus on the work assigned to them. It reuses the shared
 * helpers (task sorting and filters, the calendar grid, work-step styles, phase states) and talks
 * only to /api/member/*, which the server scopes to the signed-in member.
 * The admin pages that app.js registered are removed from ROUTES, so they are never rendered here.
 */
(() => {
    const { call, ready, subTag, filePicker, fileList, historyList, fmtStamp, fmtBytes } = FF;

    /* ---------- Small helpers ---------- */

    const greeting = () => { const hr = new Date().getHours(); return hr < 12 ? "Good morning" : hr < 18 ? "Good afternoon" : "Good evening"; };
    const dueTag = t => t.dueDate && h("span", { class: "tag " + (isOverdue(t) ? "red" : daysUntil(t.dueDate) <= 2 && !t.done ? "amber" : "blue") },
        isOverdue(t) ? `Overdue · ${fmtDate(t.dueDate)}` : `Due ${fmtDate(t.dueDate)} · ${dayLabel(t.dueDate)}`);
    const statusOptions = () => Object.entries(WORK_STATUS).map(([v, [label]]) => h("option", { value: v }, label));

    function currentPhase(phases) {
        const today = todayIso();
        return phases.find(p => p.startDate <= today && today <= p.endDate) || phases.find(p => p.startDate > today) || phases[phases.length - 1];
    }

    function setDone(task, done) {
        return FF.change(() => call(`/api/member/tasks/${task.id}/done`, { method: "PATCH", body: { done } }), done ? "Nice — task completed." : "Task reopened.");
    }

    /** A task row for the member: complete it, open it, see why it matters now. */
    function taskRow(task) {
        const box = h("input", { type: "checkbox", "aria-label": `Mark "${task.title}" as complete`, onchange: () => setDone(task, box.checked) });
        box.checked = task.done;
        return h("div", { class: "task" + (task.done ? " done" : "") + (isOverdue(task) ? " overdue" : "") },
            box,
            h("div", { class: "task-info" },
                h("div", { class: "task-name" }, h("a", { class: "task-link", href: `#/task?id=${task.id}` }, task.title)),
                h("div", { class: "task-meta" },
                    h("span", { class: "tag " + PRIORITY_TAG[task.priority] }, `${task.priority} PRIORITY`),
                    !task.done && task.status && task.status !== "NOT_STARTED" && statusTag(WORK_STATUS, task.status),
                    task.stepCount > 0 && h("span", { class: "tag gray" }, `${task.completedStepCount}/${task.stepCount} steps`),
                    !task.done && dueTag(task),
                    task.projectName && h("a", { class: "tag", href: `#/project?id=${task.projectId}` }, task.projectName))),
            h("a", { class: "icon-btn", href: `#/task?id=${task.id}`, "aria-label": `Open "${task.title}"` }, icon("right", 16)));
    }

    function submissionRow(sub) {
        return h("a", { class: "sub-row link-row" + (sub.status === "REJECTED" ? " needs-action" : ""), href: `#/submission?id=${sub.id}` },
            h("span", { class: "sub-icon" }, icon("file", 16)),
            h("div", { class: "task-info" },
                h("div", { class: "task-name" }, sub.title),
                h("div", { class: "task-meta" },
                    subTag(sub.status),
                    h("span", { class: "tag teal" }, sub.phaseName || "Removed phase"),
                    sub.currentVersion > 1 && h("span", { class: "tag gray" }, `v${sub.currentVersion}`),
                    h("span", { class: "tag gray" }, `${sub.fileCount} ${sub.fileCount === 1 ? "file" : "files"}`),
                    sub.late && h("span", { class: "tag red" }, "Late")),
                sub.status === "REJECTED" && sub.reviewComment && h("div", { class: "reject-line" }, icon("alert", 14), sub.reviewComment)),
            h("span", { class: "muted sub-when" }, fmtDate(sub.submittedAt.slice(0, 10))));
    }

    /* ---------- Submitting work ---------- */

    async function openSubmitDialog(presets = {}) {
        let projects, phases, tasks;
        try {
            [projects, phases, tasks] = await Promise.all([call("/api/member/projects"), call("/api/member/phases"), call("/api/member/tasks")]);
        } catch (error) { toast(error.message, true); return; }
        const withPhases = projects.map(p => p.project).filter(p => phases.some(ph => ph.projectId === p.id));
        if (!withPhases.length) {
            toast("None of your projects has a schedule yet, so there is nowhere to submit. Ask your administrator.", true);
            return;
        }
        const presetTask = tasks.find(t => t.id === presets.taskId);
        const startProject = String(presets.projectId ?? presetTask?.projectId ?? phases.find(p => p.id === presets.phaseId)?.projectId ?? withPhases[0].id);
        const project = h("select", { required: true, value: startProject, onchange: () => fill(null) }, withPhases.map(p => h("option", { value: p.id }, p.name)));
        const phase = h("select", { required: true, onchange: () => suggest() });
        const task = h("select");
        const title = h("input", { type: "text", required: true, maxlength: 200 });
        const note = h("textarea", { maxlength: 5000, placeholder: "What are you handing in? Anything the reviewer should know?" });
        const picker = filePicker();
        const submit = h("button", { class: "btn primary", type: "submit" }, icon("upload", 16), "Submit for review");
        let touched = false;
        title.addEventListener("input", () => { touched = true; });

        const phasesOf = () => phases.filter(p => String(p.projectId) === project.value);
        function suggest() {
            if (touched) return;
            const p = phases.find(x => String(x.id) === phase.value);
            const t = tasks.find(x => String(x.id) === task.value);
            title.value = t ? `${p ? p.name + " · " : ""}${t.title}` : p ? `${p.name} submission` : "";
        }
        function fill(phaseId) {
            const list = phasesOf();
            phase.replaceChildren(...list.map(p => h("option", { value: p.id }, `${p.name} · ${fmtDate(p.startDate)} – ${fmtDate(p.endDate)}`)));
            phase.value = String(phaseId ?? currentPhase(list)?.id ?? "");
            task.replaceChildren(h("option", { value: "" }, "No specific task"),
                ...sortTasks(tasks.filter(t => String(t.projectId) === project.value)).map(t => h("option", { value: t.id }, t.title)));
            task.value = presetTask && String(presetTask.projectId) === project.value ? String(presetTask.id) : "";
            suggest();
        }
        task.addEventListener("change", suggest);
        fill(presets.phaseId);

        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const problem = picker.problem();
                if (problem) { toast(problem, true); return; }
                const data = new FormData();
                data.append("projectId", project.value);
                data.append("phaseId", phase.value);
                if (task.value) data.append("taskId", task.value);
                data.append("title", title.value);
                data.append("note", note.value);
                [...picker.input.files].forEach(f => data.append("files", f, f.name));
                submit.disabled = true;
                submit.replaceChildren("Uploading…");
                let created;
                const ok = await FF.change(async () => { created = await call("/api/member/submissions", { method: "POST", form: data }); }, "Submitted for review.");
                if (ok) { closeModal(); location.hash = `#/submission?id=${created.id}`; }
                else { submit.disabled = false; submit.replaceChildren(icon("upload", 16), "Submit for review"); }
            },
        },
            h("h2", null, "Submit work"),
            h("div", { class: "form-grid" },
                h("label", { class: "field" }, "Project", project),
                h("label", { class: "field" }, "Phase (회차)", phase),
                h("label", { class: "field full" }, "Related task", task),
                h("label", { class: "field full" }, "Title", title),
                h("label", { class: "field full" }, "Note", note),
                picker.field),
            h("div", { class: "modal-actions" }, cancelButton(), submit)));
    }

    function openResubmitDialog(sub) {
        const note = h("textarea", { maxlength: 5000, placeholder: "What did you change?" });
        const picker = filePicker();
        const label = `Submit version ${sub.currentVersion + 1}`;
        const submit = h("button", { class: "btn primary", type: "submit" }, icon("upload", 16), label);
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const problem = picker.problem();
                if (problem) { toast(problem, true); return; }
                const data = new FormData();
                data.append("note", note.value);
                [...picker.input.files].forEach(f => data.append("files", f, f.name));
                submit.disabled = true;
                submit.replaceChildren("Uploading…");
                if (await FF.change(() => call(`/api/member/submissions/${sub.id}/resubmit`, { method: "POST", form: data }), "Resubmitted for review.")) closeModal();
                else { submit.disabled = false; submit.replaceChildren(icon("upload", 16), label); }
            },
        },
            h("h2", null, "Resubmit"),
            sub.reviewComment && h("div", { class: "review-note rejected" }, h("strong", null, "What to fix: "), sub.reviewComment),
            h("div", { class: "form-grid" }, h("label", { class: "field full" }, "Note", note), picker.field),
            h("div", { class: "modal-actions" }, cancelButton(), submit)));
    }

    /* ---------- My Overview ---------- */

    async function viewOverview() {
        const user = await ready();
        const month = monthKey(new Date());
        const [tasks, projects, subs, thisMonth, nextMonth] = await Promise.all([
            call("/api/member/tasks"), call("/api/member/projects"), call("/api/member/submissions"),
            call(`/api/member/calendar?month=${month}`), call(`/api/member/calendar?month=${shiftMonth(month, 1)}`)]);

        const open = sortTasks(tasks.filter(t => !t.done));
        const next = open[0];
        const done = tasks.filter(t => t.done).length;
        const overdue = open.filter(isOverdue);
        const dueSoon = open.filter(t => t.dueDate && !isOverdue(t) && daysUntil(t.dueDate) <= 3);
        const rejected = subs.filter(s => s.status === "REJECTED");
        const pending = subs.filter(s => s.status === "SUBMITTED");
        const today = todayIso();
        const horizon = isoDate(new Date(Date.now() + 21 * 86400000));
        const upcoming = [...thisMonth, ...nextMonth].filter(e => !e.done && e.date >= today && e.date <= horizon)
            .sort((a, b) => a.date.localeCompare(b.date)).slice(0, 7);

        const attention = [];
        if (rejected.length) attention.push(h("a", { class: "attention red", href: "#/submissions?status=REJECTED" }, icon("alert", 16),
            h("span", null, h("strong", null, `${rejected.length} ${rejected.length === 1 ? "submission needs" : "submissions need"} changes.`), " See the reason and resubmit.")));
        if (overdue.length) attention.push(h("a", { class: "attention amber", href: "#/tasks?filter=overdue" }, icon("clock", 16),
            h("span", null, h("strong", null, `${overdue.length} overdue ${overdue.length === 1 ? "task" : "tasks"}.`), " Start with the oldest.")));

        return h("div", null,
            h("section", { class: "welcome" },
                h("div", { class: "eyebrow" }, "MY WORKSPACE"),
                h("h1", null, `${greeting()}, ${user.displayName}.`),
                h("p", { class: "subtitle" }, "Make room for what matters. Here is what needs you next.")),

            attention.length > 0 && h("div", { class: "attention-list" }, attention),

            h("section", { class: "dashboard" },
                h("div", { class: "card focus-card" },
                    h("div", { class: "eyebrow" }, "NEXT BEST ACTION"),
                    h("div", { class: "focus-task" }, next ? next.title : tasks.length ? "Everything assigned to you is done." : "No tasks assigned yet."),
                    h("p", { class: "muted" }, next
                        ? [next.projectName ? `${next.projectName} · ` : "", isOverdue(next) ? "Overdue — this comes first." : next.dueDate ? `Due ${fmtDateLong(next.dueDate)}.` : "Your highest-priority open task."]
                        : tasks.length ? "Take a breath, or check your submissions." : "When your administrator assigns work to you, it appears here."),
                    next && h("div", { class: "task-meta", style: "margin-top:12px" },
                        h("span", { class: "tag " + PRIORITY_TAG[next.priority] }, `${next.priority} PRIORITY`),
                        dueTag(next),
                        next.stepCount > 0 && h("span", { class: "tag gray" }, `${next.completedStepCount}/${next.stepCount} steps`)),
                    next && h("div", { class: "row focus-actions" },
                        h("a", { class: "btn primary", href: `#/task?id=${next.id}` }, "Open task →"),
                        h("button", { class: "btn", type: "button", onclick: () => setDone(next, true) }, icon("check", 16), "Mark complete"))),
                h("div", null,
                    h("div", { class: "card" },
                        h("h2", null, "My Progress"),
                        h("div", { class: "progress-caption" }, h("span", null, "Completed tasks"), h("span", null, `${done} / ${tasks.length}`)),
                        progressBar(pct(done, tasks.length)),
                        h("p", { class: "muted progress-message" },
                            !tasks.length ? "Nothing assigned yet." : done === tasks.length ? "All of your tasks are complete!" : done === 0 ? "Even a small start counts as progress." : "Every finished task moves the team forward.")),
                    h("div", { class: "stats" },
                        h("div", { class: "stat" }, h("div", { class: "stat-number" }, open.length), h("div", { class: "stat-label" }, "OPEN TASKS")),
                        h("div", { class: "stat" + (dueSoon.length + overdue.length ? " warn" : "") },
                            h("div", { class: "stat-number" }, dueSoon.length + overdue.length), h("div", { class: "stat-label" }, "DUE IN 3 DAYS / OVERDUE"))))),

            h("section", { class: "card block-gap" },
                sectionHead("Priority Ranking", h("a", { href: "#/tasks" }, "All my tasks")),
                open.length ? h("div", { class: "task-list" }, open.slice(0, 6).map(taskRow)) : h("div", { class: "empty" }, "No open tasks.")),

            h("section", { class: "two-col" },
                h("div", { class: "card" },
                    sectionHead("Upcoming Deadlines", h("a", { href: "#/calendar" }, "Calendar")),
                    upcoming.length
                        ? h("div", { class: "deadline-list" }, upcoming.map(e => h("a", { class: "deadline-row", href: e.taskId != null ? `#/task?id=${e.taskId}` : `#/project?id=${e.projectId}` },
                            h("span", { class: "tag " + (daysUntil(e.date) <= 2 ? "red" : "gray") }, dayLabel(e.date)),
                            h("span", { class: "deadline-title" }, e.title),
                            h("span", { class: "tag " + (ENTRY_TAG[e.type] || "") }, ENTRY_LABEL[e.type] === "My responsibility" ? "Deadline" : ENTRY_LABEL[e.type]),
                            h("span", { class: "muted deadline-date" }, fmtDate(e.date)))))
                        : h("div", { class: "empty" }, "Nothing due in the next three weeks.")),
                h("div", { class: "card" },
                    sectionHead("My Submissions",
                        h("button", { class: "btn primary", type: "button", onclick: () => openSubmitDialog() }, icon("upload", 16), "Submit work")),
                    h("div", { class: "sub-counts" },
                        ["SUBMITTED", "APPROVED", "REJECTED"].map(s => h("a", { class: "sub-count", href: `#/submissions?status=${s}` },
                            h("strong", null, subs.filter(x => x.status === s).length), subTag(s)))),
                    subs.length ? h("div", { class: "task-list" }, subs.slice(0, 3).map(submissionRow)) : h("div", { class: "empty" }, "You haven't submitted anything yet."),
                    pending.length > 0 && h("p", { class: "muted", style: "margin-top:10px" }, `${pending.length} waiting for review.`))),

            h("section", { class: "card block-gap" },
                sectionHead("My Projects", h("a", { href: "#/projects" }, "View all")),
                projects.length
                    ? projects.map(({ project: p, myTasks, myDone }) => h("a", { class: "mini-project", href: `#/project?id=${p.id}` },
                        h("div", { class: "mini-project-top" }, h("strong", null, p.name), h("span", { class: "muted" }, `${myDone} / ${myTasks} of my tasks`)),
                        progressBar(pct(myDone, myTasks))))
                    : h("div", { class: "empty" }, "You're not on a project yet.")));
    }

    /* ---------- My Tasks ---------- */

    const taskState = { filter: "all" };

    async function viewTasks(params) {
        if (params.get("filter")) {
            taskState.filter = WORK_FILTERS.some(([k]) => k === params.get("filter")) ? params.get("filter") : "all";
            history.replaceState(null, "", "#/tasks");
        }
        const [tasks, projects] = await Promise.all([call("/api/member/tasks"), call("/api/member/projects")]);
        const projectId = params.get("project") || "";
        const test = WORK_FILTERS.find(([k]) => k === taskState.filter)[2];
        const visible = sortTasks(tasks.filter(t => test(t) && (!projectId || String(t.projectId) === projectId)));
        return h("div", { class: "card" },
            h("div", { class: "toolbar" },
                h("div", { class: "filters" }, WORK_FILTERS.map(([key, label, match]) => h("button", {
                    class: "filter", type: "button", "aria-pressed": String(taskState.filter === key),
                    onclick: () => { taskState.filter = key; refresh(); },
                }, `${label} (${tasks.filter(match).length})`))),
                h("select", {
                    "aria-label": "Filter by project", value: projectId,
                    onchange: e => { location.hash = e.target.value ? `#/tasks?project=${e.target.value}` : "#/tasks"; },
                }, h("option", { value: "" }, "All my projects"), projects.map(({ project: p }) => h("option", { value: p.id }, p.name)))),
            visible.length ? h("div", { class: "task-list" }, visible.map(taskRow))
                : h("div", { class: "empty" }, tasks.length ? "No tasks match this filter." : "No tasks are assigned to you yet."));
    }

    /* ---------- Task details and work steps ---------- */

    function stepBody(step, patch) {
        return {
            title: step.title, description: step.description, status: step.status, startDate: step.startDate,
            completedDate: step.completedDate, progress: step.progress, result: step.result, errorNote: step.errorNote,
            link: step.link, memo: step.memo, ...patch,
        };
    }

    function openStepDialog(task, step) {
        const title = h("input", { type: "text", required: true, maxlength: 200, value: step?.title || "" });
        const description = h("textarea", { maxlength: 5000, value: step?.description || "" });
        const status = h("select", { value: step?.status || "NOT_STARTED" }, statusOptions());
        const progress = h("input", { type: "number", min: 0, max: 100, value: step?.progress ?? 0 });
        const result = h("textarea", { maxlength: 5000, value: step?.result || "", placeholder: "What came out of this step" });
        const link = h("input", { type: "text", maxlength: 1000, value: step?.link || "", placeholder: "https://…" });
        const memo = h("textarea", { maxlength: 5000, value: step?.memo || "" });
        openModal(h("form", {
            onsubmit: async event => {
                event.preventDefault();
                const body = stepBody(step || {}, {
                    title: title.value, description: description.value, status: status.value,
                    progress: Number(progress.value), result: result.value, link: link.value, memo: memo.value,
                });
                const ok = await FF.change(() => step
                    ? call(`/api/member/steps/${step.id}`, { method: "PUT", body })
                    : call(`/api/member/tasks/${task.id}/steps`, { method: "POST", body }), step ? "Step saved." : "Step added.");
                if (ok) closeModal();
            },
        },
            h("h2", null, step ? "Edit step" : "New step"),
            h("div", { class: "form-grid" },
                h("label", { class: "field full" }, "Title", title),
                h("label", { class: "field full" }, "Description", description),
                h("label", { class: "field" }, "Status", status),
                h("label", { class: "field" }, "Progress (%)", progress),
                h("label", { class: "field full" }, "Result", result),
                h("label", { class: "field full" }, "Link", link),
                h("label", { class: "field full" }, "Memo", memo)),
            h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Save"))));
    }

    function stepRow(task, step, index, total) {
        const status = h("select", {
            class: "step-status", value: step.status, "aria-label": `Status of ${step.title}`,
            onchange: () => FF.change(() => call(`/api/member/steps/${step.id}`, { method: "PUT", body: stepBody(step, { status: status.value }) })),
        }, statusOptions());
        const move = direction => FF.change(() => call(`/api/member/steps/${step.id}/move`, { method: "POST", body: { direction } }));
        return h("div", { class: "step" + (step.status === "COMPLETED" ? " done" : "") },
            h("div", { class: "step-main" },
                h("span", { class: "step-num" }, index + 1),
                h("div", { class: "task-info" },
                    h("div", { class: "task-name" }, step.title),
                    (step.description || step.result || step.link) && h("div", { class: "muted step-note" },
                        step.result ? `Result: ${step.result}` : step.description,
                        step.link && /^https?:\/\//.test(step.link) && h("a", { href: step.link, target: "_blank", rel: "noopener noreferrer", class: "step-link" }, " · link"))),
                h("div", { class: "step-tools" },
                    status,
                    h("button", { class: "icon-btn", type: "button", "aria-label": "Move up", disabled: index === 0, onclick: () => move(-1) }, icon("up", 16)),
                    h("button", { class: "icon-btn", type: "button", "aria-label": "Move down", disabled: index === total - 1, onclick: () => move(1) }, icon("down", 16)),
                    h("button", { class: "icon-btn", type: "button", "aria-label": "Edit step", onclick: () => openStepDialog(task, step) }, icon("pencil", 16)),
                    h("button", {
                        class: "icon-btn danger", type: "button", "aria-label": "Delete step",
                        onclick: () => { if (confirm(`Delete step "${step.title}"?`)) FF.change(() => call(`/api/member/steps/${step.id}`, { method: "DELETE" }), "Step deleted."); },
                    }, icon("trash", 16)))));
    }

    async function viewTask(params) {
        const id = params.get("id");
        if (!id || !/^\d+$/.test(id)) {
            return h("div", { class: "card empty" }, "Choose a task from ", h("a", { href: "#/tasks" }, "My Tasks"), ".");
        }
        const [{ task, steps }, subs] = await Promise.all([call(`/api/member/tasks/${id}`), call("/api/member/submissions")]);
        const mySubs = subs.filter(s => s.taskId === task.id);
        const status = h("select", {
            value: task.status || "NOT_STARTED", "aria-label": "Task status",
            onchange: () => FF.change(() => call(`/api/member/tasks/${task.id}/status`, { method: "PATCH", body: { status: status.value } }), "Status updated."),
        }, statusOptions());
        const addTitle = h("input", { type: "text", maxlength: 200, placeholder: "Add the next step…", "aria-label": "New step title" });

        return h("div", { class: "stack" },
            h("div", null, h("a", { href: "#/tasks" }, "← My tasks")),
            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    h("div", null,
                        h("h2", { class: "detail-title" }, task.title),
                        h("div", { class: "task-meta" },
                            h("span", { class: "tag " + PRIORITY_TAG[task.priority] }, `${task.priority} PRIORITY`),
                            task.done ? h("span", { class: "tag green" }, "Completed") : dueTag(task),
                            task.projectName && h("a", { class: "tag", href: `#/project?id=${task.projectId}` }, task.projectName))),
                    h("div", { class: "row" },
                        h("label", { class: "field inline-field" }, "Status", status),
                        task.done
                            ? h("button", { class: "btn", type: "button", onclick: () => setDone(task, false) }, icon("undo", 16), "Reopen")
                            : h("button", { class: "btn primary", type: "button", onclick: () => setDone(task, true) }, icon("check", 16), "Mark complete"))),
                h("div", { class: "progress-caption" }, h("span", null, "Progress"), h("span", null, `${task.progress}%`)),
                progressBar(task.progress),
                detailList([
                    ["Description", task.description],
                    ["Deadline", task.dueDate ? fmtDateLong(task.dueDate) : null],
                    ["Scheduled for", task.scheduledDate ? fmtDateLong(task.scheduledDate) : null],
                    ["Responsibility", task.responsibilityTitle],
                ])),

            renderDiscussion(task.id, { base: "member" }),
            h("div", { class: "card" },
                sectionHead("Work Sequence", h("span", { class: "muted" }, `${task.completedStepCount} of ${task.stepCount} done`)),
                steps.length ? h("div", { class: "task-list" }, steps.map((s, i) => stepRow(task, s, i, steps.length)))
                    : h("div", { class: "empty" }, "Break the task into steps to see your progress."),
                h("form", {
                    class: "step-add",
                    onsubmit: async event => {
                        event.preventDefault();
                        if (!addTitle.value.trim()) return;
                        await FF.change(() => call(`/api/member/tasks/${task.id}/steps`, { method: "POST", body: { title: addTitle.value.trim(), status: "NOT_STARTED", progress: 0 } }), "Step added.");
                    },
                }, addTitle, h("button", { class: "btn", type: "submit" }, icon("plus", 16), "Add step"))),

            h("div", { class: "card" },
                sectionHead("Work Results",
                    h("button", { class: "btn primary", type: "button", onclick: () => openSubmitDialog({ taskId: task.id }) }, icon("upload", 16), "Submit work")),
                mySubs.length ? h("div", { class: "task-list" }, mySubs.map(submissionRow))
                    : h("div", { class: "empty" }, "Upload your result when it's ready; it goes to your administrator for review.")));
    }

    /* ---------- My Projects ---------- */

    async function viewProjects() {
        const projects = await call("/api/member/projects");
        return projects.length
            ? h("div", { class: "project-grid" }, projects.map(({ project: p, myTasks, myDone }) => h("a", { class: "card project-card link-card", href: `#/project?id=${p.id}` },
                h("div", { class: "project-head" }, h("h3", null, p.name), statusTag(PROJECT_STATUS, p.status)),
                h("p", { class: "muted desc" }, p.description || "No description."),
                h("div", { class: "progress-caption" }, h("span", null, "My tasks"), h("span", null, `${myDone} / ${myTasks}`)),
                progressBar(pct(myDone, myTasks)),
                h("div", { class: "project-foot" },
                    p.teamName && h("span", { class: "tag gray" }, p.teamName),
                    p.dueDate && h("span", { class: "tag blue" }, "Ends " + fmtDate(p.dueDate))))))
            : h("div", { class: "card empty" }, "You're not on a project yet. Your administrator adds you to a team.");
    }

    async function viewProject(params) {
        const id = params.get("id");
        if (!id || !/^\d+$/.test(id)) return h("div", { class: "card empty" }, "Choose a project from ", h("a", { href: "#/projects" }, "My Projects"), ".");
        const { project: p, phases, teamDeadlines, myTasks, myPhaseStatus } = await call(`/api/member/projects/${id}`);
        const now = currentPhase(phases);
        const upcomingDeadlines = teamDeadlines.filter(d => !d.done).sort((a, b) => a.dueDate.localeCompare(b.dueDate));
        return h("div", { class: "stack" },
            h("div", null, h("a", { href: "#/projects" }, "← My projects")),
            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    h("div", null,
                        h("h2", { class: "detail-title" }, p.name),
                        h("div", { class: "task-meta" }, statusTag(PROJECT_STATUS, p.status), p.teamName && h("span", { class: "tag gray" }, p.teamName),
                            now && h("span", { class: "tag green" }, `Now: ${now.name}`))),
                    h("button", { class: "btn primary", type: "button", onclick: () => openSubmitDialog({ projectId: p.id }) }, icon("upload", 16), "Submit work")),
                detailList([
                    ["Why it matters", p.purpose],
                    ["About", p.description],
                    ["Schedule", p.startDate || p.dueDate ? `${p.startDate ? fmtDate(p.startDate) : "?"} – ${p.dueDate ? fmtDate(p.dueDate) : "?"}` : null],
                    ["Development", p.devStartDate ? `${fmtDate(p.devStartDate)} – ${p.devEndDate ? fmtDate(p.devEndDate) : "?"}` : null],
                    ["Tech stack", p.techStack],
                    ["Features", p.serviceFeatures],
                ])),
            h("div", { class: "two-col flush" },
                h("div", { class: "card" },
                    sectionHead("Schedule & My Submissions"),
                    phases.length ? phases.map(ph => {
                        const [state, tag] = phaseState(ph);
                        return h("div", { class: "phase-row board-row" + (state === "Past" ? " past" : "") },
                            h("div", { class: "task-info" },
                                h("div", { class: "row" }, h("strong", null, ph.name), h("span", { class: "tag " + tag }, state), subTag(myPhaseStatus[ph.id])),
                                h("div", { class: "muted" }, `${fmtDate(ph.startDate)} – ${fmtDate(ph.endDate)}`),
                                ph.items && h("div", { class: "muted phase-items" }, ph.items.split("\n").join(" · "))),
                            h("button", {
                                class: "icon-btn", type: "button", title: "Submit for this phase", "aria-label": `Submit for ${ph.name}`,
                                onclick: () => openSubmitDialog({ projectId: p.id, phaseId: ph.id }),
                            }, icon("upload", 16)));
                    }) : h("div", { class: "empty" }, "No schedule yet.")),
                h("div", { class: "stack" },
                    h("div", { class: "card" },
                        sectionHead("My Tasks Here"),
                        myTasks.length ? h("div", { class: "task-list" }, sortTasks(myTasks).map(taskRow)) : h("div", { class: "empty" }, "No tasks assigned to you in this project.")),
                    h("div", { class: "card" },
                        sectionHead("Team Deadlines"),
                        upcomingDeadlines.length ? h("div", { class: "deadline-list" }, upcomingDeadlines.map(d => h("div", { class: "deadline-row" },
                            h("span", { class: "tag " + (daysUntil(d.dueDate) <= 2 ? "red" : "gray") }, dayLabel(d.dueDate)),
                            h("span", { class: "deadline-title" }, d.title),
                            h("span", { class: "muted deadline-date" }, fmtDate(d.dueDate)))))
                            : h("div", { class: "empty" }, "No open team deadlines.")))));
    }

    /* ---------- Calendar ---------- */

    const cal = { month: monthKey(new Date()), selected: todayIso() };

    const PERSONAL_CATEGORIES = [["DAILY", "Daily"], ["HEALTH", "Health"], ["FINANCE", "Money"], ["FAMILY", "Family"], ["LEARNING", "Learning"], ["OTHER", "Other"]];

    /** The member's own daily items for one day: only this account's items are ever shown or changed here. */
    function personalPanel(date, items) {
        const title = h("input", { type: "text", maxlength: 120, "aria-label": "Item title", placeholder: "e.g. Dentist, pay rent, study 1 hour" });
        const time = h("input", { type: "time", "aria-label": "Time" });
        const cat = h("select", { "aria-label": "Category" }, PERSONAL_CATEGORIES.map(([v, l]) => h("option", { value: v }, l)));
        const note = h("input", { type: "text", maxlength: 1000, "aria-label": "Note", placeholder: "Note (optional)" });
        const add = h("button", { class: "btn primary", type: "button", onclick: async () => {
            if (!title.value.trim()) { toast("Give the item a title.", true); title.focus(); return; }
            try {
                await call("/api/member/personal-events", { method: "POST", body: {
                    title: title.value.trim(), eventDate: date, startTime: time.value || null, category: cat.value, note: note.value.trim() || null,
                } });
                toast("Added to your calendar.");
                refresh();
            } catch (error) { toast(error.message, true); }
        } }, icon("plus", 16), "Add");
        const rows = items.map(i => h("li", { class: i.done ? "done" : "" },
            h("label", { class: "personal-check" },
                h("input", { type: "checkbox", checked: i.done ? true : null, "aria-label": `Done: ${i.title}`, onchange: async (e) => {
                    try {
                        await call(`/api/member/personal-events/${i.id}`, { method: "PUT", body: {
                            title: i.title, eventDate: i.eventDate, startTime: i.startTime || null, category: i.category, note: i.note || null, done: e.target.checked,
                        } });
                        refresh();
                    } catch (error) { toast(error.message, true); }
                } }),
                h("strong", null, i.title), i.startTime ? ` · ${i.startTime.slice(0, 5)}` : "",
                i.note ? h("div", { class: "muted small" }, i.note) : null),
            h("button", { class: "icon-btn", type: "button", "aria-label": `Delete ${i.title}`, onclick: async () => {
                if (!window.confirm(`Delete "${i.title}" from your calendar?`)) return;
                try { await call(`/api/member/personal-events/${i.id}`, { method: "DELETE" }); refresh(); }
                catch (error) { toast(error.message, true); }
            } }, icon("trash", 16))));
        return h("section", { class: "personal" },
            h("h3", { class: "ai-subhead" }, "My daily items"),
            rows.length ? h("ul", { class: "personal-list" }, rows) : h("p", { class: "muted small" }, "Nothing added for this day yet."),
            h("div", { class: "personal-form" }, title, time, cat, note, add));
    }

    async function viewCalendar() {
        const [entries, personal] = await Promise.all([
            call(`/api/member/calendar?month=${cal.month}`),
            call(`/api/member/personal-events?month=${cal.month}`),
        ]);
        const byDate = groupByDate(entries);
        const personalByDate = personal.reduce((map, item) => { (map[item.eventDate] ||= []).push(item); return map; }, {});
        const today = todayIso();
        const shift = delta => {
            cal.month = shiftMonth(cal.month, delta);
            cal.selected = cal.month === monthKey(new Date()) ? todayIso() : `${cal.month}-01`;
            refresh();
        };
        const open = e => { location.hash = e.taskId != null ? `#/task?id=${e.taskId}` : `#/project?id=${e.projectId}`; };
        const selected = byDate.get(cal.selected) || [];
        return h("div", { class: "cal-layout" },
            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    monthNav(fmtMonth(cal.month), shift),
                    h("button", { class: "btn", type: "button", onclick: () => { cal.month = monthKey(new Date()); cal.selected = todayIso(); refresh(); } }, "Today")),
                h("div", { class: "cal-grid" }, weekdayHeads(), monthCells(cal.month, (date, day) => {
                    const list = byDate.get(date) || [];
                    const mine = personalByDate[date] || [];
                    return h("button", {
                        class: `cal-day${date === today ? " today" : ""}${date === cal.selected ? " selected" : ""}`, type: "button",
                        "aria-label": `${fmtDateLong(date)}, ${list.length} items`, "aria-pressed": String(date === cal.selected),
                        onclick: () => { cal.selected = date; refresh(); },
                    }, h("span", { class: "cal-num" }, day), list.slice(0, 3).map(e => h("span", { class: chipClass(e) }, e.title)),
                        mine.slice(0, 2).map(i => h("span", { class: "chip personal" + (i.done ? " done" : "") }, i.title)),
                        list.length > 3 && h("span", { class: "chip-more" }, `+${list.length - 3} more`));
                })),
                h("div", { class: "legend" },
                    h("span", { class: "chip due" }, "My deadline"), h("span", { class: "chip sched" }, "Scheduled"),
                    h("span", { class: "chip team" }, "Team deadline"), h("span", { class: "chip phase" }, "Phase"), h("span", { class: "chip project" }, "Project end"))),
            h("div", { class: "card" },
                h("div", { class: "section-heading" }, h("h2", null, fmtDateLong(cal.selected))),
                h("h3", { class: "ai-subhead" }, "Team and project items"),
                selected.length ? h("div", { class: "entry-list" }, selected.map(e => h("button", { class: "entry" + (e.done ? " done" : ""), type: "button", onclick: () => open(e) },
                    h("div", { class: "entry-title" }, e.title),
                    h("div", { class: "task-meta" }, h("span", { class: "tag " + (ENTRY_TAG[e.type] || "") }, ENTRY_LABEL[e.type]),
                        e.done && h("span", { class: "tag green" }, "Done"), e.projectName && h("span", { class: "tag" }, e.projectName)))))
                    : h("div", { class: "empty" }, "Nothing on this date."),
                personalPanel(cal.selected, personalByDate[cal.selected] || [])));
    }

    /* ---------- My Submissions ---------- */

    const subState = { status: "all" };
    const STATUS_FILTERS = [["all", "All"], ["REJECTED", "Needs changes"], ["SUBMITTED", "Pending review"], ["APPROVED", "Approved"]];

    async function viewSubmissions(params) {
        if (params.get("status")) {
            subState.status = STATUS_FILTERS.some(([k]) => k === params.get("status")) ? params.get("status") : "all";
            history.replaceState(null, "", "#/submissions" + (params.get("phase") ? `?phase=${params.get("phase")}` : ""));
        }
        const phaseId = params.get("phase");
        const all = (await call("/api/member/submissions")).filter(s => !phaseId || String(s.phaseId) === phaseId);
        const visible = subState.status === "all" ? all : all.filter(s => s.status === subState.status);
        return h("div", { class: "card" },
            h("div", { class: "toolbar" },
                h("div", { class: "filters" }, STATUS_FILTERS.map(([key, label]) => h("button", {
                    class: "filter", type: "button", "aria-pressed": String(subState.status === key),
                    onclick: () => { subState.status = key; refresh(); },
                }, `${label} (${key === "all" ? all.length : all.filter(s => s.status === key).length})`))),
                phaseId && h("a", { class: "btn", href: "#/submissions" }, "All phases"),
                h("button", { class: "btn primary", type: "button", onclick: () => openSubmitDialog(phaseId ? { phaseId: Number(phaseId) } : {}) }, icon("upload", 16), "Submit work")),
            visible.length ? h("div", { class: "task-list" }, visible.map(submissionRow))
                : h("div", { class: "empty" }, all.length ? "Nothing here." : "You haven't submitted anything yet. Use Submit work to upload your results."));
    }

    async function viewSubmission(params) {
        const id = params.get("id");
        if (!id || !/^\d+$/.test(id)) return h("div", { class: "card empty" }, "Choose a submission from ", h("a", { href: "#/submissions" }, "My Submissions"), ".");
        const { submission: sub, files, events } = await call(`/api/member/submissions/${id}`);
        return h("div", { class: "stack" },
            h("div", null, h("a", { href: "#/submissions" }, "← My submissions")),
            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    h("div", null,
                        h("h2", { class: "detail-title" }, sub.title),
                        h("div", { class: "task-meta" }, subTag(sub.status), h("span", { class: "tag teal" }, sub.phaseName || "Removed phase"),
                            h("span", { class: "tag gray" }, `Version ${sub.currentVersion}`), sub.late && h("span", { class: "tag red" }, "Submitted after the phase ended"))),
                    sub.status === "REJECTED" && h("button", { class: "btn primary", type: "button", onclick: () => openResubmitDialog(sub) }, icon("upload", 16), "Resubmit")),
                sub.status === "REJECTED" && h("div", { class: "review-note rejected" }, h("strong", null, "What to fix: "), sub.reviewComment || "No reason was given."),
                sub.status === "APPROVED" && h("div", { class: "review-note approved" }, h("strong", null, "Approved"), sub.reviewComment ? ` — ${sub.reviewComment}` : "."),
                sub.status === "SUBMITTED" && h("div", { class: "review-note pending" }, "Waiting for review. You'll get a notification when it's decided."),
                detailList([
                    ["Project", sub.projectName],
                    ["Phase", sub.phaseName ? `${sub.phaseName} · ${fmtDate(sub.phaseStartDate)} – ${fmtDate(sub.phaseEndDate)}` : null],
                    ["Related task", sub.taskId && sub.taskTitle ? h("a", { href: `#/task?id=${sub.taskId}` }, sub.taskTitle) : null],
                    ["Submitted", fmtStamp(sub.submittedAt)],
                    ["Note", sub.note],
                    ["Reviewed", sub.reviewedAt ? `${sub.reviewedBy} · ${fmtStamp(sub.reviewedAt)}` : null],
                ])),
            h("div", { class: "two-col flush" },
                h("div", { class: "card" },
                    sectionHead("Files", h("span", { class: "muted" }, `${files.length} · ${fmtBytes(files.reduce((n, f) => n + f.sizeBytes, 0))}`)),
                    fileList(files, sub.currentVersion, f => `/api/member/submission-files/${f.id}`)),
                h("div", { class: "card" }, sectionHead("History"), historyList(events))));
    }

    /* ---------- Wiring ---------- */

    const MEMBER_ROUTES = {
        overview: ["My Overview", "overview", viewOverview],
        tasks: ["My Tasks", "work", viewTasks],
        task: ["Task Details", "list", viewTask],
        projects: ["My Projects", "folder", viewProjects],
        project: ["Project", "briefcase", viewProject],
        calendar: ["Calendar", "calendar", viewCalendar],
        submissions: ["My Submissions", "upload", viewSubmissions],
        submission: ["Submission", "file", viewSubmission],
        account: ["My Account", "user", () => FF.viewAccount()],
    };
    for (const key of Object.keys(ROUTES)) delete ROUTES[key];
    Object.assign(ROUTES, MEMBER_ROUTES);

    FF.buildNav([
        [null, [["overview", "My Overview", "overview"], ["tasks", "My Tasks", "work"], ["projects", "My Projects", "folder"], ["calendar", "Calendar", "calendar"]]],
        ["WORK RESULTS", [["submissions", "My Submissions", "upload"]]],
        ["ACCOUNT", [["account", "My Account", "user"]]],
    ]);
    FF.mountHeader({
        role: "MEMBER",
        links: [["#/overview", "overview", "My Overview"], ["#/submissions", "upload", "My Submissions"], ["#/account", "user", "My Account"]],
    });
})();
