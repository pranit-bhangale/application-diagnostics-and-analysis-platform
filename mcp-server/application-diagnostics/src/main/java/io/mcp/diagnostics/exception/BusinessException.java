package io.mcp.diagnostics.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
public class BusinessException extends BaseException {
    public BusinessException(String message) {
        super(message);
    }
}
