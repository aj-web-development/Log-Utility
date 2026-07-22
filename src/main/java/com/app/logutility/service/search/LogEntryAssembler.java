package com.app.logutility.service.search;

import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Groups physical lines into log entries: a line that matches the project's configured
 * {@link LogLineParser#timestamp(String)} starts a new entry, and every following line that has no
 * timestamp is a continuation of that entry (e.g. a stack trace, a wrapped message, pretty-printed
 * JSON). The boundary is driven entirely by the project's own line pattern, not any hardcoded
 * "looks like a trace" heuristic, so it works for any log format.
 *
 * <p>A continuation run is capped at {@code maxContinuationLines} so a project whose line pattern
 * matches nothing can't buffer an entire file into one unbounded entry — once hit, the entry is
 * flushed early and further continuation lines start a new, timestamp-less entry.
 */
public final class LogEntryAssembler implements Iterator<LogEntryAssembler.RawEntry> {

    public record RawEntry(LocalDateTime timestamp, String level, String raw) {
    }

    private final Iterator<String> lines;
    private final LogLineParser parser;
    private final int maxContinuationLines;

    private RawEntry next;
    private LocalDateTime pendingTimestamp;
    private String pendingLevel;
    private StringBuilder pendingRaw;
    private int pendingContinuationCount;

    public LogEntryAssembler(Iterator<String> lines, LogLineParser parser, int maxContinuationLines) {
        this.lines = lines;
        this.parser = parser;
        this.maxContinuationLines = Math.max(1, maxContinuationLines);
    }

    @Override
    public boolean hasNext() {
        if (next != null) {
            return true;
        }
        next = advance();
        return next != null;
    }

    @Override
    public RawEntry next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        RawEntry result = next;
        next = null;
        return result;
    }

    private RawEntry advance() {
        while (lines.hasNext()) {
            String line = lines.next();
            Optional<LocalDateTime> timestamp = parser.timestamp(line);

            if (timestamp.isPresent()) {
                RawEntry flushed = flushPending();
                startEntry(timestamp.get(), parser.level(line), line);
                if (flushed != null) {
                    return flushed;
                }
                continue;
            }

            if (pendingRaw == null) {
                // A continuation line before any timestamped line has appeared (e.g. a truncated
                // or mid-rotation file) — still surface it rather than dropping it.
                startEntry(null, null, line);
                continue;
            }

            pendingRaw.append('\n').append(line);
            pendingContinuationCount++;
            if (pendingContinuationCount >= maxContinuationLines) {
                RawEntry flushed = flushPending();
                if (flushed != null) {
                    return flushed;
                }
            }
        }
        return flushPending();
    }

    private void startEntry(LocalDateTime timestamp, String level, String firstLine) {
        pendingTimestamp = timestamp;
        pendingLevel = level;
        pendingRaw = new StringBuilder(firstLine);
        pendingContinuationCount = 0;
    }

    private RawEntry flushPending() {
        if (pendingRaw == null) {
            return null;
        }
        RawEntry entry = new RawEntry(pendingTimestamp, pendingLevel, pendingRaw.toString());
        pendingRaw = null;
        pendingTimestamp = null;
        pendingLevel = null;
        pendingContinuationCount = 0;
        return entry;
    }
}
