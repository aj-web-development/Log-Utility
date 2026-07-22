package com.app.logutility.response.parser;

import com.app.logutility.entity.project.FilterField;

/**
 * One MDC key found in an uploaded logback pattern, pre-shaped into a suggested
 * {@code FilterField}. {@code linePrefix} is populated only when the pattern shows the key
 * immediately preceded by a {@code word=} literal (e.g. {@code tid=%X{traceId}}); bracket- or
 * space-delimited occurrences (e.g. {@code [%X{sessionId}]}) leave it blank for the admin to fill in.
 */
public record MdcFieldSuggestion(String mdcKey, String suggestedKey, String suggestedLabel, String linePrefix) {
}
