package com.app.logutility.request.project;

import com.app.logutility.entity.project.MatchType;

/** One searchable-field row in a create/update {@link ProjectRequest}. */
public record FilterFieldRequest(
        String key,
        String label,
        String mdcKey,
        MatchType matchType,
        String linePrefix) {
}
