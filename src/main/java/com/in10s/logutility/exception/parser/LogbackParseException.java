package com.in10s.logutility.exception.parser;

/** Thrown when an uploaded file is not parseable logback XML. */
public class LogbackParseException extends RuntimeException {

    public LogbackParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public LogbackParseException(String message) {
        super(message);
    }
}
