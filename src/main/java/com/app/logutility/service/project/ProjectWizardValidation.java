package com.app.logutility.service.project;

import com.app.logutility.request.project.FilterFieldForm;
import com.app.logutility.request.project.ProjectWizardForm;

/**
 * What makes a {@link ProjectWizardForm} valid to persist. Shared by the Thymeleaf wizard
 * (which also uses these per-step, to gate moving on to the next step) and the REST API (which
 * only needs the final check before create/update) so the two front doors can never drift apart
 * on what counts as a valid project.
 */
public final class ProjectWizardValidation {

    private ProjectWizardValidation() {
    }

    public static String validateDetails(ProjectWizardForm draft) {
        if (draft.getName() == null || draft.getName().isBlank()) {
            return "Project name is required.";
        }
        return null;
    }

    public static String validateNodes(ProjectWizardForm draft) {
        boolean hasNamedNode = draft.getNodes().stream()
                .anyMatch(n -> n.getNodeLabel() != null && !n.getNodeLabel().isBlank());
        if (!hasNamedNode) {
            return "Add at least one node with a label.";
        }
        return null;
    }

    public static String validateFields(ProjectWizardForm draft) {
        // Filter fields are optional, but any partially filled row must have both a key and a label.
        for (FilterFieldForm f : draft.getFilterFields()) {
            boolean hasKey = f.getKey() != null && !f.getKey().isBlank();
            boolean hasLabel = f.getLabel() != null && !f.getLabel().isBlank();
            if (hasKey ^ hasLabel) {
                return "Each filter field needs both a key and a label.";
            }
        }
        return null;
    }
}
