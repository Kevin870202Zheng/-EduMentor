package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 小E 讨论点评响应 DTO。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionReplyResponse {
    /** AI同学小E 的回应文本 */
    private String reply;
}
