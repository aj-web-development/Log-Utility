package com.app.logutility.dto.search;

import java.util.List;

/**
 * What a node's scan should cover, computed without touching the filesystem.
 *
 * @param backupGlobs  one root-relative glob per candidate day (with {@code {HH}}/{@code {i}}
 *                     turned into {@code *}); resolved against the backup root at scan time
 * @param includeLive  whether the live (un-rotated) log file should also be read
 */
public record ScanPlan(List<String> backupGlobs, boolean includeLive) {
}
