package com.app.logutility.service.search;

import com.app.logutility.entity.project.FilterField;
import com.app.logutility.entity.project.MatchType;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Treats the value as a Java regular expression and matches when it is found anywhere in the line.
 * The regex is compiled once here; a syntactically invalid pattern surfaces as a
 * {@link java.util.regex.PatternSyntaxException} for the caller to handle. The field's line prefix
 * is not applied — the regex is expected to be self-contained.
 */
@Component
public class RegexMatcher implements FieldMatcher {

    @Override
    public MatchType type() {
        return MatchType.REGEX;
    }

    @Override
    public Predicate<String> build(FilterField field, String value) {
        // DOTALL so '.' can span the embedded newlines of a multi-line (e.g. stack trace) entry.
        Pattern pattern = Pattern.compile(value, Pattern.DOTALL);
        return line -> pattern.matcher(line).find();
    }
}
