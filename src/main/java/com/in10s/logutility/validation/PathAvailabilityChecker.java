package com.in10s.logutility.validation;

/**
 * Checks whether a configured filesystem path (a live log file or a backup directory) is
 * currently reachable, without ever throwing — callers always get a {@link PathCheckResult}
 * so a check can never block saving a project configuration.
 */
public interface PathAvailabilityChecker {

    /**
     * @param path a live log file path or a backup root directory path
     * @return reachable=false with a human-readable reason if the path is blank, missing, or
     *         cannot be listed; otherwise reachable=true with a file/entry count where applicable
     */
    PathCheckResult check(String path);
}
