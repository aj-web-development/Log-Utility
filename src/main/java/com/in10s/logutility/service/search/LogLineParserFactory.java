package com.in10s.logutility.service.search;

import com.in10s.logutility.entity.project.LinePattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds a per-project {@link LogLineParser} with its formatter(s) and level regex compiled once.
 * When a project has no configured {@code timestampPattern} yet (the sample-line step lands in
 * Phase 8), a set of common fallback formats keeps date-filtering useful out of the box.
 */
@Component
public class LogLineParserFactory {

    private static final List<String> FALLBACK_PATTERNS = List.of(
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss,SSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm:ss");

    private static final Pattern DEFAULT_LEVEL_PATTERN =
            Pattern.compile("\\b(TRACE|DEBUG|INFO|WARN|WARNING|ERROR|FATAL)\\b");

    public LogLineParser create(LinePattern linePattern) {
        return new DefaultLogLineParser(buildFormatters(linePattern), buildLevelPattern(linePattern));
    }

    private static List<DateTimeFormatter> buildFormatters(LinePattern linePattern) {
        List<DateTimeFormatter> formatters = new ArrayList<>();
        if (linePattern != null && StringUtils.hasText(linePattern.getTimestampPattern())) {
            try {
                formatters.add(DateTimeFormatter.ofPattern(linePattern.getTimestampPattern().trim()));
            } catch (IllegalArgumentException ignored) {
                // Misconfigured pattern — fall back to the common defaults below.
            }
        }
        if (formatters.isEmpty()) {
            for (String pattern : FALLBACK_PATTERNS) {
                formatters.add(DateTimeFormatter.ofPattern(pattern));
            }
        }
        return formatters;
    }

    private static Pattern buildLevelPattern(LinePattern linePattern) {
        if (linePattern != null && StringUtils.hasText(linePattern.getLevelPattern())) {
            try {
                return Pattern.compile(linePattern.getLevelPattern().trim());
            } catch (RuntimeException ignored) {
                // Fall through to the default keyword scan.
            }
        }
        return DEFAULT_LEVEL_PATTERN;
    }
}
