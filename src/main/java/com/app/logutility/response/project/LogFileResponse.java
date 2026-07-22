package com.app.logutility.response.project;

import com.app.logutility.entity.project.CheckStatus;

import java.util.UUID;

/** One labeled log output row nested under a {@link NodeResponse}. */
public record LogFileResponse(
        UUID logFileId,
        String fileLabel,
        String liveLogPath,
        String backupRootPath,
        String backupPathPattern,
        CheckStatus lastCheckStatus,
        String lastCheckMessage) {
}
