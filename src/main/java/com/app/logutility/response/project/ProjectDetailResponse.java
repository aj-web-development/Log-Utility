package com.app.logutility.response.project;

import java.util.List;
import java.util.UUID;

/** Full project detail returned by {@code GET/POST/PUT /api/projects/**} — the REST equivalent
 * of loading the wizard draft for editing. */
public record ProjectDetailResponse(
        UUID id,
        String name,
        String description,
        List<NodeResponse> nodes,
        List<FilterFieldResponse> filterFields,
        LinePatternResponse linePattern) {
}
