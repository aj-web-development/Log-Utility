package com.app.logutility.response.project;

import java.util.List;
import java.util.UUID;

/**
 * Read-only projection of a project for the public search page — never the JPA entity itself.
 * {@code zoneId} is the same zone {@code SearchServiceImpl} actually parses/searches this
 * project's raw log digits in (see {@code LinePattern#resolveZoneId}) - the search UI's date
 * pickers must interpret "From"/"To" in this zone, not the browser's own, or an explicit date
 * range can silently target the wrong window relative to what's on disk.
 */
public record PublicProjectView(UUID id, String name, List<PublicFilterFieldView> fields, String zoneId) {
}
