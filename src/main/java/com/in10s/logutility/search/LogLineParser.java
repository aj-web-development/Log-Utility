package com.in10s.logutility.search;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Parses the interesting leading tokens of a log line. Kept narrow (timestamp + level) so callers
 * that only need the timestamp fast-reject don't drag in more. An instance is built once per
 * project (with its formatter/regex pre-compiled) and reused across every line.
 */
public interface LogLineParser {

    /** The line's leading timestamp, or empty if it has none (e.g. a stack-trace continuation line). */
    Optional<LocalDateTime> timestamp(String line);

    /** The line's log level (e.g. {@code INFO}), or null if none could be identified. */
    String level(String line);
}
