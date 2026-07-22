package com.app.logutility.response.project;

/** The steps of the project setup wizard, in order. */
public enum WizardStep {
    DETAILS("Details"),
    NODES("Nodes"),
    SAMPLE_LINE("Sample line"),
    FIELDS("Filter fields"),
    REVIEW("Review");

    private final String label;

    WizardStep(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
