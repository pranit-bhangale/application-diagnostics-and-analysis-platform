package io.mcp.diagnostics.backend.service;

import io.mcp.diagnostics.backend.dto.BackendUrlRequest;
import io.mcp.diagnostics.backend.dto.BackendUrlResponse;

/**
 * Owns everything about checking a backend: validation, connecting,
 * health decision, timing, error mapping and logging.
 */
@FunctionalInterface
public interface BackendConnection {

    BackendUrlResponse check(BackendUrlRequest request);
}
