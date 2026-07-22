package com.app.logutility.service.search;

import com.app.logutility.entity.project.FilterField;
import com.app.logutility.entity.project.LogFile;
import com.app.logutility.entity.project.LogSource;
import com.app.logutility.entity.project.MatchType;
import com.app.logutility.exception.search.SearchOverloadedException;
import com.app.logutility.service.search.ProjectSearchLoader.LoadedProject;
import com.app.logutility.service.validation.PathAvailabilityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.app.logutility.config.search.SearchProperties;
import com.app.logutility.dto.search.ScanPlan;
import com.app.logutility.request.search.SearchRequest;
import com.app.logutility.response.search.LogLine;
import com.app.logutility.response.search.SearchProgress;
import com.app.logutility.response.search.SearchResult;
import com.app.logutility.response.search.SearchSummary;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final ProjectSearchLoader loader;
    private final DatePruner datePruner;
    private final GlobFileResolver globFileResolver;
    private final LogSourceReaderFactory readerFactory;
    private final LogLineParserFactory parserFactory;
    private final StreamingResultMerger streamingResultMerger;
    private final PathAvailabilityChecker pathChecker;
    private final SearchProperties properties;
    private final Clock clock;
    private final Semaphore searchConcurrencyGate;
    private final Map<MatchType, FieldMatcher> matchers;

    public SearchServiceImpl(ProjectSearchLoader loader,
                             DatePruner datePruner,
                             GlobFileResolver globFileResolver,
                             LogSourceReaderFactory readerFactory,
                             LogLineParserFactory parserFactory,
                             StreamingResultMerger streamingResultMerger,
                             PathAvailabilityChecker pathChecker,
                             SearchProperties properties,
                             Clock searchClock,
                             Semaphore searchConcurrencyGate,
                             List<FieldMatcher> fieldMatchers) {
        this.loader = loader;
        this.datePruner = datePruner;
        this.globFileResolver = globFileResolver;
        this.readerFactory = readerFactory;
        this.parserFactory = parserFactory;
        this.streamingResultMerger = streamingResultMerger;
        this.pathChecker = pathChecker;
        this.properties = properties;
        this.clock = searchClock;
        this.searchConcurrencyGate = searchConcurrencyGate;
        this.matchers = fieldMatchers.stream().collect(Collectors.toMap(FieldMatcher::type, m -> m));
    }

    @Override
    public SearchResult search(SearchRequest request) {
        long startMillis = System.currentTimeMillis();
        SearchContext ctx = prepare(request);

        acquireGate();
        try {
            List<LogLine> merged = new ArrayList<>();
            ScanOutcome outcome = runSearch(ctx, merged::add, progress -> { });

            List<LogLine> page = paginate(merged, request.page(), request.pageSize());
            long elapsed = System.currentTimeMillis() - startMillis;
            return new SearchResult(page, outcome.totalMatched(), outcome.truncated(),
                    outcome.unreachableNodes(), elapsed);
        } finally {
            searchConcurrencyGate.release();
        }
    }

    @Override
    public void searchStreaming(SearchRequest request, Consumer<List<LogLine>> onChunk,
                                Consumer<SearchProgress> onProgress, Consumer<SearchSummary> onComplete) {
        long startMillis = System.currentTimeMillis();
        SearchContext ctx = prepare(request);
        int chunkSize = Math.max(1, properties.getChunkSize());

        acquireGate();
        try {
            List<LogLine> buffer = new ArrayList<>(chunkSize);
            ScanOutcome outcome = runSearch(ctx, line -> {
                buffer.add(line);
                if (buffer.size() >= chunkSize) {
                    onChunk.accept(new ArrayList<>(buffer));
                    buffer.clear();
                }
            }, onProgress);
            if (!buffer.isEmpty()) {
                onChunk.accept(new ArrayList<>(buffer));
            }

            long elapsed = System.currentTimeMillis() - startMillis;
            onComplete.accept(new SearchSummary(
                    outcome.totalMatched(), outcome.truncated(), outcome.unreachableNodes(), elapsed));
        } finally {
            searchConcurrencyGate.release();
        }
    }

    // ------------------------------------------------------------------ request setup

    /** Loads the project, resolves defaults, and validates the range — all before any I/O work starts. */
    private SearchContext prepare(SearchRequest request) {
        LoadedProject project = loader.load(request.projectId());

        LocalDateTime to = request.to() != null ? request.to() : LocalDateTime.now(clock);
        LocalDateTime from = request.from() != null ? request.from() : to.minusDays(1);
        validateRange(from, to);

        LogLineParser parser = parserFactory.create(project.linePattern());
        Predicate<String> lineMatches = buildPredicate(request, project.fields());
        int maxResults = Math.max(1, properties.getMaxResults());

        return new SearchContext(project, from, to, parser, lineMatches, maxResults);
    }

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("'from' must not be after 'to'");
        }
        int maxDays = Math.max(1, properties.getMaxDateRangeDays());
        long days = Duration.between(from, to).toDays();
        if (days > maxDays) {
            throw new IllegalArgumentException(
                    "Date range too large: %d day(s) requested, maximum is %d".formatted(days, maxDays));
        }
    }

    private void acquireGate() {
        if (!searchConcurrencyGate.tryAcquire()) {
            throw new SearchOverloadedException(
                    "The server is busy handling other searches right now; please retry shortly.");
        }
    }

    private record SearchContext(LoadedProject project, LocalDateTime from, LocalDateTime to,
                                 LogLineParser parser, Predicate<String> lineMatches, int maxResults) {
    }

    // ------------------------------------------------------------------ fan-out + streaming merge

    /**
     * Runs one virtual thread per (node, log file) pair, streams every unit's matches through a
     * bounded {@link StreamingResultMerger} so memory stays proportional to
     * {@code scanUnitCount × chunkSize} rather than to the total match count, and calls
     * {@code onResult} once per merged, in-order entry. If the global {@code maxResults} cap is
     * hit, remaining tasks are cancelled (interrupting any of them parked mid-{@code put} on a
     * full queue) instead of left to finish.
     */
    private ScanOutcome runSearch(SearchContext ctx, Consumer<LogLine> onResult, Consumer<SearchProgress> onProgress) {
        ScanState state = new ScanState();
        Semaphore ioGate = new Semaphore(Math.max(1, properties.getMaxNodesParallel()));
        List<NodeProducer> producers = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        AtomicLong emitted = new AtomicLong();
        int scanUnitsTotal = ctx.project().nodes().stream().mapToInt(n -> n.getLogFiles().size()).sum();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (LogSource node : ctx.project().nodes()) {
                for (LogFile file : node.getLogFiles()) {
                    NodeProducer producer = new NodeProducer(scanLabel(node, file), properties.getChunkSize());
                    producers.add(producer);
                    futures.add(executor.submit(() ->
                            scanLogFile(node, file, ctx, ioGate, producer, state, scanUnitsTotal, onProgress)));
                }
            }

            streamingResultMerger.merge(producers, ctx.maxResults(), line -> {
                emitted.incrementAndGet();
                onResult.accept(line);
            }, () -> state.truncated.set(true));

            if (state.truncated.get()) {
                futures.forEach(f -> f.cancel(true));
            }
            futures.forEach(SearchServiceImpl::joinQuietly);
        }

        List<String> unreachable = new ArrayList<>(state.unreachableNodes);
        Collections.sort(unreachable);
        return new ScanOutcome(emitted.get(), state.truncated.get(), unreachable);
    }

    private void scanLogFile(LogSource node, LogFile file, SearchContext ctx, Semaphore ioGate,
                             NodeProducer producer, ScanState state, int scanUnitsTotal,
                             Consumer<SearchProgress> onProgress) {
        ioGate.acquireUninterruptibly();
        try {
            boolean liveReachable = StringUtils.hasText(file.getLiveLogPath())
                    && pathChecker.check(file.getLiveLogPath()).reachable();
            boolean backupReachable = StringUtils.hasText(file.getBackupRootPath())
                    && pathChecker.check(file.getBackupRootPath()).reachable();

            if (!liveReachable && !backupReachable) {
                state.unreachableNodes.add(scanLabel(node, file));
                return;
            }

            for (Path path : resolveFiles(file, ctx.from(), ctx.to(), liveReachable, backupReachable)) {
                if (state.truncated.get()) {
                    return;
                }
                scanFile(node.getNodeLabel(), file.getFileLabel(), path, ctx, state, producer);
            }
        } catch (RuntimeException e) {
            log.warn("Scan failed for {}: {}", scanLabel(node, file), e.toString());
        } finally {
            finishQuietly(producer);
            ioGate.release();
            onProgress.accept(new SearchProgress(
                    scanLabel(node, file), state.nodesCompleted.incrementAndGet(), scanUnitsTotal));
        }
    }

    private static String scanLabel(LogSource node, LogFile file) {
        return StringUtils.hasText(file.getFileLabel())
                ? node.getNodeLabel() + " · " + file.getFileLabel()
                : node.getNodeLabel();
    }

    /** Backups first (oldest→newest by name), then the live file last, so results run chronologically. */
    private List<Path> resolveFiles(LogFile file, LocalDateTime from, LocalDateTime to,
                                    boolean liveReachable, boolean backupReachable) {
        List<Path> files = new ArrayList<>();
        ScanPlan plan = datePruner.plan(file.getBackupRootPath(), file.getBackupPathPattern(), from, to);

        if (backupReachable && !plan.backupGlobs().isEmpty()) {
            Path base = Path.of(file.getBackupRootPath());
            for (String glob : plan.backupGlobs()) {
                files.addAll(globFileResolver.resolve(base, glob));
            }
            files.sort(Comparator.comparing(Path::toString));
        }
        if (plan.includeLive() && liveReachable) {
            Path live = Path.of(file.getLiveLogPath());
            if (Files.isRegularFile(live)) {
                files.add(live);
            }
        }
        return files;
    }

    private void scanFile(String nodeLabel, String fileLabel, Path path, SearchContext ctx,
                          ScanState state, NodeProducer producer) {
        Optional<LogSourceReader> reader = readerFactory.readerFor(path);
        if (reader.isEmpty()) {
            return;
        }
        try (Stream<String> lines = reader.get().readLines(path)) {
            LogEntryAssembler assembler = new LogEntryAssembler(
                    lines.iterator(), ctx.parser(), properties.getMaxContinuationLines());
            while (assembler.hasNext()) {
                if (state.truncated.get()) {
                    return;
                }
                LogEntryAssembler.RawEntry entry = assembler.next();

                // Fast-reject: an out-of-range entry is dropped whole, without running the (more
                // expensive) field/text matcher over its (possibly multi-line) body.
                LocalDateTime ts = entry.timestamp();
                if (ts != null && (ts.isBefore(ctx.from()) || ts.isAfter(ctx.to()))) {
                    continue;
                }
                if (!ctx.lineMatches().test(entry.raw())) {
                    continue;
                }
                producer.offer(new LogLine(nodeLabel, fileLabel, ts, entry.level(), entry.raw()));
            }
        } catch (IOException e) {
            log.warn("Could not read {}: {}", path, e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ predicate + paging

    private Predicate<String> buildPredicate(SearchRequest request, List<FilterField> fields) {
        List<Predicate<String>> predicates = new ArrayList<>();
        Map<String, FilterField> byKey = fields.stream()
                .collect(Collectors.toMap(FilterField::getKey, f -> f, (a, b) -> a));

        if (request.filters() != null) {
            for (Map.Entry<String, String> entry : request.filters().entrySet()) {
                if (!StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                FilterField field = byKey.get(entry.getKey());
                FieldMatcher matcher = field == null ? null : matchers.get(field.getMatchType());
                if (matcher == null) {
                    continue;
                }
                try {
                    predicates.add(matcher.build(field, entry.getValue().trim()));
                } catch (RuntimeException e) {
                    predicates.add(line -> false); // e.g. an invalid user regex matches nothing
                }
            }
        }

        if (StringUtils.hasText(request.freeText())) {
            String needle = request.freeText().trim().toLowerCase(Locale.ROOT);
            predicates.add(line -> line.toLowerCase(Locale.ROOT).contains(needle));
        }

        if (predicates.isEmpty()) {
            return line -> true;
        }
        return predicates.stream().reduce(line -> true, Predicate::and);
    }

    private static List<LogLine> paginate(List<LogLine> all, int page, int pageSize) {
        if (pageSize <= 0) {
            return all;
        }
        int fromIndex = Math.max(0, page) * pageSize;
        if (fromIndex >= all.size()) {
            return List.of();
        }
        int toIndex = Math.min(all.size(), fromIndex + pageSize);
        return new ArrayList<>(all.subList(fromIndex, toIndex));
    }

    private static void joinQuietly(Future<?> future) {
        try {
            future.get();
        } catch (Exception e) {
            log.warn("Node scan task failed: {}", e.toString());
        }
    }

    private static void finishQuietly(NodeProducer producer) {
        try {
            producer.finish();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Shared, thread-safe bookkeeping for one search across all node tasks. */
    private static final class ScanState {
        private final AtomicBoolean truncated = new AtomicBoolean(false);
        private final AtomicInteger nodesCompleted = new AtomicInteger();
        private final List<String> unreachableNodes = Collections.synchronizedList(new ArrayList<>());
    }

    private record ScanOutcome(long totalMatched, boolean truncated, List<String> unreachableNodes) {
    }
}
