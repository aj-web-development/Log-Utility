package com.app.logutility.response.project;

import com.app.logutility.entity.project.MatchType;

/** One searchable-field row in a {@link ProjectDetailResponse}. */
public record FilterFieldResponse(
        String key,
        String label,
        String mdcKey,
        MatchType matchType,
        String linePrefix) {
}
