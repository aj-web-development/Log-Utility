package com.app.logutility.controller.common;

import com.app.logutility.controller.project.ProjectApiController;
import com.app.logutility.controller.search.SearchApiController;
import com.app.logutility.controller.search.SearchStreamController;
import com.app.logutility.exception.parser.LogbackParseException;
import com.app.logutility.exception.project.ProjectNotFoundException;
import com.app.logutility.exception.search.SearchOverloadedException;
import com.app.logutility.response.common.ApiError;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * Uniform JSON error body for the REST API. Scoped via {@code assignableTypes} to the REST
 * controllers specifically (not {@code SpaController}, which just forwards to the SPA shell) —
 * this list grows as more {@code *ApiController}s are added.
 */
@RestControllerAdvice(assignableTypes = {
        SearchApiController.class, SearchStreamController.class, ProjectApiController.class })
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ProjectNotFoundException ex, HttpServletRequest request) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler({ LogbackParseException.class, IllegalArgumentException.class })
    public ResponseEntity<ApiError> handleBadRequest(RuntimeException ex, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Duplicate project name — the client asked for something that conflicts with existing state. */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleConflict(IllegalStateException ex, HttpServletRequest request) {
        return body(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /** Malformed or incomplete JSON body (e.g. missing a required field) — a client error, not a 500. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, "Malformed request body", request);
    }

    /** A path/query parameter that couldn't be converted to its target type (e.g. a non-UUID id). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        String message = "Invalid value for parameter '" + ex.getName() + "': " + ex.getValue();
        return body(HttpStatus.BAD_REQUEST, message, request);
    }

    /** A required query parameter (e.g. projectId) was omitted entirely - a client error, not a 500. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return body(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Right route, wrong HTTP verb — 405, not a 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), request);
    }

    /** The server-wide concurrent-search cap is saturated — ask the client to back off, not a 500. */
    @ExceptionHandler(SearchOverloadedException.class)
    public ResponseEntity<ApiError> handleOverloaded(SearchOverloadedException ex, HttpServletRequest request) {
        return body(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error handling {}", request.getRequestURI(), ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request);
    }

    private static ResponseEntity<ApiError> body(HttpStatus status, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), message, request.getRequestURI()));
    }
}
