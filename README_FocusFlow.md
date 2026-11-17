# FocusFlow

> **Make room for what matters.**

A project and task management platform designed to organize work, track progress, and identify what needs to be done next.

FocusFlow helps users manage projects, organize tasks into actionable work steps, and monitor their overall progress in one place.

---

## 1. Project Overview

| Category | Description |
|---|---|
| Project Name | FocusFlow |
| Project Type | Web-based Project Management |
| Development Status | In Progress |
| Main Purpose | Project & Task Management |
| Development Environment | IntelliJ IDEA |
| Language | Java 21 |
| Framework | Spring Boot |

### Project Goal

The goal of FocusFlow is to make work easier to understand and manage.

Instead of simply listing tasks, FocusFlow focuses on helping users understand:

- What am I currently working on?
- What have I completed?
- What should I work on next?
- How much progress have I made?
- What are the remaining steps?

---

## 2. Core Features

### Overview

- Dashboard for an overall project overview
- Task priority management
- Current work status
- Progress monitoring
- Quick access to important tasks

### My Tasks

- Manage personal tasks
- Organize tasks by priority and status
- Track pending and completed work

### Task Details

- Detailed task information
- Work Sequence management
- Task progress percentage
- Individual work step management
- Add and organize work steps

### Activity Log

- Track work activities
- Review changes and progress history

### Workflow Templates

- Organize reusable work processes
- Create structured workflows for repetitive tasks

### Settings

- Manage application preferences
- Configure workspace settings

---

## 3. Tech Stack

### Backend

| Category | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.1.2 |
| Web | Spring Web MVC |
| ORM | Spring Data JPA |
| Validation | Spring Boot Validation |
| Database Access | Hibernate |

### Frontend

| Category | Technology |
|---|---|
| Template Engine | Thymeleaf |
| Structure | HTML5 |
| Styling | CSS3 |
| Interaction | JavaScript |

### Database

| Category | Technology |
|---|---|
| Main Database | MySQL |
| Development / Testing | H2 |
| ORM | Hibernate |

### Development Tools

| Category | Tool |
|---|---|
| IDE | IntelliJ IDEA |
| Build Tool | Gradle (Groovy DSL) |
| Version Control | Git |
| Repository | GitHub |
| Runtime | JDK 21 |

---

## 4. Application Structure

```text
FocusFlow
│
├── src
│   ├── main
│   │   ├── java
│   │   │   └── com.example.focusflow
│   │   │       │
│   │   │       ├── config
│   │   │       ├── controller
│   │   │       ├── service
│   │   │       ├── repository
│   │   │       ├── entity
│   │   │       ├── dto
│   │   │       └── FocusFlowApplication.java
│   │   │
│   │   └── resources
│   │       ├── static
│   │       │   ├── css
│   │       │   ├── js
│   │       │   └── images
│   │       │
│   │       ├── templates
│   │       │   ├── overview.html
│   │       │   ├── my-tasks.html
│   │       │   ├── task-details.html
│   │       │   ├── activity-log.html
│   │       │   ├── workflow-templates.html
│   │       │   └── settings.html
│   │       │
│   │       └── application.properties
│   │
│   └── test
│
├── build.gradle
├── settings.gradle
├── gradlew
└── README.md
```

*Note: The directory structure represents the intended application architecture and may change as development progresses.*

---

## 5. Main Navigation

| Page | Purpose |
|---|---|
| Overview | Overall project and task overview |
| My Tasks | Personal task management |
| Task Details | Detailed task and work sequence management |
| Activity Log | Work activity history |
| Workflow Templates | Reusable workflow management |
| Settings | Application settings |

---

## 6. Set Up on a Windows PC

### Requirements

| Software | Version | Notes |
|---|---|---|
| JDK | 21 (Eclipse Temurin recommended) | `java -version` must report 21 |
| MySQL Server | 8.0 or newer | Must be running on the same PC |
| Git | any | Or download the branch as a ZIP |

Gradle does not need to be installed; the project includes its own wrapper.

### Install and Run

```powershell
git clone -b feature/academy-ready https://github.com/Saeo-kim/FOCUS_FLOW.git
cd FOCUS_FLOW\Focus_Flow
copy application-local.properties.example application-local.properties
notepad application-local.properties
.\start-focusflow.bat
```

In Notepad, enter your MySQL password and port, then save:

| Setting | Meaning | Default |
|---|---|---|
| `DB_USERNAME` | MySQL user | `root` |
| `DB_PASSWORD` | MySQL password | none |
| `DB_PORT` | MySQL port | `3306` in the example file |
| `DB_NAME` | Database name, created automatically | `focus_flow` |

The first start builds the application, which takes a few minutes. Open <http://localhost:8080>. The start window also lists the address to use from a phone on the same network.

On an empty database, the first start creates all tables and registers the 부산한끼 project: its schedule, team roles, responsibilities, tasks, deadlines, requirements and workflow template.

### Bring Your Existing Records to Another PC

The registered project starts with every task open. To continue with your actual progress, attendance and logs, copy your database. Do this **before** the first start on the new PC.

On the original PC (reads only; the file is saved in `%USERPROFILE%\focus_flow_backups`):

```powershell
.\scripts\export-data.bat 8181 root
```

Move the file privately, for example by USB. On the new PC:

```powershell
.\scripts\import-data.bat C:\path\to\focus_flow_backup_YYYYMMDD-HHmmss.sql 3306 root
```

The import refuses to run on a database that already has tables, so it cannot overwrite anything. Each PC keeps its own copy, and changes do not sync between them.

### Never Commit

- `application-local.properties` (database credentials)
- `*.sql` and `focus_flow_backup*` (attendance and personal work records)
- `.env`, API keys and tokens
- `build/`, `.gradle/`, `.idea/workspace.xml`, `.idea/dataSources*`

---

## 7. Development Roadmap

- [x] Project concept and direction
- [x] Initial application setup
- [x] Overview interface
- [x] Task priority interface
- [ ] Task registration and editing
- [ ] Task detail management
- [ ] Work Sequence functionality
- [ ] Progress tracking
- [ ] Activity Log
- [ ] Workflow Templates
- [ ] Database integration
- [ ] User authentication
- [ ] Application deployment

---

## 8. Vision

FocusFlow is built around a simple idea:

**Understand your work. Organize your next step. Keep moving forward.**

The objective is not just to manage tasks, but to create a clearer relationship between projects, individual tasks, and the actions required to complete them.

By making work visible and actionable, FocusFlow aims to help individuals and small teams spend less time figuring out what to do and more time getting things done.

---

**FocusFlow — Make room for what matters.**


---

## Appendix: Accounts, Roles and Workspaces

FocusFlow is one application with one sign-in. Where you land depends on your role.

| Address | Who | What it is |
|---|---|---|
| `/login` | Everyone | Sign in, join with an invitation code, password help, first-time administrator setup |
| `/admin` | ADMIN | Management workspace: Company Overview, Members, Task Assignment, Review Queue, Submission Tracker, Files, and every original FocusFlow page |
| `/member` | MEMBER | Personal workspace: My Overview, My Tasks, My Projects, Calendar, My Submissions, My Account |

`/` sends you to the right place. Signed-out visitors see only the login page.

### First start with accounts

1. Open `http://localhost:8080/login`. On a database without an administrator, the page asks you to create one. Your existing projects and tasks are kept as they are.
2. In **Members**, choose **Invite member**. FocusFlow shows a one-time code (valid 7 days).
3. The member opens `/login`, chooses **I have an invitation code**, and picks a password.
4. In **Task Assignment**, give tasks an owner. The member sees them at once and gets a notification.

Forgotten passwords go through the administrator: **Members → Reset access** issues a new code. Email sending is not built in.

### How access is enforced

- Every `/api/**` request is checked on the server. The original APIs are ADMIN only.
- `/api/member/**` answers only with the signed-in member's own tasks, projects, submissions and files. Another member's ids answer `404`.
- Workspace pages are refused to the wrong role, also when opened by file name.

### Database changes

Only new tables were added (`app_users`, `project_members`, `task_assignments`, `submissions`, `submission_files`, `submission_events`, `notifications`). None has a foreign key into an existing table, and no existing table or column was changed. Uploaded files are stored outside the project in `%USERPROFILE%\focus_flow_uploads`.

---

## Appendix: AI Team (Owner's Office as CFO)

FOCUS FLOW has AI teammates alongside people. Each one has a seat in the member list (kind **AI**), a title, a department and written responsibilities. The first teammate is **Owner's Office**, the CFO (Finance & Accounting). It is a separate application that keeps the financial records. FOCUS FLOW never copies them.

| Seat | Title | Department | Connected application |
| --- | --- | --- | --- |
| Owner's Office | CFO (Chief Financial Officer) | Finance & Accounting | Owner's Office (`owners-office`, port 4320 on this PC) |

### Set up the connection

1. In Owner's Office, set `OFFICE_AGENT_TOKEN` to a random string of at least 32 characters, then start it (see its README). Leave the variable unset and the agent API stays off.
2. Put the same value in FOCUS FLOW's `application-local.properties` as `owners-office.token=...`, or set the environment variable `OWNERS_OFFICE_TOKEN` before starting FOCUS FLOW.
3. Open **AI Team** in the admin workspace. The CFO shows **Connected** when both sides agree.

Without a token the CFO still appears in the member list, but nothing is sent. The token is a secret: keep it out of Git, as with the database password. It only ever goes to `127.0.0.1` and is never shown in the UI or in error messages.

### How work reaches the CFO

| You do | What happens |
| --- | --- |
| Assign a task to the CFO (Task Assignment) | The task is sent to Owner's Office and turns **In progress** |
| Mention **@CFO** in a task comment (admin or member) | A request is made from that comment; the CFO's answer is posted as a comment |
| **Ask the CFO** on the AI Team page | A task is created and assigned to the CFO, then sent. You can choose a report type or let the CFO choose from the words you use |
| **Sync now** on the AI Team page | Pulls the latest progress and sends anything still waiting |

When the report is ready, the CFO posts it as a comment on the task, the task becomes **Completed** (or **Review** if it still has open work steps), and administrators get a notification. If the CFO cannot finish, the task becomes **Blocked** and the comment says so.

Reports cover monthly reports, profit and loss, cash flow and runway, bookkeeping status, tax-preparation material (not filing), investment account totals (balances entered in Owner's Office, not market prices), planning, and risk review. Each report states its figures, its basis, and its limits.

### Data and safety

- Owner's Office returns **aggregates only** (totals, findings, recommendations). Account names, account notes and ledger lines never cross over. A test checks this with real data.
- FOCUS FLOW keeps the request, its status and the report text. It does not keep ledger rows.
- The CFO seat has no password and no access code. It cannot sign in, and it cannot be turned into a person's account.
- If Owner's Office is closed, a request waits as **Waiting to send** and is retried by the background sync (every minute by default, `owners-office.sync-interval-ms`). Each request is sent with a unique reference, so a retry never creates a duplicate.

### Adding more AI teammates later

Add an entry to `aiteam/AiTeamCatalog.java` (key, username, title, department, responsibilities, avatar). Its seat is created at the next start, and assignment and @mentions work through the same path. The planned roles are CTO (Engineering), COO (Operations), CMO (Marketing & Sales), CPO (Design & Product) and HR Manager (People & HR). Each needs an application that speaks the same agent API, or it can be built inside FOCUS FLOW.

### Database changes

Only new tables were added: `ai_requests` and `task_comments`. No existing table or column changed. The CFO seat is an ordinary `app_users` row with `kind = AI`.
