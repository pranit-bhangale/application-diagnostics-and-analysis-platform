package io.mcp.diagnostics.backend.dto;

import lombok.Builder;

@Builder
public record BackendUrlResponse(
        String backendUrl,
        boolean healthy,
        String status,
        long responseTimeMs,
        String message
) {
}
