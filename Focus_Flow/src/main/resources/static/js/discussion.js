"use strict";

/*
 * Task discussion, shared by the admin and member task pages. Comments come from people and from
 * AI teammates. Mentioning @CFO asks the CFO to answer; its report is posted here as a comment.
 * Loaded after shell.js (FF) and before the page scripts. All text is written as text nodes.
 */
function renderDiscussion(taskId, { base = "admin" } = {}) {
    const path = `/api/${base}/tasks/${taskId}/comments`;
    const thread = h("div", { class: "thread" }, h("div", { class: "muted small" }, "Loading comments…"));
    const input = h("textarea", {
        rows: 3, maxlength: 2000, "aria-label": "New comment",
        placeholder: "Write a comment. Mention @CFO to ask the AI CFO for a financial report.",
    });
    const post = h("button", { class: "btn primary", type: "button" }, "Post comment");

    const when = (s) => (s ? new Date(s).toLocaleString("en-US", { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) : "");

    function commentNode(c) {
        return h("article", { class: "comment" + (c.fromAgent ? " from-agent" : "") },
            h("header", { class: "comment-head" },
                h("strong", null, c.authorName),
                c.fromAgent && h("span", { class: "tag ai" }, "AI teammate"),
                h("span", { class: "muted small" }, when(c.createdAt))),
            h("div", { class: "comment-body" }, c.body),
            c.aiRequestId && base === "admin" && h("a", { class: "small", href: `#/ai-team?request=${c.aiRequestId}` }, "See the request"));
    }

    async function load() {
        try {
            const items = await FF.call(path);
            thread.replaceChildren(...(items.length
                ? items.map(commentNode)
                : [h("div", { class: "muted small" }, "No comments yet.")]));
        } catch (error) {
            thread.replaceChildren(h("div", { class: "muted small" }, error.message));
        }
    }

    post.addEventListener("click", async () => {
        const body = input.value.trim();
        if (!body) {
            toast("Write something before posting.", true);
            input.focus();
            return;
        }
        post.disabled = true;
        try {
            const mentioned = /(^|[^A-Za-z0-9_.@])@cfo\b/i.test(body);
            await FF.call(path, { method: "POST", body: { body } });
            input.value = "";
            await load();
            if (mentioned) toast("Sent to the CFO. The answer will appear here.");
        } catch (error) {
            toast(error.message, true);
        } finally {
            post.disabled = false;
        }
    });

    load();
    return h("section", { class: "card discussion", "aria-label": "Discussion" },
        h("div", { class: "section-heading" },
            h("h2", null, "Discussion"),
            h("span", { class: "muted small" }, "@CFO asks the AI CFO")),
        thread,
        h("div", { class: "comment-form" }, input, h("div", { class: "row" }, post)));
}
