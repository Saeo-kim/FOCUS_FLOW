package com.example.project_flow.aiteam;

import com.example.project_flow.admin.account.AccountDtos.LoginRequest;
import com.example.project_flow.admin.account.AccountKind;
import com.example.project_flow.admin.account.AccountService;
import com.example.project_flow.admin.account.AppUser;
import com.example.project_flow.admin.account.AppUserRepository;
import com.example.project_flow.admin.account.UserRole;
import com.example.project_flow.admin.work.TaskCommentService;
import com.example.project_flow.admin.work.TaskCommentService.CommentView;
import com.example.project_flow.admin.work.WorkAccessService;
import com.example.project_flow.dto.TaskDtos.TaskRequest;
import com.example.project_flow.dto.TaskDtos.TaskResponse;
import com.example.project_flow.entity.Priority;
import com.example.project_flow.entity.Task;
import com.example.project_flow.entity.WorkStatus;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.repository.TaskRepository;
import com.example.project_flow.service.TaskService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The AI team end to end, against a real database and a fake Owner's Office on localhost.
 * The fake checks the bearer token the way the real agent API does, so a wrong token fails here too.
 */
@SpringBootTest
class AiTeamIntegrationTest {

    static final String TOKEN = "integration-token-" + "x".repeat(40);
    static final FakeOffice OFFICE = startOffice();

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("owners-office.base-url", OFFICE::baseUrl);
        registry.add("owners-office.token", () -> TOKEN);
        registry.add("owners-office.sync-interval-ms", () -> "3600000");
    }

    @BeforeAll
    static void resetOffice() {
        OFFICE.down = false;
    }

    @AfterAll
    static void stopOffice() {
        OFFICE.server.stop(0);
    }

    @Autowired AiTeamService team;
    @Autowired AiRequestService ai;
    @Autowired AiRequestRepository requests;
    @Autowired AppUserRepository users;
    @Autowired TaskService tasks;
    @Autowired TaskRepository taskRepository;
    @Autowired TaskCommentService comments;
    @Autowired WorkAccessService access;
    @Autowired AccountService accounts;

    @Test
    void cfoHasAnAiSeatThatCannotSignIn() {
        AppUser seat = team.activeSeat("cfo").orElseThrow();
        assertEquals(AccountKind.AI, seat.getKind());
        assertEquals("CFO (Chief Financial Officer)", seat.getTitle());
        assertEquals("FINANCE_ACCOUNTING", seat.getDepartment().name());
        assertFalse(seat.isPasswordSet());
        assertTrue(seat.isActive());

        assertTrue(team.members().stream().anyMatch(m -> m.key().equals("cfo") && m.userId().equals(seat.getId())));
        assertThrows(ConflictException.class, () -> accounts.login(new LoginRequest(seat.getUsername(), "any-password-here")));
    }

    @Test
    void assigningWorkSendsItWithAnIdempotencyKeyAndStartsTheTask() {
        AppUser admin = admin();
        AppUser seat = team.activeSeat("cfo").orElseThrow();
        int before = OFFICE.received.size();

        AiRequest request = ai.askNew(admin, "cfo", "cash_flow", "11월 현금흐름 점검", "자금 소진 시점을 알려 주세요", null);

        assertEquals(AiRequestStatus.SENT, request.getStatus());
        assertNotNull(request.getOfficeTaskId());
        assertTrue(request.getExternalRef().startsWith("ff-"));
        assertEquals(before + 1, OFFICE.received.size());
        Map<String, Object> sent = OFFICE.received.get(OFFICE.received.size() - 1);
        assertEquals(request.getExternalRef(), sent.get("external_ref"));
        assertEquals("cash_flow", sent.get("kind"));
        assertEquals("focus-flow", sent.get("requested_by"));

        Task task = taskRepository.findById(request.getTaskId()).orElseThrow();
        assertEquals(WorkStatus.IN_PROGRESS, task.getStatus());
        assertTrue(access.taskIds(seat.getId()).contains(task.getId()), "the CFO holds the task");
    }

    @Test
    void reportComesBackAsACommentAndCompletesTheTask() {
        AppUser admin = admin();
        AiRequest request = ai.askNew(admin, "cfo", null, "월간 재무 보고서 작성", "", null);
        OFFICE.complete(request.getOfficeTaskId(), "월간 재무 보고서 (2026-11): 사업 매출 1,000,000원",
                Map.of("kind", "monthly_report", "recommendations", List.of("비용 구조를 점검하세요."), "limitations", List.of("세무 신고 대행 아님")));

        ai.sync();

        AiRequest done = requests.findById(request.getId()).orElseThrow();
        assertEquals(AiRequestStatus.COMPLETED, done.getStatus());
        assertEquals("monthly_report", done.getKind(), "the kind the CFO chose is kept");
        assertTrue(done.getResultJson().contains("비용 구조"));
        assertEquals(WorkStatus.COMPLETED, taskRepository.findById(request.getTaskId()).orElseThrow().getStatus());

        List<CommentView> thread = comments.list(request.getTaskId());
        CommentView report = thread.get(thread.size() - 1);
        assertTrue(report.fromAgent());
        assertEquals("Owner's Office", report.authorName());
        assertTrue(report.body().contains("사업 매출 1,000,000원"));
        assertTrue(report.body().contains("비용 구조를 점검하세요."));
    }

    @Test
    void mentioningCfoInACommentAsksAndTheAnswerIsPosted() {
        AppUser admin = admin();
        TaskResponse task = tasks.create(new TaskRequest("세금 준비 점검", "", Priority.MEDIUM, null, null, null, null, null));

        CommentView plain = comments.post(admin, task.id(), "그냥 메모입니다.");
        assertNull(plain.aiRequestId());
        assertTrue(ai.listForTask(task.id()).isEmpty(), "a comment without a mention asks nothing");

        CommentView asked = comments.post(admin, task.id(), "@CFO 올해 세금 자료 정리해 주세요");
        assertNotNull(asked.aiRequestId());
        AiRequest request = requests.findById(asked.aiRequestId()).orElseThrow();
        assertEquals(asked.id(), request.getCommentId());
        assertEquals("Comment request: 세금 준비 점검", request.getTitle());

        OFFICE.complete(request.getOfficeTaskId(), "세무 준비 자료 (2026): 사업 매출 0원",
                Map.of("kind", "tax_summary", "limitations", List.of("세무사 검토 필요")));
        ai.sync();

        List<CommentView> thread = comments.list(task.id());
        assertEquals(3, thread.size());
        assertTrue(thread.get(2).fromAgent());
        assertTrue(thread.get(2).body().contains("세무 준비 자료"));
    }

    @Test
    void outageKeepsTheRequestQueuedAndTheNextSyncSendsIt() {
        AppUser admin = admin();
        TaskResponse task = tasks.create(new TaskRequest("투자 현황 확인", "", Priority.LOW, null, null, null, null, null));

        OFFICE.down = true;
        try {
            ai.onAssigned(admin, task.id(), team.activeSeat("cfo").orElseThrow().getId());
            List<AiRequest> queued = requests.findByTaskIdOrderByIdAsc(task.id());
            assertEquals(1, queued.size());
            assertEquals(AiRequestStatus.QUEUED, queued.get(0).getStatus());
            assertNotNull(queued.get(0).getLastError());
            assertFalse(queued.get(0).getLastError().contains(TOKEN), "errors never show the token");
        } finally {
            OFFICE.down = false;
        }

        ai.sync();

        List<AiRequest> after = requests.findByTaskIdOrderByIdAsc(task.id());
        assertEquals(1, after.size(), "the retry does not create a second request");
        assertEquals(AiRequestStatus.SENT, after.get(0).getStatus());
        assertNull(after.get(0).getLastError());
        assertEquals(WorkStatus.IN_PROGRESS, taskRepository.findById(task.id()).orElseThrow().getStatus());
    }

    @Test
    void assigningAgainWhileInFlightDoesNotDuplicate() {
        AppUser admin = admin();
        TaskResponse task = tasks.create(new TaskRequest("중복 방지", "", Priority.LOW, null, null, null, null, null));
        Long seatId = team.activeSeat("cfo").orElseThrow().getId();

        ai.onAssigned(admin, task.id(), seatId);
        ai.onAssigned(admin, task.id(), seatId);

        assertEquals(1, requests.findByTaskIdOrderByIdAsc(task.id()).size());
    }

    @Test
    void removingAnOwnerSendsNothing() {
        AppUser admin = admin();
        TaskResponse task = tasks.create(new TaskRequest("담당자 해제", "", Priority.LOW, null, null, null, null, null));
        ai.onAssigned(admin, task.id(), null);
        assertTrue(requests.findByTaskIdOrderByIdAsc(task.id()).isEmpty());
    }

    @Test
    void aFailedReportBlocksTheTaskAndSaysSo() {
        AppUser admin = admin();
        AiRequest request = ai.askNew(admin, "cfo", "bookkeeping", "장부 점검", "", null);
        OFFICE.fail(request.getOfficeTaskId(), "입력 기록을 읽을 수 없습니다.");

        ai.sync();

        assertEquals(AiRequestStatus.FAILED, requests.findById(request.getId()).orElseThrow().getStatus());
        assertEquals(WorkStatus.BLOCKED, taskRepository.findById(request.getTaskId()).orElseThrow().getStatus());
        List<CommentView> thread = comments.list(request.getTaskId());
        assertTrue(thread.get(thread.size() - 1).fromAgent());
    }

    @Test
    void mentionMatchingIsWholeWordOnly() {
        assertTrue(AiMentions.mentionsCfo("@CFO 부탁"));
        assertTrue(AiMentions.mentionsCfo("hi @cfo, see this"));
        assertFalse(AiMentions.mentionsCfo("email me at name@cfo.example"));
        assertFalse(AiMentions.mentionsCfo("@CFOs are a team"));
        assertFalse(AiMentions.mentionsCfo(null));
    }

    @Test
    void nothingIsSentWithoutAToken() {
        OwnersOfficeClientProbe probe = new OwnersOfficeClientProbe();
        assertFalse(probe.configured());
        assertFalse(probe.reachable());
        assertThrows(RuntimeException.class, () -> probe.createTask("ff-x", null, "t", ""));
    }

    private AppUser admin() {
        String name = "admin-" + UUID.randomUUID().toString().substring(0, 8);
        return users.save(new AppUser(name, "Test Admin", "not-used", UserRole.ADMIN, LocalDateTime.now()));
    }

    /* ---------- fake Owner's Office ---------- */

    static FakeOffice startOffice() {
        try {
            return new FakeOffice();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class FakeOffice {
        final HttpServer server;
        final ObjectMapper json = new ObjectMapper();
        final Map<Long, Map<String, Object>> byId = new ConcurrentHashMap<>();
        final Map<String, Map<String, Object>> byRef = new ConcurrentHashMap<>();
        final List<Map<String, Object>> received = new CopyOnWriteArrayList<>();
        final AtomicLong seq = new AtomicLong(100);
        volatile boolean down;

        FakeOffice() throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void complete(long id, String summary, Map<String, Object> result) {
            Map<String, Object> t = byId.get(id);
            t.put("status", "completed");
            t.put("summary", summary);
            t.put("result", result);
        }

        void fail(long id, String error) {
            Map<String, Object> t = byId.get(id);
            t.put("status", "failed");
            t.put("error", error);
        }

        private void handle(HttpExchange ex) throws IOException {
            if (down) {
                reply(ex, 503, "{}");
                return;
            }
            if (!("Bearer " + TOKEN).equals(ex.getRequestHeaders().getFirst("Authorization"))) {
                reply(ex, 401, "{\"error\":\"인증이 필요합니다.\"}");
                return;
            }
            String path = ex.getRequestURI().getPath();
            if ("GET".equals(ex.getRequestMethod()) && "/api/agent/health".equals(path)) {
                reply(ex, 200, "{\"ok\":true}");
                return;
            }
            if ("POST".equals(ex.getRequestMethod()) && "/api/agent/tasks".equals(path)) {
                Map<String, Object> body = json.readValue(ex.getRequestBody().readAllBytes(), Map.class);
                received.add(body);
                String ref = (String) body.get("external_ref");
                if (byRef.containsKey(ref)) {
                    reply(ex, 200, json.writeValueAsString(byRef.get(ref)));
                    return;
                }
                long id = seq.incrementAndGet();
                Map<String, Object> task = new LinkedHashMap<>();
                task.put("id", id);
                task.put("kind", body.get("kind") == null ? "monthly_report" : body.get("kind"));
                task.put("status", "queued");
                task.put("summary", "");
                task.put("result", null);
                task.put("error", null);
                byId.put(id, task);
                byRef.put(ref, task);
                reply(ex, 201, json.writeValueAsString(task));
                return;
            }
            if ("GET".equals(ex.getRequestMethod()) && path.startsWith("/api/agent/tasks/")) {
                Map<String, Object> task = byId.get(Long.parseLong(path.substring("/api/agent/tasks/".length())));
                if (task == null) {
                    reply(ex, 404, "{}");
                    return;
                }
                reply(ex, 200, json.writeValueAsString(task));
                return;
            }
            reply(ex, 404, "{}");
        }

        private static void reply(HttpExchange ex, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (var out = ex.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    /** A client with no token configured, to show nothing is sent. */
    static final class OwnersOfficeClientProbe {
        private final com.example.project_flow.integration.ownersoffice.OwnersOfficeClient client =
                new com.example.project_flow.integration.ownersoffice.OwnersOfficeClient(
                        "http://127.0.0.1:1", "");

        boolean configured() { return client.configured(); }
        boolean reachable() { return client.reachable(); }
        void createTask(String ref, String kind, String title, String instructions) {
            client.createTask(ref, kind, title, instructions);
        }
    }
}
