package com.app.logutility.config.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.concurrent.Semaphore;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfig {

    /** Injectable clock so the "include the live file?" decision is deterministic in tests. */
    @Bean
    public Clock searchClock() {
        return Clock.systemDefaultZone();
    }

    /** Server-wide cap on searches running at once, across all users (search has no login). */
    @Bean
    public Semaphore searchConcurrencyGate(SearchProperties properties) {
        return new Semaphore(Math.max(1, properties.getMaxConcurrentSearches()));
    }
}
