package com.edumentor.common.exception;

public class DuplicateResourceException extends BusinessException {

    public DuplicateResourceException(String resource, Object value) {
        super(409, resource + " 已存在: " + value);
    }

    public DuplicateResourceException(String message) {
        super(409, message);
    }
}
