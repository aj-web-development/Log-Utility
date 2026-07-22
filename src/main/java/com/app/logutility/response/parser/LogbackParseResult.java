package com.app.logutility.response.parser;

import java.util.List;
import com.app.logutility.entity.project.LogSource;

/**
 * Everything extracted from an uploaded {@code logback-spring.xml}. {@code liveLogPathHint} and
 * {@code backupRootHint} are informational only (raw, post-variable-substitution literals shown
 * to the admin) — per the wizard's design these are never used to pre-fill
 * {@code LogSource.liveLogPath}/{@code backupRootPath}, since those are resolved from
 * environment variables at runtime and differ per real node.
 */
public record LogbackParseResult(
        List<MdcFieldSuggestion> mdcFields,
        String backupPathPattern,
        String liveLogPathHint,
        String backupRootHint) {
}
