package com.edumentor.moment.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 法律风险检测结果（存 moments.ai_review JSONB）。
 * <p>设计文档: .youcoder/plans/moments-legal-review-design.html (v1.0) §4.2</p>
 * <p>involvesLegal=false 时不入库（ai_review 存 null）。</p>
 */
@Data
public class LegalReviewResult {

    /** 是否涉及法律问题 */
    private Boolean involvesLegal;

    /** 问题类别（交通/消费/网络/校园/人身财产/行政…） */
    private String category;

    /** 法律性质定性（民事违法/行政违法/刑事违法/一般违规） */
    private String legalNature;

    /** 法律依据（法条引用，不确定时写"依据《XX法》相关规定"） */
    private String legalBasis;

    /** 风险提示 */
    private String riskTips;

    /** 建议列表（2-4 条） */
    private List<String> suggestions = new ArrayList<>();

    /** 置信度：high/medium/low */
    private String confidence;
}
