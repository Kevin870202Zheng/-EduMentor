package com.edumentor.common.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class ValidationException extends BusinessException {

    private final List<FieldError> fieldErrors;

    public ValidationException(String message) {
        super(400, message);
        this.fieldErrors = List.of();
    }

    public ValidationException(String message, List<FieldError> fieldErrors) {
        super(400, message);
        this.fieldErrors = fieldErrors;
    }

    @Getter
    public static class FieldError {
        private final String field;
        private final String message;
        private final Object rejectedValue;

        public FieldError(String field, String message, Object rejectedValue) {
            this.field = field;
            this.message = message;
            this.rejectedValue = rejectedValue;
        }
    }
}
