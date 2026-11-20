package com.example.project_flow.config;

import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.Project;
import com.example.project_flow.entity.ProjectPhase;
import com.example.project_flow.entity.ProjectStatus;
import com.example.project_flow.entity.Responsibility;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkStatus;
import com.example.project_flow.entity.WorkStep;
import com.example.project_flow.entity.WorkflowTemplate;
import com.example.project_flow.entity.WorkspaceSettings;
import com.example.project_flow.repository.ProjectPhaseRepository;
import com.example.project_flow.repository.ProjectRepository;
import com.example.project_flow.repository.ResponsibilityRepository;
import com.example.project_flow.repository.TaskRepository;
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
 * Registers the "JPA exam and AI code verification prep" study project once, built from
 * Final/JPA + AI code verification study guide.html (defect-lab).
 *
 * The plan follows the guide's own order: diagnose -> concepts A-D -> 9 defect cases -> mock exam.
 * Every case task carries the guide's six investigation steps as work steps, and every step
 * says what to write down, so a task is only "done" when there is a record to show for it.
 */
@Component
@Order(3)
public class JpaExamStudySeeder implements ApplicationRunner {

    private static final String PROJECT_NAME = "JPA exam and AI code verification prep";
    private static final String OWNER = "Me";

    /** The guide's 6-step investigation, with what to record at each step. */
    private static final String[][] CASE_STEPS = {
            {"1 Report", "Open the problem screen in the running app and reproduce it.", "One line describing the symptom on screen"},
            {"2 Scene", "Run this case's Mission test in IntelliJ and confirm the red result.", "Name of the failing test"},
            {"3 Evidence", "Read the failure message and the [measure] numbers: what was expected and what actually happened.", "expected __ but was __"},
            {"4 Suspect", "Follow the request line by line through Controller → Service → Repository. Does this line send SQL?", "Suspicious file:line"},
            {"5 Culprit and fix", "Before opening the answer box, write one line with your guess, then fix it. Do not change the test code.", "My guess / actual cause"},
            {"6 Green light", "Run again, confirm the numbers changed, and add one line to the AI code verification log (.xlsx). Run concurrency cases 3 times.", "Before → After numbers"},
    };

    private static final List<String> CONCEPT_STEPS = List.of(
            "① Read the analogy", "② Draw the one-page diagram", "③ Explain code lines (🔶 mark lines that send SQL)",
            "④ Explain the reasons behind the measurements instead of memorizing numbers", "⑤ Five common misconceptions, true or false", "⑥ Write the 3 key exam points from memory");

    private final WorkspaceSettingsRepository settings;
    private final ProjectRepository projects;
    private final ProjectPhaseRepository phases;
    private final ResponsibilityRepository responsibilities;
    private final TaskRepository tasks;
    private final WorkStepRepository steps;
    private final WorkflowTemplateRepository templates;
    private final ActivityLogService activity;
    private final Clock clock;

    public JpaExamStudySeeder(WorkspaceSettingsRepository settings, ProjectRepository projects, ProjectPhaseRepository phases,
                              ResponsibilityRepository responsibilities, TaskRepository tasks, WorkStepRepository steps,
                              WorkflowTemplateRepository templates, ActivityLogService activity, Clock clock) {
        this.settings = settings;
        this.projects = projects;
        this.phases = phases;
        this.responsibilities = responsibilities;
        this.tasks = tasks;
        this.steps = steps;
        this.templates = templates;
        this.activity = activity;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        WorkspaceSettings current = settings.findById(WorkspaceSettings.SINGLETON_ID).orElse(null);
        if (current == null || current.isStudyPlanSeeded()) {
            return;
        }
        Project project = createProject();
        createPhases(project);
        createWork(project);
        createTemplates();
        current.markStudyPlanSeeded();
        settings.save(current);
        activity.record("PROJECT_CREATED", "Registered study project \"" + PROJECT_NAME + "\"");
    }

    private Project createProject() {
        Project project = new Project(LocalDateTime.now(clock));
        project.update(PROJECT_NAME,
                "Follow the study guide (defect-lab) in order: 5 diagnostic questions → concepts A·B·C·D → 9 cases → 20-question mock exam. "
                        + "Of 15 tests, only 2 pass at first. The goal: all green, plus 9 lines in the verification log.",
                ProjectStatus.ACTIVE, date(11, 28));
        project.updateDetails("Exam Study", "Personal", null, date(11, 18), date(11, 18), date(11, 28),
                "Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security, Thymeleaf, MySQL 8, JUnit 5",
                "A Association mapping, B lazy loading·proxies·N+1, C Persistence context·dirty checking, D JPQL and Repository, 9 defect cases");
        project.changePurpose("Why: not for an exam score, but to become someone who can verify code written by AI.\n"
                + "1) JPA appears in most Spring backend job postings, and N+1 is a common interview question.\n"
                + "2) The 9 defect-lab cases are real defects that cost a service money and trust (duplicate payments, personal data exposure).\n"
                + "3) If you can explain this, you direct AI work. If not, you just paste AI code as-is.\n"
                + "Done looks like: you can look at a line of code and say how many SQL queries it sends here.");
        return projects.save(project);
    }

    private void createPhases(Project project) {
        phase(project, "Phase 1 Prep", date(11, 18), date(11, 18),
                "First 30 minutes: open and run defect-lab\nCheck the 4 startup errors\nRun the tests and read the red results\n5 diagnostic questions");
        phase(project, "Phase 2 Concepts", date(11, 19), date(11, 21),
                "A Association mapping\nB Lazy loading, proxies, N+1 (most often wrong)\nC Persistence context\nD JPQL and Repository");
        phase(project, "Phase 3 Cases", date(11, 22), date(11, 26),
                "Cases 1→2→3 in order\nCases 5 and 7: solo, before looking at the answer\nOne verification log line per case");
        phase(project, "Phase 4 Exam", date(11, 27), date(11, 28),
                "Redo the wrong-answer notes\n20-question mock exam, 30 minutes\nPrint one A4 cheat sheet page\nExplain one defect in 60 seconds");
    }

    private void phase(Project project, String name, LocalDate start, LocalDate end, String items) {
        ProjectPhase phase = new ProjectPhase();
        phase.setProject(project);
        phase.update(name, start, end, items);
        phases.save(phase);
    }

    private void createWork(Project project) {
        Responsibility prep = responsibility(project, "Prep · diagnosis",
                "Done when you can start the app, run the tests, and read the red results.", date(11, 18), date(11, 18));
        task(project, prep, "Open and run defect-lab (DB lab/lab1234, 8080)", "4 startup errors: port 8080 in use, DB password, DB missing or no permission, MySQL not running.", 18, Priority.HIGH);
        task(project, prep, "Run all tests → confirm 2 / 15 green", "Gradle window test or .\\gradlew.bat test. Only 2 passing at first is normal.", 18, Priority.HIGH);
        task(project, prep, "Solve the 5 diagnostic questions and note weak topics", "Wrong answers are fine. Use the results to pick which topic to start with.", 18, Priority.MEDIUM);

        Responsibility concepts = responsibility(project, "4 JPA concept topics",
                "Six boxes per topic (analogy, drawing, code, measurements, misconceptions, exam points). Done when you can write the 3 exam-point lines from memory.", date(11, 19), date(11, 21));
        conceptTask(project, concepts, "A Association mapping: foreign key and owner",
                "The foreign key is on the N side (review.store_id). Owner = the foreign-key field (Review.store). mappedBy = mirror (read-only).", 19);
        conceptTask(project, concepts, "B Lazy loading · proxies · N+1",
                "LAZY = SELECT at the moment of use. List query 1 + N repeats = N+1 (50 places → 51 queries). Fixes: fetch join / EntityGraph / batch size / DTO.", 20);
        conceptTask(project, concepts, "C Persistence context · dirty checking",
                "Snapshot comparison → UPDATE on commit (save not needed). Two lookups of the same id = 1 SQL (first-level cache).", 21);
        conceptTask(project, concepts, "D JPQL · Repository",
                "Method name → SQL. update/delete need @Modifying. select new DTO(...) + left join + count = 1 SQL.", 21);

        Responsibility cases = responsibility(project, "9 defect cases",
                "Only change src/main. Tests are requirements, so don't change them. One verification log line per case.", date(11, 22), date(11, 26));
        caseTask(project, cases, "Case 1: the restaurant list secretly sends 51 SQL queries (N+1)", "Mission1 · target SQL 51 → 1 · topics B·D", 22);
        caseTask(project, cases, "Case 2: 100 people favorited it, but the count shows 13 (lost update)", "Mission2 · 13 → 100, duplicates 3 → 1 · topic C · run 3 times", 22);
        caseTask(project, cases, "Case 3: a customer spent 2,000 points with only 1,000", "Mission3 · conditional UPDATE where point >= :amount · run 3 times", 23);
        caseTask(project, cases, "Case 4: editing someone else's review (IDOR) + review XSS", "Mission4 · 302 → 403, th:utext → th:text", 23);
        caseTask(project, cases, "Case 5: a single-use coupon was used four times (solo first)", "Mission5 · same pattern as case 3 · successes 4 → 1", 24);
        caseTask(project, cases, "Case 6: a neighboring shop owner can see our customers", "Mission6 · 200 → 403, mask phone numbers", 24);
        caseTask(project, cases, "Case 7: seven rows left by rapid bookmark clicks (solo first)", "Mission7 · same pattern as case 2 · UNIQUE constraint · 7 → 1", 25);
        caseTask(project, cases, "Case 8: AI recommended tourist spot #9999", "Mission8 · check AI answer against DB (findAllById) · 500 → normal", 25);
        caseTask(project, cases, "Case 9: the screen freezes when the weather server is slow", "Mission9 · 1-second timeout + notice text · 5179ms → about 1s", 26);

        Responsibility exam = responsibility(project, "Right before the exam",
                "20-question mock exam in 30 minutes, one cheat-sheet page, explain one defect in 60 seconds.", date(11, 27), date(11, 28));
        task(project, exam, "Redo the wrong-answer notes", "Only the questions you got wrong. Write your answer before reading the explanation.", 27, Priority.HIGH);
        task(project, exam, "20-question mock exam · 30 minutes", "Time yourself. The goal is listing the topics you got wrong, not the score.", 27, Priority.HIGH);
        task(project, exam, "Print the A4 cheat sheet and fill in the blanks", "Cover the numbers and keywords on the printout and fill them in again.", 28, Priority.MEDIUM);
        task(project, exam, "Explain one defect in 60 seconds (situation → discovery → cause → fix → result → prevention)", "Record it and listen back. Where you get stuck is what you still don't know.", 28, Priority.MEDIUM);
    }

    private Responsibility responsibility(Project project, String title, String description, LocalDate start, LocalDate due) {
        Responsibility responsibility = new Responsibility();
        responsibility.setProject(project);
        responsibility.update(title, description, OWNER, start, due, Priority.HIGH, WorkStatus.NOT_STARTED, null);
        return responsibilities.save(responsibility);
    }

    private Task task(Project project, Responsibility responsibility, String title, String description, int day, Priority priority) {
        Task task = new Task(LocalDateTime.now(clock));
        task.update(title, description, priority, date(11, day), date(11, day), project);
        task.assignResponsibility(responsibility);
        task.changeStatus(WorkStatus.NOT_STARTED, LocalDateTime.now(clock));
        return tasks.save(task);
    }

    private void conceptTask(Project project, Responsibility responsibility, String title, String summary, int day) {
        Task task = task(project, responsibility, title, summary, day, Priority.HIGH);
        int order = 0;
        for (String step : CONCEPT_STEPS) {
            steps.save(new WorkStep(task, step, order++));
        }
    }

    private void caseTask(Project project, Responsibility responsibility, String title, String summary, int day) {
        Task task = task(project, responsibility, title, summary, day, Priority.HIGH);
        int order = 0;
        for (String[] step : CASE_STEPS) {
            WorkStep workStep = new WorkStep(task, step[0], order++);
            workStep.describe(step[0], step[1], null, null, null, "Record: " + step[2]);
            steps.save(workStep);
        }
    }

    private void createTemplates() {
        WorkflowTemplate investigation = new WorkflowTemplate();
        investigation.update("Defect investigation: 6 steps", "Steps for finding, fixing and proving one defect in AI-written code (defect-lab)",
                List.of("1 Report", "2 Scene", "3 Evidence", "4 Suspect", "5 Culprit and fix", "6 Green light + verification log"));
        templates.save(investigation);

        WorkflowTemplate concept = new WorkflowTemplate();
        concept.update("Study JPA concepts in 6 boxes", "Steps to take one concept to 'can explain it'", CONCEPT_STEPS);
        templates.save(concept);
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
