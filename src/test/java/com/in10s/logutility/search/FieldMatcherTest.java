package com.in10s.logutility.search;

import com.in10s.logutility.project.FilterField;
import com.in10s.logutility.project.MatchType;
import org.junit.jupiter.api.Test;

import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure tests for each FieldMatcher strategy. */
class FieldMatcherTest {

    private FilterField field(String key, MatchType type, String linePrefix) {
        FilterField f = new FilterField();
        f.setKey(key);
        f.setMatchType(type);
        f.setLinePrefix(linePrefix);
        return f;
    }

    @Test
    void exactTokenRespectsPrefixAndTokenBoundaries() {
        Predicate<String> matcher = new ExactTokenMatcher()
                .build(field("tid", MatchType.EXACT_TOKEN, "tid="), "abc123");

        assertThat(matcher.test("2026-07-21 INFO tid=abc123 - done")).isTrue();
        assertThat(matcher.test("2026-07-21 INFO tid=abc123x - done")).isFalse(); // longer token
        assertThat(matcher.test("2026-07-21 INFO tid=zzabc123 - done")).isFalse(); // prefix must abut
        assertThat(matcher.test("2026-07-21 INFO no tid here")).isFalse();
    }

    @Test
    void exactTokenWithoutPrefixMatchesStandaloneToken() {
        Predicate<String> matcher = new ExactTokenMatcher()
                .build(field("id", MatchType.EXACT_TOKEN, null), "abc123");

        assertThat(matcher.test("request id abc123 processed")).isTrue();
        assertThat(matcher.test("request abc1234 processed")).isFalse();
    }

    @Test
    void substringMatchesAnywhereCaseSensitive() {
        Predicate<String> matcher = new SubstringMatcher()
                .build(field("s", MatchType.SUBSTRING, null), "OrderPlaced");

        assertThat(matcher.test("event=OrderPlacedEvent fired")).isTrue();
        assertThat(matcher.test("event=orderplaced fired")).isFalse(); // case-sensitive
    }

    @Test
    void substringHonoursLinePrefix() {
        Predicate<String> matcher = new SubstringMatcher()
                .build(field("sid", MatchType.SUBSTRING, "sid="), "S-42");

        assertThat(matcher.test("... sid=S-42abc ...")).isTrue();
        assertThat(matcher.test("... S-42 without prefix ...")).isFalse();
    }

    @Test
    void regexMatchesAndCompilesOnce() {
        Predicate<String> matcher = new RegexMatcher()
                .build(field("r", MatchType.REGEX, null), "user=\\d{3,}");

        assertThat(matcher.test("action user=12345 done")).isTrue();
        assertThat(matcher.test("action user=12 done")).isFalse();
    }

    @Test
    void regexRejectsInvalidPatternAtBuildTime() {
        assertThatThrownBy(() -> new RegexMatcher()
                .build(field("r", MatchType.REGEX, null), "user=[unclosed"))
                .isInstanceOf(java.util.regex.PatternSyntaxException.class);
    }

    @Test
    void eachMatcherReportsItsType() {
        assertThat(new ExactTokenMatcher().type()).isEqualTo(MatchType.EXACT_TOKEN);
        assertThat(new SubstringMatcher().type()).isEqualTo(MatchType.SUBSTRING);
        assertThat(new RegexMatcher().type()).isEqualTo(MatchType.REGEX);
    }
}
