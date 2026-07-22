package com.in10s.logutility.request.project;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import com.in10s.logutility.entity.project.LinePattern;

/** Mutable form-backing object for the wizard's Sample line step. All fields are optional. */
@Getter
@Setter
public class LinePatternForm implements Serializable {

    private String sampleLine;
    private String timestampPattern;
    private String timestampRegexOrPosition;
    private String levelPattern;
    private String loggerPattern;

    /** Whether any field has content worth persisting as a {@code LinePattern}. */
    public boolean hasAnyContent() {
        return hasText(timestampPattern) || hasText(timestampRegexOrPosition)
                || hasText(levelPattern) || hasText(loggerPattern);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
