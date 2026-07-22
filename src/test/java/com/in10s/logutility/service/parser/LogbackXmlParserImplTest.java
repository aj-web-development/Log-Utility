package com.in10s.logutility.service.parser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.in10s.logutility.exception.parser.LogbackParseException;
import com.in10s.logutility.response.parser.LogbackParseResult;
import com.in10s.logutility.response.parser.MdcFieldSuggestion;

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
