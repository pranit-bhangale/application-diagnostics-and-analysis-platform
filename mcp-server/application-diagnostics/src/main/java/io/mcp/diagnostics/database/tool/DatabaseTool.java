package io.mcp.diagnostics.database.tool;

import io.mcp.diagnostics.database.dto.DatabaseHealthRequest;
import io.mcp.diagnostics.database.dto.DatabaseHealthResponse;
import io.mcp.diagnostics.database.service.DatabaseHealthChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DatabaseTool {

    private final DatabaseHealthChecker healthChecker;

    @McpTool(
            name = "check_database_health",
            title = "Check Database Health",
            description = """
                    Checks relational database connectivity and availability using JDBC.
                    Supports PostgreSQL.
                    Accepts database type, host, port, database name, and credentials.
                    Returns connection health status, database product, version, and response latency.
                    """,
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    title = "JDBC Database Health Check",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true
            )
    )
    public DatabaseHealthResponse checkDatabaseHealth(
            @McpToolParam(description = "Database engine") String dbType,
            @McpToolParam(description = "Hostname or IP address of the database server.") String host,
            @McpToolParam(description = "Port on which the database server listens.") Integer port,
            @McpToolParam(description = "Name of the target database or database service.") String dbName,
            @McpToolParam(description = "Database authentication username.") String username,
            @McpToolParam(description = "Database authentication password.") String password,
            @McpToolParam(description = "Connection validation timeout in seconds.", required = false) Integer timeoutSeconds
    ) {
        return this.healthChecker.check(DatabaseHealthRequest.builder()
                .dbType(dbType)
                .host(host)
                .port(port)
                .dbName(dbName)
                .username(username)
                .password(password)
                .timeoutSeconds(timeoutSeconds)
                .build());
    }
}