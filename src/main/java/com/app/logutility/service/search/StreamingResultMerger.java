package com.app.logutility.service.search;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.Consumer;
import com.app.logutility.response.search.LogLine;

/**
 * Merges N already-chronological {@link NodeProducer} streams into one globally timestamp-ordered
 * stream, one entry at a time, without ever materializing more than one buffered item per node —
 * a classic bounded k-way merge. This relies on each node producing its matches in ascending
 * timestamp order already (true today: backups oldest-to-newest, live file last, lines within a
 * file chronological — see {@code SearchServiceImpl.resolveFiles}).
 *
 * <p>Entries with no timestamp (rare once {@link LogEntryAssembler} folds continuation lines into
 * their parent entry — e.g. a file that opens mid-continuation) sort after every timestamped entry
 * from the other producers, approximating the old flat "nulls last" ordering without requiring the
 * whole result set to be buffered first.
 */
@Component
public class StreamingResultMerger {

    /**
     * Emits merged entries to {@code onResult} until every producer is exhausted or
     * {@code maxResults} entries have been emitted, in which case {@code onTruncated} runs and the
     * merge stops immediately (remaining producers are left for the caller to cancel).
     */
    public void merge(List<NodeProducer> producers, int maxResults,
                      Consumer<LogLine> onResult, Runnable onTruncated) {
        PriorityQueue<Peeked> heap = new PriorityQueue<>(
                Math.max(1, producers.size()), java.util.Comparator.comparing(Peeked::sortKey));

        for (NodeProducer producer : producers) {
            peekInto(producer, heap);
        }

        int emitted = 0;
        while (!heap.isEmpty()) {
            Peeked head = heap.poll();
            onResult.accept(head.line());
            emitted++;
            if (emitted >= maxResults) {
                onTruncated.run();
                return;
            }
            peekInto(head.producer(), heap);
        }
    }

    private void peekInto(NodeProducer producer, PriorityQueue<Peeked> heap) {
        try {
            producer.take().ifPresent(line -> heap.add(new Peeked(producer, line)));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private record Peeked(NodeProducer producer, LogLine line) {
        LocalDateTime sortKey() {
            return line.timestamp() == null ? LocalDateTime.MAX : line.timestamp();
        }
    }
}
