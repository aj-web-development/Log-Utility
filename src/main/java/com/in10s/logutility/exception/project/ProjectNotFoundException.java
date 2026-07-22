package com.in10s.logutility.exception.project;

import java.util.UUID;

/** Thrown when a project id doesn't correspond to any persisted {@code Project}. */
public class ProjectNotFoundException extends RuntimeException {

    public ProjectNotFoundException(UUID id) {
        super("Project not found: " + id);
    }
}
