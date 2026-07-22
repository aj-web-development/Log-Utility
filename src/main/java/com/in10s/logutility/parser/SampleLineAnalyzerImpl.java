package com.in10s.logutility.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class SampleLineAnalyzerImpl implements SampleLineAnalyzer {

    // yyyy-MM-dd, then a space or 'T', then HH:mm:ss, then an optional .SSS / ,SSS fraction.
    private static final Pattern TIMESTAMP = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})([ T])(\\d{2}:\\d{2}:\\d{2})(?:([.,])(\\d{1,9}))?");

    private static final Pattern LEVEL =
            Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b", Pattern.CASE_INSENSITIVE);

    // A dotted identifier, e.g. com.acme.service.OrderService or c.a.s.OrderService.
    private static final String LOGGER_REGEX = "\\b[a-zA-Z_$][\\w$]*(?:\\.[a-zA-Z_$][\\w$]*)+\\b";
    private static final Pattern LOGGER = Pattern.compile(LOGGER_REGEX);

    /** Same default the search engine's LogLineParserFactory falls back to — always a useful suggestion. */
    private static final String DEFAULT_LEVEL_PATTERN = "\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b";

    @Override
    public SampleLineAnalysis analyze(String sampleLine) {
        String line = sampleLine == null ? "" : sampleLine;

        TokenMatch timestamp = findTimestamp(line);
        int afterTimestamp = timestamp != null ? timestamp.end() : 0;

        TokenMatch level = find(LEVEL, line, afterTimestamp);
        int afterLevel = level != null ? level.end() : afterTimestamp;

        TokenMatch logger = find(LOGGER, line, afterLevel);

        String timestampPattern = timestamp != null ? inferTimestampPattern(line, timestamp) : null;
        String timestampRegex = timestamp != null ? TIMESTAMP.pattern() : null;
        String loggerPattern = logger != null ? LOGGER_REGEX : null;

        List<HighlightSegment> segments = buildSegments(line, timestamp, level, logger);

        return new SampleLineAnalysis(
                timestamp, timestampPattern, timestampRegex,
                level, DEFAULT_LEVEL_PATTERN,
                logger, loggerPattern,
                segments);
    }

    private static TokenMatch findTimestamp(String line) {
        Matcher matcher = TIMESTAMP.matcher(line);
        if (!matcher.find()) {
            return null;
        }
        return new TokenMatch(matcher.group(), matcher.start(), matcher.end());
    }

    private static TokenMatch find(Pattern pattern, String line, int fromIndex) {
        Matcher matcher = pattern.matcher(line);
        if (!matcher.find(Math.min(fromIndex, line.length()))) {
            return null;
        }
        return new TokenMatch(matcher.group(), matcher.start(), matcher.end());
    }

    private static String inferTimestampPattern(String line, TokenMatch timestamp) {
        Matcher matcher = TIMESTAMP.matcher(timestamp.text());
        if (!matcher.matches()) {
            return null; // defensive — the same regex just matched this exact substring
        }
        boolean literalT = "T".equals(matcher.group(2));
        String fractionSeparator = matcher.group(4);
        int fractionLength = matcher.group(5) != null ? matcher.group(5).length() : 0;

        StringBuilder pattern = new StringBuilder("yyyy-MM-dd");
        pattern.append(literalT ? "'T'" : " ");
        pattern.append("HH:mm:ss");
        if (fractionLength > 0) {
            pattern.append(fractionSeparator).append("S".repeat(fractionLength));
        }
        return pattern.toString();
    }

    /** Sorted, non-overlapping candidates (timestamp > level > logger priority) turned into segments. */
    private static List<HighlightSegment> buildSegments(String line, TokenMatch timestamp, TokenMatch level,
                                                         TokenMatch logger) {
        List<TokenMatch> candidates = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        addIfNoOverlap(candidates, labels, timestamp, "timestamp");
        addIfNoOverlap(candidates, labels, level, "level");
        addIfNoOverlap(candidates, labels, logger, "logger");

        List<int[]> order = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            order.add(new int[]{i});
        }
        order.sort(Comparator.comparingInt(o -> candidates.get(o[0]).start()));

        List<HighlightSegment> segments = new ArrayList<>();
        int cursor = 0;
        for (int[] o : order) {
            TokenMatch match = candidates.get(o[0]);
            String label = labels.get(o[0]);
            if (match.start() > cursor) {
                segments.add(new HighlightSegment(line.substring(cursor, match.start()), null));
            }
            segments.add(new HighlightSegment(line.substring(match.start(), match.end()), label));
            cursor = match.end();
        }
        if (cursor < line.length()) {
            segments.add(new HighlightSegment(line.substring(cursor), null));
        }
        if (segments.isEmpty() && !line.isEmpty()) {
            segments.add(new HighlightSegment(line, null));
        }
        return segments;
    }

    private static void addIfNoOverlap(List<TokenMatch> kept, List<String> labels, TokenMatch candidate, String label) {
        if (candidate == null) {
            return;
        }
        for (TokenMatch existing : kept) {
            if (candidate.start() < existing.end() && existing.start() < candidate.end()) {
                return; // overlaps an already-kept, higher-priority match — drop this one
            }
        }
        kept.add(candidate);
        labels.add(label);
    }
}
