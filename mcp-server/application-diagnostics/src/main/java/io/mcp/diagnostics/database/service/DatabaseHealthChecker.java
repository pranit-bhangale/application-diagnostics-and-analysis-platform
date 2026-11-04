package io.mcp.diagnostics.database.service;

import io.mcp.diagnostics.database.dto.DatabaseHealthRequest;
import io.mcp.diagnostics.database.dto.DatabaseHealthResponse;

public interface DatabaseHealthChecker {

    String resolve(DatabaseHealthRequest request);

    DatabaseHealthResponse check(DatabaseHealthRequest request);
}