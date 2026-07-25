package com.app.logutility.response.search;

import java.time.Instant;

/**
 * One matched log line, tagged with the node and labeled output (e.g. "Application", "Error") it
 * came from. {@code timestamp} is the line's parsed wall-clock digits converted through the
 * project's configured zone to an absolute instant (so multi-project results and the frontend's
 * display conversion are unambiguous); it and {@code level} are best-effort parses (null when the
 * line could not be parsed); {@code raw} is always the full original line (or, once a multi-line
 * entry is assembled, the full joined entry).
 */
public record LogLine(String nodeLabel, String fileLabel, Instant timestamp, String level, String raw) {
}
