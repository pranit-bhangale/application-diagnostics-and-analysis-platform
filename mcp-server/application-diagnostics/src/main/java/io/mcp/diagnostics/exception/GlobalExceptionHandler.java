package io.mcp.diagnostics.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLTimeoutException;
import java.sql.SQLTransientConnectionException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logging policy (each exception is logged exactly once, in {@link #logException}):
 * <ul>
 *   <li>4xx  -> WARN, one line, no stack trace (client problem)</li>
 *   <li>5xx  -> ERROR, one line plus stack trace (our / downstream problem)</li>
 * </ul>
 * Handlers never call {@code log.*} themselves; they only build the response.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BaseException.class)
    public ResponseEntity<ErrorResponse> handleBaseException
            (final BaseException ex, final HttpServletRequest request) {
        final ResponseStatus responseStatus = ex.getClass().getAnnotation(ResponseStatus.class);
        final HttpStatus status = responseStatus != null
                ? responseStatus.value() : HttpStatus.INTERNAL_SERVER_ERROR;
        return buildResponse(status, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation
            (final MethodArgumentNotValidException ex, final HttpServletRequest request) {
        final Map<String, String> errors = new LinkedHashMap<>();
        for (final FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return buildValidationResponse(errors, ex, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation
            (final ConstraintViolationException ex, final HttpServletRequest request) {
        final Map<String, String> errors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v ->
                errors.put(v.getPropertyPath().toString(), v.getMessage()));
        return buildValidationResponse(errors, ex, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody
            (final HttpMessageNotReadableException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, "Malformed or unreadable request body.", ex, request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam
            (final MissingServletRequestParameterException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Required parameter '" + ex.getParameterName() + "' is missing.", ex, request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch
            (final MethodArgumentTypeMismatchException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST,
                "Parameter '" + ex.getName() + "' has an invalid value.", ex, request);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported
            (final HttpRequestMethodNotSupportedException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported
            (final HttpMediaTypeNotSupportedException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize
            (final MaxUploadSizeExceededException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.UNPROCESSABLE_CONTENT,
                "File size exceeds the allowed limit (25MB).", ex, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument
            (final IllegalArgumentException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), ex, request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound
            (final NoResourceFoundException ex, final HttpServletRequest request) {
        final String uri = request.getRequestURI();
        final boolean swagger = uri.startsWith("/swagger-ui/")
                || uri.equals("/swagger-ui.html")
                || uri.startsWith("/v3/api-docs");
        return buildResponse(HttpStatus.NOT_FOUND,
                swagger ? "Swagger resource not found." : "The requested resource was not found.", ex, request);
    }

    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorResponse> handleSqlException
            (final SQLException ex, final HttpServletRequest request) {
        final DbError error = resolveDbError(ex);
        return buildResponse(error.status(), error.message(), ex, request);
    }

    private DbError resolveDbError(final SQLException ex) {
        final String state = ex.getSQLState() == null ? "" : ex.getSQLState();
        // Authentication: wrong / missing username or password
        if (state.startsWith("28")) {
            return new DbError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database authentication failed. Please contact the administrator.");
        }
        // Server demanded a password but none was configured
        if (state.equals("08004")
                || (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("no password was provided"))) {
            return new DbError(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Database credentials are missing or rejected. Please contact the administrator.");
        }
        // Database does not exist
        if (state.equals("3D000")) {
            return new DbError(HttpStatus.SERVICE_UNAVAILABLE, "The configured database does not exist.");
        }
        // Query timeout / cancelled
        if (ex instanceof SQLTimeoutException || state.equals("57014")) {
            return new DbError(HttpStatus.GATEWAY_TIMEOUT, "The database took too long to respond.");
        }
        // Connection failures, server shutdown, too many connections
        if (state.startsWith("08")
                || state.startsWith("53")
                || state.equals("57P01") || state.equals("57P02") || state.equals("57P03")
                || ex instanceof SQLTransientConnectionException
                || ex instanceof SQLNonTransientConnectionException) {
            return new DbError(HttpStatus.SERVICE_UNAVAILABLE,
                    "Unable to connect to the database. Please try again later.");
        }
        // Integrity constraint violations
        if (state.equals("23505")) {
            return new DbError(HttpStatus.CONFLICT, "A record with the same unique value already exists.");
        }
        if (state.startsWith("23")) {
            return new DbError(HttpStatus.CONFLICT,
                    "The operation violates a data integrity constraint (missing reference, null or invalid value).");
        }
        // Deadlock / serialization failure
        if (state.startsWith("40")) {
            return new DbError(HttpStatus.CONFLICT,
                    "The operation conflicted with another request. Please try again.");
        }
        // Bad data sent to the database (invalid format, value too long)
        if (state.startsWith("22")) {
            return new DbError(HttpStatus.BAD_REQUEST, "Invalid data provided.");
        }
        // Syntax error / undefined table or column and everything else
        return new DbError(HttpStatus.INTERNAL_SERVER_ERROR, "A database error occurred.");
    }

    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleBackendUnreachable
            (final ResourceAccessException ex, final HttpServletRequest request) {
        if (ex.getCause() instanceof SocketTimeoutException) {
            return buildResponse(HttpStatus.GATEWAY_TIMEOUT, "The backend service timed out.", ex, request);
        }
        return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, "The backend service is unreachable.", ex, request);
    }

    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleBackendClientError
            (final HttpClientErrorException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_GATEWAY,
                "The backend service rejected the request (" + ex.getStatusCode().value() + ").", ex, request);
    }

    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleBackendServerError
            (final HttpServerErrorException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_GATEWAY,
                "The backend service failed (" + ex.getStatusCode().value() + ").", ex, request);
    }

    @ExceptionHandler(RestClientResponseException.class)
    public ResponseEntity<ErrorResponse> handleBackendResponse
            (final RestClientResponseException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_GATEWAY, "The backend service returned an error.", ex, request);
    }

    @ExceptionHandler(RestClientException.class)
    public ResponseEntity<ErrorResponse> handleRestClient
            (final RestClientException ex, final HttpServletRequest request) {
        return buildResponse(HttpStatus.BAD_GATEWAY, "Communication with the backend service failed.", ex, request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleRuntimeException
            (final RuntimeException ex, final HttpServletRequest request) {
        final SQLException sql = findCause(ex);
        if (sql != null) {
            return handleSqlException(sql, request);
        }
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", ex, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException
            (final Exception ex, final HttpServletRequest request) {
        final SQLException sql = findCause(ex);
        if (sql != null) {
            return handleSqlException(sql, request);
        }
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", ex, request);
    }

    private ResponseEntity<ErrorResponse> buildResponse
            (final HttpStatus status, final String message, final Throwable ex, final HttpServletRequest request) {
        logException(status, message, ex, request);
        return ResponseEntity.status(status)
                .body(ErrorResponse.builder()
                        .status(status.value())
                        .message(message)
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    private ResponseEntity<ErrorResponse> buildValidationResponse
            (final Map<String, String> errors, final Throwable ex, final HttpServletRequest request) {
        logException(HttpStatus.BAD_REQUEST, "Validation failed: " + errors, ex, request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.builder()
                        .status(HttpStatus.BAD_REQUEST.value())
                        .message("Validation failed.")
                        .errors(errors)
                        .path(request.getRequestURI())
                        .timestamp(Instant.now())
                        .build());
    }

    private void logException
            (final HttpStatus status, final String message, final Throwable ex, final HttpServletRequest request) {
        final String detail = describe(ex);
        if (status.is5xxServerError()) {
            log.error("{} {} -> {} | {} | {}",
                    request.getMethod(), request.getRequestURI(), status.value(), message, detail, ex);
        } else {
            log.warn("{} {} -> {} | {} | {}",
                    request.getMethod(), request.getRequestURI(), status.value(), message, detail);
        }
    }

    /**
     * Short, single-line description of what actually went wrong.
     */
    private String describe(final Throwable ex) {
        final StringBuilder sb = new StringBuilder(ex.getClass().getSimpleName());
        if (ex instanceof SQLException sql) {
            sb.append(" [SQLState=")
                    .append(sql.getSQLState())
                    .append(", code=")
                    .append(sql.getErrorCode())
                    .append(']');
        }
        sb.append(" | root cause: ").append(rootMessage(ex));
        return sb.toString();
    }

    private String rootMessage(final Throwable ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        final String msg = root.getMessage();
        return root.getClass().getSimpleName() + (msg == null ? "" : ": " + msg);
    }

    @SuppressWarnings("unchecked")
    private <T extends Throwable> T findCause(final Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof SQLException) return ((Class<T>) SQLException.class).cast(current);
            if (current.getCause() == current) break;
            current = current.getCause();
        }
        return null;
    }

    private record DbError(HttpStatus status, String message) {
    }
}