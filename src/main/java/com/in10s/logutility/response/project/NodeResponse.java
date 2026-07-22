package com.in10s.logutility.response.project;

import com.in10s.logutility.entity.project.CheckStatus;

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
