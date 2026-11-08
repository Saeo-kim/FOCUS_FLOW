package com.example.project_flow.entity;

import com.example.project_flow.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One attendance shift. The state machine lives here so every invalid transition is rejected
 * regardless of which caller attempts it.
 */
@Entity
@Table(name = "attendance_records",
        uniqueConstraints = @UniqueConstraint(name = "uk_attendance_work_date", columnNames = "work_date"))
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(nullable = false)
    private LocalDateTime checkInAt;

    private LocalDateTime checkOutAt;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startedAt ASC")
    private List<AttendanceBreak> breaks = new ArrayList<>();

    protected AttendanceRecord() {
    }

    public AttendanceRecord(LocalDateTime checkInAt) {
        this.workDate = checkInAt.toLocalDate();
        this.checkInAt = checkInAt;
    }

    public AttendanceState getState() {
        if (checkOutAt != null) {
            return AttendanceState.CHECKED_OUT;
        }
        return openBreak().isPresent() ? AttendanceState.ON_BREAK : AttendanceState.WORKING;
    }

    public void startBreak(LocalDateTime now) {
        requireNotCheckedOut();
        if (openBreak().isPresent()) {
            throw new ConflictException("You are already on a break.");
        }
        breaks.add(new AttendanceBreak(this, now));
    }

    public void endBreak(LocalDateTime now) {
        requireNotCheckedOut();
        openBreak()
                .orElseThrow(() -> new ConflictException("You are not on a break."))
                .end(now);
    }

    public void checkOut(LocalDateTime now) {
        requireNotCheckedOut();
        if (openBreak().isPresent()) {
            throw new ConflictException("End your break before checking out.");
        }
        this.checkOutAt = now;
    }

    /**
     * Reopens a shift that was already checked out. The time away is kept as a break, so it does
     * not count as work and the earlier check-out time stays on record.
     */
    public void resume(LocalDateTime now) {
        if (checkOutAt == null) {
            throw new ConflictException("You are already checked in.");
        }
        AttendanceBreak away = new AttendanceBreak(this, checkOutAt);
        away.end(now);
        breaks.add(away);
        checkOutAt = null;
    }

    /**
     * Closes a shift left open on an earlier day at the time the person actually stopped. A break
     * still open at that point ends at the same time, otherwise it would absorb the whole gap.
     */
    public void closeMissedCheckOut(LocalDateTime at, LocalDateTime now) {
        requireNotCheckedOut();
        if (!workDate.isBefore(now.toLocalDate())) {
            throw new ConflictException("A check-out time can only be set for a shift from an earlier day.");
        }
        if (at.isAfter(now)) {
            throw new ConflictException("The check-out time cannot be in the future.");
        }
        if (!at.isAfter(latestEventBefore())) {
            throw new ConflictException("The check-out time must be after your last recorded activity.");
        }
        openBreak().ifPresent(openBreak -> openBreak.end(at));
        this.checkOutAt = at;
    }

    private LocalDateTime latestEventBefore() {
        LocalDateTime latest = checkInAt;
        for (AttendanceBreak item : breaks) {
            LocalDateTime event = item.getEndedAt() != null ? item.getEndedAt() : item.getStartedAt();
            if (event.isAfter(latest)) {
                latest = event;
            }
        }
        return latest;
    }

    public long breakSeconds(LocalDateTime now) {
        LocalDateTime end = checkOutAt != null ? checkOutAt : now;
        return breaks.stream().mapToLong(item -> item.seconds(end)).sum();
    }

    /** Time from check-in to check-out (or {@code now} while the shift is open), excluding breaks. */
    public long workedSeconds(LocalDateTime now) {
        LocalDateTime end = checkOutAt != null ? checkOutAt : now;
        long total = Duration.between(checkInAt, end).getSeconds();
        return Math.max(0, total - breakSeconds(now));
    }

    private Optional<AttendanceBreak> openBreak() {
        return breaks.stream().filter(AttendanceBreak::isOpen).findFirst();
    }

    private void requireNotCheckedOut() {
        if (checkOutAt != null) {
            throw new ConflictException("You have already checked out of this shift.");
        }
    }

    public Long getId() {
        return id;
    }

    public LocalDate getWorkDate() {
        return workDate;
    }

    public LocalDateTime getCheckInAt() {
        return checkInAt;
    }

    public LocalDateTime getCheckOutAt() {
        return checkOutAt;
    }

    public List<AttendanceBreak> getBreaks() {
        return breaks;
    }
}
