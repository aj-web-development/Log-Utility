package com.app.logutility.request.project;

/** One node/server row in a create/update {@link ProjectRequest}. */
public record NodeRequest(
        String nodeLabel,
        String liveLogPath,
        String backupRootPath,
        String backupPathPattern) {
}
