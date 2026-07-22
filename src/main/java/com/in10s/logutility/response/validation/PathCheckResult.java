package com.in10s.logutility.response.validation;

import com.in10s.logutility.service.validation.PathAvailabilityChecker;
/** Outcome of a single {@link PathAvailabilityChecker} check. */
public record PathCheckResult(boolean reachable, long fileCount, String message) {
}
