package com.app.logutility.config.search;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Tuning knobs for the search engine, bound from {@code search.*} in application.yml. */
@ConfigurationProperties(prefix = "search")
@Getter
@Setter
public class SearchProperties {

    /** Hard cap on the number of matched lines returned; scanning stops once it is reached. */
    private int maxResults = 5000;

    /** Upper bound on how many nodes are scanned concurrently (bounds real filesystem I/O). */
    private int maxNodesParallel = 16;
}
