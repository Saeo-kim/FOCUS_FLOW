package com.example.project_flow.entity;

import com.example.project_flow.exception.ConflictException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttendanceRecordTest {

    private static final LocalDateTime NINE = LocalDateTime.of(2026, 11, 9, 9, 0);

    private final AttendanceRecord record = new AttendanceRecord(NINE);

    @Test
    void newShiftIsWorking() {
        assertThat(record.getState()).isEqualTo(AttendanceState.WORKING);
        assertThat(record.getWorkDate()).isEqualTo(NINE.toLocalDate());
    }

    @Test
    void workedTimeExcludesCompletedBreaks() {
        record.startBreak(NINE.plusHours(3));
        record.endBreak(NINE.plusHours(4));
        record.startBreak(NINE.plusHours(6));
        record.endBreak(NINE.plusHours(6).plusMinutes(15));
        record.checkOut(NINE.plusHours(9));

        LocalDateTime muchLater = NINE.plusDays(3);
        assertThat(record.breakSeconds(muchLater)).isEqualTo(75 * 60);
        assertThat(record.workedSeconds(muchLater)).isEqualTo((9 * 60 - 75) * 60L);
        assertThat(record.getState()).isEqualTo(AttendanceState.CHECKED_OUT);
    }

    @Test
    void workedTimeStopsAccumulatingDuringAnOpenBreak() {
        record.startBreak(NINE.plusHours(2));

        assertThat(record.getState()).isEqualTo(AttendanceState.ON_BREAK);
        assertThat(record.workedSeconds(NINE.plusHours(2).plusMinutes(40))).isEqualTo(2 * 3600);
    }

    @Test
    void cannotStartTwoBreaks() {
        record.startBreak(NINE.plusHours(1));

        assertThatThrownBy(() -> record.startBreak(NINE.plusHours(2)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotEndBreakWhenNotOnOne() {
        assertThatThrownBy(() -> record.endBreak(NINE.plusHours(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void cannotCheckOutDuringBreak() {
        record.startBreak(NINE.plusHours(1));

        assertThatThrownBy(() -> record.checkOut(NINE.plusHours(2)))
                .isInstanceOf(ConflictException.class);
        assertThat(record.getCheckOutAt()).isNull();
    }

    @Test
    void nothingIsAllowedAfterCheckOut() {
        record.checkOut(NINE.plusHours(8));

        assertThatThrownBy(() -> record.checkOut(NINE.plusHours(9))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> record.startBreak(NINE.plusHours(9))).isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> record.endBreak(NINE.plusHours(9))).isInstanceOf(ConflictException.class);
    }

    @Test
    void resumingKeepsTheTimeAwayAsABreak() {
        record.checkOut(NINE.plusHours(1));

        record.resume(NINE.plusHours(3));
        record.checkOut(NINE.plusHours(5));

        LocalDateTime later = NINE.plusHours(9);
        assertThat(record.getState()).isEqualTo(AttendanceState.CHECKED_OUT);
        assertThat(record.breakSeconds(later)).isEqualTo(2 * 3600);
        assertThat(record.workedSeconds(later)).isEqualTo(3 * 3600);
    }

    @Test
    void cannotResumeAShiftThatIsStillOpen() {
        assertThatThrownBy(() -> record.resume(NINE.plusHours(1)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void missedCheckOutEndsAnOpenBreakAtTheChosenTime() {
        record.startBreak(NINE.plusHours(8));
        LocalDateTime nextDay = NINE.plusDays(1);

        record.closeMissedCheckOut(NINE.plusHours(8).plusMinutes(30), nextDay);

        assertThat(record.getState()).isEqualTo(AttendanceState.CHECKED_OUT);
        assertThat(record.breakSeconds(nextDay)).isEqualTo(30 * 60);
        assertThat(record.workedSeconds(nextDay)).isEqualTo(8 * 3600);
    }

    @Test
    void missedCheckOutIsRejectedForTodaysShift() {
        assertThatThrownBy(() -> record.closeMissedCheckOut(NINE.plusHours(1), NINE.plusHours(2)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void missedCheckOutMustFollowLastActivityAndNotBeInTheFuture() {
        LocalDateTime nextDay = NINE.plusDays(1);
        record.startBreak(NINE.plusHours(4));

        assertThatThrownBy(() -> record.closeMissedCheckOut(NINE.plusHours(3), nextDay))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> record.closeMissedCheckOut(nextDay.plusHours(1), nextDay))
                .isInstanceOf(ConflictException.class);
    }
}
