package com.app.logutility.response.project;

import java.util.List;
import java.util.UUID;

/** One node/server row in a {@link ProjectDetailResponse}. */
public record NodeResponse(
        UUID logSourceId,
        String nodeLabel,
        List<LogFileResponse> logFiles) {
}
