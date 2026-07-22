package com.in10s.logutility.response.search;

import java.time.LocalDateTime;

/**
 * One matched log line, tagged with the node it came from. {@code timestamp} and {@code level}
 * are best-effort parses (null when the line could not be parsed); {@code raw} is always the
 * full original line.
 */
public record LogLine(String nodeLabel, LocalDateTime timestamp, String level, String raw) {
}
