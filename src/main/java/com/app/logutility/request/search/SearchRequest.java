package com.app.logutility.request.search;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * A search over one project's logs.
 *
 * @param projectId the project to search
 * @param from      inclusive start of the time range, an absolute instant (converted to the
 *                  project's configured zone before comparing against raw log timestamps)
 * @param to        inclusive end of the time range, likewise an absolute instant
 * @param filters   filter-field key -> value; only non-blank values are applied (all AND-ed)
 * @param freeText  optional case-insensitive substring that must also appear in the line
 * @param page      zero-based page index into the (capped, timestamp-sorted) results
 * @param pageSize  page size; &lt;= 0 means "all results up to the cap"
 */
public record SearchRequest(
        UUID projectId,
        Instant from,
        Instant to,
        Map<String, String> filters,
        String freeText,
        int page,
        int pageSize) {
}
