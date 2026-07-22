package com.app.logutility.response.parser;

/**
 * One piece of a sample line for rendering as a highlighted preview, in left-to-right order.
 * {@code label} is one of {@code timestamp}/{@code level}/{@code logger} for a detected token, or
 * null for the plain text in between.
 */
public record HighlightSegment(String text, String label) {
}
