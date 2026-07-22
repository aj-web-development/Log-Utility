package com.app.logutility.controller.search;

import com.app.logutility.config.search.SearchProperties;
import com.app.logutility.request.search.SearchRequest;
import com.app.logutility.response.search.LogLine;
import com.app.logutility.response.search.SearchProgress;
import com.app.logutility.response.search.SearchSummary;
import com.app.logutility.service.search.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SSE variant of {@link SearchApiController#search}: streams matches as ordered "chunk" events
 * instead of waiting for the whole (potentially large) result set, so the client sees progress
 * immediately and the server never has to hold more than one chunk per node in memory at once
 * (see {@code StreamingResultMerger}). Ends with one "done" event carrying the summary.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Public: streamed search over Server-Sent Events")
@SecurityRequirements
public class SearchStreamController {

    private static final Logger log = LoggerFactory.getLogger(SearchStreamController.class);
    private static final String FILTER_PARAM_PREFIX = "filter_";

    private final SearchService searchService;
    private final SearchProperties properties;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public SearchStreamController(SearchService searchService, SearchProperties properties) {
        this.searchService = searchService;
        this.properties = properties;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Search a project's logs over SSE",
            description = "Same parameters as POST /api/search (filters as filter_<key>=<value>); "
                    + "emits 'chunk' events of matched lines in order, then one 'done' event with the summary.")
    public SseEmitter stream(@RequestParam UUID projectId,
                             @RequestParam(required = false) String from,
                             @RequestParam(required = false) String to,
                             @RequestParam(required = false) String freeText,
                             @RequestParam Map<String, String> allParams) {
        SearchRequest request = buildRequest(projectId, from, to, freeText, allParams);

        SseEmitter emitter = new SseEmitter(properties.getSseTimeoutMillis());
        executor.execute(() -> runStream(request, emitter));
        return emitter;
    }

    @GetMapping("/export")
    @Operation(summary = "Download a project's matched log lines",
            description = "Same parameters as GET /stream; streams the matched lines as a plain-text .log "
                    + "attachment instead of holding the whole result set in memory.")
    public void export(@RequestParam UUID projectId,
                       @RequestParam(required = false) String from,
                       @RequestParam(required = false) String to,
                       @RequestParam(required = false) String freeText,
                       @RequestParam Map<String, String> allParams,
                       HttpServletResponse response) throws IOException {
        SearchRequest request = buildRequest(projectId, from, to, freeText, allParams);

        response.setContentType("text/plain;charset=UTF-8");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"search-results-" + LocalDate.now() + ".log\"");

        // Synchronous, unlike /stream: a plain streamed response has no "return immediately"
        // requirement, so there's no need to free the request thread onto a background executor.
        PrintWriter writer = response.getWriter();
        searchService.searchStreaming(request,
                chunk -> chunk.forEach(line -> writer.println(line.raw())),
                progress -> { },
                summary -> writer.flush());
    }

    private static SearchRequest buildRequest(UUID projectId, String from, String to, String freeText,
                                              Map<String, String> allParams) {
        Map<String, String> filters = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : allParams.entrySet()) {
            if (entry.getKey().startsWith(FILTER_PARAM_PREFIX) && StringUtils.hasText(entry.getValue())) {
                filters.put(entry.getKey().substring(FILTER_PARAM_PREFIX.length()), entry.getValue());
            }
        }
        return new SearchRequest(projectId, parseDateTime(from), parseDateTime(to), filters, freeText, 0, 0);
    }

    private void runStream(SearchRequest request, SseEmitter emitter) {
        try {
            searchService.searchStreaming(request,
                    (List<LogLine> chunk) -> sendQuietly(emitter, "chunk", chunk),
                    (SearchProgress progress) -> sendQuietly(emitter, "progress", progress),
                    (SearchSummary summary) -> sendQuietly(emitter, "done", summary));
            emitter.complete();
        } catch (Exception e) {
            log.warn("Streamed search failed: {}", e.toString());
            emitter.completeWithError(e);
        }
    }

    private static <T> void sendQuietly(SseEmitter emitter, String eventName, T data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            // the client went away mid-stream; nothing more to send
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
