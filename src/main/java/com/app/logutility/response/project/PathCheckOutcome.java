package com.app.logutility.response.project;

import com.app.logutility.entity.project.CheckStatus;

/**
 * The combined result of checking a node's live/backup paths (whichever are configured) as one
 * status — a {@code LogSource} has one check result, not one per path.
 */
public record PathCheckOutcome(CheckStatus status, String message) {
}
