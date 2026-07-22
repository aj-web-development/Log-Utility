package com.in10s.logutility.entity.project;

/** Result of the most recent path-availability check for a {@link LogSource}. */
public enum CheckStatus {
    /** The configured path was listable at last check. */
    REACHABLE,
    /** The configured path could not be listed at last check. */
    UNREACHABLE,
    /** Never checked yet. */
    UNKNOWN
}
