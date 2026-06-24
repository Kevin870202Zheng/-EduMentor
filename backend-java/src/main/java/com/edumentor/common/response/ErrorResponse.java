package com.edumentor.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private int code;
    private String message;
    private List<FieldError> errors;
    private String timestamp;

    public static ErrorResponse of(int code, String message) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public static ErrorResponse of(int code, String message, List<FieldError> errors) {
        return ErrorResponse.builder()
            .code(code)
            .message(message)
            .errors(errors)
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldError {
        private String field;
        private String message;
        private Object rejectedValue;
    }
}
