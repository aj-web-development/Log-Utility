package com.app.logutility.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Tags every log line emitted while handling a request with a trace id (MDC "traceId", the
 * {@code tid=%X{traceId}} field in logback-spring.xml) so concurrent requests - this app runs up
 * to {@code search.max-concurrent-searches} searches at once, all logging to the same file/console
 * - can be told apart. Reuses an incoming {@code X-Trace-Id} header if the caller already has one
 * (e.g. a reverse proxy), otherwise mints a fresh one; echoes it back on the response either way.
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String traceId = StringUtils.hasText(request.getHeader(HEADER))
                ? request.getHeader(HEADER)
                : UUID.randomUUID().toString();
        MDC.put(MDC_KEY, traceId);
        response.setHeader(HEADER, traceId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
