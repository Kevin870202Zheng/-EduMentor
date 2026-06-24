package com.edumentor.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String timestamp;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code(200)
            .message("操作成功")
            .data(data)
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
            .code(200)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> ok() {
        return ApiResponse.<T>builder()
            .code(200)
            .message("操作成功")
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> error(int code, String message) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public static <T> ApiResponse<T> error(int code, String message, T data) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .data(data)
            .timestamp(LocalDateTime.now().toString())
            .build();
    }

    public boolean isSuccess() {
        return code == 200;
    }
}
