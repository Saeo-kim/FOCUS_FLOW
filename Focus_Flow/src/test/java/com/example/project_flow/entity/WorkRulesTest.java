package com.example.project_flow.entity;

import com.example.project_flow.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkRulesTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 9);
    private static final LocalDateTime NOW = TODAY.atTime(10, 0);

    private final WorkStep step = new WorkStep(null, "Step", 0);

    @Test
    void onlyACompletedStepReachesFullProgress() {
        step.track(WorkStatus.IN_PROGRESS, 100, null, null, TODAY);
        assertThat(step.getProgress()).isEqualTo(99);

        step.track(WorkStatus.COMPLETED, 10, null, null, TODAY);
        assertThat(step.getProgress()).isEqualTo(100);

        step.track(WorkStatus.NOT_STARTED, 50, null, null, TODAY);
        assertThat(step.getProgress()).isZero();
    }

    @Test
    void stepDatesFollowStatusUnlessEnteredByHand() {
        LocalDate earlier = TODAY.minusDays(2);

        step.track(WorkStatus.IN_PROGRESS, 0, null, null, TODAY);
        assertThat(step.getStartDate()).isEqualTo(TODAY);

        step.track(WorkStatus.COMPLETED, 0, earlier, earlier, TODAY);
        assertThat(step.getStartDate()).isEqualTo(earlier);
        assertThat(step.getCompletedDate()).isEqualTo(earlier);

        step.track(WorkStatus.REVIEW, 0, earlier, earlier, TODAY);
        assertThat(step.getCompletedDate()).isNull();
    }

    @Test
    void taskStatusAndDoneFlagStayInStep() {
        Task task = new Task(NOW);
        assertThat(task.getStatus()).isEqualTo(WorkStatus.NOT_STARTED);

        task.changeStatus(WorkStatus.COMPLETED, NOW);
        assertThat(task.isDone()).isTrue();
        assertThat(task.getCompletedAt()).isEqualTo(NOW);

        task.changeStatus(WorkStatus.BLOCKED, NOW);
        assertThat(task.isDone()).isFalse();
        assertThat(task.getCompletedAt()).isNull();

        task.markDone(true, NOW);
        assertThat(task.getStatus()).isEqualTo(WorkStatus.COMPLETED);
    }

    @Test
    void requirementCannotBeVerifiedWithoutATestAndAResult() {
        Requirement requirement = new Requirement();

        assertThatThrownBy(() -> requirement.recordCheck(true, false, true, "looks fine"))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> requirement.recordCheck(true, true, true, " "))
                .isInstanceOf(ConflictException.class);
        assertThat(requirement.isVerified()).isFalse();

        requirement.recordCheck(true, true, true, "Ran the flow against MySQL");
        assertThat(requirement.isVerified()).isTrue();
    }

    @Test
    void aiWorkNeedsPassingChecksAndAHumanReviewBeforeApproval() {
        AiVerification ai = new AiVerification(NOW);
        assertThat(ai.isVerified()).isFalse();

        assertThatThrownBy(() -> ai.review(CheckStatus.PASSED, CheckStatus.NOT_RUN, "checked", true, false))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> ai.review(CheckStatus.PASSED, CheckStatus.PASSED, null, true, false))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> ai.review(CheckStatus.PASSED, CheckStatus.PASSED, "checked", false, true))
                .isInstanceOf(ConflictException.class);

        ai.review(CheckStatus.PASSED, CheckStatus.PASSED, "Read the diff and ran it", true, true);
        assertThat(ai.isApproved()).isTrue();
    }

    @Test
    void resolvingAnErrorIsWrittenToItsTimeline() {
        ErrorLog log = new ErrorLog();

        log.changeStatus(ErrorStatus.RESOLVED, NOW);
        log.changeStatus(ErrorStatus.RESOLVED, NOW.plusHours(1));

        assertThat(log.getEvents()).hasSize(1);
        assertThat(log.getEvents().getFirst().getNote()).isEqualTo("Marked as resolved");
    }
}
