package io.mcp.diagnostics.backend.connection;

import org.springframework.http.HttpStatusCode;
import org.springframework.util.function.ThrowingFunction;
import org.springframework.util.function.ThrowingSupplier;
import org.springframework.web.client.RestClient;

import java.util.function.Consumer;
import java.util.function.Function;

public final class ConnectionLoan {

    private ConnectionLoan() {
    }

    /**
     * Open via supplier, lend to function, always close via consumer.
     */
    public static <C, R> R withConnection(ThrowingSupplier<C> open, ThrowingFunction<C, R> work, Consumer<C> close) {
        C connection = open.get();
        try {
            return work.apply(connection);
        } finally {
            close.accept(connection);
        }
    }

    /**
     * Functional try/catch: map success and failure to the same result type.
     * Anything thrown by the supplier is routed to onFailure.
     */
    public static <T, R> R attempt
    (ThrowingSupplier<T> action, Function<T, R> onSuccess, Function<Exception, R> onFailure) {
        try {
            return onSuccess.apply(action.get());
        } catch (Exception e) {
            return onFailure.apply(e);
        }
    }

    /**
     * Lend a RestClient to the function. RestClient holds no resource that needs closing,
     * so the close step is a no-op consumer.
     */
    public static <R> R withRestClient(ThrowingSupplier<RestClient> open, ThrowingFunction<RestClient, R> work) {
        return withConnection(open, work, _ -> {
        });
    }

    /**
     * GET the url and return only the HTTP status (never throws on 4xx/5xx).
     */
    public static HttpStatusCode fetchStatus(RestClient client, String url) {
        return client.get()
                .uri(url)
                .exchange((_, response) -> response.getStatusCode());
    }
}