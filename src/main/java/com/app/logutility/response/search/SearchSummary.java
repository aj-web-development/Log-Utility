package com.app.logutility.response.search;

import java.util.List;

/**
 * The tail-end summary of a streamed search — the same bookkeeping fields as {@link SearchResult}
 * without a page of lines, since a streaming consumer already received every matched line as chunks.
 *
 * @param totalMatched     total matches emitted (equals the cap when {@code truncated})
 * @param truncated        true if the result cap was hit and scanning stopped early
 * @param unreachableNodes labels of nodes skipped because their paths could not be reached
 * @param elapsedMillis    wall-clock time the search took, for display
 */
public record SearchSummary(
        long totalMatched,
        boolean truncated,
        List<String> unreachableNodes,
        long elapsedMillis) {
}
