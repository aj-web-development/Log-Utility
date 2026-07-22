package com.in10s.logutility.parser;

/**
 * Extracts searchable-field and backup-pattern suggestions from a {@code logback-spring.xml}
 * (or plain {@code logback.xml}) file, so the project wizard can be pre-populated instead of
 * built entirely by hand.
 */
public interface LogbackXmlParser {

    /**
     * @param xmlContent the raw file contents
     * @throws LogbackParseException if the content is not well-formed XML
     */
    LogbackParseResult parse(String xmlContent);
}
