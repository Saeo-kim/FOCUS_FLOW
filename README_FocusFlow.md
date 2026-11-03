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

## 6. Development Environment

### Requirements

- JDK 21
- IntelliJ IDEA
- Gradle
- MySQL (when using the MySQL configuration)

### Run Locally

Clone the repository:

```bash
git clone https://github.com/Saeo-kim/focusflow.git
```

Move into the project directory:

```bash
cd focusflow
```

Run the application on Windows:

```bash
gradlew.bat bootRun
```

Open the application:

```text
http://localhost:8080
```

*The repository URL and startup configuration should be adjusted to match the actual GitHub repository and application settings.*

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
