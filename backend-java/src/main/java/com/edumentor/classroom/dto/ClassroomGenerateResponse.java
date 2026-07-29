package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 课堂生成响应 DTO。
 * 生成是异步的，返回 jobId 用于轮询状态。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClassroomGenerateResponse {
    private String jobId;
    private String status;
    private String classroomId;
    private String message;
}
