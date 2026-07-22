package com.app.logutility.request.search;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * A search over one project's logs.
 *
 * @param projectId the project to search
 * @param from      inclusive start of the wall-clock time range
 * @param to        inclusive end of the wall-clock time range
 * @param filters   filter-field key -> value; only non-blank values are applied (all AND-ed)
 * @param freeText  optional case-insensitive substring that must also appear in the line
 * @param page      zero-based page index into the (capped, timestamp-sorted) results
 * @param pageSize  page size; &lt;= 0 means "all results up to the cap"
 */
public record SearchRequest(
        UUID projectId,
        LocalDateTime from,
        LocalDateTime to,
        Map<String, String> filters,
        String freeText,
        int page,
        int pageSize) {
}
