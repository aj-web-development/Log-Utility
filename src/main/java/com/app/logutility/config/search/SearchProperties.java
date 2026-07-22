package com.app.logutility.config.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tuning knobs for the search engine, bound from {@code search.*} in application.yml. */
@ConfigurationProperties(prefix = "search")
@Getter
@Setter
public class SearchProperties {

    /** Hard cap on the number of matched lines returned; scanning stops once it is reached. */
    private int maxResults = 5000;

    /** Upper bound on how many (node, log file) scans run concurrently (bounds real filesystem I/O). */
    private int maxNodesParallel = 16;

    /** Continuation lines (no timestamp) folded into one entry before it is force-flushed. */
    private int maxContinuationLines = 300;

    /** Largest from/to span a single search may cover. */
    private int maxDateRangeDays = 30;

    /** Upper bound on searches running at once across all users; extra requests are rejected. */
    private int maxConcurrentSearches = 8;

    /** Number of matched lines batched per SSE "chunk" event. */
    private int chunkSize = 200;

    /** How long an SSE connection may stay open before it's forced closed. */
    private long sseTimeoutMillis = 60_000;
}
