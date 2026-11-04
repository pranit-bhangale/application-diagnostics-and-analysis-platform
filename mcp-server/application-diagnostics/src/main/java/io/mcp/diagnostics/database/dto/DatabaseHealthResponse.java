package io.mcp.diagnostics.database.dto;

import lombok.Builder;

@Builder
public record DatabaseHealthResponse(
        boolean healthy,
        String databaseProduct,
        String databaseVersion,
        long responseTimeMs,
        String message
) {
}