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
 * Registers the "JPA 시험 · AI 코드 검증 대비" study project once, built from
 * 파이널/JPA + AI 코드 검증 시험공부 자습서.html (defect-lab).
 *
 * The plan follows the guide's own order: diagnose -> concepts A-D -> 9 defect cases -> mock exam.
 * Every case task carries the guide's six investigation steps as work steps, and every step
 * says what to write down, so a task is only "done" when there is a record to show for it.
 */
@Component
@Order(3)
public class JpaExamStudySeeder implements ApplicationRunner {

    private static final String PROJECT_NAME = "JPA 시험 · AI 코드 검증 대비";
    private static final String OWNER = "나";

    /** The guide's 6-step investigation, with what to record at each step. */
    private static final String[][] CASE_STEPS = {
            {"1 신고", "실행 중인 앱에서 문제 화면을 직접 눌러 본다.", "화면에서 본 증상 한 줄"},
            {"2 현장", "IntelliJ에서 이 사건의 Mission 테스트를 실행해 빨간불을 확인한다.", "실패한 테스트 이름"},
            {"3 증거", "실패 메시지와 [측정] 숫자를 읽는다: 무엇을 기대했고 실제로 무엇이 나왔나.", "expected __ but was __"},
            {"4 용의자", "요청이 지나가는 코드를 Controller → Service → Repository 순서로 한 줄씩 따라간다. 이 줄에서 SQL이 나가는가?", "의심 가는 파일:줄"},
            {"5 범인과 수정", "정답 상자를 열기 전에 내 추측을 한 줄 적고, 그다음 수정한다. 테스트 코드는 고치지 않는다.", "내 추측 / 실제 원인"},
            {"6 초록불", "다시 실행해 숫자가 바뀐 것을 확인하고 검증일지(4_AI코드_검증일지.xlsx)에 한 줄 적는다. 동시성 사건은 3번 실행.", "Before → After 숫자"},
    };

    private static final List<String> CONCEPT_STEPS = List.of(
            "① 비유 읽기", "② 한 장 그림 따라 그리기", "③ 코드 줄 해석 (🔶 SQL 나가는 줄 표시)",
            "④ 측정표 숫자 외우지 말고 이유 말하기", "⑤ 자주 하는 오해 5개 O/X", "⑥ 시험 포인트 3줄을 안 보고 쓰기");

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
                "자습서(defect-lab) 순서 그대로: 진단 5문제 → 개념 A·B·C·D → 사건 9건 → 모의시험 20문항. "
                        + "테스트 15개 중 처음엔 2개만 초록불. 모두 초록불 + 검증일지 9줄이 끝 목표.",
                ProjectStatus.ACTIVE, date(11, 28));
        project.updateDetails("Exam Study", "개인", null, date(11, 18), date(11, 18), date(11, 28),
                "Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security, Thymeleaf, MySQL 8, JUnit 5",
                "A 연관관계 매핑, B 지연로딩·프록시·N+1, C 영속성 컨텍스트·변경감지, D JPQL·Repository, 결함 사건 9건");
        project.changePurpose("왜 하는가: 시험 점수가 아니라 \"AI가 짠 코드를 내가 검증할 수 있는 사람\"이 되기 위해서다.\n"
                + "1) JPA는 Spring 백엔드 채용 공고 대부분에 나오고, N+1은 면접 단골 질문이다.\n"
                + "2) defect-lab의 9개 사건은 실제 서비스에서 돈과 신뢰를 잃게 만드는 결함이다 (중복 결제, 개인정보 노출).\n"
                + "3) 이것을 설명할 수 있으면 AI에게 일을 시키는 사람이 되고, 못 하면 AI가 준 코드를 그대로 붙여넣는 사람이 된다.\n"
                + "끝났을 때의 모습: 코드 한 줄을 보고 \"여기서 SQL이 몇 번 나간다\"고 말할 수 있다.");
        return projects.save(project);
    }

    private void createPhases(Project project) {
        phase(project, "1단계 준비", date(11, 18), date(11, 18),
                "처음 30분: defect-lab 열고 실행\n시작 에러 4가지 확인\n테스트 실행과 빨간불 읽는 법\n진단 5문제");
        phase(project, "2단계 개념", date(11, 19), date(11, 21),
                "A 연관관계 매핑\nB 지연로딩·프록시·N+1 (가장 많이 틀림)\nC 영속성 컨텍스트\nD JPQL·Repository");
        phase(project, "3단계 사건", date(11, 22), date(11, 26),
                "사건 1→2→3 순서대로\n사건 5·7은 정답 보기 전 혼자\n사건마다 검증일지 한 줄");
        phase(project, "4단계 시험", date(11, 27), date(11, 28),
                "오답노트 다시 풀기\n모의시험 20문항 30분\n치트시트 A4 1쪽 인쇄\n결함 하나 60초 설명");
    }

    private void phase(Project project, String name, LocalDate start, LocalDate end, String items) {
        ProjectPhase phase = new ProjectPhase();
        phase.setProject(project);
        phase.update(name, start, end, items);
        phases.save(phase);
    }

    private void createWork(Project project) {
        Responsibility prep = responsibility(project, "준비 · 진단",
                "앱을 켜고 테스트를 돌려 빨간불을 읽을 수 있으면 끝.", date(11, 18), date(11, 18));
        task(project, prep, "defect-lab 열고 실행 (DB lab/lab1234, 8080)", "시작 에러 4가지: 8080 포트 사용 중, DB 비밀번호, DB 없음/권한, MySQL 꺼짐.", 18, Priority.HIGH);
        task(project, prep, "전체 테스트 실행 → 초록불 2 / 15 확인", "Gradle 창 test 또는 .\\gradlew.bat test. 처음엔 2개만 통과가 정상.", 18, Priority.HIGH);
        task(project, prep, "진단 5문제 풀고 약한 주제 적기", "틀려도 됨. 결과로 어떤 주제부터 할지 정한다.", 18, Priority.MEDIUM);

        Responsibility concepts = responsibility(project, "JPA 개념 4주제",
                "주제마다 6칸(비유·그림·코드·측정·오해·시험포인트). 시험포인트 3줄을 안 보고 쓸 수 있으면 끝.", date(11, 19), date(11, 21));
        conceptTask(project, concepts, "A 연관관계 매핑: 외래키와 주인",
                "외래키는 N쪽(review.store_id). 주인 = 외래키 필드(Review.store). mappedBy = 거울(읽기 전용).", 19);
        conceptTask(project, concepts, "B 지연로딩 · 프록시 · N+1",
                "LAZY = 쓰는 순간 SELECT. 목록 1 + 반복 N = N+1 (50곳 → 51번). 해결: fetch join / EntityGraph / batch size / DTO.", 20);
        conceptTask(project, concepts, "C 영속성 컨텍스트 · 변경 감지",
                "스냅샷 비교 → 커밋 때 UPDATE (save 불필요). 같은 id 두 번 조회 = SQL 1번(1차 캐시).", 21);
        conceptTask(project, concepts, "D JPQL · Repository",
                "메서드 이름 → SQL. update/delete는 @Modifying. select new DTO(...) + left join + count = SQL 1번.", 21);

        Responsibility cases = responsibility(project, "결함 사건 9건",
                "src/main만 고친다. 테스트는 요구사항이라 고치지 않는다. 사건마다 검증일지 한 줄.", date(11, 22), date(11, 26));
        caseTask(project, cases, "사건 1 맛집 목록이 몰래 보내는 SQL 51번 (N+1)", "Mission1 · 목표 SQL 51 → 1 · 주제 B·D", 22);
        caseTask(project, cases, "사건 2 100명이 찜했는데 찜 수는 13 (갱신 유실)", "Mission2 · 13 → 100, 중복 3 → 1 · 주제 C · 3번 실행", 22);
        caseTask(project, cases, "사건 3 1,000점으로 2,000점을 쓴 고객", "Mission3 · 조건부 UPDATE where point >= :amount · 3번 실행", 23);
        caseTask(project, cases, "사건 4 남의 리뷰 수정(IDOR) + 리뷰 XSS", "Mission4 · 302 → 403, th:utext → th:text", 23);
        caseTask(project, cases, "사건 5 한 장짜리 쿠폰이 네 번 쓰였다 (혼자 먼저)", "Mission5 · 사건 3과 같은 무늬 · 성공 4 → 1", 24);
        caseTask(project, cases, "사건 6 옆 가게 사장님이 우리 고객을 본다", "Mission6 · 200 → 403, 전화번호 마스킹", 24);
        caseTask(project, cases, "사건 7 북마크 연타가 남긴 일곱 줄 (혼자 먼저)", "Mission7 · 사건 2와 같은 무늬 · UNIQUE 제약 · 7 → 1", 25);
        caseTask(project, cases, "사건 8 AI가 추천한 9999번 관광지", "Mission8 · AI 답을 DB와 대조(findAllById) · 500 → 정상", 25);
        caseTask(project, cases, "사건 9 날씨 서버가 느리면 화면도 멈춘다", "Mission9 · 타임아웃 1초 + 안내 문구 · 5179ms → 1초대", 26);

        Responsibility exam = responsibility(project, "시험 직전",
                "모의시험 20문항 30분, 치트시트 1쪽, 결함 하나 60초 설명.", date(11, 27), date(11, 28));
        task(project, exam, "오답노트 다시 풀기", "틀린 문제만. 해설을 보기 전에 내 답을 먼저 적는다.", 27, Priority.HIGH);
        task(project, exam, "모의시험 20문항 · 30분", "시간을 재고 푼다. 점수보다 틀린 주제를 적는 것이 목적.", 27, Priority.HIGH);
        task(project, exam, "치트시트 A4 1쪽 인쇄 후 빈칸 채우기", "인쇄본에서 숫자와 키워드를 가리고 다시 채운다.", 28, Priority.MEDIUM);
        task(project, exam, "결함 하나 60초 설명 (상황→발견→원인→개선→결과→재발방지)", "녹음해서 다시 들어 본다. 막히는 지점이 아직 모르는 부분.", 28, Priority.MEDIUM);
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
            workStep.describe(step[0], step[1], null, null, null, "기록할 것: " + step[2]);
            steps.save(workStep);
        }
    }

    private void createTemplates() {
        WorkflowTemplate investigation = new WorkflowTemplate();
        investigation.update("결함 사건 수사 6단계", "AI가 짠 코드의 결함 하나를 찾고 고치고 증명하는 순서 (defect-lab)",
                List.of("1 신고", "2 현장", "3 증거", "4 용의자", "5 범인과 수정", "6 초록불 + 검증일지"));
        templates.save(investigation);

        WorkflowTemplate concept = new WorkflowTemplate();
        concept.update("JPA 개념 6칸 공부", "개념 하나를 '설명할 수 있다'까지 끌어올리는 순서", CONCEPT_STEPS);
        templates.save(concept);
    }

    private static LocalDate date(int month, int day) {
        return LocalDate.of(2026, month, day);
    }
}
