"use strict";

/*
 * MEMBER workspace (member.html). Loaded after app.js, workspace.js and shell.js.
 * The admin pages registered by app.js are removed from ROUTES here, so a member's browser never
 * renders them; the server refuses their APIs regardless.
 */
(() => {
    async function viewOverview() {
        const user = await FF.ready();
        return h("div", null,
            h("section", { class: "welcome" },
                h("div", { class: "eyebrow" }, "YOUR WORKSPACE"),
                h("h1", null, `Hello, ${user.displayName}.`),
                h("p", { class: "subtitle" }, "Make room for what matters.")),
            h("div", { class: "card empty" }, "Your assigned projects and tasks will appear here."));
    }

    const MEMBER_ROUTES = {
        overview: ["My Overview", "overview", viewOverview],
        account: ["My Account", "user", () => FF.viewAccount()],
    };
    for (const key of Object.keys(ROUTES)) delete ROUTES[key];
    Object.assign(ROUTES, MEMBER_ROUTES);

    FF.buildNav([[null, [["overview", "My Overview", "overview"]]], ["ACCOUNT", [["account", "My Account", "user"]]]]);
    FF.mountHeader({ role: "MEMBER", links: [["#/overview", "overview", "My Overview"], ["#/account", "user", "My Account"]] });
})();
