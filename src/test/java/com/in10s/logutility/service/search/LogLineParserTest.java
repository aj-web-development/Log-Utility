package com.in10s.logutility.service.search;

import com.in10s.logutility.entity.project.LinePattern;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the timestamp fast-reject parse and level extraction. */
class LogLineParserTest {

    private final LogLineParserFactory factory = new LogLineParserFactory();

    private LinePattern pattern(String timestampPattern, String levelPattern) {
        LinePattern lp = new LinePattern();
        lp.setTimestampPattern(timestampPattern);
        lp.setLevelPattern(levelPattern);
        return lp;
    }

    @Test
    void parsesLeadingTimestampWithConfiguredPattern() {
        LogLineParser parser = factory.create(pattern("yyyy-MM-dd HH:mm:ss.SSS", null));

        Optional<LocalDateTime> ts = parser.timestamp(
                "2026-07-21 14:30:15.123 [main] INFO com.acme.App tid=abc - started");

        assertThat(ts).contains(LocalDateTime.of(2026, 7, 21, 14, 30, 15, 123_000_000));
    }

    @Test
    void ignoresTrailingZoneOffsetAndKeepsWallClock() {
        LogLineParser parser = factory.create(pattern("yyyy-MM-dd HH:mm:ss.SSS Z", null));

        Optional<LocalDateTime> ts = parser.timestamp("2026-07-21 14:30:15.123 +0530 rest of line");

        assertThat(ts).contains(LocalDateTime.of(2026, 7, 21, 14, 30, 15, 123_000_000));
    }

    @Test
    void returnsEmptyForContinuationLineWithoutTimestamp() {
        LogLineParser parser = factory.create(pattern("yyyy-MM-dd HH:mm:ss.SSS", null));

        assertThat(parser.timestamp("    at com.acme.Foo.bar(Foo.java:42)")).isEmpty();
    }

    @Test
    void usesFallbackFormatsWhenNoPatternConfigured() {
        LogLineParser parser = factory.create(null);

        assertThat(parser.timestamp("2026-07-21 14:30:15 something"))
                .contains(LocalDateTime.of(2026, 7, 21, 14, 30, 15));
        assertThat(parser.timestamp("2026-07-21T14:30:15.500 iso-ish"))
                .contains(LocalDateTime.of(2026, 7, 21, 14, 30, 15, 500_000_000));
    }

    @Test
    void fastRejectExampleUsesTimestampOnly() {
        // Demonstrates the fast-reject predicate a scanner applies before matchers.
        LogLineParser parser = factory.create(pattern("yyyy-MM-dd HH:mm:ss.SSS", null));
        LocalDateTime from = LocalDateTime.of(2026, 7, 21, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 7, 21, 23, 59, 59);

        LocalDateTime inRange = parser.timestamp("2026-07-21 10:00:00.000 x").orElseThrow();
        LocalDateTime outOfRange = parser.timestamp("2026-07-22 10:00:00.000 x").orElseThrow();

        assertThat(inRange.isBefore(from) || inRange.isAfter(to)).isFalse();
        assertThat(outOfRange.isBefore(from) || outOfRange.isAfter(to)).isTrue();
    }

    @Test
    void extractsLevelWithDefaultKeywordScan() {
        LogLineParser parser = factory.create(null);

        assertThat(parser.level("2026-07-21 10:00:00 [main] WARN com.acme - careful")).isEqualTo("WARN");
        assertThat(parser.level("2026-07-21 10:00:00 no level word")).isNull();
    }
}
