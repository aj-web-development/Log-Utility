package com.in10s.logutility.request.project;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import com.in10s.logutility.entity.project.LogSource;
import com.in10s.logutility.service.project.ProjectService;

/**
 * The wizard's working draft. A single instance is kept in the HTTP session while the admin
 * moves through the steps; each step POST merges its inputs into this draft, and the Review
 * step persists it via {@link ProjectService}. Also serves as the per-request binding target
 * for step submissions (only the fields present on a given step are populated on bind, then
 * copied into the session draft). {@code projectId} is null for a new project.
 */
@Getter
@Setter
public class ProjectWizardForm implements Serializable {

    private UUID projectId;
    private String name;
    private String description;
    private List<NodeForm> nodes = new ArrayList<>();
    private LinePatternForm linePattern = new LinePatternForm();
    private List<FilterFieldForm> filterFields = new ArrayList<>();

    /**
     * Informational-only hints surfaced on the Nodes step after a logback XML upload (raw,
     * post-variable-substitution literals showing what the XML declared) — never bound to
     * {@code LogSource.liveLogPath}/{@code backupRootPath}, which the admin must fill in
     * manually since those are resolved from environment variables at runtime.
     */
    private String uploadLiveLogHint;
    private String uploadBackupRootHint;

    public boolean isEditing() {
        return projectId != null;
    }
}
