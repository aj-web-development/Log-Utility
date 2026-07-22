package com.app.logutility.request.project;

import com.app.logutility.entity.project.CheckStatus;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;
import com.app.logutility.service.project.ProjectService;

/** Mutable form-backing object for one labeled log output row nested under a {@link NodeForm}. */
@Getter
@Setter
public class LogFileForm implements Serializable {

    private String fileLabel;
    private String liveLogPath;
    private String backupRootPath;
    private String backupPathPattern;

    /**
     * Id of the persisted {@code LogFile} this row was loaded from, or null for an output added
     * during the current wizard session. Lets the "Test path" button record its result onto the
     * real row (see {@link ProjectService#recordLogFileCheck}) even before the wizard is saved.
     */
    private UUID logFileId;

    /** Last known check result for display only; refreshed live by the "Test path" button. */
    private CheckStatus lastCheckStatus;
    private String lastCheckMessage;
}
