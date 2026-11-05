package io.mcp.diagnostics.backend.exception;

import io.mcp.diagnostics.exception.BaseException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class BackendUnhealthyException extends BaseException {

    public BackendUnhealthyException(String message) {
        super(message);
    }

    public HttpStatus getStatusCode() {
        return HttpStatus.SERVICE_UNAVAILABLE;
    }
}