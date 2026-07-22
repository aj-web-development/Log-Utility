package com.in10s.logutility;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Application entry point. Extends {@link SpringBootServletInitializer} so the same code base
 * can be built either as a runnable jar (default) or as a traditional WAR (via the war17 /
 * war21 Maven profiles) that boots inside an external servlet container. The initializer is a
 * no-op for jar execution, so it is safe in both packagings.
 */
@SpringBootApplication
public class LogutilityApplication extends SpringBootServletInitializer {

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(LogutilityApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(LogutilityApplication.class, args);
    }
}
