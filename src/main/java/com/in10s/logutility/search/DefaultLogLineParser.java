package com.in10s.logutility.search;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.time.temporal.TemporalAccessor;
import java.text.ParsePosition;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Default {@link LogLineParser}. Timestamp extraction uses {@link DateTimeFormatter#parseUnresolved}
 * from index 0, which looks only at the leading timestamp substring and never throws — so the
 * fast-reject stays cheap even for the many lines it must skip. Zone/offset tokens are parsed but
 * ignored: the wall-clock local date-time as written in the log is what is compared against the
 * (also wall-clock) search range.
 */
class DefaultLogLineParser implements LogLineParser {

    private final List<DateTimeFormatter> formatters;
    private final Pattern levelPattern;

    DefaultLogLineParser(List<DateTimeFormatter> formatters, Pattern levelPattern) {
        this.formatters = formatters;
        this.levelPattern = levelPattern;
    }

    @Override
    public Optional<LocalDateTime> timestamp(String line) {
        for (DateTimeFormatter formatter : formatters) {
            TemporalAccessor parsed = formatter.parseUnresolved(line, new ParsePosition(0));
            if (parsed == null) {
                continue;
            }
            LocalDateTime dateTime = toLocalDateTime(parsed);
            if (dateTime != null) {
                return Optional.of(dateTime);
            }
        }
        return Optional.empty();
    }

    @Override
    public String level(String line) {
        Matcher matcher = levelPattern.matcher(line);
        if (matcher.find()) {
            return matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        }
        return null;
    }

    private static LocalDateTime toLocalDateTime(TemporalAccessor parsed) {
        // parseUnresolved does not run resolvers, so the common "yyyy" pattern lands in
        // YEAR_OF_ERA (proleptic YEAR only comes from "uuuu"); accept either as the year.
        Integer year = field(parsed, ChronoField.YEAR);
        if (year == null) {
            year = field(parsed, ChronoField.YEAR_OF_ERA);
        }
        Integer month = field(parsed, ChronoField.MONTH_OF_YEAR);
        Integer day = field(parsed, ChronoField.DAY_OF_MONTH);
        if (year == null || month == null || day == null) {
            return null;
        }
        int hour = orZero(field(parsed, ChronoField.HOUR_OF_DAY));
        int minute = orZero(field(parsed, ChronoField.MINUTE_OF_HOUR));
        int second = orZero(field(parsed, ChronoField.SECOND_OF_MINUTE));
        int nano = orZero(field(parsed, ChronoField.NANO_OF_SECOND));
        try {
            return LocalDateTime.of(year, month, day, hour, minute, second, nano);
        } catch (RuntimeException e) {
            return null; // out-of-range field values in a malformed line
        }
    }

    private static Integer field(TemporalAccessor parsed, ChronoField chronoField) {
        return parsed.isSupported(chronoField) ? (int) parsed.getLong(chronoField) : null;
    }

    private static int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
