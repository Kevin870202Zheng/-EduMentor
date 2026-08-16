package com.edumentor.classroom.dto;

import com.edumentor.classroom.entity.enums.MootCourtPhase;
import lombok.Data;

/**
 * 学生（法官）提交判决的请求体。
 * <p>
 * result 取值：
 * <ul>
 *   <li>SUPPORT  — 支持原告诉讼请求</li>
 *   <li>REJECT   — 驳回原告诉讼请求</li>
 *   <li>PARTIAL  — 部分支持原告诉讼请求</li>
 * </ul>
 * </p>
 */
@Data
public class MootCourtJudgmentRequest {

    /** 法庭阶段：PRE 课前 / POST 课后 */
    private MootCourtPhase phase;

    /** 判决结果：SUPPORT / REJECT / PARTIAL */
    private String result;

    /** 判决理由（法官陈述） */
    private String reason;
}
