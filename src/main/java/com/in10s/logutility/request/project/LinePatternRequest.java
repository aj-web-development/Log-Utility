package com.in10s.logutility.request.project;

/** How to parse a project's log lines, in a create/update {@link ProjectRequest}. Optional. */
public record LinePatternRequest(
        String timestampPattern,
        String timestampRegexOrPosition,
        String levelPattern,
        String loggerPattern) {
}
