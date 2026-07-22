package com.in10s.logutility.project.config;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only projection for the admin project list. Populated by a single JPQL query with count
 * subqueries (see {@code ProjectRepository#findAllSummaries}) to avoid an N+1 over each
 * project's nodes and fields.
 */
public record ProjectSummaryDto(
        UUID id,
        String name,
        String description,
        long nodeCount,
        long fieldCount,
        Instant updatedAt) {
}
