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
 * Registers the 부산한끼 project once. Nothing is created in a finished state: every task, step and
 * requirement starts open, and past deadlines stay unconfirmed until the user records them.
 */
@Component
@Order(2)
public class BusanHankkiSeeder implements ApplicationRunner {

    private static final String PROJECT_NAME = "부산한끼";
    private static final String OWNER = "팀원 D";
    private static final List<String> FEATURE_STEPS = List.of(
            "요구사항 확인", "기존 Entity 분석", "DB 관계 확인", "구현 방식 결정", "코드 작성",
            "예외 처리", "테스트", "결과 확인", "작업 기록 작성");

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
        project.update(PROJECT_NAME, "부산 로컬 맛집 리뷰 서비스", ProjectStatus.ACTIVE, date(11, 30));
        project.updateDetails("Final Team Project", "2조", 2, date(10, 28), date(11, 5), date(11, 30),
                "Java, Spring Boot, Thymeleaf, MySQL 8, Spring AI, AWS",
                "맛집 등록, 맛집 리뷰, 평점, 즐겨찾기, AI 리뷰 요약, AI 맛집 추천");
        return projects.save(project);
    }

    private void createPhases(Project project) {
        phase(project, "1차", date(10, 28), date(10, 30), "사전 준비\n보충 수업\n주제 탐색\n자료 학습");
        phase(project, "2차", date(11, 2), date(11, 6), "프로젝트 주제 선정\n역할 분담\n기획 및 설계\nGitHub 저장소 구성");
        phase(project, "3차", date(11, 9), date(11, 13), "Entity 구성\n회원 로그인\nCRUD\n주요 화면 개발");
        phase(project, "4차", date(11, 16), date(11, 20), "JWT\nGoogle 로그인\nAI 기능\n1차 자동 배포");
        phase(project, "5차", date(11, 23), date(11, 27), "기능 통합\n테스트\n최종 배포\n결과 보고서");
    }

    private void phase(Project project, String name, LocalDate start, LocalDate end, String items) {
        ProjectPhase phase = new ProjectPhase();
        phase.setProject(project);
        phase.update(name, start, end, items);
        phases.save(phase);
    }

    private void createRoles(Project project) {
        role(project, "팀장", "일정, 회의, 저장소, PR, 공통 레이아웃, 메인 화면", false);
        role(project, "부팀장", "CI/CD, AWS, 서버, 테스트, README, 배포", false);
        role(project, "팀원 A", "회원가입, 로그인, JWT, Google 로그인", false);
        role(project, "팀원 B", "맛집 CRUD 및 사진", false);
        role(project, "팀원 C", "리뷰 및 AI 리뷰 요약", false);
        role(project, OWNER, "즐겨찾기 및 AI 맛집 추천", true);
    }

    private void role(Project project, String name, String duties, boolean mine) {
        TeamRole role = new TeamRole();
        role.setProject(project);
        role.update(name, duties, mine);
        roles.save(role);
    }

    private void createDeadlines(Project project) {
        String all = "팀장 총괄, 전체 팀원 참여";
        String myPart = "담당 파트(즐겨찾기, AI 맛집 추천) 내용 작성 및 확인";
        team(project, "파이널 팀프로젝트 기획 및 설계 XLSX", DeadlineCategory.DELIVERABLE, date(11, 6), all, myPart, null);
        team(project, "프로젝트 주간보고서 XLSX (3차)", DeadlineCategory.WEEKLY_REPORT, date(11, 13), null, myPart, null);
        team(project, "프로젝트 주간보고서 XLSX (4차)", DeadlineCategory.WEEKLY_REPORT, date(11, 20), null, myPart, null);
        team(project, "프로젝트 주간보고서 XLSX (5차)", DeadlineCategory.WEEKLY_REPORT, date(11, 27), null, myPart, null);
        team(project, "최종 배포", DeadlineCategory.DEPLOYMENT, date(11, 27), "부팀장", null, null);
        team(project, "프로젝트 결과보고서 PPTX", DeadlineCategory.FINAL_REPORT, date(11, 30), all, myPart, "2026-11-30 이전 제출");
        team(project, "프로젝트 JAR", DeadlineCategory.JAR, date(11, 30), "부팀장 총괄", "담당 기능이 포함되어 있는지 확인", "2026-11-30 이전 제출");
        team(project, "AI 코드 검증 일지 XLSX 최종 제출", DeadlineCategory.AI_LOG, date(11, 30), "팀장 취합, 전체 팀원 참여", myPart, null);
        team(project, "최종 발표", DeadlineCategory.PRESENTATION, date(11, 30), null, null, null);
        for (int day : List.of(13, 20, 27)) {
            Deadline check = new Deadline();
            check.setProject(project);
            check.update("AI 코드 검증 일지 금요일 확인", DeadlineCategory.AI_LOG, DeadlineScope.PERSONAL, date(11, day),
                    OWNER, "이번 주 AI 작성 코드의 검증 기록 확인", false, "매주 금요일 확인");
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
        Responsibility favorites = responsibility(project, "즐겨찾기 / 위시리스트",
                "맛집을 즐겨찾기에 추가하고 관리하는 기능을 구현한다.", date(11, 9), date(11, 13));
        Responsibility recommendation = responsibility(project, "AI 맛집 추천",
                "사용자의 조건과 정보를 바탕으로 맛집을 추천하는 기능을 구현한다.", date(11, 16), date(11, 20));

        Task add = null;
        Object[][] favoriteTasks = {
                {"즐겨찾기 요구사항 분석", 10, Priority.MEDIUM},
                {"Entity 및 데이터 구조 확인", 10, Priority.MEDIUM},
                {"사용자와 맛집의 관계 확인", 10, Priority.MEDIUM},
                {"즐겨찾기 등록 기능 구현", 11, Priority.HIGH},
                {"즐겨찾기 취소 기능 구현", 11, Priority.HIGH},
                {"즐겨찾기 목록 조회 구현", 12, Priority.HIGH},
                {"중복 등록 방지", 12, Priority.HIGH},
                {"로그인 사용자별 데이터 구분", 12, Priority.HIGH},
                {"예외 처리", 13, Priority.MEDIUM},
                {"API 및 화면 연동", 13, Priority.HIGH},
                {"즐겨찾기 테스트", 13, Priority.HIGH},
                {"즐겨찾기 결과 검증", 13, Priority.HIGH},
        };
        for (Object[] row : favoriteTasks) {
            Task task = task(project, favorites, (String) row[0], (int) row[1], (Priority) row[2]);
            if ("즐겨찾기 등록 기능 구현".equals(row[0])) {
                add = task;
            }
        }
        Object[][] recommendationTasks = {
                {"AI 추천 기능 요구사항 분석", 16, Priority.MEDIUM},
                {"추천 입력 조건 정의", 16, Priority.MEDIUM},
                {"맛집 데이터와 AI 기능의 연결 구조 확인", 17, Priority.MEDIUM},
                {"Spring AI 사용 구조 확인", 17, Priority.MEDIUM},
                {"추천 서비스 구현", 18, Priority.HIGH},
                {"프롬프트 구성", 18, Priority.HIGH},
                {"추천 결과 처리", 19, Priority.HIGH},
                {"AI 추천 예외 및 응답 처리", 19, Priority.MEDIUM},
                {"AI 추천 화면 연동", 20, Priority.HIGH},
                {"AI 추천 테스트", 20, Priority.HIGH},
                {"추천 결과 검증", 20, Priority.HIGH},
        };
        for (Object[] row : recommendationTasks) {
            task(project, recommendation, (String) row[0], (int) row[1], (Priority) row[2]);
        }

        int order = 0;
        for (String title : FEATURE_STEPS) {
            steps.save(new WorkStep(add, title, order++));
        }

        for (String title : List.of("즐겨찾기 등록", "즐겨찾기 취소", "즐겨찾기 목록 조회", "중복 등록 방지", "로그인 사용자별 데이터 구분")) {
            requirement(project, favorites, title, "즐겨찾기");
        }
        for (String title : List.of("추천 입력 조건 처리", "맛집 데이터 기반 추천 결과 제공", "AI 응답 실패 시 예외 처리", "추천 결과 화면 표시")) {
            requirement(project, recommendation, title, "AI 맛집 추천");
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
        template.update("Feature Implementation", "기능 하나를 구현하고 검증하는 기본 순서", FEATURE_STEPS);
        templates.save(template);
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
