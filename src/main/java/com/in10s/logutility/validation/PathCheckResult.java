package com.in10s.logutility.validation;

/** Outcome of a single {@link PathAvailabilityChecker} check. */
public record PathCheckResult(boolean reachable, long fileCount, String message) {
}
