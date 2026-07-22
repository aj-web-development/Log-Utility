package com.app.logutility.request.project;

/** One labeled log output row nested under a {@link NodeRequest}. */
public record LogFileRequest(
        String fileLabel,
        String liveLogPath,
        String backupRootPath,
        String backupPathPattern) {
}
