"use strict";

/*
 * Admin edition of FOCUS FLOW, loaded only by admin-dashboard.html after app.js and workspace.js.
 * It adds routes, header actions and sidebar links; it never redefines anything from those files.
 * Everything lives inside this function so no name can collide with the shared globals.
 */
(() => {
    Object.assign(ICONS, {
        bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
        user: '<circle cx="12" cy="8" r="5"/><path d="M20 21a8 8 0 0 0-16 0"/>',
        shield: '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/>',
        upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" x2="12" y1="3" y2="15"/>',
        download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/>',
        check: '<path d="M20 6 9 17l-5-5"/>',
        x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
        undo: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>',
        external: '<path d="M15 3h6v6"/><path d="M10 14 21 3"/><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',
    });

    const MAX_FILE_MB = 50;
    const MAX_FILES = 10;

    const SUB_STATUS = {
        SUBMITTED: ["Pending review", "amber"],
        APPROVED: ["Approved", "green"],
        REJECTED: ["Rejected", "red"],
        NONE: ["Not submitted", "gray"],
    };
    const EVENT_LABEL = {
        SUBMITTED: "submitted", RESUBMITTED: "resubmitted", APPROVED: "approved",
        REJECTED: "rejected", REOPENED: "reopened", EDITED: "edited",
    };
    const EVENT_TAG = { SUBMITTED: "blue", RESUBMITTED: "blue", APPROVED: "green", REJECTED: "red", REOPENED: "amber", EDITED: "gray" };

    const subTag = status => h("span", { class: "tag " + SUB_STATUS[status || "NONE"][1] }, SUB_STATUS[status || "NONE"][0]);

    /* ---------- Admin API ---------- */

    const auth = { status: null, pending: null };
    const inbox = { unread: 0, items: [] };

    /** Like api() in app.js, plus the header the server requires on admin changes, and multipart bodies. */
    async function adminApi(path, { method = "GET", body, form } = {}) {
        const headers = { "X-FocusFlow-Admin": "1" };
        let payload;
        if (form) payload = form;
        else if (body !== undefined) {
            headers["Content-Type"] = "application/json";
            payload = JSON.stringify(body);
        }
        let response;
        try {
            response = await fetch(path, { method, headers, body: payload, credentials: "same-origin" });
        } catch {
            throw new Error("Can't reach the server. Check that FOCUS FLOW is running.");
        }
        if (response.status === 401 && auth.status) {
            auth.status = { ...auth.status, authenticated: false, user: null };
            paintHeader();
        }
        if (response.status === 204) return null;
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const fallback = response.status === 413 ? `A file is too large. Each file can be up to ${MAX_FILE_MB} MB.` : "Something went wrong. Please try again.";
            throw new Error(data?.message || fallback);
        }
        return data;
    }

    function loadAuth(force = false) {
        if (auth.status && !force) return Promise.resolve(auth.status);
        if (!auth.pending) {
            auth.pending = adminApi("/api/admin/auth/status")
                .then(status => { auth.status = status; return status; })
                .finally(() => { auth.pending = null; });
        }
        return auth.pending;
    }

    const me = () => auth.status?.authenticated ? auth.status.user : null;

    async function loadInbox() {
        if (!me()) return;
        try {
            Object.assign(inbox, await adminApi("/api/admin/notifications?limit=20"));
        } catch {
            return; // The bell simply keeps its last state.
        }
        paintHeader();
        if (!document.getElementById("bellPanel").hidden) paintBellPanel();
    }

    /** Runs an admin change through app.js's act(), then refreshes the bell. */
    async function adminAct(fn, message) {
        const ok = await act(fn, message);
        if (ok) loadInbox();
        return ok;
    }

    /* ---------- Formatting ---------- */

    function fmtBytes(n) {
        if (n < 1024) return `${n} B`;
        if (n < 1024 * 1024) return `${(n / 1024).toFixed(1)} KB`;
        if (n < 1024 * 1024 * 1024) return `${(n / 1024 / 1024).toFixed(1)} MB`;
        return `${(n / 1024 / 1024 / 1024).toFixed(2)} GB`;
    }

    const fmtStamp = s => s ? `${fmtDate(s.slice(0, 10))} · ${fmtTime(s)}` : "—";

    function ago(s) {
        const seconds = Math.max(0, Math.floor((Date.now() - new Date(s.slice(0, 19)).getTime()) / 1000));
        if (seconds < 60) return "Just now";
        if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
        if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
        return fmtDate(s.slice(0, 10));
    }

    const initial = name => (name || "?").trim().charAt(0).toUpperCase() || "?";

    /* ---------- Header: bell and account ---------- */

    const bellButton = document.getElementById("bellButton");
    const profileButton = document.getElementById("profileButton");
    const bellPanel = document.getElementById("bellPanel");
    const profilePanel = document.getElementById("profilePanel");

    function paintHeader() {
        const user = me();
        const count = user ? inbox.unread : 0;
        bellButton.replaceChildren(icon("bell", 20)); // replaceChildren would print a null argument as "null"
        if (count > 0) bellButton.append(h("span", { class: "badge" }, count > 99 ? "99+" : String(count)));
        bellButton.setAttribute("aria-label", count > 0 ? `Notifications, ${count} unread` : "Notifications");
        profileButton.replaceChildren(user ? h("span", null, initial(user.displayName)) : icon("user", 18));
        profileButton.setAttribute("aria-label", user ? `Account: ${user.displayName}` : "Account, not signed in");
        if (!profilePanel.hidden) paintProfilePanel();
    }

    function closePopovers() {
        for (const [button, panel] of [[bellButton, bellPanel], [profileButton, profilePanel]]) {
            panel.hidden = true;
            button.setAttribute("aria-expanded", "false");
        }
    }

    function togglePopover(button, panel, paint) {
        const opening = panel.hidden;
        closePopovers();
        if (!opening) return;
        paint();
        panel.hidden = false;
        button.setAttribute("aria-expanded", "true");
    }

    function paintBellPanel() {
        if (!me()) {
            bellPanel.replaceChildren(
                h("div", { class: "pop-head" }, h("h3", null, "Notifications")),
                h("div", { class: "empty" }, "Sign in as the administrator to see notifications."),
                h("a", { class: "menu-link", href: "#/admin", onclick: closePopovers }, icon("login", 16), "Sign in"));
            return;
        }
        bellPanel.replaceChildren(
            h("div", { class: "pop-head" },
                h("h3", null, "Notifications"),
                inbox.unread > 0 && h("button", {
                    class: "link-btn", type: "button",
                    onclick: async () => {
                        try { await adminApi("/api/admin/notifications/read-all", { method: "POST" }); } catch (e) { toast(e.message, true); }
                        loadInbox();
                    },
                }, "Mark all read")),
            inbox.items.length
                ? h("div", { class: "notice-list" }, inbox.items.map(n => h("button", {
                    class: "notice" + (n.read ? "" : " unread"), type: "button",
                    onclick: async () => {
                        closePopovers();
                        if (!n.read) adminApi(`/api/admin/notifications/${n.id}/read`, { method: "POST" }).then(loadInbox, () => {});
                        if (n.link) location.hash = n.link;
                    },
                },
                    h("span", { class: "notice-dot", "aria-hidden": "true" }),
                    h("span", { class: "notice-body" },
                        h("span", { class: "notice-title" }, n.title),
                        n.message && h("span", { class: "notice-msg" }, n.message),
                        h("span", { class: "notice-time" }, ago(n.createdAt))))))
                : h("div", { class: "empty" }, "You're all caught up."));
    }

    function paintProfilePanel() {
        const user = me();
        const link = (href, ic, label, extra = {}) => h("a", { class: "menu-link", href, onclick: closePopovers, ...extra }, icon(ic, 16), label);
        profilePanel.replaceChildren(user
            ? h("div", null,
                h("div", { class: "profile-head" },
                    h("span", { class: "avatar-btn static", "aria-hidden": "true" }, initial(user.displayName)),
                    h("div", { class: "profile-id" },
                        h("strong", null, user.displayName),
                        h("div", { class: "muted" }, "@" + user.username)),
                    h("span", { class: "tag" }, user.role)),
                link("#/admin", "shield", "Admin Dashboard"),
                link("#/submissions", "upload", "Submissions"),
                link("#/admin-account", "user", "Account settings"),
                h("div", { class: "menu-sep" }),
                link("/", "external", "Open original FOCUS FLOW", { target: "_blank", rel: "noopener" }),
                h("button", { class: "menu-link danger", type: "button", onclick: signOut }, icon("logout", 16), "Sign out"))
            : h("div", null,
                h("div", { class: "profile-head" },
                    h("span", { class: "avatar-btn static", "aria-hidden": "true" }, icon("user", 18)),
                    h("div", { class: "profile-id" }, h("strong", null, "Not signed in"), h("div", { class: "muted" }, "Administrator"))),
                link("#/admin", "login", "Sign in"),
                link("/", "external", "Open original FOCUS FLOW", { target: "_blank", rel: "noopener" })));
    }

    async function signOut() {
        closePopovers();
        try {
            await adminApi("/api/admin/auth/logout", { method: "POST" });
        } catch (error) {
            toast(error.message, true);
            return;
        }
        auth.status = { ...auth.status, authenticated: false, user: null };
        Object.assign(inbox, { unread: 0, items: [] });
        paintHeader();
        toast("Signed out.");
        refresh();
    }

    bellButton.addEventListener("click", event => { event.stopPropagation(); togglePopover(bellButton, bellPanel, paintBellPanel); });
    profileButton.addEventListener("click", event => { event.stopPropagation(); togglePopover(profileButton, profilePanel, paintProfilePanel); });
    for (const panel of [bellPanel, profilePanel]) panel.addEventListener("click", event => event.stopPropagation());
    document.addEventListener("click", closePopovers);
    document.addEventListener("keydown", event => { if (event.key === "Escape") closePopovers(); });
    window.addEventListener("hashchange", closePopovers);

    setInterval(() => { if (me() && !document.hidden) loadInbox(); }, 60000);
    document.addEventListener("visibilitychange", () => { if (!document.hidden) loadInbox(); });

    /* ---------- Sign-in gate ---------- */

    /** Returns a sign-in (or first-time setup) card when no administrator is signed in, otherwise null. */
    async function gate() {
        const status = await loadAuth();
        if (status.authenticated) return null;
        return status.setupRequired ? setupCard() : loginCard();
    }

    function signedIn(status, message) {
        auth.status = status;
        paintHeader();
        loadInbox();
        toast(message);
        refresh();
    }

    function passwordInput(attrs = {}) {
        return h("input", { type: "password", required: true, maxlength: 128, ...attrs });
    }

    function loginCard() {
        const username = h("input", { type: "text", required: true, maxlength: 40, autocomplete: "username" });
        const password = passwordInput({ autocomplete: "current-password" });
        const submit = h("button", { class: "btn primary", type: "submit" }, icon("login", 16), "Sign in");
        return h("form", {
            class: "card auth-card",
            onsubmit: async event => {
                event.preventDefault();
                submit.disabled = true;
                try {
                    const status = await adminApi("/api/admin/auth/login", { method: "POST", body: { username: username.value, password: password.value } });
                    signedIn(status, `Welcome back, ${status.user.displayName}.`);
                } catch (error) {
                    toast(error.message, true);
                    submit.disabled = false;
                    password.select();
                }
            },
        },
            h("div", { class: "eyebrow" }, "ADMIN CONSOLE"),
            h("h1", { class: "auth-title" }, "Sign in"),
            h("p", { class: "muted" }, "The admin dashboard, submissions and notifications are only for the administrator."),
            h("div", { class: "stack auth-fields" },
                h("label", { class: "field" }, "Username", username),
                h("label", { class: "field" }, "Password", password)),
            h("div", { class: "modal-actions" }, h("a", { class: "btn", href: "/" }, "Original FOCUS FLOW"), submit));
    }

    function setupCard() {
        const username = h("input", { type: "text", required: true, maxlength: 40, pattern: "[A-Za-z0-9._\\-]{3,40}", autocomplete: "username", value: "admin" });
        const displayName = h("input", { type: "text", required: true, maxlength: 60, autocomplete: "name" });
        const password = passwordInput({ minlength: 8, autocomplete: "new-password" });
        const confirmPw = passwordInput({ minlength: 8, autocomplete: "new-password" });
        const submit = h("button", { class: "btn primary", type: "submit" }, icon("shield", 16), "Create administrator");
        return h("form", {
            class: "card auth-card",
            onsubmit: async event => {
                event.preventDefault();
                if (password.value !== confirmPw.value) {
                    toast("The two passwords don't match.", true);
                    confirmPw.select();
                    return;
                }
                submit.disabled = true;
                try {
                    const status = await adminApi("/api/admin/auth/setup", {
                        method: "POST", body: { username: username.value, displayName: displayName.value, password: password.value },
                    });
                    signedIn(status, "Administrator account created.");
                } catch (error) {
                    toast(error.message, true);
                    submit.disabled = false;
                }
            },
        },
            h("div", { class: "eyebrow" }, "FIRST-TIME SETUP"),
            h("h1", { class: "auth-title" }, "Create the administrator"),
            h("p", { class: "muted" }, "This happens once. The account protects reviews and uploaded files; your existing projects and tasks are not changed."),
            h("div", { class: "stack auth-fields" },
                h("label", { class: "field" }, "Your name", displayName),
                h("label", { class: "field" }, "Username", username, h("span", { class: "muted hint" }, "3–40 letters, numbers, dots, dashes or underscores.")),
                h("label", { class: "field" }, "Password", password, h("span", { class: "muted hint" }, "At least 8 characters.")),
                h("label", { class: "field" }, "Confirm password", confirmPw)),
            h("div", { class: "modal-actions" }, submit));
    }

    /* ---------- Dialogs ---------- */

    /** A file picker that lists what was chosen and refuses files the server would reject. */
    function filePicker() {
        const input = h("input", { type: "file", multiple: true, class: "file-input" });
        const list = h("div", { class: "picked" });
        const describe = () => {
            const files = [...input.files];
            list.replaceChildren(...files.map(f => h("div", { class: "picked-row" + (f.size > MAX_FILE_MB * 1024 * 1024 ? " too-big" : "") },
                icon("file", 14), h("span", { class: "picked-name" }, f.name), h("span", { class: "muted" }, fmtBytes(f.size)))));
        };
        input.addEventListener("change", describe);
        const problem = () => {
            const files = [...input.files];
            if (!files.length) return "Attach at least one file.";
            if (files.length > MAX_FILES) return `Attach up to ${MAX_FILES} files at a time.`;
            const big = files.find(f => f.size > MAX_FILE_MB * 1024 * 1024);
            if (big) return `"${big.name}" is larger than ${MAX_FILE_MB} MB.`;
            const empty = files.find(f => f.size === 0);
            return empty ? `"${empty.name}" is empty.` : null;
        };
        const field = h("label", { class: "field full" }, "Files",
            h("div", { class: "dropzone" }, icon("upload", 18), input),
            h("span", { class: "muted hint" }, `Up to ${MAX_FILES} files, ${MAX_FILE_MB} MB each.`),
            list);
        return { field, input, problem };
    }

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
        const locked = await gate();
        if (locked) return locked;
        const [dash, projects, tasks] = await Promise.all([adminApi("/api/admin/dashboard"), api("/api/projects"), api("/api/tasks")]);
        const user = me();
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
        const locked = await gate();
        if (locked) return locked;
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
        const locked = await gate();
        if (locked) return locked;
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

        const versions = [...new Set(files.map(f => f.version))].sort((a, b) => b - a);

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
                    versions.length
                        ? versions.map(v => h("div", { class: "file-version" },
                            h("div", { class: "detail-group" }, `Version ${v}${v === sub.currentVersion ? " · current" : ""}`),
                            files.filter(f => f.version === v).map(f => h("a", {
                                class: "file-row", href: `/api/admin/submission-files/${f.id}`, download: f.originalName,
                            },
                                icon("file", 16),
                                h("span", { class: "file-name" }, f.originalName),
                                h("span", { class: "muted" }, fmtBytes(f.sizeBytes)),
                                icon("download", 16)))))
                        : h("div", { class: "empty" }, "No files.")),
                h("div", { class: "card" },
                    sectionHead("History"),
                    h("ol", { class: "history" }, events.map(e => h("li", null,
                        h("span", { class: "tag " + EVENT_TAG[e.type] }, EVENT_LABEL[e.type]),
                        h("div", { class: "history-body" },
                            h("div", null, h("strong", null, e.actor), ` · version ${e.version}`),
                            e.comment && h("div", { class: "muted" }, e.comment),
                            h("div", { class: "notice-time" }, fmtStamp(e.createdAt)))))))));
    }

    /* ---------- Account ---------- */

    async function viewAccount() {
        const locked = await gate();
        if (locked) return locked;
        const user = me();
        const displayName = h("input", { type: "text", required: true, maxlength: 60, value: user.displayName });
        const current = passwordInput({ autocomplete: "current-password" });
        const next = passwordInput({ minlength: 8, autocomplete: "new-password" });
        const confirmPw = passwordInput({ minlength: 8, autocomplete: "new-password" });

        return h("div", { class: "two-col flush" },
            h("form", {
                class: "card",
                onsubmit: async event => {
                    event.preventDefault();
                    const ok = await act(async () => {
                        const updated = await adminApi("/api/admin/auth/profile", { method: "PUT", body: { displayName: displayName.value } });
                        auth.status = { ...auth.status, user: updated };
                        paintHeader();
                    }, "Profile saved.");
                    return ok;
                },
            },
                sectionHead("Profile", h("span", { class: "tag" }, user.role)),
                h("div", { class: "stack" },
                    h("label", { class: "field" }, "Name", displayName),
                    detailList([
                        ["Username", "@" + user.username],
                        ["Created", fmtStamp(user.createdAt)],
                        ["Last sign-in", fmtStamp(user.lastLoginAt)],
                    ]),
                    h("div", null, h("button", { class: "btn primary", type: "submit" }, "Save profile")))),
            h("form", {
                class: "card",
                onsubmit: async event => {
                    event.preventDefault();
                    if (next.value !== confirmPw.value) { toast("The two new passwords don't match.", true); return; }
                    await act(() => adminApi("/api/admin/auth/password", {
                        method: "PUT", body: { currentPassword: current.value, newPassword: next.value },
                    }), "Password changed.");
                },
            },
                sectionHead("Password"),
                h("div", { class: "stack" },
                    h("label", { class: "field" }, "Current password", current),
                    h("label", { class: "field" }, "New password", next),
                    h("label", { class: "field" }, "Confirm new password", confirmPw),
                    h("div", { class: "row" },
                        h("button", { class: "btn primary", type: "submit" }, "Change password"),
                        h("button", { class: "btn danger", type: "button", onclick: signOut }, icon("logout", 16), "Sign out")))));
    }

    /* ---------- Wiring ---------- */

    const ADMIN_ROUTES = {
        admin: ["Admin Dashboard", "shield", viewAdmin],
        submissions: ["Submissions", "upload", viewSubmissions],
        submission: ["Submission Details", "file", viewSubmission],
        "admin-account": ["Account", "user", viewAccount],
    };
    Object.assign(ROUTES, ADMIN_ROUTES);

    // app.js has already built the original sidebar links; the admin links go in their own section.
    const nav = document.getElementById("nav");
    nav.append(h("div", { class: "nav-section" }, "ADMIN"),
        ...["admin", "submissions", "admin-account"].map(route =>
            h("a", { class: "nav-link", href: `#/${route}`, "data-route": route }, icon(ADMIN_ROUTES[route][1]), ADMIN_ROUTES[route][0])));

    // Opening /admin-dashboard with no page chosen lands on the dashboard rather than the overview.
    if (!location.hash || location.hash === "#" || location.hash === "#/") history.replaceState(null, "", "#/admin");

    paintHeader();
    loadAuth().then(() => { paintHeader(); loadInbox(); }, () => {});
})();
