package com.app.logutility.service.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.app.logutility.exception.parser.LogbackParseException;
import com.app.logutility.response.parser.LogbackParseResult;
import com.app.logutility.response.parser.MdcFieldSuggestion;

/** Pure unit tests — no Spring context — for the logback-spring.xml parser. */
class LogbackXmlParserImplTest {

    private final LogbackXmlParser parser = new LogbackXmlParserImpl();

    private static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <configuration>
                <springProperty name="LOG_PATH" source="logging.path" defaultValue="/var/log/orders"/>
                <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                    <file>${LOG_PATH}/app.log</file>
                    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                        <fileNamePattern>${LOG_PATH}/archive/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                        <maxHistory>30</maxHistory>
                    </rollingPolicy>
                    <encoder>
                        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} tid=%X{traceId} [%X{sessionId}] - %msg%n</pattern>
                    </encoder>
                </appender>
                <root level="INFO">
                    <appender-ref ref="FILE"/>
                </root>
            </configuration>
            """;

    @Test
    void extractsMdcFieldsWithOrderAndLinePrefix() {
        LogbackParseResult result = parser.parse(SAMPLE_XML);

        assertThat(result.mdcFields()).extracting(MdcFieldSuggestion::mdcKey)
                .containsExactly("traceId", "sessionId");

        MdcFieldSuggestion traceId = result.mdcFields().get(0);
        assertThat(traceId.linePrefix()).isEqualTo("tid=");
        assertThat(traceId.suggestedLabel()).isEqualTo("Trace Id");

        MdcFieldSuggestion sessionId = result.mdcFields().get(1);
        assertThat(sessionId.linePrefix()).isNull(); // bracket-delimited, no "word=" before it
    }

    @Test
    void convertsRollingPatternToPlaceholderStyleRootRelative() {
        LogbackParseResult result = parser.parse(SAMPLE_XML);

        assertThat(result.backupPathPattern()).isEqualTo("app.{date}.{i}.log.gz");
        assertThat(result.backupRootHint()).isEqualTo("/var/log/orders/archive/");
    }

    @Test
    void resolvesLiveLogPathHintViaSpringProperty() {
        LogbackParseResult result = parser.parse(SAMPLE_XML);

        assertThat(result.liveLogPathHint()).isEqualTo("/var/log/orders/app.log");
    }

    @Test
    void patternWithoutRotationTokensYieldsNoSuggestion() {
        String xml = """
                <configuration>
                    <appender name="FILE" class="ch.qos.logback.core.FileAppender">
                        <file>/var/log/app.log</file>
                        <encoder><pattern>%msg%n</pattern></encoder>
                    </appender>
                </configuration>
                """;

        LogbackParseResult result = parser.parse(xml);

        assertThat(result.backupPathPattern()).isNull();
        assertThat(result.mdcFields()).isEmpty();
    }

    @Test
    void emptyContentThrows() {
        assertThatThrownBy(() -> parser.parse("   ")).isInstanceOf(LogbackParseException.class);
    }

    @Test
    void malformedXmlThrows() {
        assertThatThrownBy(() -> parser.parse("<configuration><unclosed>"))
                .isInstanceOf(LogbackParseException.class);
    }

    @Test
    void doctypeDeclarationIsRejected() {
        // Regression test: DOCTYPE (and therefore any XXE payload riding on it) must be
        // rejected outright rather than resolved, per the parser's disallow-doctype-decl setting.
        String maliciousXml = """
                <?xml version="1.0"?>
                <!DOCTYPE configuration [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <configuration>
                    <appender name="FILE"><file>&xxe;</file></appender>
                </configuration>
                """;

        assertThatThrownBy(() -> parser.parse(maliciousXml)).isInstanceOf(LogbackParseException.class);
    }

    @Test
    void resolvesPlainPropertyElementsNotJustSpringProperty() {
        // Regression test: a bare <property> (Logback's own mechanism, not the Spring Boot
        // extension) must resolve too, or ${LOG_PATH} is left as literal text.
        String xml = """
                <configuration>
                    <property name="LOG_PATH" value="/var/log/orders"/>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <file>${LOG_PATH}/app.log</file>
                        <rollingPolicy>
                            <fileNamePattern>${LOG_PATH}/archive/app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                        </rollingPolicy>
                        <encoder><pattern>%msg%n</pattern></encoder>
                    </appender>
                </configuration>
                """;

        LogbackParseResult result = parser.parse(xml);

        assertThat(result.liveLogPathHint()).isEqualTo("/var/log/orders/app.log");
        assertThat(result.backupPathPattern()).isEqualTo("app.{date}.{i}.log.gz");
        assertThat(result.backupRootHint()).isEqualTo("/var/log/orders/archive/");
    }

    @Test
    void variableNestedInsideDateTokenResolvesCleanlyWhenPropertyIsDefined() {
        // Regression test for the exact real-world pattern that motivated this fix: Logback's
        // "mark auxiliary %d tokens with aux" convention (%d{${DATE_PATTERN},aux}) puts a ${...}
        // substitution var *inside* the %d{...} braces.
        String xml = """
                <configuration>
                    <property name="LOGS_FILE_NAME" value="uniserve-360-api"/>
                    <property name="DATE_PATTERN" value="yyyy-MM-dd"/>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <file>/logs/uniserve-web/${LOGS_FILE_NAME}.log</file>
                        <rollingPolicy>
                            <fileNamePattern>/logs/uniserve-web/${LOGS_FILE_NAME}-logs-backup/%d{${DATE_PATTERN},aux}/${LOGS_FILE_NAME}.%d{HH}.%i.log.gz</fileNamePattern>
                        </rollingPolicy>
                        <encoder><pattern>%msg%n</pattern></encoder>
                    </appender>
                </configuration>
                """;

        LogbackParseResult result = parser.parse(xml);

        assertThat(result.backupPathPattern()).isEqualTo("{date}/uniserve-360-api.{HH}.{i}.log.gz");
        assertThat(result.backupRootHint()).isEqualTo("/logs/uniserve-web/uniserve-360-api-logs-backup/");
    }

    @Test
    void unresolvedVariableInsideDateTokenDoesNotCorruptTrailingLiteralText() {
        // Even if DATE_PATTERN is never defined anywhere in the file (e.g. it's only ever
        // supplied as a JVM system property, invisible to this offline XML parse), the embedded
        // '}' from the literal, unresolved "${DATE_PATTERN}" text must not prematurely close the
        // %d{...} match and leak ",aux}" into the text before the next token.
        String xml = """
                <configuration>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <file>/logs/uniserve-web/uniserve-360-api.log</file>
                        <rollingPolicy>
                            <fileNamePattern>/logs/uniserve-web-logs-backup/%d{${DATE_PATTERN},aux}/uniserve-360-api.%d{HH}.%i.log.gz</fileNamePattern>
                        </rollingPolicy>
                        <encoder><pattern>%msg%n</pattern></encoder>
                    </appender>
                </configuration>
                """;

        LogbackParseResult result = parser.parse(xml);

        assertThat(result.backupPathPattern()).isEqualTo("{date}/uniserve-360-api.{HH}.{i}.log.gz");
        assertThat(result.backupRootHint()).isEqualTo("/logs/uniserve-web-logs-backup/");
    }

    @Test
    void handlesMultipleAppendersUsingFirstFileNamePattern() {
        String xml = """
                <configuration>
                    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
                        <encoder><pattern>%msg%n</pattern></encoder>
                    </appender>
                    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
                        <file>/var/log/app.log</file>
                        <rollingPolicy>
                            <fileNamePattern>/var/log/archive/app.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
                        </rollingPolicy>
                        <encoder><pattern>tid=%X{traceId} %msg%n</pattern></encoder>
                    </appender>
                </configuration>
                """;

        LogbackParseResult result = parser.parse(xml);
        assertThat(result.backupPathPattern()).isEqualTo("app.{date}.log.gz");
        List<MdcFieldSuggestion> fields = result.mdcFields();
        assertThat(fields).hasSize(1);
        assertThat(fields.get(0).mdcKey()).isEqualTo("traceId");
    }
}
