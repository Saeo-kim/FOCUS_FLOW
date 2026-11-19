package com.example.project_flow.personal;

import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

/** Personal calendar items. Every operation is scoped to the signed-in account. */
@Service
public class PersonalEventService {

    public record Request(String title, LocalDate eventDate, LocalTime startTime, String category, String note, Boolean done) {
    }

    public record View(Long id, String title, LocalDate eventDate, LocalTime startTime, String category, String note,
                       boolean done, LocalDateTime updatedAt) {
        static View of(PersonalEvent e) {
            return new View(e.getId(), e.getTitle(), e.getEventDate(), e.getStartTime(), e.getCategory().name(),
                    e.getNote(), e.isDone(), e.getUpdatedAt());
        }
    }

    private final PersonalEventRepository events;
    private final Clock clock;

    public PersonalEventService(PersonalEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<View> month(Long userId, YearMonth month) {
        return events.findByUserIdAndEventDateBetweenOrderByEventDateAscStartTimeAscIdAsc(userId, month.atDay(1), month.atEndOfMonth())
                .stream().map(View::of).toList();
    }

    @Transactional
    public View create(Long userId, Request q) {
        PersonalEvent e = events.save(new PersonalEvent(userId, title(q.title()), date(q.eventDate()), q.startTime(),
                category(q.category()), note(q.note()), now()));
        return View.of(e);
    }

    @Transactional
    public View update(Long userId, Long id, Request q) {
        PersonalEvent e = owned(userId, id);
        e.update(title(q.title()), date(q.eventDate()), q.startTime(), category(q.category()), note(q.note()), now());
        if (q.done() != null) e.setDone(q.done(), now());
        return View.of(e);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        events.delete(owned(userId, id));
    }

    /** Someone else's item is reported as not found, so existence is not revealed. */
    private PersonalEvent owned(Long userId, Long id) {
        return events.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("That item is not on your calendar."));
    }

    private static String title(String value) {
        if (value == null || value.isBlank()) throw new ConflictException("Give the item a title.");
        String t = value.trim();
        if (t.length() > 120) throw new ConflictException("Keep the title under 120 characters.");
        return t;
    }

    private static LocalDate date(LocalDate value) {
        if (value == null) throw new ConflictException("Choose the date.");
        return value;
    }

    private static PersonalEvent.Category category(String value) {
        if (value == null || value.isBlank()) return PersonalEvent.Category.OTHER;
        try {
            return PersonalEvent.Category.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ConflictException("Choose a category from the list.");
        }
    }

    private static String note(String value) {
        if (value == null || value.isBlank()) return null;
        String t = value.trim();
        if (t.length() > 1000) throw new ConflictException("Keep the note under 1000 characters.");
        return t;
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
