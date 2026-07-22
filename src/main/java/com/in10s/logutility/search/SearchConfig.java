package com.in10s.logutility.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfig {

    /** Injectable clock so the "include the live file?" decision is deterministic in tests. */
    @Bean
    public Clock searchClock() {
        return Clock.systemDefaultZone();
    }
}
