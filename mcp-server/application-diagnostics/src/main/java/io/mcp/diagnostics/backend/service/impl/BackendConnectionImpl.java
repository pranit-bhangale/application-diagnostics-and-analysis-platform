package io.mcp.diagnostics.backend.service.impl;

import io.mcp.diagnostics.backend.connection.ConnectionLoan;
import io.mcp.diagnostics.backend.dto.BackendUrlRequest;
import io.mcp.diagnostics.backend.dto.BackendUrlResponse;
import io.mcp.diagnostics.backend.exception.BackendUnhealthyException;
import io.mcp.diagnostics.backend.service.BackendConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

@Slf4j
@Service
public class BackendConnectionImpl implements BackendConnection {

    private static final Predicate<String> IS_VALID_URL = url -> {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            return uri.getHost() != null
                    && uri.getScheme() != null
                    && Set.of("http", "https").contains(uri.getScheme().toLowerCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    };

    private static final Predicate<HttpStatusCode> IS_HEALTHY = HttpStatusCode::is2xxSuccessful;

    private final Supplier<Long> clock = System::currentTimeMillis;

    private final ThrowingSupplier<RestClient> clientSupplier;

    public BackendConnectionImpl(RestClient backendRestClient) {
        this.clientSupplier = () -> backendRestClient;
    }

    @Override
    public BackendUrlResponse check(BackendUrlRequest request) {
        String url = request.backendUrl();
        if (!IS_VALID_URL.test(url)) {
            return BackendUrlResponse.builder()
                    .backendUrl(url)
                    .healthy(false)
                    .status("INVALID_URL")
                    .responseTimeMs(0)
                    .message("URL must be a valid http/https URL")
                    .build();
        }
        long start = clock.get();
        Supplier<Long> elapsed = () -> clock.get() - start;
        ThrowingSupplier<HttpStatusCode> healthyStatus = () ->
                ConnectionLoan.withRestClient(this.clientSupplier, client -> {
                    HttpStatusCode status = ConnectionLoan.fetchStatus(client, url.trim());
                    if (!IS_HEALTHY.test(status)) {
                        throw new BackendUnhealthyException(status.toString());
                    }
                    return status;
                });
        Function<HttpStatusCode, BackendUrlResponse> onSuccess = status ->
                BackendUrlResponse.builder()
                        .backendUrl(url).healthy(true)
                        .status(String.valueOf(status.value()))
                        .responseTimeMs(elapsed.get())
                        .message("Backend is healthy")
                        .build();

        Function<Exception, BackendUrlResponse> onFailure = ex -> {
            String statusLabel;
            if (ex instanceof BackendUnhealthyException unhealthy) {
                statusLabel = String.valueOf(unhealthy.getStatusCode().value());
            } else if (ex instanceof ResourceAccessException) {
                statusLabel = "UNREACHABLE";
            } else {
                statusLabel = "ERROR";
            }
            return BackendUrlResponse.builder()
                    .backendUrl(url).healthy(false)
                    .status(statusLabel)
                    .responseTimeMs(elapsed.get())
                    .message(ex.getMessage())
                    .build();
        };
        return ConnectionLoan.attempt(healthyStatus, onSuccess, onFailure);
    }
}
