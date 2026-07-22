package com.app.logutility.response.search;

import java.util.List;

/**
 * The outcome of a search.
 *
 * @param lines            the page of matched lines, timestamp-sorted ascending
 * @param totalMatched     total matches collected (equals the cap when {@code truncated})
 * @param truncated        true if the result cap was hit and scanning stopped early
 * @param unreachableNodes labels of nodes skipped because their paths could not be reached
 * @param elapsedMillis    wall-clock time the search took, for display
 */
public record SearchResult(
        List<LogLine> lines,
        long totalMatched,
        boolean truncated,
        List<String> unreachableNodes,
        long elapsedMillis) {
}
