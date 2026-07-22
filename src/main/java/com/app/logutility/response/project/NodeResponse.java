package com.app.logutility.response.project;

import com.app.logutility.entity.project.CheckStatus;

import java.util.UUID;

/** One node/server row in a {@link ProjectDetailResponse}. */
public record NodeResponse(
        UUID logSourceId,
        String nodeLabel,
        String liveLogPath,
        String backupRootPath,
        String backupPathPattern,
        CheckStatus lastCheckStatus,
        String lastCheckMessage) {
}
