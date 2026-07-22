package com.app.logutility.service.search;

import com.app.logutility.entity.project.LinePattern;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies multi-line entry grouping: any line without a timestamp joins the previous entry. */
class LogEntryAssemblerTest {

    private final LogLineParserFactory factory = new LogLineParserFactory();

    private LogLineParser parser() {
        LinePattern lp = new LinePattern();
        lp.setTimestampPattern("yyyy-MM-dd HH:mm:ss.SSS");
        return factory.create(lp);
    }

    private static List<LogEntryAssembler.RawEntry> collect(LogEntryAssembler assembler) {
        List<LogEntryAssembler.RawEntry> entries = new ArrayList<>();
        while (assembler.hasNext()) {
            entries.add(assembler.next());
        }
        return entries;
    }

    @Test
    void singleLineEntriesPassThroughUnchanged() {
        List<String> lines = List.of(
                "2026-07-21 10:00:00.000 INFO first",
                "2026-07-21 10:00:01.000 ERROR second");

        List<LogEntryAssembler.RawEntry> entries = collect(new LogEntryAssembler(lines.iterator(), parser(), 300));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).raw()).isEqualTo("2026-07-21 10:00:00.000 INFO first");
        assertThat(entries.get(1).raw()).isEqualTo("2026-07-21 10:00:01.000 ERROR second");
    }

    @Test
    void continuationLinesAreFoldedIntoThePrecedingEntry() {
        List<String> lines = List.of(
                "2026-07-21 10:00:00.000 ERROR boom",
                "    at com.acme.Foo.bar(Foo.java:1)",
                "    at com.acme.Foo.baz(Foo.java:2)",
                "2026-07-21 10:00:01.000 INFO next");

        List<LogEntryAssembler.RawEntry> entries = collect(new LogEntryAssembler(lines.iterator(), parser(), 300));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).raw()).isEqualTo(
                "2026-07-21 10:00:00.000 ERROR boom\n"
                        + "    at com.acme.Foo.bar(Foo.java:1)\n"
                        + "    at com.acme.Foo.baz(Foo.java:2)");
        assertThat(entries.get(0).timestamp()).isEqualTo(LocalDateTime.of(2026, 7, 21, 10, 0, 0));
        assertThat(entries.get(1).raw()).isEqualTo("2026-07-21 10:00:01.000 INFO next");
    }

    @Test
    void trailingContinuationLinesAreFlushedAtEndOfFile() {
        List<String> lines = List.of(
                "2026-07-21 10:00:00.000 ERROR boom",
                "    at com.acme.Foo.bar(Foo.java:1)");

        List<LogEntryAssembler.RawEntry> entries = collect(new LogEntryAssembler(lines.iterator(), parser(), 300));

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).raw()).contains("boom").contains("Foo.bar");
    }

    @Test
    void continuationBeforeAnyTimestampStillSurfacesAsAnEntry() {
        List<String> lines = List.of(
                "    orphan continuation before any timestamped line",
                "2026-07-21 10:00:00.000 INFO first");

        List<LogEntryAssembler.RawEntry> entries = collect(new LogEntryAssembler(lines.iterator(), parser(), 300));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).timestamp()).isNull();
        assertThat(entries.get(0).raw()).contains("orphan continuation");
        assertThat(entries.get(1).timestamp()).isEqualTo(LocalDateTime.of(2026, 7, 21, 10, 0, 0));
    }

    @Test
    void continuationRunIsCappedSoAMisconfiguredPatternCantBufferForever() {
        List<String> lines = new ArrayList<>();
        lines.add("2026-07-21 10:00:00.000 ERROR boom");
        for (int i = 0; i < 10; i++) {
            lines.add("    continuation line " + i);
        }

        List<LogEntryAssembler.RawEntry> entries = collect(new LogEntryAssembler(lines.iterator(), parser(), 3));

        // First entry force-flushed after the header + 3 continuation lines; the remaining 7
        // continuation lines form further capped/overflow entries rather than one unbounded buffer.
        assertThat(entries).hasSizeGreaterThan(1);
        assertThat(entries.get(0).raw().lines()).hasSize(4);
        assertThat(entries.stream().flatMap(e -> e.raw().lines())).hasSize(11);
    }
}
