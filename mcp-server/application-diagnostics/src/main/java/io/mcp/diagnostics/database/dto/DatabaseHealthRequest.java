package io.mcp.diagnostics.database.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Builder;

@Builder(toBuilder = true)
public record DatabaseHealthRequest(

        @JsonProperty(required = true)
        @JsonPropertyDescription("JDBC subprotocol of the database: postgresql, mysql or mariadb")
        String dbType,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Hostname or IP address, e.g. 'localhost'")
        String host,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Port the database listens on, e.g. 5432 or 3306")
        Integer port,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Database name, e.g. 'orders'")
        String dbName,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Username, if authentication is required")
        String username,

        @JsonProperty(required = true)
        @JsonPropertyDescription("Password, if authentication is required")
        String password,

        @JsonPropertyDescription("Database timeout, Optional if not provided default value is 5")
        Integer timeoutSeconds
) {
}