package com.in10s.logutility.search;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/** Merges every node's matched lines into one timestamp-sorted list (ascending; nulls last). */
@Component
public class ResultMerger {

    private static final Comparator<LogLine> BY_TIMESTAMP =
            Comparator.comparing(LogLine::timestamp, Comparator.nullsLast(Comparator.naturalOrder()));

    public List<LogLine> mergeSorted(List<LogLine> lines) {
        lines.sort(BY_TIMESTAMP);
        return lines;
    }
}
