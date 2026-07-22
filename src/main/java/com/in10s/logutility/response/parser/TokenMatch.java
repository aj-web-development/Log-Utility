package com.in10s.logutility.response.parser;

/** A detected token's text and its character range within the sample line. */
public record TokenMatch(String text, int start, int end) {
}
