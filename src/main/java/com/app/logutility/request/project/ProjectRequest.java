package com.app.logutility.request.project;

import java.util.List;

/** Body for {@code POST /api/projects} (create) and {@code PUT /api/projects/{id}} (update). */
public record ProjectRequest(
        String name,
        String description,
        List<NodeRequest> nodes,
        List<FilterFieldRequest> filterFields,
        LinePatternRequest linePattern) {
}
