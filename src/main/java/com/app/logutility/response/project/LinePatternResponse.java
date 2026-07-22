package com.app.logutility.response.project;

/** How a project's log lines are parsed, in a {@link ProjectDetailResponse}. Null if unset. */
public record LinePatternResponse(
        String timestampPattern,
        String timestampRegexOrPosition,
        String levelPattern,
        String loggerPattern) {
}
