package com.in10s.logutility.service.search;

import com.in10s.logutility.entity.project.FilterField;
import com.in10s.logutility.entity.project.MatchType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Predicate;

/** Matches when the line contains the (optionally line-prefixed) value as a case-sensitive substring. */
@Component
public class SubstringMatcher implements FieldMatcher {

    @Override
    public MatchType type() {
        return MatchType.SUBSTRING;
    }

    @Override
    public Predicate<String> build(FilterField field, String value) {
        String prefix = StringUtils.hasText(field.getLinePrefix()) ? field.getLinePrefix() : "";
        String needle = prefix + value;
        return line -> line.contains(needle);
    }
}
