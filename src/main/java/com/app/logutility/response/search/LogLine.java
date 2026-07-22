package com.app.logutility.response.search;

import java.time.LocalDateTime;

/**
 * One matched log line, tagged with the node and labeled output (e.g. "Application", "Error") it
 * came from. {@code timestamp} and {@code level} are best-effort parses (null when the line could
 * not be parsed); {@code raw} is always the full original line (or, once a multi-line entry is
 * assembled, the full joined entry).
 */
public record LogLine(String nodeLabel, String fileLabel, LocalDateTime timestamp, String level, String raw) {
}
