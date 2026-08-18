package com.edumentor.classroom.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 小E 讨论点评请求 DTO。
 * 前端讨论面板提交学生观点后，调用本接口生成 AI同学小E 的回应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscussionReplyRequest {
    /** 讨论话题（discussion action 的 topic） */
    private String topic;
    /** 引导语（discussion action 的 prompt） */
    private String prompt;
    /** 学生表达的观点（快捷选择或自由输入） */
    private String studentView;
    /** 预设观点选项（可选，供小E 引用其他同学可能的角度） */
    private List<String> options;
}
