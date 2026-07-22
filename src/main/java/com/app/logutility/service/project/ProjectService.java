package com.app.logutility.service.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.app.logutility.entity.project.LogSource;
import com.app.logutility.request.project.ProjectWizardForm;
import com.app.logutility.response.project.PathCheckOutcome;
import com.app.logutility.response.project.ProjectSummaryDto;
import com.app.logutility.response.project.PublicProjectView;

/** Application service for project configuration CRUD, driving the admin list and wizard. */
public interface ProjectService {

    /** All projects as lightweight summaries, ordered by name. */
    List<ProjectSummaryDto> listProjects();

    /** Read-only view for the public search page (name + filter fields); empty if not found. */
    Optional<PublicProjectView> findPublicView(UUID id);

    /**
     * Whether another project already uses the given name.
     *
     * @param excludingProjectId the project being edited (excluded from the check), or null when creating
     */
    boolean nameExists(String name, UUID excludingProjectId);

    /** Creates a new project or updates an existing one from the wizard draft; returns its id. */
    UUID saveFromWizard(ProjectWizardForm form);

    /** Loads an existing project into a fresh wizard draft for editing. */
    ProjectWizardForm loadForEdit(UUID id);

    /** Deletes a project and its nodes/fields; a no-op if it no longer exists. */
    void deleteProject(UUID id);

    /**
     * Persists the outcome of a "Test path" check onto an existing {@code LogSource}, purely
     * for later display — this never blocks or gates saving a project. A no-op if the log
     * source no longer exists (e.g. it was removed from the wizard draft before saving).
     */
    void recordLogSourceCheck(UUID logSourceId, boolean reachable, String message);

    /**
     * Checks whichever of a node's live/backup paths are non-blank and folds them into one
     * combined result (a {@code LogSource} has one check status, not one per path). Never throws
     * — an unreachable or unconfigured path is a normal outcome, not an error. If
     * {@code logSourceId} is given, the combined result is also recorded onto that row via
     * {@link #recordLogSourceCheck}.
     */
    PathCheckOutcome checkPaths(String livePath, String backupPath, UUID logSourceId);
}
