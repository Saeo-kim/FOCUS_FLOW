package com.example.project_flow.service;

import com.example.project_flow.dto.AttendanceDtos.HistoryResponse;
import com.example.project_flow.dto.AttendanceDtos.RecordResponse;
import com.example.project_flow.dto.AttendanceDtos.StatusResponse;
import com.example.project_flow.entity.AttendanceRecord;
import com.example.project_flow.entity.AttendanceState;
import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.repository.AttendanceRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Service
public class AttendanceService {

    private final AttendanceRecordRepository records;
    private final SettingsService settings;
    private final ActivityLogService activity;
    private final Clock clock;

    public AttendanceService(AttendanceRecordRepository records, SettingsService settings,
                             ActivityLogService activity, Clock clock) {
        this.records = records;
        this.settings = settings;
        this.activity = activity;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StatusResponse status() {
        LocalDateTime now = now();
        Optional<AttendanceRecord> open = records.findOpen().stream().findFirst();
        AttendanceRecord record = open.or(() -> records.findByWorkDate(now.toLocalDate())).orElse(null);
        return toStatus(record, now);
    }

    @Transactional
    public StatusResponse checkIn() {
        LocalDateTime now = now();
        Optional<AttendanceRecord> open = records.findOpenForUpdate().stream().findFirst();
        if (open.isPresent()) {
            LocalDate openDate = open.get().getWorkDate();
            throw new ConflictException(openDate.equals(now.toLocalDate())
                    ? "You are already checked in."
                    : "Check out of your open shift from " + openDate + " first.");
        }
        Optional<AttendanceRecord> finishedToday = records.findByWorkDateForUpdate(now.toLocalDate());
        if (finishedToday.isPresent()) {
            finishedToday.get().resume(now);
            activity.record("ATTENDANCE_CHECK_IN", "Checked in again");
            return toStatus(finishedToday.get(), now);
        }
        AttendanceRecord record = records.saveAndFlush(new AttendanceRecord(now));
        activity.record("ATTENDANCE_CHECK_IN", "Checked in");
        return toStatus(record, now);
    }

    @Transactional
    public StatusResponse startBreak() {
        LocalDateTime now = now();
        AttendanceRecord record = requireOpen("Check in before starting a break.", now);
        record.startBreak(now);
        activity.record("ATTENDANCE_BREAK_START", "Started a break");
        return toStatus(record, now);
    }

    @Transactional
    public StatusResponse endBreak() {
        LocalDateTime now = now();
        AttendanceRecord record = requireOpen("You are not on a break.", now);
        record.endBreak(now);
        activity.record("ATTENDANCE_BREAK_END", "Ended a break");
        return toStatus(record, now);
    }

    /**
     * @param at when the person actually stopped working; only accepted for a shift left open on
     *           an earlier day. Null means "now".
     */
    @Transactional
    public StatusResponse checkOut(LocalDateTime at) {
        LocalDateTime now = now();
        AttendanceRecord record = requireOpen("Check in before checking out.", now);
        if (at != null) {
            record.closeMissedCheckOut(at, now);
        } else {
            record.checkOut(now);
        }
        activity.record("ATTENDANCE_CHECK_OUT", "Checked out");
        return toStatus(record, now);
    }

    @Transactional(readOnly = true)
    public HistoryResponse history(YearMonth month) {
        LocalDateTime now = now();
        YearMonth target = month != null ? month : YearMonth.from(now);
        List<RecordResponse> rows = records.findHistory(target.atDay(1), target.atEndOfMonth()).stream()
                .map(record -> RecordResponse.from(record, now))
                .toList();
        long total = rows.stream().mapToLong(RecordResponse::workedSeconds).sum();
        return new HistoryResponse(target.toString(), rows, total, rows.size());
    }

    private AttendanceRecord requireOpen(String messageWhenNoShift, LocalDateTime now) {
        return records.findOpenForUpdate().stream().findFirst().orElseThrow(() -> {
            boolean finishedToday = records.existsByWorkDate(now.toLocalDate());
            return new ConflictException(finishedToday ? "You have already checked out today." : messageWhenNoShift);
        });
    }

    private StatusResponse toStatus(AttendanceRecord record, LocalDateTime now) {
        int target = settings.current().getDailyTargetMinutes();
        if (record == null) {
            return new StatusResponse(AttendanceState.NOT_CHECKED_IN, null, false, target);
        }
        boolean missedCheckOut = record.getCheckOutAt() == null && record.getWorkDate().isBefore(now.toLocalDate());
        return new StatusResponse(record.getState(), RecordResponse.from(record, now), missedCheckOut, target);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
