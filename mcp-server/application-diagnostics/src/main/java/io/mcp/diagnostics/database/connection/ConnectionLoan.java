package io.mcp.diagnostics.database.connection;

import org.springframework.util.function.ThrowingFunction;
import org.springframework.util.function.ThrowingSupplier;

import java.sql.Connection;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ConnectionLoan {

    private ConnectionLoan() {
    }

    /**
     * Open via supplier, lend to function, always close via consumer.
     */
    public static <R> R withConnection(
            ThrowingSupplier<Connection> open,
            ThrowingFunction<Connection, R> work,
            Consumer<Connection> close
    ) {
        Connection connection = open.get();
        try {
            return work.apply(connection);
        } finally {
            close.accept(connection);
        }
    }

    /**
     * Functional try/catch: map success and failure to the same result type.
     */
    public static <T, R> R attempt(
            ThrowingSupplier<T> action,
            Function<T, R> onSuccess,
            Function<Exception, R> onFailure
    ) {
        try {
            return onSuccess.apply(action.get());
        } catch (Exception e) {
            return onFailure.apply(e);
        }
    }
}
