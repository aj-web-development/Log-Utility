package com.in10s.logutility.project.config;

import com.in10s.logutility.project.MatchType;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/** Mutable form-backing object for one filter-field row in the wizard's Filter fields step. */
@Getter
@Setter
public class FilterFieldForm implements Serializable {

    private String key;
    private String label;
    private String mdcKey;
    private MatchType matchType = MatchType.EXACT_TOKEN;
    private String linePrefix;
}
