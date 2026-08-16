package com.edumentor.classroom.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 生成的模拟法庭案件（结构化输出，存 moot_court_sessions.case_content JSONB）。
 * <p>
 * 扁平结构（非嵌套对象），便于 LLMService.askStructured 的反射式 JSON Schema 约束与 Jackson 解析。
 * </p>
 */
@Data
public class MootCourtCase {

    /** 案件标题，如「王某诉某物业公司物业服务合同纠纷案」 */
    private String caseTitle;

    /** 案件事实（案情叙述，中立客观） */
    private String fact;

    /** 争议焦点列表（2-3 个） */
    private List<String> disputes = new ArrayList<>();

    /** 相关法律知识/条文要点（从课堂知识点提炼） */
    private List<String> legalPoints = new ArrayList<>();

    /** 原告姓名/名称 */
    private String plaintiffName;

    /** 原告的诉讼请求（主张） */
    private String plaintiffClaim;

    /** 被告姓名/名称 */
    private String defendantName;

    /** 被告的抗辩理由 */
    private String defendantDefense;

    /** 案件难度 1-5 */
    private Integer difficulty = 3;
}
