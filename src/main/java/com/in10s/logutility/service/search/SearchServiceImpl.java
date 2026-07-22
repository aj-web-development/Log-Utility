package com.in10s.logutility.service.search;

import com.in10s.logutility.entity.project.FilterField;
import com.in10s.logutility.entity.project.LogSource;
import com.in10s.logutility.entity.project.MatchType;
import com.in10s.logutility.service.search.ProjectSearchLoader.LoadedProject;
import com.in10s.logutility.service.validation.PathAvailabilityChecker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import com.in10s.logutility.config.search.SearchProperties;
import com.in10s.logutility.dto.search.ScanPlan;
import com.in10s.logutility.request.search.SearchRequest;
import com.in10s.logutility.response.search.LogLine;
import com.in10s.logutility.response.search.SearchResult;

@Service
public class SearchServiceImpl implements SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchServiceImpl.class);

    private final ProjectSearchLoader loader;
    private final DatePruner datePruner;
    private final GlobFileResolver globFileResolver;
    private final LogSourceReaderFactory readerFactory;
    private final LogLineParserFactory parserFactory;
    private final ResultMerger resultMerger;
    private final PathAvailabilityChecker pathChecker;
    private final SearchProperties properties;
    private final Clock clock;
    private final Map<MatchType, FieldMatcher> matchers;

    public SearchServiceImpl(ProjectSearchLoader loader,
                             DatePruner datePruner,
                             GlobFileResolver globFileResolver,
                             LogSourceReaderFactory readerFactory,
                             LogLineParserFactory parserFactory,
                             ResultMerger resultMerger,
                             PathAvailabilityChecker pathChecker,
                             SearchProperties properties,
                             Clock searchClock,
                             List<FieldMatcher> fieldMatchers) {
        this.loader = loader;
        this.datePruner = datePruner;
        this.globFileResolver = globFileResolver;
        this.readerFactory = readerFactory;
        this.parserFactory = parserFactory;
        this.resultMerger = resultMerger;
        this.pathChecker = pathChecker;
        this.properties = properties;
        this.clock = searchClock;
        this.matchers = fieldMatchers.stream().collect(Collectors.toMap(FieldMatcher::type, m -> m));
    }

    @Override
    public SearchResult search(SearchRequest request) {
        long startMillis = System.currentTimeMillis();
        LoadedProject project = loader.load(request.projectId());

        LocalDateTime to = request.to() != null ? request.to() : LocalDateTime.now(clock);
        LocalDateTime from = request.from() != null ? request.from() : to.minusDays(1);

        LogLineParser parser = parserFactory.create(project.linePattern());
        Predicate<String> lineMatches = buildPredicate(request, project.fields());

        int maxResults = Math.max(1, properties.getMaxResults());
        ScanState state = new ScanState(maxResults);
        Semaphore gate = new Semaphore(Math.max(1, properties.getMaxNodesParallel()));

        // One virtual thread per node; the semaphore bounds concurrent filesystem I/O.
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (LogSource node : project.nodes()) {
                futures.add(executor.submit(() ->
                        scanNode(node, from, to, parser, lineMatches, gate, state)));
            }
            for (Future<?> future : futures) {
                joinQuietly(future);
            }
        }

        List<LogLine> merged = resultMerger.mergeSorted(new ArrayList<>(state.collected));
        if (merged.size() > maxResults) {
            merged = merged.subList(0, maxResults);
            state.truncated.set(true);
        }
        long totalMatched = merged.size();
        List<LogLine> page = paginate(merged, request.page(), request.pageSize());
        long elapsed = System.currentTimeMillis() - startMillis;

        List<String> unreachable = new ArrayList<>(state.unreachableNodes);
        Collections.sort(unreachable);
        return new SearchResult(page, totalMatched, state.truncated.get(), unreachable, elapsed);
    }

    // ------------------------------------------------------------------ per-node scan

    private void scanNode(LogSource node, LocalDateTime from, LocalDateTime to,
                          LogLineParser parser, Predicate<String> lineMatches,
                          Semaphore gate, ScanState state) {
        gate.acquireUninterruptibly();
        try {
            boolean liveReachable = StringUtils.hasText(node.getLiveLogPath())
                    && pathChecker.check(node.getLiveLogPath()).reachable();
            boolean backupReachable = StringUtils.hasText(node.getBackupRootPath())
                    && pathChecker.check(node.getBackupRootPath()).reachable();

            if (!liveReachable && !backupReachable) {
                state.unreachableNodes.add(node.getNodeLabel());
                return;
            }

            for (Path file : resolveFiles(node, from, to, liveReachable, backupReachable)) {
                if (state.truncated.get()) {
                    return;
                }
                scanFile(node.getNodeLabel(), file, from, to, parser, lineMatches, state);
            }
        } catch (RuntimeException e) {
            log.warn("Scan failed for node {}: {}", node.getNodeLabel(), e.toString());
        } finally {
            gate.release();
        }
    }

    /** Backups first (oldest→newest by name), then the live file last, so results run chronologically. */
    private List<Path> resolveFiles(LogSource node, LocalDateTime from, LocalDateTime to,
                                    boolean liveReachable, boolean backupReachable) {
        List<Path> files = new ArrayList<>();
        ScanPlan plan = datePruner.plan(node.getBackupRootPath(), node.getBackupPathPattern(), from, to);

        if (backupReachable && !plan.backupGlobs().isEmpty()) {
            Path base = Path.of(node.getBackupRootPath());
            for (String glob : plan.backupGlobs()) {
                files.addAll(globFileResolver.resolve(base, glob));
            }
            files.sort(Comparator.comparing(Path::toString));
        }
        if (plan.includeLive() && liveReachable) {
            Path live = Path.of(node.getLiveLogPath());
            if (Files.isRegularFile(live)) {
                files.add(live);
            }
        }
        return files;
    }

    private void scanFile(String nodeLabel, Path file, LocalDateTime from, LocalDateTime to,
                          LogLineParser parser, Predicate<String> lineMatches, ScanState state) {
        Optional<LogSourceReader> reader = readerFactory.readerFor(file);
        if (reader.isEmpty()) {
            return;
        }
        try (Stream<String> lines = reader.get().readLines(file)) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                if (state.truncated.get()) {
                    return;
                }
                String line = iterator.next();

                // Fast-reject: parse only the leading timestamp and drop out-of-range lines
                // before running the (more expensive) field/text matchers.
                Optional<LocalDateTime> timestamp = parser.timestamp(line);
                if (timestamp.isPresent()) {
                    LocalDateTime ts = timestamp.get();
                    if (ts.isBefore(from) || ts.isAfter(to)) {
                        continue;
                    }
                }
                if (!lineMatches.test(line)) {
                    continue;
                }

                int position = state.count.incrementAndGet();
                if (position > state.maxResults) {
                    state.truncated.set(true);
                    return;
                }
                state.collected.add(new LogLine(nodeLabel, timestamp.orElse(null), parser.level(line), line));
            }
        } catch (IOException e) {
            log.warn("Could not read {}: {}", file, e.toString());
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

    /** Shared, thread-safe accumulation state for one search across all node tasks. */
    private static final class ScanState {
        private final int maxResults;
        private final ConcurrentLinkedQueue<LogLine> collected = new ConcurrentLinkedQueue<>();
        private final AtomicInteger count = new AtomicInteger();
        private final AtomicBoolean truncated = new AtomicBoolean(false);
        private final List<String> unreachableNodes = Collections.synchronizedList(new ArrayList<>());

        private ScanState(int maxResults) {
            this.maxResults = maxResults;
        }
    }
}
