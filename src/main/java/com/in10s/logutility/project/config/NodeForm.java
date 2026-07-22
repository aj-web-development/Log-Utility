package com.in10s.logutility.project.config;

import com.in10s.logutility.project.CheckStatus;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/** Mutable form-backing object for one node row in the wizard's Nodes step. */
@Getter
@Setter
public class NodeForm implements Serializable {

    private String nodeLabel;
    private String liveLogPath;
    private String backupRootPath;
    private String backupPathPattern;

    /**
     * Id of the persisted {@code LogSource} this row was loaded from, or null for a node added
     * during the current wizard session. Lets the "Test path" button record its result onto the
     * real row (see {@link ProjectService#recordLogSourceCheck}) even before the wizard is saved.
     */
    private UUID logSourceId;

    /** Last known check result for display only; refreshed live by the "Test path" button. */
    private CheckStatus lastCheckStatus;
    private String lastCheckMessage;
}
