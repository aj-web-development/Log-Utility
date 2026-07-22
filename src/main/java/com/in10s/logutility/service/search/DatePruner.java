package com.in10s.logutility.service.search;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import com.in10s.logutility.dto.search.ScanPlan;

/**
 * Prunes a search to the set of candidate <em>days</em> before any filesystem access — the cheap,
 * coarse dimension that maps to distinct rotated files/folders. For each day in the requested
 * range it fills the {@code {date}} placeholder and turns {@code {HH}} / {@code {i}} (and any
 * other placeholder) into a {@code *} glob; the finer hour/index dimensions are resolved by
 * globbing at scan time and the per-line timestamp fast-reject handles sub-day precision.
 *
 * <p><b>Assumption:</b> {@code {date}} is a day-granularity token formatted {@code yyyy-MM-dd}
 * (the Logback default and the overwhelmingly common convention). Projects whose rotated files use
 * a different folder-date format would need that captured explicitly — a documented limitation.
 */
@Component
public class DatePruner {

    private static final DateTimeFormatter DATE_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    /** Guards against pathological ranges producing an enormous number of globs. */
    private static final long MAX_DAYS = 1000;

    private final Clock clock;

    public DatePruner(Clock searchClock) {
        this.clock = searchClock;
    }

    public ScanPlan plan(String backupRootPattern, String backupPathPattern, LocalDateTime from, LocalDateTime to) {
        List<String> globs = new ArrayList<>();

        if (StringUtils.hasText(backupPathPattern) && !from.isAfter(to)) {
            LocalDate day = from.toLocalDate();
            LocalDate end = to.toLocalDate();
            long emitted = 0;
            while (!day.isAfter(end) && emitted < MAX_DAYS) {
                globs.add(fillPlaceholders(backupPathPattern, day));
                day = day.plusDays(1);
                emitted++;
            }
        }

        // The live file holds the most recent (un-rotated) entries; include it whenever the range
        // reaches into today or later.
        LocalDateTime startOfToday = LocalDate.now(clock).atStartOfDay();
        boolean includeLive = !to.isBefore(startOfToday);

        return new ScanPlan(globs, includeLive);
    }

    private static String fillPlaceholders(String pattern, LocalDate day) {
        return pattern
                .replace("{date}", day.format(DATE_FOLDER_FORMAT))
                .replaceAll("\\{[^}]*}", "*"); // {HH}, {i}, and anything else become wildcards
    }
}
