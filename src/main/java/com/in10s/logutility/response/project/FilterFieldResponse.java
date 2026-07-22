package com.in10s.logutility.response.project;

import com.in10s.logutility.entity.project.MatchType;

/** One searchable-field row in a {@link ProjectDetailResponse}. */
public record FilterFieldResponse(
        String key,
        String label,
        String mdcKey,
        MatchType matchType,
        String linePrefix) {
}
