package com.app.logutility.service.search;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import com.app.logutility.dto.search.ScanPlan;

/** Pure tests for the (filesystem-free) day-pruning math and the include-live decision. */
class DatePrunerTest {

    private DatePruner prunerWithToday(LocalDateTime today) {
        Clock fixed = Clock.fixed(today.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        return new DatePruner(fixed);
    }

    @Test
    void oneGlobPerDayWithDateFilledAndHourIndexWildcarded() {
        DatePruner pruner = prunerWithToday(LocalDateTime.of(2026, 7, 21, 12, 0));

        ScanPlan plan = pruner.plan("/var/log/archive", "app.{date}.{i}.log.gz",
                LocalDateTime.of(2026, 7, 19, 8, 0),
                LocalDateTime.of(2026, 7, 21, 10, 0));

        assertThat(plan.backupGlobs()).containsExactly(
                "app.2026-07-19.*.log.gz",
                "app.2026-07-20.*.log.gz",
                "app.2026-07-21.*.log.gz");
    }

    @Test
    void datePlaceholderInDirectoryAndHourWildcarded() {
        DatePruner pruner = prunerWithToday(LocalDateTime.of(2026, 7, 21, 12, 0));

        ScanPlan plan = pruner.plan("/logs", "{date}/{HH}/app.{i}.log.gz",
                LocalDateTime.of(2026, 7, 20, 0, 0),
                LocalDateTime.of(2026, 7, 20, 23, 0));

        assertThat(plan.backupGlobs()).containsExactly("2026-07-20/*/app.*.log.gz");
    }

    @Test
    void includesLiveFileWhenRangeReachesToday() {
        DatePruner pruner = prunerWithToday(LocalDateTime.of(2026, 7, 21, 9, 0));

        ScanPlan plan = pruner.plan("/var/log", "app.{date}.log.gz",
                LocalDateTime.of(2026, 7, 21, 0, 0),
                LocalDateTime.of(2026, 7, 21, 8, 0));

        assertThat(plan.includeLive()).isTrue();
    }

    @Test
    void excludesLiveFileForAPastOnlyRange() {
        DatePruner pruner = prunerWithToday(LocalDateTime.of(2026, 7, 21, 9, 0));

        ScanPlan plan = pruner.plan("/var/log", "app.{date}.log.gz",
                LocalDateTime.of(2026, 7, 18, 0, 0),
                LocalDateTime.of(2026, 7, 20, 23, 59));

        assertThat(plan.includeLive()).isFalse();
        assertThat(plan.backupGlobs()).hasSize(3);
    }

    @Test
    void noBackupGlobsWhenPatternMissing() {
        DatePruner pruner = prunerWithToday(LocalDateTime.of(2026, 7, 21, 9, 0));

        ScanPlan plan = pruner.plan("/var/log", "  ",
                LocalDateTime.of(2026, 7, 20, 0, 0),
                LocalDateTime.of(2026, 7, 21, 0, 0));

        assertThat(plan.backupGlobs()).isEmpty();
        assertThat(plan.includeLive()).isTrue();
    }
}
