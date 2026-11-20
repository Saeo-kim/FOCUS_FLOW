package com.example.project_flow.config;

import com.example.project_flow.entity.Deadline;
import com.example.project_flow.entity.DeadlineCategory;
import com.example.project_flow.entity.DeadlineScope;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.Project;
import com.example.project_flow.entity.ProjectPhase;
import com.example.project_flow.entity.ProjectStatus;
import com.example.project_flow.entity.Requirement;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.TeamRole;
import com.example.project_flow.entity.WorkStatus;
import com.example.project_flow.entity.WorkStep;
import com.example.project_flow.entity.WorkflowTemplate;
import com.example.project_flow.entity.WorkspaceSettings;
import com.example.project_flow.repository.DeadlineRepository;
import com.example.project_flow.repository.ProjectPhaseRepository;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.RequirementRepository;
import com.example.project_flow.repository.ResponsibilityRepository;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.repository.TeamRoleRepository;
import com.example.project_flow.repository.WorkStepRepository;
import com.example.project_flow.repository.WorkflowTemplateRepository;
import com.example.project_flow.repository.WorkspaceSettingsRepository;
import com.example.project_flow.service.ActivityLogService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Registers the Busan Hankki project once. Nothing is created in a finished state: every task, step and
 * requirement starts open, and past deadlines stay unconfirmed until the user records them.
 */
@Component
@Order(2)
public class BusanHankkiSeeder implements ApplicationRunner {

    private static final String PROJECT_NAME = "Busan Hankki";
    private static final String OWNER = "Member D";
    private static final List<String> FEATURE_STEPS = List.of(
            "Confirm requirements", "Analyze existing entities", "Check DB relationships", "Decide implementation approach", "Write code",
            "Exception handling", "Testing", "Check results", "Write work log");

    private final WorkspaceSettingsRepository settings;
    private final ProjectRepository projects;
    private final ProjectPhaseRepository phases;
    private final TeamRoleRepository roles;
    private final ResponsibilityRepository responsibilities;
    private final TaskRepository tasks;
    private final WorkStepRepository steps;
    private final DeadlineRepository deadlines;
    private final RequirementRepository requirements;
    private final WorkflowTemplateRepository templates;
    private final ActivityLogService activity;
    private final Clock clock;

    public BusanHankkiSeeder(WorkspaceSettingsRepository settings, ProjectRepository projects, ProjectPhaseRepository phases,
                             TeamRoleRepository roles, ResponsibilityRepository responsibilities, TaskRepository tasks,
                             WorkStepRepository steps, DeadlineRepository deadlines, RequirementRepository requirements,
                             WorkflowTemplateRepository templates, ActivityLogService activity, Clock clock) {
        this.settings = settings;
        this.projects = projects;
        this.phases = phases;
        this.roles = roles;
        this.responsibilities = responsibilities;
        this.tasks = tasks;
        this.steps = steps;
        this.deadlines = deadlines;
        this.requirements = requirements;
        this.templates = templates;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        WorkspaceSettings current = settings.findById(WorkspaceSettings.SINGLETON_ID).orElseThrow();
        if (current.isStarterProjectSeeded()) {
            return;
        }
        Project project = createProject();
        createPhases(project);
        createRoles(project);
        createDeadlines(project);
        createWork(project);
        createTemplate();
        current.markStarterProjectSeeded();
        settings.save(current);
        activity.record("PROJECT_CREATED", "Registered project \"" + PROJECT_NAME + "\" with its schedule and responsibilities");
    }

    private Project createProject() {
        Project project = new Project(LocalDateTime.now(clock));
        project.update(PROJECT_NAME, "Busan local restaurant review service", ProjectStatus.ACTIVE, date(11, 30));
        project.updateDetails("Final Team Project", "Team 2", 2, date(10, 28), date(11, 5), date(11, 30),
                "Java, Spring Boot, Thymeleaf, MySQL 8, Spring AI, AWS",
                "Restaurant registration, reviews, ratings, favorites, AI review summary, AI restaurant recommendation");
        return projects.save(project);
    }

    private void createPhases(Project project) {
        phase(project, "Phase 1", date(10, 28), date(10, 30), "Preparation\nSupplementary class\nTopic research\nStudy materials");
        phase(project, "Phase 2", date(11, 2), date(11, 6), "Choose project topic\nDivide roles\nPlanning and design\nSet up GitHub repository");
        phase(project, "Phase 3", date(11, 9), date(11, 13), "Build entities\nMember login\nCRUD\nDevelop main screens");
        phase(project, "Phase 4", date(11, 16), date(11, 20), "JWT\nGoogle login\nAI features\nfirst automatic deployment");
        phase(project, "Phase 5", date(11, 23), date(11, 27), "Feature integration\nTesting\nFinal deployment\nResults report");
    }

    private void phase(Project project, String name, LocalDate start, LocalDate end, String items) {
        ProjectPhase phase = new ProjectPhase();
        phase.setProject(project);
        phase.update(name, start, end, items);
        phases.save(phase);
    }

    private void createRoles(Project project) {
        role(project, "Team lead", "Schedule, meetings, repository, PRs, common layout, main screen", false);
        role(project, "Deputy lead", "CI/CD, AWS, server, testing, README, deployment", false);
        role(project, "Member A", "Sign-up, login, JWT, Google login", false);
        role(project, "Member B", "Restaurant CRUD and photos", false);
        role(project, "Member C", "Reviews and AI review summary", false);
        role(project, OWNER, "Favorites and AI restaurant recommendation", true);
    }

    private void role(Project project, String name, String duties, boolean mine) {
        TeamRole role = new TeamRole();
        role.setProject(project);
        role.update(name, duties, mine);
        roles.save(role);
    }

    private void createDeadlines(Project project) {
        String all = "Team lead oversees, all members take part";
        String myPart = "Write and review own part (favorites, AI recommendation)";
        team(project, "Final team project planning and design XLSX", DeadlineCategory.DELIVERABLE, date(11, 6), all, myPart, null);
        team(project, "Project weekly report XLSX (Phase 3)", DeadlineCategory.WEEKLY_REPORT, date(11, 13), null, myPart, null);
        team(project, "Project weekly report XLSX (Phase 4)", DeadlineCategory.WEEKLY_REPORT, date(11, 20), null, myPart, null);
        team(project, "Project weekly report XLSX (Phase 5)", DeadlineCategory.WEEKLY_REPORT, date(11, 27), null, myPart, null);
        team(project, "Final deployment", DeadlineCategory.DEPLOYMENT, date(11, 27), "Deputy lead", null, null);
        team(project, "Project results report PPTX", DeadlineCategory.FINAL_REPORT, date(11, 30), all, myPart, "Submit before 2026-11-30");
        team(project, "Project JAR", DeadlineCategory.JAR, date(11, 30), "Deputy lead oversees", "Check that your own features are included", "Submit before 2026-11-30");
        team(project, "Final submission of AI code verification log XLSX", DeadlineCategory.AI_LOG, date(11, 30), "Team lead collects, all members take part", myPart, null);
        team(project, "Final presentation", DeadlineCategory.PRESENTATION, date(11, 30), null, null, null);
        for (int day : List.of(13, 20, 27)) {
            Deadline check = new Deadline();
            check.setProject(project);
            check.update("Check AI code verification log on Friday", DeadlineCategory.AI_LOG, DeadlineScope.PERSONAL, date(11, day),
                    OWNER, "Check this week's verification records for AI-written code", false, "Check every Friday");
            deadlines.save(check);
        }
    }

    private void team(Project project, String title, DeadlineCategory category, LocalDate due,
                      String owner, String personalAction, String note) {
        Deadline deadline = new Deadline();
        deadline.setProject(project);
        deadline.update(title, category, DeadlineScope.TEAM, due, owner, personalAction, false, note);
        deadlines.save(deadline);
    }

    private void createWork(Project project) {
        Responsibility favorites = responsibility(project, "Favorites / wishlist",
                "Implement adding and managing favorite restaurants.", date(11, 9), date(11, 13));
        Responsibility recommendation = responsibility(project, "AI restaurant recommendation",
                "Implement restaurant recommendations based on the user's conditions and information.", date(11, 16), date(11, 20));

        Task add = null;
        Object[][] favoriteTasks = {
                {"Analyze favorites requirements", 10, Priority.MEDIUM},
                {"Check entity and data structure", 10, Priority.MEDIUM},
                {"Check user-restaurant relationship", 10, Priority.MEDIUM},
                {"Implement adding favorites", 11, Priority.HIGH},
                {"Implement removing favorites", 11, Priority.HIGH},
                {"Implement favorites list view", 12, Priority.HIGH},
                {"Prevent duplicate entries", 12, Priority.HIGH},
                {"Separate data per logged-in user", 12, Priority.HIGH},
                {"Exception handling", 13, Priority.MEDIUM},
                {"Connect API and screens", 13, Priority.HIGH},
                {"Favorites testing", 13, Priority.HIGH},
                {"Verify favorites results", 13, Priority.HIGH},
        };
        for (Object[] row : favoriteTasks) {
            Task task = task(project, favorites, (String) row[0], (int) row[1], (Priority) row[2]);
            if ("Implement adding favorites".equals(row[0])) {
                add = task;
            }
        }
        Object[][] recommendationTasks = {
                {"Analyze AI recommendation requirements", 16, Priority.MEDIUM},
                {"Define recommendation input conditions", 16, Priority.MEDIUM},
                {"Check how restaurant data connects to AI features", 17, Priority.MEDIUM},
                {"Check Spring AI usage structure", 17, Priority.MEDIUM},
                {"Implement recommendation service", 18, Priority.HIGH},
                {"Build prompts", 18, Priority.HIGH},
                {"Handle recommendation results", 19, Priority.HIGH},
                {"Handle AI recommendation exceptions and responses", 19, Priority.MEDIUM},
                {"Connect AI recommendation screen", 20, Priority.HIGH},
                {"AI recommendation testing", 20, Priority.HIGH},
                {"Verify recommendation results", 20, Priority.HIGH},
        };
        for (Object[] row : recommendationTasks) {
            task(project, recommendation, (String) row[0], (int) row[1], (Priority) row[2]);
        }

        int order = 0;
        for (String title : FEATURE_STEPS) {
            steps.save(new WorkStep(add, title, order++));
        }

        for (String title : List.of("Add favorites", "Remove favorites", "View favorites list", "Prevent duplicate entries", "Separate data per logged-in user")) {
            requirement(project, favorites, title, "Favorites");
        }
        for (String title : List.of("Handle recommendation input conditions", "Provide recommendations from restaurant data", "Handle AI response failures", "Show recommendation results on screen")) {
            requirement(project, recommendation, title, "AI restaurant recommendation");
        }
    }

    private Responsibility responsibility(Project project, String title, String description, LocalDate start, LocalDate due) {
        Responsibility responsibility = new Responsibility();
        responsibility.setProject(project);
        responsibility.update(title, description, OWNER, start, due, Priority.HIGH, WorkStatus.NOT_STARTED, null);
        return responsibilities.save(responsibility);
    }

    private Task task(Project project, Responsibility responsibility, String title, int day, Priority priority) {
        Task task = new Task(LocalDateTime.now(clock));
        task.update(title, null, priority, date(11, day), null, project);
        task.assignResponsibility(responsibility);
        task.changeStatus(WorkStatus.NOT_STARTED, LocalDateTime.now(clock));
        return tasks.save(task);
    }

    private void requirement(Project project, Responsibility responsibility, String title, String feature) {
        Requirement requirement = new Requirement();
        requirement.setProject(project);
        requirement.describe(title, null, feature, responsibility, null, null);
        requirements.save(requirement);
    }

    private void createTemplate() {
        WorkflowTemplate template = new WorkflowTemplate();
        template.update("Feature Implementation", "Basic order for implementing and verifying one feature", FEATURE_STEPS);
        templates.save(template);
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
