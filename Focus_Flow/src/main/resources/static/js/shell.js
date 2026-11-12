"use strict";

/*
 * Shared by the ADMIN and MEMBER workspaces (loaded after app.js): the signed-in session, API calls,
 * the header bell and account menu, and a few formatting helpers. Exposed as the single global FF.
 */
const FF = (() => {
    Object.assign(ICONS, {
        bell: '<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
        user: '<circle cx="12" cy="8" r="5"/><path d="M20 21a8 8 0 0 0-16 0"/>',
        users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
        shield: '<path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z"/><path d="m9 12 2 2 4-4"/>',
        upload: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" x2="12" y1="3" y2="15"/>',
        download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" x2="12" y1="15" y2="3"/>',
        check: '<path d="M20 6 9 17l-5-5"/>',
        x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
        undo: '<path d="M9 14 4 9l5-5"/><path d="M4 9h10.5a5.5 5.5 0 0 1 0 11H11"/>',
        external: '<path d="M15 3h6v6"/><path d="M10 14 21 3"/><path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6"/>',
        target: '<circle cx="12" cy="12" r="10"/><circle cx="12" cy="12" r="6"/><circle cx="12" cy="12" r="2"/>',
        grid: '<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M3 9h18"/><path d="M3 15h18"/><path d="M9 3v18"/><path d="M15 3v18"/>',
        key: '<circle cx="7.5" cy="15.5" r="5.5"/><path d="m21 2-9.6 9.6"/><path d="m15.5 7.5 3 3L22 7l-3-3"/>',
        alert: '<circle cx="12" cy="12" r="10"/><line x1="12" x2="12" y1="8" y2="12"/><line x1="12" x2="12.01" y1="16" y2="16"/>',
    });

    const MAX_FILE_MB = 50;
    const MAX_FILES = 10;

    // Any API answer of 401 means the session ended: go back to the login page instead of showing errors.
    const nativeFetch = window.fetch.bind(window);
    let leaving = false;
    window.fetch = async (input, init) => {
        const response = await nativeFetch(input, init);
        const url = typeof input === "string" ? input : input.url;
        if (response.status === 401 && !leaving && /^\/api\//.test(new URL(url, location.href).pathname)
            && !/^\/api\/(admin\/)?auth\/(status|login|setup|invitation)$/.test(new URL(url, location.href).pathname)) {
            leaving = true;
            location.href = "/login?expired=1";
        }
        return response;
    };

    /** Like api() in app.js, plus the header the newer APIs require on changes, and multipart bodies. */
    async function call(path, { method = "GET", body, form } = {}) {
        const headers = { "X-FocusFlow": "1" };
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
        if (response.status === 204) return null;
        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const fallback = response.status === 413 ? `A file is too large. Each file can be up to ${MAX_FILE_MB} MB.` : "Something went wrong. Please try again.";
            const error = new Error(data?.message || fallback);
            error.status = response.status;
            throw error;
        }
        return data;
    }

    /* ---------- Session ---------- */

    const auth = { status: null, pending: null };
    const inbox = { unread: 0, items: [] };

    function loadAuth(force = false) {
        if (auth.status && !force) return Promise.resolve(auth.status);
        if (!auth.pending) {
            auth.pending = call("/api/auth/status")
                .then(status => { auth.status = status; return status; })
                .finally(() => { auth.pending = null; });
        }
        return auth.pending;
    }

    const me = () => auth.status?.authenticated ? auth.status.user : null;

    /** The signed-in user, once the session is known. Views await this, since app.js may render first. */
    const ready = async () => me() || (await loadAuth()).user;

    function setUser(user) {
        auth.status = { ...auth.status, user };
        paintHeader();
    }

    async function signOut() {
        closePopovers();
        try {
            await call("/api/auth/logout", { method: "POST" });
        } catch (error) {
            toast(error.message, true);
            return;
        }
        location.href = "/login?signedOut=1";
    }

    /** Runs a change through app.js's act() (toast + re-render), then refreshes the bell. */
    async function change(fn, message) {
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

    /* ---------- Submissions, shared by both workspaces ---------- */

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

    /** A file picker that lists what was chosen and refuses files the server would reject. */
    function filePicker() {
        const input = h("input", { type: "file", multiple: true, class: "file-input" });
        const list = h("div", { class: "picked" });
        input.addEventListener("change", () => {
            list.replaceChildren(...[...input.files].map(f => h("div", { class: "picked-row" + (f.size > MAX_FILE_MB * 1024 * 1024 ? " too-big" : "") },
                icon("file", 14), h("span", { class: "picked-name" }, f.name), h("span", { class: "muted" }, fmtBytes(f.size)))));
        });
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

    /** The version-by-version file list of a submission. {@code fileUrl} builds the download link. */
    function fileList(files, currentVersion, fileUrl) {
        const versions = [...new Set(files.map(f => f.version))].sort((a, b) => b - a);
        return versions.length
            ? versions.map(v => h("div", { class: "file-version" },
                h("div", { class: "detail-group" }, `Version ${v}${v === currentVersion ? " · current" : ""}`),
                files.filter(f => f.version === v).map(f => h("a", { class: "file-row", href: fileUrl(f), download: f.originalName },
                    icon("file", 16),
                    h("span", { class: "file-name" }, f.originalName),
                    h("span", { class: "muted" }, fmtBytes(f.sizeBytes)),
                    icon("download", 16)))))
            : h("div", { class: "empty" }, "No files.");
    }

    function historyList(events) {
        return h("ol", { class: "history" }, events.map(e => h("li", null,
            h("span", { class: "tag " + EVENT_TAG[e.type] }, EVENT_LABEL[e.type]),
            h("div", { class: "history-body" },
                h("div", null, h("strong", null, e.actor), ` · version ${e.version}`),
                e.comment && h("div", { class: "muted" }, e.comment),
                h("div", { class: "notice-time" }, fmtStamp(e.createdAt))))));
    }

    /* ---------- Header: bell and account ---------- */

    const bellButton = document.getElementById("bellButton");
    const profileButton = document.getElementById("profileButton");
    const bellPanel = document.getElementById("bellPanel");
    const profilePanel = document.getElementById("profilePanel");
    let profileLinks = [];

    function paintHeader() {
        const user = me();
        const count = user ? inbox.unread : 0;
        bellButton.replaceChildren(icon("bell", 20)); // replaceChildren would print a null argument as "null"
        if (count > 0) bellButton.append(h("span", { class: "badge" }, count > 99 ? "99+" : String(count)));
        bellButton.setAttribute("aria-label", count > 0 ? `Notifications, ${count} unread` : "Notifications");
        profileButton.replaceChildren(user ? h("span", null, initial(user.displayName)) : icon("user", 18));
        profileButton.setAttribute("aria-label", user ? `Account: ${user.displayName}` : "Account");
        const foot = document.getElementById("workspaceName");
        // Admins keep the workspace name from Settings there (app.js fills it in).
        if (user && foot && user.role !== "ADMIN") foot.textContent = `${user.displayName} · ${user.role === "ADMIN" ? "Administrator" : "Member"}`;
        if (!profilePanel.hidden) paintProfilePanel();
    }

    async function loadInbox() {
        if (!me()) return;
        try {
            Object.assign(inbox, await call("/api/me/notifications?limit=20"));
        } catch {
            return; // The bell keeps its last state.
        }
        paintHeader();
        if (!bellPanel.hidden) paintBellPanel();
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
        bellPanel.replaceChildren(
            h("div", { class: "pop-head" },
                h("h3", null, "Notifications"),
                inbox.unread > 0 && h("button", {
                    class: "link-btn", type: "button",
                    onclick: async () => {
                        try { await call("/api/me/notifications/read-all", { method: "POST" }); } catch (e) { toast(e.message, true); }
                        loadInbox();
                    },
                }, "Mark all read")),
            inbox.items.length
                ? h("div", { class: "notice-list" }, inbox.items.map(n => h("button", {
                    class: "notice" + (n.read ? "" : " unread"), type: "button",
                    onclick: () => {
                        closePopovers();
                        if (!n.read) call(`/api/me/notifications/${n.id}/read`, { method: "POST" }).then(loadInbox, () => {});
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
        if (!user) return;
        profilePanel.replaceChildren(h("div", null,
            h("div", { class: "profile-head" },
                h("span", { class: "avatar-btn static", "aria-hidden": "true" }, initial(user.displayName)),
                h("div", { class: "profile-id" },
                    h("strong", null, user.displayName),
                    h("div", { class: "muted" }, "@" + user.username)),
                h("span", { class: "tag" + (user.role === "ADMIN" ? "" : " teal") }, user.role === "ADMIN" ? "Admin" : "Member")),
            profileLinks.map(([href, ic, label]) => h("a", { class: "menu-link", href, onclick: closePopovers }, icon(ic, 16), label)),
            h("div", { class: "menu-sep" }),
            h("button", { class: "menu-link danger", type: "button", onclick: signOut }, icon("logout", 16), "Sign out")));
    }

    /**
     * Wires the header for a workspace. {@code links} are the account-menu entries as [href, icon, label].
     * Resolves once the session is known; a missing session goes back to the login page.
     */
    async function mountHeader({ links, role }) {
        profileLinks = links;
        bellButton.addEventListener("click", event => { event.stopPropagation(); togglePopover(bellButton, bellPanel, paintBellPanel); });
        profileButton.addEventListener("click", event => { event.stopPropagation(); togglePopover(profileButton, profilePanel, paintProfilePanel); });
        for (const panel of [bellPanel, profilePanel]) panel.addEventListener("click", event => event.stopPropagation());
        document.addEventListener("click", closePopovers);
        document.addEventListener("keydown", event => { if (event.key === "Escape") closePopovers(); });
        window.addEventListener("hashchange", closePopovers);
        setInterval(() => { if (me() && !document.hidden) loadInbox(); }, 60000);
        document.addEventListener("visibilitychange", () => { if (!document.hidden) loadInbox(); });
        paintHeader();
        const status = await loadAuth();
        if (!status.authenticated) { location.href = "/login"; return status; }
        if (role && status.user.role !== role) { location.href = status.home; return status; }
        paintHeader();
        loadInbox();
        return status;
    }

    /** Replaces the sidebar with grouped sections: [[heading or null, [[route, label, icon], ...]], ...]. */
    function buildNav(sections) {
        const nav = document.getElementById("nav");
        nav.replaceChildren(...sections.flatMap(([heading, items]) => [
            heading && h("div", { class: "nav-section" }, heading),
            ...items.map(([route, label, ic]) => h("a", { class: "nav-link", href: `#/${route}`, "data-route": route }, icon(ic), label)),
        ].filter(Boolean)));
    }

    /* ---------- Account page, the same for both roles ---------- */

    async function viewAccount() {
        const user = await ready();
        const pw = attrs => h("input", { type: "password", required: true, maxlength: 128, ...attrs });
        const displayName = h("input", { type: "text", required: true, maxlength: 60, value: user.displayName });
        const current = pw({ autocomplete: "current-password" });
        const next = pw({ minlength: 8, autocomplete: "new-password" });
        const confirmPw = pw({ minlength: 8, autocomplete: "new-password" });

        return h("div", { class: "two-col flush" },
            h("form", {
                class: "card",
                onsubmit: event => {
                    event.preventDefault();
                    act(async () => setUser(await call("/api/auth/profile", { method: "PUT", body: { displayName: displayName.value } })), "Profile saved.");
                },
            },
                sectionHead("Profile", h("span", { class: "tag" + (user.role === "ADMIN" ? "" : " teal") }, user.role === "ADMIN" ? "Administrator" : "Member")),
                h("div", { class: "stack" },
                    h("label", { class: "field" }, "Name", displayName),
                    detailList([
                        ["Username", "@" + user.username],
                        ["Email", user.email],
                        ["Member since", fmtStamp(user.createdAt)],
                        ["Last sign-in", fmtStamp(user.lastLoginAt)],
                    ]),
                    h("div", null, h("button", { class: "btn primary", type: "submit" }, "Save profile")))),
            h("form", {
                class: "card",
                onsubmit: event => {
                    event.preventDefault();
                    if (next.value !== confirmPw.value) { toast("The two new passwords don't match.", true); return; }
                    act(() => call("/api/auth/password", { method: "PUT", body: { currentPassword: current.value, newPassword: next.value } }), "Password changed.");
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

    return {
        call, loadAuth, me, ready, setUser, signOut, change, loadInbox, mountHeader, buildNav, closePopovers, viewAccount,
        fmtBytes, fmtStamp, ago, initial, filePicker, fileList, historyList, subTag,
        SUB_STATUS, EVENT_LABEL, MAX_FILE_MB, MAX_FILES,
    };
})();
