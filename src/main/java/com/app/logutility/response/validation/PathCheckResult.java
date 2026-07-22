package com.app.logutility.response.validation;

import com.app.logutility.service.validation.PathAvailabilityChecker;

/** Outcome of a single {@link PathAvailabilityChecker} check. */
public record PathCheckResult(boolean reachable, long fileCount, String message) {
}
