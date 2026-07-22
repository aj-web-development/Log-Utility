package com.app.logutility.request.project;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mutable form-backing object for one node row in the wizard's Nodes step. A node can write more
 * than one distinct log output (app.log, error.log, access.log, ...), each its own
 * {@link LogFileForm}.
 */
@Getter
@Setter
public class NodeForm implements Serializable {

    private String nodeLabel;
    private List<LogFileForm> logFiles = new ArrayList<>();

    /** Id of the persisted {@code LogSource} this row was loaded from, or null for a new node. */
    private UUID logSourceId;
}
