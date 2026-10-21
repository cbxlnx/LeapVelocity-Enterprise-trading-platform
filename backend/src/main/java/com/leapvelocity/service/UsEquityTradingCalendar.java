package com.leapvelocity.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Bounded, offline NYSE session calendar; never extrapolates unreviewed years. */
public final class UsEquityTradingCalendar {
    public static final String VERSION = "NYSE_2024_2028_REVIEWED_2026_10_19";
    public static final LocalDate COVERED_FROM = LocalDate.of(2024, 1, 1);
    public static final LocalDate COVERED_TO = LocalDate.of(2028, 12, 31);
    private static final Set<LocalDate> CLOSURES = loadClosures();

    private UsEquityTradingCalendar() { }

    public static Optional<List<LocalDate>> sessions(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)
                || from.isBefore(COVERED_FROM) || to.isAfter(COVERED_TO)) return Optional.empty();
        return Optional.of(from.datesUntil(to.plusDays(1))
                .filter(day -> day.getDayOfWeek() != DayOfWeek.SATURDAY
                        && day.getDayOfWeek() != DayOfWeek.SUNDAY && !CLOSURES.contains(day))
                .toList());
    }

    private static Set<LocalDate> loadClosures() {
        var stream = UsEquityTradingCalendar.class.getResourceAsStream("/calendars/us-equity-closures.txt");
        if (stream == null) throw new IllegalStateException("Bundled US equity calendar is missing");
        try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            Set<LocalDate> dates = new TreeSet<>();
            for (String line : reader.lines().toList()) {
                if (line.isBlank() || line.startsWith("#")) continue;
                LocalDate day = LocalDate.parse(line.trim());
                if (day.isBefore(COVERED_FROM) || day.isAfter(COVERED_TO) || !dates.add(day)) {
                    throw new IllegalStateException("Invalid bundled calendar closure: " + day);
                }
            }
            return Set.copyOf(dates);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read bundled US equity calendar", exception);
        }
    }
}
