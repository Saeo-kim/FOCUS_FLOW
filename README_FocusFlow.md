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
