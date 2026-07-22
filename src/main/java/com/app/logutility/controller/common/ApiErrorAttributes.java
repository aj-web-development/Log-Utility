package com.app.logutility.controller.common;

import com.app.logutility.response.common.ApiError;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@link ApiExceptionHandler} only runs once a request has been matched to one of its listed
 * REST controllers — a request under {@code /api/**} that matches no route at all, or fails
 * before a handler method is invoked, falls through to Spring Boot's default {@code /error}
 * handling instead, whose JSON body doesn't match {@link ApiError}'s shape (notably, it omits
 * {@code message} unless {@code server.error.include-message} is set). This reshapes exactly
 * those {@code /api/**} responses into the same {@code ApiError} fields, without touching error
 * rendering for the Thymeleaf UI's paths.
 */
@Component
public class ApiErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options.including(ErrorAttributeOptions.Include.MESSAGE));
        Object path = attributes.get("path");
        if (path == null || !path.toString().startsWith("/api/")) {
            return attributes;
        }

        int status = attributes.get("status") instanceof Integer s ? s : 500;
        String error = String.valueOf(attributes.getOrDefault("error", "Error"));
        // Never leak a raw exception message for a genuine server error, same rule as ApiExceptionHandler.
        String message = status >= 500 ? "An unexpected error occurred" : String.valueOf(attributes.get("message"));

        Map<String, Object> shaped = new LinkedHashMap<>();
        shaped.put("timestamp", Instant.now());
        shaped.put("status", status);
        shaped.put("error", error);
        shaped.put("message", message);
        shaped.put("path", path);
        return shaped;
    }
}
