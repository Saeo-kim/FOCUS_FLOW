package com.example.project_flow.dto;

import com.example.project_flow.entity.AttendanceBreak;
import com.example.project_flow.entity.AttendanceRecord;
import com.example.project_flow.entity.AttendanceState;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    public record CheckOutRequest(LocalDateTime at) {
    }

    public record BreakResponse(LocalDateTime startedAt, LocalDateTime endedAt, long seconds) {

        static BreakResponse from(AttendanceBreak item, LocalDateTime now) {
            return new BreakResponse(item.getStartedAt(), item.getEndedAt(), item.seconds(now));
        }
    }

    public record RecordResponse(
            Long id,
            LocalDate workDate,
            AttendanceState state,
            LocalDateTime checkInAt,
            LocalDateTime checkOutAt,
            List<BreakResponse> breaks,
            long workedSeconds,
            long breakSeconds) {

        public static RecordResponse from(AttendanceRecord record, LocalDateTime now) {
            return new RecordResponse(
                    record.getId(),
                    record.getWorkDate(),
                    record.getState(),
                    record.getCheckInAt(),
                    record.getCheckOutAt(),
                    record.getBreaks().stream().map(item -> BreakResponse.from(item, now)).toList(),
                    record.workedSeconds(now),
                    record.breakSeconds(now));
        }
    }

    /**
     * @param record          today's shift, or an older shift that was never closed
     * @param missedCheckOut  true when {@code record} is still open from an earlier day
     */
    public record StatusResponse(
            AttendanceState state,
            RecordResponse record,
            boolean missedCheckOut,
            int dailyTargetMinutes) {
    }

    public record HistoryResponse(
            String month,
            List<RecordResponse> records,
            long totalWorkedSeconds,
            int daysWorked) {
    }
}
