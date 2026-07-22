package com.in10s.logutility.entity.project;

import com.in10s.logutility.service.search.FieldMatcher;
/**
 * How a {@link FilterField}'s value is matched against a log line. The chosen constant
 * selects the corresponding {@code FieldMatcher} strategy in the search engine (Phase 6),
 * so adding a new match type is additive and never edits existing matchers.
 */
public enum MatchType {
    /** Whole-token equality, e.g. a trace id delimited by whitespace or {@code key=value}. */
    EXACT_TOKEN,
    /** Case-sensitive substring containment. */
    SUBSTRING,
    /** Java regular-expression match. */
    REGEX
}
