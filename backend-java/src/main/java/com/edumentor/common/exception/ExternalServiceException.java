package com.edumentor.common.exception;

import lombok.Getter;

@Getter
public class ExternalServiceException extends BusinessException {

    private final String serviceName;

    public ExternalServiceException(String serviceName, String message) {
        super(502, message);
        this.serviceName = serviceName;
    }

    public ExternalServiceException(String serviceName, String message, Throwable cause) {
        super(502, message, cause);
        this.serviceName = serviceName;
    }
}
