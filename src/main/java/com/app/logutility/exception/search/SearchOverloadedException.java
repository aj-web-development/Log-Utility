package com.app.logutility.exception.search;

/** Thrown when {@code search.max-concurrent-searches} is already saturated by other requests. */
public class SearchOverloadedException extends RuntimeException {

    public SearchOverloadedException(String message) {
        super(message);
    }
}
