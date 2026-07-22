package com.app.logutility.response.project;

import java.util.List;
import java.util.UUID;

/** Read-only projection of a project for the public search page — never the JPA entity itself. */
public record PublicProjectView(UUID id, String name, List<PublicFilterFieldView> fields) {
}
