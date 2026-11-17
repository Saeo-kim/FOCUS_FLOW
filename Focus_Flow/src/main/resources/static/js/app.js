"use strict";

/* ---------- Helpers ---------- */

const ICONS = {
    overview: '<rect width="7" height="9" x="3" y="3" rx="1"/><rect width="7" height="5" x="14" y="3" rx="1"/><rect width="7" height="9" x="14" y="12" rx="1"/><rect width="7" height="5" x="3" y="16" rx="1"/>',
    work: '<path d="m9 11 3 3L22 4"/><path d="M21 12v7a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11"/>',
    folder: '<path d="M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z"/>',
    calendar: '<path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/>',
    clock: '<circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/>',
    activity: '<path d="M22 12h-4l-3 9L9 3l-3 9H2"/>',
    settings: '<line x1="21" x2="14" y1="4" y2="4"/><line x1="10" x2="3" y1="4" y2="4"/><line x1="21" x2="12" y1="12" y2="12"/><line x1="8" x2="3" y1="12" y2="12"/><line x1="21" x2="16" y1="20" y2="20"/><line x1="12" x2="3" y1="20" y2="20"/><line x1="14" x2="14" y1="2" y2="6"/><line x1="8" x2="8" y1="10" y2="14"/><line x1="16" x2="16" y1="18" y2="22"/>',
    plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
    pencil: '<path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>',
    trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
    left: '<path d="m15 18-6-6 6-6"/>',
    right: '<path d="m9 18 6-6-6-6"/>',
    menu: '<line x1="4" x2="20" y1="12" y2="12"/><line x1="4" x2="20" y1="6" y2="6"/><line x1="4" x2="20" y1="18" y2="18"/>',
    login: '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" x2="3" y1="12" y2="12"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" x2="9" y1="12" y2="12"/>',
    coffee: '<path d="M17 8h1a4 4 0 1 1 0 8h-1"/><path d="M3 8h14v9a4 4 0 0 1-4 4H7a4 4 0 0 1-4-4Z"/><line x1="6" x2="6" y1="2" y2="4"/><line x1="10" x2="10" y1="2" y2="4"/><line x1="14" x2="14" y1="2" y2="4"/>',
    play: '<polygon points="6 3 20 12 6 21 6 3"/>',
    flag: '<path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"/><line x1="4" x2="4" y1="22" y2="15"/>',
    list: '<line x1="8" x2="21" y1="6" y2="6"/><line x1="8" x2="21" y1="12" y2="12"/><line x1="8" x2="21" y1="18" y2="18"/><line x1="3" x2="3.01" y1="6" y2="6"/><line x1="3" x2="3.01" y1="12" y2="12"/><line x1="3" x2="3.01" y1="18" y2="18"/>',
    briefcase: '<rect width="20" height="14" x="2" y="7" rx="2"/><path d="M16 21V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v16"/>',
    layers: '<polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/>',
    file: '<path d="M15 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7Z"/><path d="M14 2v4a2 2 0 0 0 2 2h4"/><path d="M10 13H8"/><path d="M16 17H8"/><path d="M16 13h-2"/>',
    up: '<path d="m18 15-6-6-6 6"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
};

function icon(name, size = 18) {
    const span = document.createElement("span");
    span.className = "icon";
    span.innerHTML = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${ICONS[name]}</svg>`;
    return span;
}

/** Builds DOM nodes; user data only ever reaches the page as text nodes or attribute values. */
function h(tag, attrs, ...children) {
    const node = document.createElement(tag);
    const { value, ...rest } = attrs || {};
    for (const [key, val] of Object.entries(rest)) {
        if (val == null || val === false) continue;
        if (key === "class") node.className = val;
        else if (key.startsWith("on")) node.addEventListener(key.slice(2), val);
        else node.setAttribute(key, val === true ? "" : val);
    }
    for (const child of children.flat(Infinity)) {
        if (child != null && child !== false) node.append(child);
    }
    if (value != null) node.value = value; // after children, so <select> has its options
    return node;
}

async function api(path, { method = "GET", body } = {}) {
    let response;
    try {
        response = await fetch(path, {
            method,
            headers: body === undefined ? {} : { "Content-Type": "application/json" },
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw new Error("Can't reach the server. Check that FOCUS FLOW is running.");
    }
    if (response.status === 204) return null;
    const data = await response.json().catch(() => null);
    if (!response.ok) throw new Error(data?.message || "Something went wrong. Please try again.");
    return data;
}

let toastTimer;
function toast(message, isError = false) {
    const el = document.getElementById("toast");
    el.textContent = message;
    el.className = "toast show" + (isError ? " error" : "");
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.className = "toast"; }, 3200);
}

/** Runs a change, reports its failure as a toast, and re-renders the page on success. */
async function act(fn, successMessage) {
    try {
        await fn();
        if (successMessage) toast(successMessage);
        await refresh();
        return true;
    } catch (error) {
        toast(error.message, true);
        return false;
    }
}

const pad = n => String(n).padStart(2, "0");
const isoDate = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
const todayIso = () => isoDate(new Date());
const monthKey = d => `${d.getFullYear()}-${pad(d.getMonth() + 1)}`;
const parseDate = s => { const [y, m, d] = s.split("-").map(Number); return new Date(y, m - 1, d); };
const parseMonth = s => { const [y, m] = s.split("-").map(Number); return new Date(y, m - 1, 1); };
const shiftMonth = (s, delta) => { const d = parseMonth(s); return monthKey(new Date(d.getFullYear(), d.getMonth() + delta, 1)); };

const fmtMonth = s => parseMonth(s).toLocaleDateString("en-US", { month: "long", year: "numeric" });
const fmtDate = s => parseDate(s).toLocaleDateString("en-US", { month: "short", day: "numeric" });
const fmtDateLong = s => parseDate(s).toLocaleDateString("en-US", { weekday: "long", month: "long", day: "numeric" });
const fmtDateRow = s => parseDate(s).toLocaleDateString("en-US", { weekday: "short", month: "short", day: "numeric" });
// Timestamps carry microseconds, which not every mobile browser parses; seconds are enough here.
const fmtTime = s => s ? new Date(s.slice(0, 19)).toLocaleTimeString("en-US", { hour: "numeric", minute: "2-digit" }) : "—";

function fmtHM(seconds) {
    const minutes = Math.floor(seconds / 60);
    return `${Math.floor(minutes / 60)}h ${pad(minutes % 60)}m`;
}

function fmtHMS(seconds) {
    return `${pad(Math.floor(seconds / 3600))}:${pad(Math.floor(seconds / 60) % 60)}:${pad(seconds % 60)}`;
}

/* ---------- Tasks ---------- */

const PRIORITY_ORDER = { HIGH: 1, MEDIUM: 2, LOW: 3 };
const PRIORITY_TAG = { HIGH: "red", MEDIUM: "amber", LOW: "gray" };

function sortTasks(tasks) {
    return [...tasks].sort((a, b) =>
        Number(a.done) - Number(b.done)
        || PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority]
        || (a.dueDate || "9999").localeCompare(b.dueDate || "9999")
        || a.id - b.id);
}

const isOverdue = t => !t.done && t.dueDate && t.dueDate < todayIso();

/** Today's list: open work that is undated or has come due, plus whatever was finished or due today. */
function isTodayTask(t) {
    const today = todayIso();
    if (t.done) {
        return t.completedAt?.slice(0, 10) === today || t.dueDate === today || t.scheduledDate === today;
    }
    if (!t.dueDate && !t.scheduledDate) return true;
    return (t.dueDate && t.dueDate <= today) || (t.scheduledDate && t.scheduledDate <= today);
}

function taskRow(task) {
    const checkbox = h("input", {
        type: "checkbox",
        "aria-label": `Mark "${task.title}" as complete`,
        onchange: () => act(() => api(`/api/tasks/${task.id}/done`, { method: "PATCH", body: { done: checkbox.checked } })),
    });
    checkbox.checked = task.done;

    return h("div", { class: "task" + (task.done ? " done" : "") },
        checkbox,
        h("div", { class: "task-info" },
            h("div", { class: "task-name" }, h("a", { class: "task-link", href: `#/task?id=${task.id}` }, task.title)),
            h("div", { class: "task-meta" },
                h("span", { class: "tag " + PRIORITY_TAG[task.priority] }, `${task.priority} PRIORITY`),
                !task.done && task.status !== "NOT_STARTED" && h("span", { class: "tag " + WORK_STATUS[task.status][1] }, WORK_STATUS[task.status][0]),
                task.stepCount > 0 && h("span", { class: "tag gray" }, `${task.completedStepCount}/${task.stepCount} steps`),
                task.dueDate && h("span", { class: "tag " + (isOverdue(task) ? "red" : "blue") },
                    (isOverdue(task) ? "Overdue · " : "Due ") + fmtDate(task.dueDate)),
                task.scheduledDate && h("span", { class: "tag gray" }, "Scheduled " + fmtDate(task.scheduledDate)),
                task.projectName && h("a", { class: "tag", href: `#/my-work?project=${task.projectId}` }, task.projectName))),
        h("div", { class: "task-actions" },
            h("button", { class: "icon-btn", type: "button", "aria-label": "Edit task", onclick: () => openTaskDialog(task) }, icon("pencil", 16)),
            h("button", {
                class: "icon-btn danger", type: "button", "aria-label": "Delete task",
                onclick: () => {
                    if (confirm(`Delete "${task.title}"?`)) act(() => api(`/api/tasks/${task.id}`, { method: "DELETE" }), "Task deleted.");
                },
            }, icon("trash", 16))));
}

function priorityOptions() {
    return [["HIGH", "High"], ["MEDIUM", "Medium"], ["LOW", "Low"]].map(([v, l]) => h("option", { value: v }, l));
}

function openModal(content) {
    const modal = document.getElementById("modal");
    modal.replaceChildren(content);
    if (!modal.open) modal.showModal();
}

const closeModal = () => document.getElementById("modal").close();

const cancelButton = () => h("button", { class: "btn", type: "button", onclick: closeModal }, "Cancel");

async function openTaskDialog(task, defaults = {}) {
    let projects, responsibilities;
    try {
        [projects, responsibilities] = await Promise.all([api("/api/projects"), api("/api/responsibilities")]);
    } catch (error) {
        toast(error.message, true);
        return;
    }
    const values = task || defaults;
    const title = h("input", { type: "text", maxlength: 200, required: true, value: values.title || "" });
    const description = h("textarea", { maxlength: 1000, value: values.description || "" });
    const priority = h("select", { value: values.priority || "MEDIUM" }, priorityOptions());
    const due = h("input", { type: "date", value: values.dueDate || "" });
    const scheduled = h("input", { type: "date", value: values.scheduledDate || "" });
    const status = h("select", { value: values.status || "NOT_STARTED" },
        Object.entries(WORK_STATUS).map(([v, [label]]) => h("option", { value: v }, label)));
    const responsibility = h("select");
    const project = h("select", {
        value: values.projectId ?? "",
        onchange: () => fillResponsibilities(""),
    },
        h("option", { value: "" }, "No project"),
        projects.map(p => h("option", { value: p.id }, p.name)));

    function fillResponsibilities(selected) {
        responsibility.replaceChildren(h("option", { value: "" }, "No responsibility"),
            ...responsibilities.filter(r => String(r.projectId) === project.value).map(r => h("option", { value: r.id }, r.title)));
        responsibility.value = selected ?? "";
    }
    fillResponsibilities(values.responsibilityId ?? "");

    const form = h("form", {
        onsubmit: async event => {
            event.preventDefault();
            const body = {
                title: title.value,
                description: description.value,
                priority: priority.value,
                dueDate: due.value || null,
                scheduledDate: scheduled.value || null,
                projectId: project.value ? Number(project.value) : null,
                responsibilityId: responsibility.value ? Number(responsibility.value) : null,
                status: status.value,
            };
            const saved = await act(
                () => task ? api(`/api/tasks/${task.id}`, { method: "PUT", body }) : api("/api/tasks", { method: "POST", body }),
                task ? "Task updated." : "Task added.");
            if (saved) closeModal();
        },
    },
        h("h2", null, task ? "Edit task" : "New task"),
        h("div", { class: "form-grid" },
            h("label", { class: "field full" }, "Title", title),
            h("label", { class: "field full" }, "Description", description),
            h("label", { class: "field" }, "Priority", priority),
            h("label", { class: "field" }, "Status", status),
            h("label", { class: "field" }, "Project", project),
            h("label", { class: "field" }, "Responsibility", responsibility),
            h("label", { class: "field" }, "Deadline", due),
            h("label", { class: "field" }, "Scheduled for", scheduled)),
        h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Save")));
    openModal(form);
}

function openProjectDialog(project) {
    const name = h("input", { type: "text", maxlength: 120, required: true, value: project?.name || "" });
    const description = h("textarea", { maxlength: 1000, value: project?.description || "" });
    const status = h("select", { value: project?.status || "ACTIVE" },
        Object.entries(PROJECT_STATUS).map(([v, [label]]) => h("option", { value: v }, label)));
    const due = h("input", { type: "date", value: project?.dueDate || "" });
    const projectType = h("input", { type: "text", maxlength: 80, value: project?.projectType || "" });
    const teamName = h("input", { type: "text", maxlength: 80, value: project?.teamName || "" });
    const teamNumber = h("input", { type: "number", min: 0, value: project?.teamNumber ?? "" });
    const start = h("input", { type: "date", value: project?.startDate || "" });
    const devStart = h("input", { type: "date", value: project?.devStartDate || "" });
    const devEnd = h("input", { type: "date", value: project?.devEndDate || "" });
    const techStack = h("textarea", { maxlength: 1000, value: project?.techStack || "" });
    const features = h("textarea", { maxlength: 1000, value: project?.serviceFeatures || "" });
    const purpose = h("textarea", { maxlength: 2000, rows: 4, value: project?.purpose || "",
        placeholder: "Why does this project matter? What changes for you when it is done?" });

    const form = h("form", {
        onsubmit: async event => {
            event.preventDefault();
            const body = {
                name: name.value, description: description.value, status: status.value, dueDate: due.value || null,
                projectType: projectType.value, teamName: teamName.value,
                teamNumber: teamNumber.value === "" ? null : Number(teamNumber.value),
                startDate: start.value || null, devStartDate: devStart.value || null, devEndDate: devEnd.value || null,
                techStack: techStack.value, serviceFeatures: features.value, purpose: purpose.value,
            };
            const saved = await act(
                () => project ? api(`/api/projects/${project.id}`, { method: "PUT", body }) : api("/api/projects", { method: "POST", body }),
                project ? "Project updated." : "Project created.");
            if (saved) closeModal();
        },
    },
        h("h2", null, project ? "Edit project" : "New project"),
        h("div", { class: "form-grid" },
            h("label", { class: "field full" }, "Name", name),
            h("label", { class: "field full" }, "Purpose (why this matters)", purpose),
            h("label", { class: "field full" }, "Description", description),
            h("label", { class: "field" }, "Status", status),
            h("label", { class: "field" }, "Project type", projectType),
            h("label", { class: "field" }, "Team name", teamName),
            h("label", { class: "field" }, "Team number", teamNumber),
            h("label", { class: "field" }, "Start date", start),
            h("label", { class: "field" }, "End date", due),
            h("label", { class: "field" }, "Development start", devStart),
            h("label", { class: "field" }, "Development end", devEnd),
            h("label", { class: "field full" }, "Tech stack", techStack),
            h("label", { class: "field full" }, "Service features", features)),
        h("div", { class: "modal-actions" }, cancelButton(), h("button", { class: "btn primary", type: "submit" }, "Save")));
    openModal(form);
}

/* ---------- Attendance shared ---------- */

const ATT_STATE = {
    NOT_CHECKED_IN: ["Not checked in", "gray"],
    WORKING: ["Working", "green"],
    ON_BREAK: ["On break", "amber"],
    CHECKED_OUT: ["Checked out", "blue"],
};

let live = null;

function setLive(att) {
    live = att.record
        ? { state: att.state, worked: att.record.workedSeconds, brk: att.record.breakSeconds, target: att.dailyTargetMinutes * 60, at: Date.now() }
        : null;
}

function paintLive() {
    if (!live) return;
    const elapsed = Math.floor((Date.now() - live.at) / 1000);
    const worked = live.worked + (live.state === "WORKING" ? elapsed : 0);
    const brk = live.brk + (live.state === "ON_BREAK" ? elapsed : 0);
    const text = { "worked-hms": fmtHMS(worked), "worked-hm": fmtHM(worked), "break-hm": fmtHM(brk) };
    document.querySelectorAll("[data-live]").forEach(el => {
        if (el.dataset.live === "progress") el.style.width = Math.min(100, Math.round(worked / live.target * 100)) + "%";
        else el.textContent = text[el.dataset.live];
    });
}

setInterval(paintLive, 1000);

function attendanceAction(path, message, body) {
    document.querySelectorAll(".att-btn").forEach(b => { b.disabled = true; });
    return act(() => api(`/api/attendance/${path}`, { method: "POST", body }), message).then(ok => {
        if (!ok) refresh();
    });
}

const ATT_ACTIONS = [
    ["check-in", "Check In", "login", "Checked in.", ["NOT_CHECKED_IN", "CHECKED_OUT"], "primary"],
    ["break/start", "Start Break", "coffee", "Break started.", ["WORKING"], ""],
    ["break/end", "End Break", "play", "Break ended.", ["ON_BREAK"], ""],
    ["check-out", "Check Out", "logout", "Checked out.", ["WORKING"], "primary"],
];

function attendanceButtons(att, onlyEnabled) {
    return ATT_ACTIONS
        .filter(([, , , , states]) => !onlyEnabled || states.includes(att.state))
        .map(([path, label, ic, message, states, style]) =>
            h("button", {
                class: `btn att-btn ${style}`, type: "button",
                disabled: !states.includes(att.state),
                onclick: () => {
                    const resuming = path === "check-in" && att.state === "CHECKED_OUT";
                    if (resuming && !confirm("You already checked out today. Resume this shift? The time since check-out will count as a break.")) return;
                    attendanceAction(path, message);
                },
            }, icon(ic, 16), label));
}

/* ---------- Calendar shared ---------- */

function groupByDate(entries) {
    const map = new Map();
    for (const entry of entries) {
        if (!map.has(entry.date)) map.set(entry.date, []);
        map.get(entry.date).push(entry);
    }
    return map;
}

function monthCells(month, makeDay) {
    const first = parseMonth(month);
    const year = first.getFullYear();
    const m = first.getMonth();
    const days = new Date(year, m + 1, 0).getDate();
    const cells = [];
    for (let i = 0; i < first.getDay(); i++) cells.push(h("div", { class: "cal-day blank" }));
    for (let day = 1; day <= days; day++) cells.push(makeDay(isoDate(new Date(year, m, day)), day));
    return cells;
}

const WEEKDAYS = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
const weekdayHeads = () => WEEKDAYS.map(d => h("div", { class: "cal-head" }, d));

const ENTRY_LABEL = {
    TASK_DUE: "Deadline", TASK_SCHEDULED: "Scheduled", PROJECT_DUE: "Project deadline",
    DEADLINE_TEAM: "Team deadline", DEADLINE_PERSONAL: "Personal deadline",
    RESPONSIBILITY_DUE: "My responsibility", TEAM_PHASE: "Team schedule",
};
const CHIP_CLASS = {
    TASK_DUE: "due", TASK_SCHEDULED: "sched", PROJECT_DUE: "project",
    DEADLINE_TEAM: "team", DEADLINE_PERSONAL: "personal",
    RESPONSIBILITY_DUE: "personal", TEAM_PHASE: "phase",
};
const ENTRY_TAG = {
    TASK_DUE: "blue", TASK_SCHEDULED: "gray", PROJECT_DUE: "",
    DEADLINE_TEAM: "teal", DEADLINE_PERSONAL: "", RESPONSIBILITY_DUE: "", TEAM_PHASE: "teal",
};
const ENTRY_TAB = { DEADLINE_TEAM: "deadlines", DEADLINE_PERSONAL: "deadlines", RESPONSIBILITY_DUE: "responsibilities", TEAM_PHASE: "schedule" };

function chipClass(entry) {
    return `chip ${CHIP_CLASS[entry.type]}${entry.important ? " important" : ""}${entry.done ? " done" : ""}`;
}

async function openEntry(entry) {
    if (ENTRY_TAB[entry.type]) {
        location.hash = `#/project?id=${entry.projectId}&tab=${ENTRY_TAB[entry.type]}`;
        return;
    }
    try {
        if (entry.taskId != null) {
            const task = (await api("/api/tasks")).find(t => t.id === entry.taskId);
            if (task) openTaskDialog(task);
        } else {
            const project = (await api("/api/projects")).find(p => p.id === entry.projectId);
            if (project) openProjectDialog(project);
        }
    } catch (error) {
        toast(error.message, true);
    }
}

function entryRow(entry, showDate) {
    return h("button", {
        class: `entry${entry.important ? " important" : ""}${entry.done ? " done" : ""}`,
        type: "button",
        onclick: () => openEntry(entry),
    },
        h("div", { class: "entry-title" }, entry.title),
        h("div", { class: "task-meta" },
            showDate && h("span", { class: "tag gray" }, fmtDate(entry.date)),
            h("span", { class: "tag " + ENTRY_TAG[entry.type] }, ENTRY_LABEL[entry.type]),
            entry.important && h("span", { class: "tag red" }, "Important"),
            entry.done && h("span", { class: "tag green" }, "Done"),
            entry.taskId != null && entry.projectName && h("span", { class: "tag" }, entry.projectName)));
}

/* ---------- Views ---------- */

const PROJECT_STATUS = { ACTIVE: ["Active", "green"], ON_HOLD: ["On hold", "amber"], COMPLETED: ["Completed", "blue"] };

const pct = (done, total) => total === 0 ? 0 : Math.round(done / total * 100);

function progressBar(percent, liveKey) {
    return h("div", { class: "progress-track" },
        h("div", { class: "progress-bar", style: `width:${percent}%`, "data-live": liveKey }));
}

async function viewOverview() {
    const month = monthKey(new Date());
    const [tasks, projects, att, entries, activity] = await Promise.all([
        api("/api/tasks"), api("/api/projects"), api("/api/attendance/status"),
        api(`/api/calendar?month=${month}`), api("/api/activity?limit=6"),
    ]);
    setLive(att);
    const deadlines = await upcomingDeadlines(tasks);

    const sorted = sortTasks(tasks.filter(isTodayTask));
    const completed = sorted.filter(t => t.done).length;
    const remaining = sorted.length - completed;
    const percent = pct(completed, sorted.length);
    const next = sorted.find(t => !t.done);

    const taskInput = h("input", { id: "taskInput", type: "text", placeholder: "Add a new task...", maxlength: 200, required: true, "aria-label": "New task title" });
    const quickPriority = h("select", { value: "MEDIUM", "aria-label": "Priority" }, priorityOptions());
    const quickDue = h("input", { type: "date", "aria-label": "Deadline" });

    const addForm = h("form", {
        class: "add-form",
        onsubmit: async event => {
            event.preventDefault();
            const title = taskInput.value.trim();
            if (!title) return;
            const ok = await act(() => api("/api/tasks", {
                method: "POST",
                body: { title, priority: quickPriority.value, dueDate: quickDue.value || null },
            }), "Task added.");
            if (ok) document.getElementById("taskInput")?.focus();
        },
    }, taskInput, quickPriority, quickDue, h("button", { class: "btn primary", type: "submit" }, icon("plus", 16), "Add"));

    const byDate = groupByDate(entries);
    const today = todayIso();
    const upcoming = entries.filter(e => e.date >= today && !e.done).slice(0, 3);

    const record = att.record;

    return h("div", null,
        h("section", { class: "welcome" },
            h("div", { class: "eyebrow" }, "YOUR PERSONAL WORKSPACE"),
            h("h1", null, "Make room for", h("br"), "what matters."),
            h("p", { class: "subtitle" }, "You don't have to do everything at once.", h("br"), "Finish what matters now, one thing at a time.")),

        h("section", { class: "dashboard" },
            h("div", { class: "card focus-card" },
                h("div", { class: "eyebrow" }, "TODAY'S WORK · NEXT BEST ACTION"),
                h("div", { class: "focus-task" }, next ? next.title : "Add what you'll work on next."),
                h("p", { class: "muted" }, next ? "This is your highest-priority open task." : "Everything is done, or it's time to add something new."),
                h("div", { style: "margin-top:12px" }, h("span", { class: "tag" }, next ? `${next.priority} PRIORITY` : "ALL CLEAR")),
                h("button", {
                    class: "btn primary", type: "button", disabled: !next,
                    onclick: () => act(() => api(`/api/tasks/${next.id}/done`, { method: "PATCH", body: { done: true } })),
                }, "Complete this task →")),

            h("div", null,
                h("div", { class: "card" },
                    h("h2", null, "Today's Progress"),
                    h("div", { class: "progress-caption" }, h("span", null, "Completed Tasks"), h("span", null, `${completed} / ${sorted.length}`)),
                    progressBar(percent),
                    h("p", { class: "muted progress-message" },
                        sorted.length === 0 ? "Add a new task to get started."
                            : percent === 100 ? "You've finished everything on today's list!"
                                : completed === 0 ? "Even a small start counts as progress."
                                    : "Every little thing you finish moves you forward.")),
                h("div", { class: "stats" },
                    h("div", { class: "stat" }, h("div", { class: "stat-number" }, remaining), h("div", { class: "stat-label" }, "REMAINING TASKS")),
                    h("div", { class: "stat" }, h("div", { class: "stat-number" }, completed), h("div", { class: "stat-label" }, "COMPLETED"))))),

        h("section", { class: "card block-gap" },
            h("div", { class: "section-heading" }, h("h2", null, "Priority Ranking"), h("span", { class: "tag" }, "MY TASKS")),
            sorted.length
                ? h("div", { class: "task-list" }, sorted.map(taskRow))
                : h("div", { class: "empty" }, "Nothing on today's list."),
            addForm),

        upcomingDeadlinesCard(deadlines),

        h("section", { class: "two-col" },
            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    h("h2", null, "Attendance"),
                    h("span", { class: "tag " + ATT_STATE[att.state][1] }, ATT_STATE[att.state][0])),
                att.missedCheckOut && h("p", { class: "muted", style: "color:var(--amber)" },
                    `Your shift from ${fmtDate(record.workDate)} is still open. `, h("a", { href: "#/attendance" }, "Review it")),
                h("div", { class: "att-facts", style: "margin-top:4px" },
                    h("div", null, h("div", { class: "fact-label" }, "Check in"), h("div", { class: "fact-value" }, fmtTime(record?.checkInAt))),
                    h("div", null, h("div", { class: "fact-label" }, "Check out"), h("div", { class: "fact-value" }, fmtTime(record?.checkOutAt))),
                    h("div", null, h("div", { class: "fact-label" }, "Worked"), h("div", { class: "fact-value", "data-live": "worked-hm" }, fmtHM(record?.workedSeconds || 0)))),
                h("div", { class: "att-mini-actions" },
                    attendanceButtons(att, true),
                    h("a", { class: "btn", href: "#/attendance" }, "Details"))),

            h("div", { class: "card" },
                h("div", { class: "section-heading" },
                    h("h2", null, "Work Calendar"),
                    h("a", { href: "#/calendar" }, fmtMonth(month))),
                h("div", { class: "cal-grid mini mini-cal" },
                    weekdayHeads(),
                    monthCells(month, (date, day) => {
                        const dayEntries = byDate.get(date) || [];
                        return h("a", { class: "cal-day" + (date === today ? " today" : ""), href: `#/calendar?date=${date}`, "aria-label": `${fmtDateLong(date)}, ${dayEntries.length} items` },
                            h("span", { class: "cal-num" }, day),
                            h("span", { class: "dots" }, dayEntries.slice(0, 3).map(e => h("span", { class: "dot" + (e.important ? " important" : "") }))));
                    })),
                h("div", { class: "entry-list", style: "margin-top:12px" },
                    upcoming.length ? upcoming.map(e => entryRow(e, true)) : h("div", { class: "muted" }, "Nothing else scheduled this month.")))),

        h("section", { class: "two-col" },
            h("div", { class: "card" },
                h("div", { class: "section-heading" }, h("h2", null, "Projects"), h("a", { href: "#/projects" }, "View all")),
                projects.length
                    ? projects.filter(p => p.status !== "COMPLETED").slice(0, 4).map(p => h("a", { class: "mini-project", href: `#/my-work?project=${p.id}` },
                        h("div", { class: "mini-project-top" }, h("strong", null, p.name), h("span", { class: "muted" }, `${p.completedTaskCount} / ${p.taskCount}`)),
                        progressBar(pct(p.completedTaskCount, p.taskCount))))
                    : h("div", { class: "empty" }, "No projects yet.")),
            h("div", { class: "card" },
                h("div", { class: "section-heading" }, h("h2", null, "Recent Activity"), h("a", { href: "#/activity" }, "View all")),
                activity.length ? activity.map(logRow) : h("div", { class: "empty" }, "No activity yet."))));
}

const WORK_FILTERS = [
    ["all", "All", () => true],
    ["today", "Today", isTodayTask],
    ["upcoming", "Upcoming", t => !t.done && !isTodayTask(t)],
    ["overdue", "Overdue", isOverdue],
    ["completed", "Completed", t => t.done],
];

const workState = { filter: "all" };

async function viewMyWork(params) {
    const [tasks, projects] = await Promise.all([api("/api/tasks"), api("/api/projects")]);
    const projectId = params.get("project") || "";

    const test = WORK_FILTERS.find(([key]) => key === workState.filter)[2];
    const visible = sortTasks(tasks.filter(t => test(t) && (!projectId || String(t.projectId) === projectId)));

    return h("div", { class: "card" },
        h("div", { class: "toolbar" },
            h("div", { class: "filters" }, WORK_FILTERS.map(([key, label, match]) => h("button", {
                class: "filter", type: "button", "aria-pressed": String(workState.filter === key),
                onclick: () => { workState.filter = key; refresh(); },
            }, `${label} (${tasks.filter(match).length})`))),
            h("select", {
                "aria-label": "Filter by project", value: projectId,
                onchange: event => { location.hash = event.target.value ? `#/my-work?project=${event.target.value}` : "#/my-work"; },
            }, h("option", { value: "" }, "All projects"), projects.map(p => h("option", { value: p.id }, p.name))),
            h("button", {
                class: "btn primary", type: "button",
                onclick: () => openTaskDialog(null, projectId ? { projectId: Number(projectId) } : {}),
            }, icon("plus", 16), "New task")),
        visible.length ? h("div", { class: "task-list" }, visible.map(taskRow)) : h("div", { class: "empty" }, "No tasks here."));
}

async function viewProjects() {
    const projects = await api("/api/projects");

    return h("div", null,
        h("div", { class: "section-heading" },
            h("div", { class: "muted" }, `${projects.length} ${projects.length === 1 ? "project" : "projects"}`),
            h("button", { class: "btn primary", type: "button", onclick: () => openProjectDialog(null) }, icon("plus", 16), "New project")),
        projects.length
            ? h("div", { class: "project-grid" }, projects.map(p => h("div", { class: "card project-card" },
                h("div", { class: "project-head" },
                    h("h3", null, h("a", { class: "task-link", href: `#/project?id=${p.id}` }, p.name)),
                    h("span", { class: "tag " + PROJECT_STATUS[p.status][1] }, PROJECT_STATUS[p.status][0])),
                h("p", { class: "muted desc" }, p.description || "No description."),
                progressBar(pct(p.completedTaskCount, p.taskCount)),
                h("div", { class: "project-foot" },
                    h("a", { href: `#/my-work?project=${p.id}` }, `${p.completedTaskCount} of ${p.taskCount} tasks done`),
                    p.dueDate && h("span", { class: "tag blue" }, "Due " + fmtDate(p.dueDate)),
                    h("div", { class: "task-actions" },
                        h("button", { class: "icon-btn", type: "button", "aria-label": "Edit project", onclick: () => openProjectDialog(p) }, icon("pencil", 16)),
                        h("button", {
                            class: "icon-btn danger", type: "button", "aria-label": "Delete project",
                            onclick: () => {
                                if (confirm(`Delete project "${p.name}"? Its tasks will be kept without a project.`)) {
                                    act(() => api(`/api/projects/${p.id}`, { method: "DELETE" }), "Project deleted.");
                                }
                            },
                        }, icon("trash", 16)))))))
            : h("div", { class: "card empty" }, "Create a project to group related tasks."));
}

const calState = { month: monthKey(new Date()), selected: todayIso() };

function monthNav(label, onShift) {
    return h("div", { class: "month-nav" },
        h("button", { class: "icon-btn", type: "button", "aria-label": "Previous month", onclick: () => onShift(-1) }, icon("left")),
        h("div", { class: "month-label" }, label),
        h("button", { class: "icon-btn", type: "button", "aria-label": "Next month", onclick: () => onShift(1) }, icon("right")));
}

async function viewCalendar(params) {
    const requested = params.get("date");
    if (requested && /^\d{4}-\d{2}-\d{2}$/.test(requested)) {
        calState.selected = requested;
        calState.month = requested.slice(0, 7);
        history.replaceState(null, "", "#/calendar");
    }

    const entries = await api(`/api/calendar?month=${calState.month}`);
    const byDate = groupByDate(entries);
    const today = todayIso();
    const selected = calState.selected;
    const selectedEntries = byDate.get(selected) || [];

    const shift = delta => {
        calState.month = shiftMonth(calState.month, delta);
        calState.selected = calState.month === monthKey(new Date()) ? todayIso() : `${calState.month}-01`;
        refresh();
    };

    return h("div", { class: "cal-layout" },
        h("div", { class: "card" },
            h("div", { class: "section-heading" },
                monthNav(fmtMonth(calState.month), shift),
                h("button", {
                    class: "btn", type: "button",
                    onclick: () => { calState.month = monthKey(new Date()); calState.selected = todayIso(); refresh(); },
                }, "Today")),
            h("div", { class: "cal-grid" },
                weekdayHeads(),
                monthCells(calState.month, (date, day) => {
                    const dayEntries = byDate.get(date) || [];
                    return h("button", {
                        class: `cal-day${date === today ? " today" : ""}${date === selected ? " selected" : ""}`,
                        type: "button",
                        "aria-label": `${fmtDateLong(date)}, ${dayEntries.length} items`,
                        "aria-pressed": String(date === selected),
                        onclick: () => { calState.selected = date; refresh(); },
                    },
                        h("span", { class: "cal-num" }, day),
                        dayEntries.slice(0, 3).map(e => h("span", { class: chipClass(e) }, e.title)),
                        dayEntries.length > 3 && h("span", { class: "chip-more" }, `+${dayEntries.length - 3} more`));
                })),
            h("div", { class: "legend" },
                h("span", { class: "chip important" }, "Important"),
                h("span", { class: "chip due" }, "Deadline"),
                h("span", { class: "chip sched" }, "Scheduled"),
                h("span", { class: "chip project" }, "Project deadline"),
                h("span", { class: "chip personal" }, "My deadline"),
                h("span", { class: "chip team" }, "Team deadline"),
                h("span", { class: "chip phase" }, "Team schedule"))),

        h("div", { class: "card" },
            h("div", { class: "section-heading" },
                h("h2", null, fmtDateLong(selected)),
                h("button", { class: "icon-btn", type: "button", "aria-label": "Add task on this date", onclick: () => openTaskDialog(null, { dueDate: selected }) }, icon("plus"))),
            selectedEntries.length
                ? h("div", { class: "entry-list" }, selectedEntries.map(e => entryRow(e, false)))
                : h("div", { class: "empty" }, "Nothing on this date.")));
}

const attState = { month: monthKey(new Date()) };

function missedCheckOutBanner(att) {
    const record = att.record;
    const nowLocal = `${todayIso()}T${pad(new Date().getHours())}:${pad(new Date().getMinutes())}`;
    const input = h("input", {
        type: "datetime-local", "aria-label": "Actual check-out time",
        min: record.checkInAt.slice(0, 16), max: nowLocal, value: `${record.workDate}T18:00`,
    });
    return h("div", { class: "banner" },
        h("span", null, `Your shift from ${fmtDate(record.workDate)} was never checked out. Enter when you actually finished:`),
        input,
        h("button", {
            class: "btn att-btn", type: "button",
            onclick: () => input.value ? attendanceAction("check-out", "Shift closed.", { at: input.value }) : toast("Enter a check-out time.", true),
        }, "Close shift"));
}

async function viewAttendance() {
    const [att, hist] = await Promise.all([
        api("/api/attendance/status"),
        api(`/api/attendance/history?month=${attState.month}`),
    ]);
    setLive(att);
    const record = att.record;
    const target = att.dailyTargetMinutes * 60;
    const worked = record?.workedSeconds || 0;

    return h("div", { class: "stack" },
        h("div", null,
            att.missedCheckOut && missedCheckOutBanner(att),
            h("div", { class: "att-layout" },
                h("div", { class: "card" },
                    h("div", { class: "section-heading" },
                        h("h2", null, record ? `Shift · ${fmtDateRow(record.workDate)}` : "Today"),
                        h("span", { class: "tag " + ATT_STATE[att.state][1] }, ATT_STATE[att.state][0])),
                    h("div", { class: "fact-label" }, "Worked, excluding breaks"),
                    h("div", { class: "timer", "data-live": "worked-hms" }, fmtHMS(worked)),
                    h("div", { class: "progress-caption" },
                        h("span", null, "Daily target"),
                        h("span", null, fmtHM(target))),
                    progressBar(Math.min(100, pct(worked, target)), "progress"),
                    h("div", { class: "att-facts" },
                        h("div", null, h("div", { class: "fact-label" }, "Check in"), h("div", { class: "fact-value" }, fmtTime(record?.checkInAt))),
                        h("div", null, h("div", { class: "fact-label" }, "Check out"), h("div", { class: "fact-value" }, fmtTime(record?.checkOutAt))),
                        h("div", null, h("div", { class: "fact-label" }, "Break time"), h("div", { class: "fact-value", "data-live": "break-hm" }, fmtHM(record?.breakSeconds || 0)))),
                    h("div", { class: "att-actions" }, attendanceButtons(att, false))),

                h("div", { class: "card" },
                    h("div", { class: "section-heading" }, h("h2", null, "Breaks")),
                    record?.breaks.length
                        ? record.breaks.map(b => h("div", { class: "break-row" },
                            h("span", null, `${fmtTime(b.startedAt)} – ${b.endedAt ? fmtTime(b.endedAt) : "now"}`),
                            h("span", { class: "muted" }, fmtHM(b.seconds))))
                        : h("div", { class: "empty" }, "No breaks recorded.")))),

        h("div", { class: "card" },
            h("div", { class: "section-heading" },
                h("h2", null, "Attendance History"),
                monthNav(fmtMonth(attState.month), delta => { attState.month = shiftMonth(attState.month, delta); refresh(); })),
            h("div", { class: "stats", style: "margin:0 0 16px" },
                h("div", { class: "stat" }, h("div", { class: "stat-number" }, fmtHM(hist.totalWorkedSeconds)), h("div", { class: "stat-label" }, "TOTAL WORKED")),
                h("div", { class: "stat" }, h("div", { class: "stat-number" }, hist.daysWorked), h("div", { class: "stat-label" }, "DAYS WORKED"))),
            hist.records.length
                ? h("div", { class: "table-wrap" }, h("table", null,
                    h("thead", null, h("tr", null, ["Date", "Check in", "Check out", "Breaks", "Work hours", "Status"].map(c => h("th", { scope: "col" }, c)))),
                    h("tbody", null, hist.records.map(r => h("tr", null,
                        h("td", null, fmtDateRow(r.workDate)),
                        h("td", null, fmtTime(r.checkInAt)),
                        h("td", null, fmtTime(r.checkOutAt)),
                        h("td", null, fmtHM(r.breakSeconds)),
                        h("td", null, h("strong", null, fmtHM(r.workedSeconds))),
                        h("td", null, h("span", { class: "tag " + ATT_STATE[r.state][1] }, ATT_STATE[r.state][0])))))))
                : h("div", { class: "empty" }, "No attendance records for this month.")));
}

function logIcon(type) {
    if (type.startsWith("ATTENDANCE")) return "clock";
    if (type.startsWith("PROJECT")) return "folder";
    if (type.startsWith("TASK") || type.startsWith("STEP")) return "work";
    if (type.startsWith("SETTINGS")) return "settings";
    return "file";
}

function logRow(log) {
    return h("div", { class: "log" },
        h("span", { class: "log-icon" }, icon(logIcon(log.type), 14)),
        h("div", { class: "log-body" }, log.message),
        h("span", { class: "log-time" }, fmtTime(log.createdAt)));
}

async function viewActivity() {
    const logs = await api("/api/activity?limit=200");
    let lastDay = null;
    const rows = [];
    for (const log of logs) {
        const day = log.createdAt.slice(0, 10);
        if (day !== lastDay) {
            rows.push(h("div", { class: "day-label" }, fmtDateLong(day)));
            lastDay = day;
        }
        rows.push(logRow(log));
    }
    return h("div", { class: "card" }, rows.length ? rows : h("div", { class: "empty" }, "No activity yet."));
}

async function viewSettings() {
    const settings = await api("/api/settings");
    const displayName = h("input", { type: "text", maxlength: 60, required: true, value: settings.displayName });
    const hours = h("input", { type: "number", min: 1, max: 24, step: 0.5, required: true, value: settings.dailyTargetMinutes / 60 });

    return h("form", {
        class: "card settings-card",
        onsubmit: event => {
            event.preventDefault();
            act(() => api("/api/settings", {
                method: "PUT",
                body: { displayName: displayName.value, dailyTargetMinutes: Math.round(Number(hours.value) * 60) },
            }), "Settings saved.").then(loadWorkspaceName);
        },
    },
        h("div", { class: "stack" },
            h("label", { class: "field" }, "Workspace name", displayName),
            h("label", { class: "field" }, "Daily work target (hours)", hours),
            h("div", null, h("button", { class: "btn primary", type: "submit" }, "Save settings"))));
}

/* ---------- Shell ---------- */

const ROUTES = {
    overview: ["Overview", "overview", viewOverview],
    "my-work": ["My Tasks", "work", viewMyWork],
    task: ["Task Details", "list", params => viewTaskDetails(params)],
    projects: ["Projects", "folder", viewProjects],
    project: ["Project Details", "briefcase", params => viewProjectDetails(params)],
    calendar: ["Work Calendar", "calendar", viewCalendar],
    attendance: ["Attendance", "clock", viewAttendance],
    activity: ["Activity Log", "activity", viewActivity],
    templates: ["Workflow Templates", "layers", () => viewTemplates()],
    reports: ["Reports", "file", params => viewReports(params)],
    settings: ["Settings", "settings", viewSettings],
};

function currentRoute() {
    const [path, query] = location.hash.replace(/^#\/?/, "").split("?");
    return { name: ROUTES[path] ? path : "overview", params: new URLSearchParams(query || "") };
}

const view = document.getElementById("view");
let renderToken = 0;
let renderedDay = todayIso();

async function refresh() {
    const token = ++renderToken;
    const { name, params } = currentRoute();
    const [title, , render] = ROUTES[name];

    document.getElementById("pageTitle").textContent = title;
    document.title = `${title} · FOCUS FLOW`;
    document.querySelectorAll(".nav-link").forEach(link => {
        if (link.dataset.route === name) link.setAttribute("aria-current", "page");
        else link.removeAttribute("aria-current");
    });
    renderedDay = todayIso();
    document.getElementById("today").textContent = new Date().toLocaleDateString("en-US", {
        year: "numeric", month: "long", day: "numeric", weekday: "long",
    });

    try {
        const content = await render(params);
        if (token !== renderToken) return; // a newer navigation has already taken over
        view.replaceChildren(content);
        paintLive();
    } catch (error) {
        if (token !== renderToken) return;
        view.replaceChildren(h("div", { class: "card empty" },
            h("p", null, "This page couldn't be loaded."),
            h("p", { class: "muted" }, error.message),
            h("button", { class: "btn", type: "button", style: "margin-top:12px", onclick: refresh }, "Try again")));
    }
}

function setSidebar(open) {
    document.getElementById("sidebar").classList.toggle("open", open);
    document.getElementById("scrim").classList.toggle("open", open);
}

async function loadWorkspaceName() {
    try {
        document.getElementById("workspaceName").textContent = (await api("/api/settings")).displayName;
    } catch {
        // The current page already reports connection problems.
    }
}

document.getElementById("nav").append(...Object.entries(ROUTES).map(([route, [label, ic]]) =>
    h("a", { class: "nav-link", href: `#/${route}`, "data-route": route }, icon(ic), label)));
document.getElementById("menuButton").append(icon("menu"));
document.getElementById("menuButton").addEventListener("click", () => setSidebar(true));
document.getElementById("scrim").addEventListener("click", () => setSidebar(false));

window.addEventListener("hashchange", () => { setSidebar(false); refresh(); });

// A tab left open overnight must not keep showing yesterday's list and shift.
document.addEventListener("visibilitychange", () => {
    if (!document.hidden && todayIso() !== renderedDay && !document.getElementById("modal").open) refresh();
});

// Deferred so workspace.js, which holds the project pages, has finished loading.
document.addEventListener("DOMContentLoaded", () => {
    loadWorkspaceName();
    refresh();
});
