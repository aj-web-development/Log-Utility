package com.app.logutility.service.search;

import com.app.logutility.response.search.LogLine;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the bounded k-way merge across {@link NodeProducer}s: ordering, truncation, isolation. */
class StreamingResultMergerTest {

    private final StreamingResultMerger merger = new StreamingResultMerger();

    private static LogLine line(String node, Instant ts) {
        return new LogLine(node, "Application", ts, "INFO", node + "@" + ts);
    }

    /** A producer pre-loaded and already finished — enough capacity that offer() never blocks. */
    private static NodeProducer filled(String label, List<LogLine> lines) throws InterruptedException {
        NodeProducer producer = new NodeProducer(label, lines.size() + 1);
        for (LogLine line : lines) {
            producer.offer(line);
        }
        producer.finish();
        return producer;
    }

    @Test
    void mergesMultipleProducersInTimestampOrder() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        NodeProducer a = filled("nodeA", List.of(line("nodeA", t0), line("nodeA", t0.plusSeconds((2) * 60L))));
        NodeProducer b = filled("nodeB", List.of(line("nodeB", t0.plusSeconds((1) * 60L)), line("nodeB", t0.plusSeconds((3) * 60L))));

        List<LogLine> merged = new ArrayList<>();
        AtomicBoolean truncated = new AtomicBoolean(false);
        merger.merge(List.of(a, b), 100, merged::add, () -> truncated.set(true));

        assertThat(truncated).isFalse();
        assertThat(merged).extracting(LogLine::timestamp)
                .containsExactly(t0, t0.plusSeconds((1) * 60L), t0.plusSeconds((2) * 60L), t0.plusSeconds((3) * 60L));
    }

    @Test
    void stopsAtMaxResultsAndReportsTruncation() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        List<LogLine> lines = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            lines.add(line("nodeA", t0.plusSeconds((i) * 60L)));
        }
        NodeProducer a = filled("nodeA", lines);

        List<LogLine> merged = new ArrayList<>();
        AtomicBoolean truncated = new AtomicBoolean(false);
        merger.merge(List.of(a), 3, merged::add, () -> truncated.set(true));

        assertThat(truncated).isTrue();
        assertThat(merged).hasSize(3);
    }

    @Test
    void oneProducerFinishingEarlyDoesNotStallTheOthers() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        NodeProducer early = filled("early", List.of(line("early", t0)));
        NodeProducer later = filled("later", List.of(
                line("later", t0.plusSeconds((1) * 60L)), line("later", t0.plusSeconds((2) * 60L)), line("later", t0.plusSeconds((3) * 60L))));

        List<LogLine> merged = new ArrayList<>();
        merger.merge(List.of(early, later), 100, merged::add, () -> { });

        assertThat(merged).hasSize(4);
        assertThat(merged.get(0).nodeLabel()).isEqualTo("early");
    }

    @Test
    void nullTimestampEntriesSortAfterTimestampedOnes() throws InterruptedException {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        NodeProducer withNull = filled("nodeA", List.of(line("nodeA", null)));
        NodeProducer withTimestamp = filled("nodeB", List.of(line("nodeB", t0)));

        List<LogLine> merged = new ArrayList<>();
        merger.merge(List.of(withNull, withTimestamp), 100, merged::add, () -> { });

        assertThat(merged).extracting(LogLine::nodeLabel).containsExactly("nodeB", "nodeA");
    }

    @Test
    void concurrentProducersOnRealThreadsStillMergeInOrder() throws Exception {
        Instant t0 = Instant.parse("2026-01-01T00:00:00Z");
        // Deliberately small capacity so offer() actually blocks/hands off while merge() drains
        // concurrently on this thread - exercises the real backpressure path, not just a preloaded queue.
        NodeProducer a = new NodeProducer("nodeA", 2);
        NodeProducer b = new NodeProducer("nodeB", 2);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            executor.submit(() -> produce(a, "nodeA", t0, 0));
            executor.submit(() -> produce(b, "nodeB", t0, 1));

            List<LogLine> merged = new ArrayList<>();
            merger.merge(List.of(a, b), 100, merged::add, () -> { });

            assertThat(merged).hasSize(10);
            assertThat(merged).extracting(LogLine::timestamp).isSorted();
        }
    }

    private static void produce(NodeProducer producer, String label, Instant t0, int offsetParity) {
        try {
            for (int i = 0; i < 5; i++) {
                producer.offer(line(label, t0.plusSeconds((i * 2 + offsetParity) * 60L)));
            }
            producer.finish();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
