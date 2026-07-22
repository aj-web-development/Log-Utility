package com.in10s.logutility.search;

import com.in10s.logutility.project.FilterField;
import com.in10s.logutility.project.MatchType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Matches when the (optionally line-prefixed) value appears as a whole token — i.e. bounded by
 * non-identifier characters — so {@code tid=abc} does not match a line containing {@code tid=abcd}.
 * Identifier characters are word chars plus '.' and '-' (common in ids/UUIDs).
 */
@Component
public class ExactTokenMatcher implements FieldMatcher {

    @Override
    public MatchType type() {
        return MatchType.EXACT_TOKEN;
    }

    @Override
    public Predicate<String> build(FilterField field, String value) {
        String prefix = StringUtils.hasText(field.getLinePrefix()) ? field.getLinePrefix() : "";
        String needle = prefix + value;
        Pattern pattern = Pattern.compile("(?<![\\w.-])" + Pattern.quote(needle) + "(?![\\w.-])");
        return line -> pattern.matcher(line).find();
    }
}
