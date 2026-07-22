package com.in10s.logutility.request.project;

import com.in10s.logutility.entity.project.MatchType;

/** One searchable-field row in a create/update {@link ProjectRequest}. */
public record FilterFieldRequest(
        String key,
        String label,
        String mdcKey,
        MatchType matchType,
        String linePrefix) {
}
