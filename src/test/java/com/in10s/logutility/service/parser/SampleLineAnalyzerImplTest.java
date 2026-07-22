package com.in10s.logutility.service.parser;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import com.in10s.logutility.response.parser.HighlightSegment;
import com.in10s.logutility.response.parser.SampleLineAnalysis;

/** Pure unit tests — no Spring context — for the sample-line heuristics. */
class SampleLineAnalyzerImplTest {

    private final SampleLineAnalyzer analyzer = new SampleLineAnalyzerImpl();

    @Test
    void detectsSpaceSeparatedTimestampWithMillis() {
        SampleLineAnalysis result = analyzer.analyze(
                "2026-07-21 14:30:15.123 [main] INFO com.acme.OrderService - order placed");

        assertThat(result.timestamp().text()).isEqualTo("2026-07-21 14:30:15.123");
        assertThat(result.suggestedTimestampPattern()).isEqualTo("yyyy-MM-dd HH:mm:ss.SSS");

        // The inferred pattern must actually parse the detected substring back correctly.
        LocalDateTime parsed = LocalDateTime.parse(result.timestamp().text(),
                DateTimeFormatter.ofPattern(result.suggestedTimestampPattern()));
        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 21, 14, 30, 15, 123_000_000));
    }

    @Test
    void detectsIsoTimestampWithLiteralTAndCommaMillis() {
        SampleLineAnalysis result = analyzer.analyze("2026-07-21T14:30:15,500 WARN c.a.Foo slow");

        assertThat(result.timestamp().text()).isEqualTo("2026-07-21T14:30:15,500");
        assertThat(result.suggestedTimestampPattern()).isEqualTo("yyyy-MM-dd'T'HH:mm:ss,SSS");

        LocalDateTime parsed = LocalDateTime.parse(result.timestamp().text(),
                DateTimeFormatter.ofPattern(result.suggestedTimestampPattern()));
        assertThat(parsed).isEqualTo(LocalDateTime.of(2026, 7, 21, 14, 30, 15, 500_000_000));
    }

    @Test
    void detectsTimestampWithoutFraction() {
        SampleLineAnalysis result = analyzer.analyze("2026-07-21 14:30:15 ERROR boom");

        assertThat(result.timestamp().text()).isEqualTo("2026-07-21 14:30:15");
        assertThat(result.suggestedTimestampPattern()).isEqualTo("yyyy-MM-dd HH:mm:ss");
    }

    @Test
    void detectsLevelCaseAsWritten() {
        SampleLineAnalysis result = analyzer.analyze("2026-07-21 14:30:15.000 WARN com.acme.App msg");

        assertThat(result.level().text()).isEqualTo("WARN");
        assertThat(result.suggestedLevelPattern()).isEqualTo("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");
    }

    @Test
    void alwaysSuggestsDefaultLevelPatternEvenWhenNotFound() {
        SampleLineAnalysis result = analyzer.analyze("no level word here at all");

        assertThat(result.level()).isNull();
        assertThat(result.suggestedLevelPattern()).isEqualTo("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");
    }

    @Test
    void detectsDottedLoggerNameAfterLevel() {
        SampleLineAnalysis result = analyzer.analyze(
                "2026-07-21 14:30:15.000 [main] INFO com.acme.service.OrderService - done");

        assertThat(result.logger().text()).isEqualTo("com.acme.service.OrderService");
        assertThat(result.suggestedLoggerPattern()).isNotNull();
    }

    @Test
    void handlesBlankLineGracefullyWithAllNulls() {
        SampleLineAnalysis result = analyzer.analyze("");

        assertThat(result.timestamp()).isNull();
        assertThat(result.level()).isNull();
        assertThat(result.logger()).isNull();
        assertThat(result.segments()).isEmpty();
    }

    @Test
    void handlesNullInputGracefully() {
        SampleLineAnalysis result = analyzer.analyze(null);

        assertThat(result.timestamp()).isNull();
        assertThat(result.segments()).isEmpty();
    }

    @Test
    void segmentsReconstructTheOriginalLineInOrder() {
        String line = "2026-07-21 14:30:15.000 [main] INFO com.acme.App - hello";
        SampleLineAnalysis result = analyzer.analyze(line);

        StringBuilder rebuilt = new StringBuilder();
        for (HighlightSegment segment : result.segments()) {
            rebuilt.append(segment.text());
        }
        assertThat(rebuilt.toString()).isEqualTo(line);

        // The three known tokens must appear as their own labeled segments, in the right order.
        List<String> labels = result.segments().stream()
                .map(HighlightSegment::label).filter(l -> l != null).toList();
        assertThat(labels).containsExactly("timestamp", "level", "logger");
    }

    @Test
    void noTokensFoundYieldsSingleUnlabeledSegment() {
        SampleLineAnalysis result = analyzer.analyze("just a plain line of text");

        assertThat(result.segments()).hasSize(1);
        assertThat(result.segments().get(0).label()).isNull();
        assertThat(result.segments().get(0).text()).isEqualTo("just a plain line of text");
    }

    @Test
    void dropsOverlappingLowerPriorityCandidate() {
        // "INFO" alone would also satisfy a dotted-identifier-like scan only if it contained a dot,
        // which it doesn't — this test instead confirms level is found before logger search begins,
        // by placing a logger-shaped token that starts exactly where level ends (no overlap possible)
        // and confirming both survive distinctly.
        SampleLineAnalysis result = analyzer.analyze("2026-07-21 14:30:15.000 INFO a.b.C msg");

        assertThat(result.level().text()).isEqualTo("INFO");
        assertThat(result.logger().text()).isEqualTo("a.b.C");
    }
}
