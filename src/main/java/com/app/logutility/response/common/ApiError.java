package com.app.logutility.response.common;

import java.time.Instant;

/** Uniform JSON error body returned by every {@code /api/**} endpoint on failure. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
