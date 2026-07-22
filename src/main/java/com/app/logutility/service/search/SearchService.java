package com.app.logutility.service.search;

import com.app.logutility.request.search.SearchRequest;
import com.app.logutility.response.search.LogLine;
import com.app.logutility.response.search.SearchProgress;
import com.app.logutility.response.search.SearchResult;
import com.app.logutility.response.search.SearchSummary;

import java.util.List;
import java.util.function.Consumer;

/** Entry point to the search engine: one request in, one aggregated result out. */
public interface SearchService {

    SearchResult search(SearchRequest request);

    /**
     * Runs the same search but delivers matches as ordered chunks via {@code onChunk} (sized from
     * {@code search.chunk-size}) as they become available, instead of waiting for the whole capped
     * result set — the basis for the SSE streaming endpoint. {@code onProgress} fires once per node
     * as it finishes scanning (so a slow node doesn't leave the client sitting idle between chunks),
     * and {@code onComplete} runs exactly once, after the last chunk.
     */
    void searchStreaming(SearchRequest request, Consumer<List<LogLine>> onChunk,
                         Consumer<SearchProgress> onProgress, Consumer<SearchSummary> onComplete);
}
