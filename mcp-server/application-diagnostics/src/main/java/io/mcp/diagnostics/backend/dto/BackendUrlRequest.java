package io.mcp.diagnostics.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

public record BackendUrlRequest(
        @JsonProperty(required = true)
        @JsonPropertyDescription("Backend Url")
        String backendUrl
) {
}
