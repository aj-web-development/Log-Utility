package com.in10s.logutility.search;

import com.in10s.logutility.project.FilterField;
import com.in10s.logutility.project.MatchType;

import java.util.function.Predicate;

/**
 * Strategy for matching one filter field's value against a log line. Implementations are Spring
 * beans registered by their {@link #type()}, so supporting a new {@link MatchType} means adding a
 * new bean — never editing the existing matchers or their selection (open/closed).
 */
public interface FieldMatcher {

    /** The match type this strategy handles. */
    MatchType type();

    /**
     * Builds a line predicate for a specific field + value. Any per-value work (e.g. compiling a
     * regex) is done once here, not per line.
     */
    Predicate<String> build(FilterField field, String value);
}
