package io.mcp.diagnostics.backend.tool;

import io.mcp.diagnostics.backend.dto.BackendUrlRequest;
import io.mcp.diagnostics.backend.dto.BackendUrlResponse;
import io.mcp.diagnostics.backend.service.BackendConnection;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BackendTool {

    private final BackendConnection backendConnection;

    @McpTool(
            name = "check_backend_health",
            title = "Check Backend Health",
            description = """
                    Connects to the given backend URL and reports whether it is healthy,
                    its HTTP status, and the response time in milliseconds.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    title = "Backend Health Check",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true
            )
    )
    public BackendUrlResponse checkBackendHealth(
            @McpToolParam(description = "Backend URL to check") BackendUrlRequest request
    ) {
        return this.backendConnection.check(request);
    }
}
