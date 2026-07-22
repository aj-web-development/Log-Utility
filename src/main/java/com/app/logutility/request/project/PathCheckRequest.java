package com.app.logutility.request.project;

import java.util.UUID;

/**
 * Body for {@code POST /api/projects/path-check}. {@code logFileId} is optional — pass it only
 * when checking an already-persisted log output, so the result is also recorded onto it (matching
 * the wizard's "Test path" button); omit it for an ad-hoc check of paths not yet saved.
 */
public record PathCheckRequest(String livePath, String backupPath, UUID logFileId) {
}
