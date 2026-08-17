package com.edumentor.arbitration.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 生成的模拟仲裁案件（结构化输出，存 arbitration_sessions.case_content JSONB）。
 * <p>
 * 扁平结构（非嵌套对象），便于 LLMService.askStructured 的反射式 JSON Schema 约束与 Jackson 解析。
 * 结构与 MootCourtCase 一致（案件模型通用），仅语义为「仲裁案件」。
 * </p>
 */
@Data
public class ArbitrationCase {

    /** 案件标题，如「小宇诉晨光玩具店买卖合同纠纷案」 */
    private String caseTitle;

    /** 案件事实（案情叙述，中立客观） */
    private String fact;

    /** 争议焦点列表（2-3 个） */
    private List<String> disputes = new ArrayList<>();

    /** 相关法律知识要点（从知识点提炼，用于课后评估） */
    private List<String> legalPoints = new ArrayList<>();

    /** 原告姓名/称呼（普通老百姓，如「小宇爸爸」） */
    private String plaintiffName;

    /** 原告的主张（生活化表达） */
    private String plaintiffClaim;

    /** 被告姓名/称呼（普通老百姓，如「张老板」） */
    private String defendantName;

    /** 被告的抗辩理由（生活化表达） */
    private String defendantDefense;

    /** 案件难度 1-5 */
    private Integer difficulty = 3;
}
