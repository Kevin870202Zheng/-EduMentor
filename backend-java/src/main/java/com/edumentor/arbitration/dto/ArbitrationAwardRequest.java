package com.edumentor.arbitration.dto;

import com.edumentor.arbitration.entity.enums.ArbitrationPhase;
import lombok.Data;

/**
 * 学生（仲裁人）提交裁决书的请求体。
 * <p>
 * result 取值（仲裁语境）：
 * <ul>
 *   <li>SUPPORT  — 支持申请人（原告）请求</li>
 *   <li>REJECT   — 驳回申请人（原告）请求</li>
 *   <li>PARTIAL  — 部分支持申请人请求</li>
 * </ul>
 * </p>
 */
@Data
public class ArbitrationAwardRequest {

    /** 阶段（PRE/POST），缺省取 POST */
    private ArbitrationPhase phase;

    /** 裁决结果：SUPPORT / REJECT / PARTIAL */
    private String result;

    /** 裁决理由（自由文本） */
    private String reason;
}
