"use strict";

/* The public login page: sign in, join with an invitation code, reset access, or create the first administrator. */
(() => {
    const card = document.getElementById("card");
    const params = new URLSearchParams(location.search);

    function h(tag, attrs, ...children) {
        const node = document.createElement(tag);
        for (const [key, val] of Object.entries(attrs || {})) {
            if (val == null || val === false) continue;
            if (key === "class") node.className = val;
            else if (key.startsWith("on")) node.addEventListener(key.slice(2), val);
            else node.setAttribute(key, val === true ? "" : val);
        }
        for (const child of children.flat()) if (child != null && child !== false) node.append(child);
        return node;
    }

    async function post(path, body) {
        let response;
        try {
            response = await fetch(path, {
                method: "POST", credentials: "same-origin",
                headers: { "Content-Type": "application/json", "X-FocusFlow": "1" },
                body: JSON.stringify(body),
            });
        } catch {
            throw new Error("Can't reach the server. Check that FOCUS FLOW is running.");
        }
        const data = await response.json().catch(() => null);
        if (!response.ok) throw new Error(data?.message || "Something went wrong. Please try again.");
        return data;
    }

    const message = (kind, text) => h("div", { class: `login-msg ${kind}`, role: kind === "error" ? "alert" : "status" }, text);
    const field = (label, input, hint) => h("label", { class: "field" }, label, input, hint && h("span", { class: "muted hint" }, hint));
    const input = attrs => h("input", { required: true, ...attrs });
    const linkButton = (text, onclick) => h("button", { class: "link-btn", type: "button", onclick }, text);

    /** Builds a form whose submit shows errors inline and keeps the button from double-submitting. */
    function form({ title, eyebrow, intro, fields, submitLabel, onSubmit, footer, notice }) {
        const status = h("div", { class: "login-status" }, notice);
        const submit = h("button", { class: "btn primary login-submit", type: "submit" }, submitLabel);
        const node = h("form", {
            novalidate: false,
            onsubmit: async event => {
                event.preventDefault();
                submit.disabled = true;
                status.replaceChildren();
                try {
                    await onSubmit(text => { throw new Error(text); });
                } catch (error) {
                    status.replaceChildren(message("error", error.message));
                    submit.disabled = false;
                }
            },
        },
            eyebrow && h("div", { class: "eyebrow" }, eyebrow),
            h("h2", { class: "login-title" }, title),
            intro && h("p", { class: "muted login-intro" }, intro),
            status,
            h("div", { class: "stack login-fields" }, fields),
            submit,
            footer && h("div", { class: "login-links" }, footer));
        card.replaceChildren(node);
        node.querySelector("input")?.focus();
    }

    const goHome = status => { location.href = status.home || "/"; };

    function showSignIn(notice) {
        const username = input({ type: "text", maxlength: 120, autocomplete: "username", name: "username" });
        const password = input({ type: "password", maxlength: 128, autocomplete: "current-password", name: "password" });
        form({
            eyebrow: "WELCOME BACK",
            title: "Sign in to FOCUS FLOW",
            notice,
            fields: [field("Username or email", username), field("Password", password)],
            submitLabel: "Sign in",
            onSubmit: async () => goHome(await post("/api/auth/login", { username: username.value, password: password.value })),
            footer: [
                linkButton("Forgot password?", () => showForgot()),
                h("span", { class: "dot-sep", "aria-hidden": "true" }, "·"),
                linkButton("I have an invitation code", () => showCode()),
            ],
        });
    }

    function showCode(notice) {
        const code = input({ type: "text", maxlength: 20, autocomplete: "one-time-code", placeholder: "XXXX-XXXX-XXXX", class: "code-input", spellcheck: "false" });
        const password = input({ type: "password", minlength: 8, maxlength: 128, autocomplete: "new-password" });
        const confirm = input({ type: "password", minlength: 8, maxlength: 128, autocomplete: "new-password" });
        form({
            eyebrow: "JOIN YOUR TEAM",
            title: "Set up your account",
            intro: "Enter the code your administrator gave you and choose a password. The same code works for a password reset.",
            notice,
            fields: [field("Invitation or reset code", code), field("New password", password, "At least 8 characters."), field("Confirm password", confirm)],
            submitLabel: "Activate and sign in",
            onSubmit: async fail => {
                if (password.value !== confirm.value) fail("The two passwords don't match.");
                goHome(await post("/api/auth/invitation", { code: code.value, password: password.value }));
            },
            footer: [linkButton("← Back to sign in", () => showSignIn())],
        });
    }

    function showForgot() {
        card.replaceChildren(h("div", null,
            h("div", { class: "eyebrow" }, "PASSWORD HELP"),
            h("h2", { class: "login-title" }, "Forgot your password?"),
            h("p", { class: "muted login-intro" },
                "FOCUS FLOW doesn't send email yet, so resets go through your administrator."),
            h("ol", { class: "login-steps" },
                h("li", null, "Ask your administrator to choose ", h("strong", null, "Reset access"), " for your account in Members."),
                h("li", null, "They will give you a one-time code, valid for 7 days."),
                h("li", null, "Enter it under ", h("strong", null, "I have an invitation code"), " and choose a new password.")),
            h("button", { class: "btn primary login-submit", type: "button", onclick: () => showCode() }, "I have a code"),
            h("div", { class: "login-links" }, linkButton("← Back to sign in", () => showSignIn()))));
    }

    function showSetup() {
        const displayName = input({ type: "text", maxlength: 60, autocomplete: "name" });
        const username = input({ type: "text", maxlength: 40, pattern: "[A-Za-z0-9._\\-]{3,40}", autocomplete: "username", value: "admin" });
        const password = input({ type: "password", minlength: 8, maxlength: 128, autocomplete: "new-password" });
        const confirm = input({ type: "password", minlength: 8, maxlength: 128, autocomplete: "new-password" });
        form({
            eyebrow: "FIRST-TIME SETUP",
            title: "Create the administrator",
            intro: "This happens once. Your existing projects and tasks are kept exactly as they are.",
            fields: [
                field("Your name", displayName),
                field("Username", username, "3–40 letters, numbers, dots, dashes or underscores."),
                field("Password", password, "At least 8 characters."),
                field("Confirm password", confirm),
            ],
            submitLabel: "Create administrator",
            onSubmit: async fail => {
                if (password.value !== confirm.value) fail("The two passwords don't match.");
                goHome(await post("/api/auth/setup", { username: username.value, displayName: displayName.value, password: password.value }));
            },
        });
    }

    async function start() {
        let status;
        try {
            status = await (await fetch("/api/auth/status", { credentials: "same-origin" })).json();
        } catch {
            card.replaceChildren(message("error", "Can't reach the server. Check that FOCUS FLOW is running."));
            return;
        }
        if (status.authenticated) { goHome(status); return; }
        if (status.setupRequired) { showSetup(); return; }
        const notice = params.has("expired") ? message("info", "Your session ended. Please sign in again.")
            : params.has("signedOut") ? message("info", "You've signed out.") : null;
        if (params.has("code")) showCode(notice); else showSignIn(notice);
        if (params.has("expired") || params.has("signedOut")) history.replaceState(null, "", "/login");
    }

    start();
})();
