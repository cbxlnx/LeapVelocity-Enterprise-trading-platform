package com.leapvelocity.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class UsEquityTradingCalendarTest {
    @Test
    void publishedYearTotalsIncludeTheExceptionalCarterClosure() {
        // Weekdays less the published NYSE full-day closures, including 2025-01-09.
        int[] counts = {252, 250, 251, 251, 251};
        for (int year = 2024; year <= 2028; year++) {
            var sessions = UsEquityTradingCalendar.sessions(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).orElseThrow();
            assertEquals(counts[year - 2024], sessions.size(), String.valueOf(year));
            assertEquals(sessions.stream().distinct().sorted().toList(), sessions);
        }
    }

    @Test
    void holidaysExceptionalClosuresAndWeekendsAreExcludedButHalfDaysRemain() {
        for (String closed : List.of("2024-03-29", "2025-01-09", "2026-04-03", "2026-07-03", "2026-10-17", "2027-06-18")) {
            var day = LocalDate.parse(closed);
            assertTrue(UsEquityTradingCalendar.sessions(day, day).orElseThrow().isEmpty(), closed);
        }
        // Columbus Day is a bank holiday, not an NYSE closure. Dec 31, 2027 is not a substitute New Year's holiday.
        for (String open : List.of("2024-07-03", "2025-11-28", "2026-10-12", "2026-11-27", "2027-12-31", "2028-07-03")) {
            var day = LocalDate.parse(open);
            assertEquals(List.of(day), UsEquityTradingCalendar.sessions(day, day).orElseThrow(), open);
        }
    }

    @Test
    void unsupportedOrInvalidRangesDoNotFallBackToWeekdays() {
        assertTrue(UsEquityTradingCalendar.sessions(LocalDate.parse("2023-12-31"), LocalDate.parse("2024-01-02")).isEmpty());
        assertTrue(UsEquityTradingCalendar.sessions(LocalDate.parse("2028-12-29"), LocalDate.parse("2029-01-01")).isEmpty());
        assertTrue(UsEquityTradingCalendar.sessions(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-19")).isEmpty());
        assertTrue(UsEquityTradingCalendar.sessions(null, LocalDate.parse("2026-10-19")).isEmpty());
    }
}
