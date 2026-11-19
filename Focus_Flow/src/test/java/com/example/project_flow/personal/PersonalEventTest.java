package com.example.project_flow.personal;

import com.example.project_flow.exception.ConflictException;
import com.example.project_flow.exception.NotFoundException;
import com.example.project_flow.personal.PersonalEventService.Request;
import com.example.project_flow.personal.PersonalEventService.View;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class PersonalEventTest {

    @Autowired PersonalEventService service;

    private static long newUserId() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000L) + 1_000_000L;
    }

    private static Request req(String title, LocalDate date) {
        return new Request(title, date, LocalTime.of(9, 30), "DAILY", "a note", null);
    }

    @Test
    void aMemberSeesAndChangesOnlyTheirOwnItems() {
        long jihyun = newUserId();
        long other = newUserId();
        LocalDate day = LocalDate.of(2026, 11, 20);
        View mine = service.create(jihyun, req("Dentist", day));
        assertEquals("Dentist", mine.title());
        assertEquals(1, service.month(jihyun, YearMonth.of(2026, 11)).size());
        assertTrue(service.month(other, YearMonth.of(2026, 11)).isEmpty(), "another member sees nothing of it");

        assertThrows(NotFoundException.class, () -> service.update(other, mine.id(), req("hijacked", day)),
                "another member cannot change it");
        assertThrows(NotFoundException.class, () -> service.delete(other, mine.id()),
                "another member cannot delete it");
        assertEquals("Dentist", service.month(jihyun, YearMonth.of(2026, 11)).get(0).title(), "the item is unchanged");
    }

    @Test
    void aMemberCanEditTickAndDeleteTheirOwnItem() {
        long user = newUserId();
        LocalDate day = LocalDate.of(2026, 11, 21);
        View item = service.create(user, req("Pay rent", day));
        View edited = service.update(user, item.id(), new Request("Pay rent (done)", day, LocalTime.of(10, 0), "FINANCE", null, true));
        assertEquals("Pay rent (done)", edited.title());
        assertTrue(edited.done());
        assertEquals("FINANCE", edited.category());
        service.delete(user, item.id());
        assertTrue(service.month(user, YearMonth.of(2026, 11)).stream().noneMatch(v -> v.id().equals(item.id())));
    }

    @Test
    void monthListingIsLimitedToThatMonth() {
        long user = newUserId();
        service.create(user, req("in month", LocalDate.of(2026, 12, 3)));
        service.create(user, req("next month", LocalDate.of(2027, 1, 3)));
        assertEquals(1, service.month(user, YearMonth.of(2026, 12)).size());
    }

    @Test
    void validationRejectsEmptyTitleBadCategoryAndMissingDate() {
        long user = newUserId();
        LocalDate day = LocalDate.of(2026, 11, 22);
        assertThrows(ConflictException.class, () -> service.create(user, new Request("  ", day, null, "DAILY", null, null)));
        assertThrows(ConflictException.class, () -> service.create(user, new Request("x", day, null, "NOT_A_CATEGORY", null, null)));
        assertThrows(ConflictException.class, () -> service.create(user, new Request("x", null, null, "DAILY", null, null)));
        assertFalse(service.month(user, YearMonth.of(2026, 11)).stream().anyMatch(v -> v.title().equals("x")));
    }
}
