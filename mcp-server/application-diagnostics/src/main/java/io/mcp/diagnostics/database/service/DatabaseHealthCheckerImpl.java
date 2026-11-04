package io.mcp.diagnostics.database.service;

import io.mcp.diagnostics.database.connection.ConnectionLoan;
import io.mcp.diagnostics.database.dto.DatabaseHealthRequest;
import io.mcp.diagnostics.database.dto.DatabaseHealthResponse;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingFunction;
import org.springframework.util.function.ThrowingSupplier;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@Slf4j
@Service
public class DatabaseHealthCheckerImpl implements DatabaseHealthChecker {

    private static final Pattern SAFE_TOKEN = Pattern.compile("^[A-Za-z0-9._-]+$");

    private static final Predicate<String> IS_SAFE =
            value -> value != null && SAFE_TOKEN.matcher(value).matches();

    private static final Predicate<Integer> IS_VALID_PORT =
            port -> port != null && port >= 1 && port <= 65535;

    private static final Function<DatabaseHealthRequest, String> TO_URL = r ->
            "jdbc:%s://%s:%d/%s".formatted(r.dbType().trim().toLowerCase(Locale.ROOT),
                    r.host(), r.port(), r.dbName());

    private static final Consumer<Connection> CLOSE_QUIETLY = connection -> {
        try {
            connection.close();
        } catch (SQLException e) {
            log.warn("Failed to close connection: {}", e.getMessage());
        }
    };

    private static <T> void require(T value, Predicate<T> rule, String message) {
        Optional.ofNullable(value).filter(rule)
                .orElseThrow(() -> new IllegalArgumentException(message));
    }

    private static DatabaseHealthResponse success(Probe probe, long elapsedMs) {
        return DatabaseHealthResponse.builder()
                .healthy(probe.valid())
                .databaseProduct(probe.product())
                .databaseVersion(probe.version())
                .responseTimeMs(elapsedMs)
                .message(probe.valid() ? "Database is reachable" : "Connection opened but validation failed")
                .build();
    }

    private static DatabaseHealthResponse failure(Exception error, long elapsedMs) {
        Throwable cause = Optional.ofNullable(error.getCause()).orElse(error);
        return DatabaseHealthResponse.builder()
                .healthy(false)
                .responseTimeMs(elapsedMs)
                .message("%s: %s".formatted(cause.getClass().getSimpleName(), cause.getMessage()))
                .build();
    }

    @Override
    public String resolve(DatabaseHealthRequest request) {
        require(request.dbType(), IS_SAFE, "dbType is required");
        require(request.host(), IS_SAFE, "host is required");
        require(request.port(), IS_VALID_PORT, "port is required");
        require(request.dbName(), IS_SAFE, "dbName is required");
        return TO_URL.apply(request);
    }

    @Override
    public DatabaseHealthResponse check(DatabaseHealthRequest request) {
        long start = System.nanoTime();
        Supplier<Long> elapsedMs = () -> TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        ThrowingSupplier<Connection> open = () -> DriverManager.getConnection(
                resolve(request), request.username(), request.password());
        ThrowingFunction<Connection, Probe> probe = connection -> {
            int timeout = request.timeoutSeconds() == null ? 5 : request.timeoutSeconds();
            boolean valid = connection.isValid(timeout);
            var meta = connection.getMetaData();
            return Probe.builder()
                    .valid(valid)
                    .product(meta.getDatabaseProductName())
                    .version(meta.getDatabaseProductVersion())
                    .build();
        };
        return ConnectionLoan.attempt(
                () -> ConnectionLoan.withConnection(open, probe, CLOSE_QUIETLY),
                result -> success(result, elapsedMs.get()),
                error -> failure(error, elapsedMs.get()));
    }

    @Builder
    private record Probe(boolean valid, String product, String version) {
    }
}