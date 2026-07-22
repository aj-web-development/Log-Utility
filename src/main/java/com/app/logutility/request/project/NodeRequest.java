package com.app.logutility.request.project;

import java.util.List;

/** One node/server row in a create/update {@link ProjectRequest}. */
public record NodeRequest(
        String nodeLabel,
        List<LogFileRequest> logFiles) {
}
