package com.app.logutility.service.search;

import com.app.logutility.response.search.LogLine;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * One node's side of the producer/consumer handoff into {@link StreamingResultMerger}: the node's
 * scanning thread {@link #offer(LogLine)}s matched entries into a small bounded queue (backpressure
 * — the scanning thread parks when it's full, keeping memory bounded regardless of how many total
 * matches exist) and calls {@link #finish()} exactly once when done, however it exits (normally,
 * on error, or on cancellation). The merge coordinator drains it with {@link #take()}.
 */
final class NodeProducer {

    private static final LogLine POISON = new LogLine(null, null, null, null, null);

    private final String nodeLabel;
    private final BlockingQueue<LogLine> queue;

    NodeProducer(String nodeLabel, int capacity) {
        this.nodeLabel = nodeLabel;
        this.queue = new ArrayBlockingQueue<>(Math.max(1, capacity));
    }

    String nodeLabel() {
        return nodeLabel;
    }

    /** Blocks (parking the calling virtual thread) while the buffer is full. */
    void offer(LogLine line) throws InterruptedException {
        queue.put(line);
    }

    /** Must be called exactly once by the producer, in a {@code finally}, regardless of outcome. */
    void finish() throws InterruptedException {
        queue.put(POISON);
    }

    /** Blocks until the next item is ready, or returns empty once {@link #finish()} was observed. */
    Optional<LogLine> take() throws InterruptedException {
        LogLine line = queue.take();
        return line == POISON ? Optional.empty() : Optional.of(line);
    }
}
