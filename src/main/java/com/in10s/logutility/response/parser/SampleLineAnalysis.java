package com.in10s.logutility.response.parser;

import java.util.List;

/**
 * Best-effort analysis of one pasted sample log line. Any of the token matches may be null when
 * nothing plausible was found; the suggested patterns are still populated with sensible defaults
 * where possible so the admin has something to confirm or correct rather than a blank field.
 *
 * @param timestamp                  the detected timestamp token, or null
 * @param suggestedTimestampPattern  a {@link java.time.format.DateTimeFormatter} pattern inferred
 *                                   from the timestamp token, or null if none was detected
 * @param suggestedTimestampRegex    a regex that locates the same timestamp shape in a line, or null
 * @param level                      the detected level token, or null
 * @param suggestedLevelPattern      a regex matching the standard level keywords (always populated
 *                                   — it is a fixed, generically useful default)
 * @param logger                    the detected logger token, or null
 * @param suggestedLoggerPattern     a regex matching a dotted logger/class name, or null if none detected
 * @param segments                   the whole line split into plain/highlighted pieces, in order,
 *                                   for rendering the preview
 */
public record SampleLineAnalysis(
        TokenMatch timestamp,
        String suggestedTimestampPattern,
        String suggestedTimestampRegex,
        TokenMatch level,
        String suggestedLevelPattern,
        TokenMatch logger,
        String suggestedLoggerPattern,
        List<HighlightSegment> segments) {
}
