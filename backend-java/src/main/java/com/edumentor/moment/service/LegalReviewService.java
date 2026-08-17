package com.edumentor.moment.service;

import com.edumentor.engine.llm.LLMService;
import com.edumentor.moment.dto.LegalReviewResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI 法律风险检测服务（同学圈核心差异化能力）。
 * <p>
 * 对动态文本做法律风险分析：识别涉及法律问题的内容（交通违法/消费维权/网络言论/校园欺凌/
 * 侵犯隐私/盗窃损坏财物/合同纠纷等生活场景），输出法律性质定性 + 法条依据 + 风险提示 + 建议；
 * 不涉及法律问题的内容返回 involvesLegal=false。
 * </p>
 * 设计文档: .youcoder/plans/moments-legal-review-design.html (v1.0) §4
 */
@Service
public class LegalReviewService {

    private static final Logger log = LoggerFactory.getLogger(LegalReviewService.class);

    private final LLMService llmService;

    public LegalReviewService(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * 同步检测动态文本是否涉及法律问题。
     *
     * @return 涉及法律时返回完整 LegalReviewResult；不涉及时 involvesLegal=false；
     *         LLM 调用失败/超时时返回 null（调用方降级为"无提示"，不阻塞发布）
     */
    public LegalReviewResult review(String content) {
        if (content == null || content.isBlank()) {
            LegalReviewResult r = new LegalReviewResult();
            r.setInvolvesLegal(false);
            return r;
        }
        // 极短内容（<4字）直接判定不涉及，节省一次 LLM 调用
        if (content.trim().length() < 4) {
            LegalReviewResult r = new LegalReviewResult();
            r.setInvolvesLegal(false);
            return r;
        }

        String systemPrompt = "你是一名面向中小学生的普法教育 AI。请判断用户的朋友圈动态内容是否涉及法律问题。\n"
                + "【识别范围】以下生活场景属于涉及法律问题：\n"
                + "1. 交通违法（骑车逆行、闯红灯、无证驾驶、酒驾等）；\n"
                + "2. 未成年人消费维权（购买商品/服务纠纷、诱导消费等）；\n"
                + "3. 网络言论（侮辱、诽谤、人肉搜索、造谣传谣）；\n"
                + "4. 校园欺凌、打架斗殴；\n"
                + "5. 侵犯隐私、盗窃、故意损坏他人财物；\n"
                + "6. 合同/交易纠纷、诈骗、赌博、有偿代课代考等。\n"
                + "【不涉及】纯学习、生活、情感分享（如\"今天作业好多\"\"周末去公园了\"\"这道题终于做出来了\"）一律判定不涉及。\n\n"
                + "【输出要求】\n"
                + "1. involvesLegal 布尔值；\n"
                + "2. 涉及法律时：给出类别 category、法律性质定性 legalNature（民事违法/行政违法/刑事违法/一般违规）、"
                + "法律依据 legalBasis（优先引用准确法条条款，不确定时写\"依据《XX法》相关规定\"，禁止编造条款号）、"
                + "风险提示 riskTips（1-2 句）、建议 suggestions（2-4 条，具体可操作）；\n"
                + "3. confidence 置信度：high/medium/low；\n"
                + "4. 语气提示性、教育性，面向中小学生，不得输出用户个人信息；\n"
                + "5. 不涉及法律问题时，category/legalNature/legalBasis/riskTips/suggestions 置空或给默认值，involvesLegal=false。";

        String userPrompt = "学生朋友圈动态内容：\n\"" + content.trim() + "\"";

        try {
            LegalReviewResult result = llmService.askStructured(systemPrompt, userPrompt,
                    LegalReviewResult.class, "legal-review");
            if (result == null) {
                log.warn("AI 法律检测返回 null，降级为不涉及");
                return null;
            }
            if (!Boolean.TRUE.equals(result.getInvolvesLegal())) {
                LegalReviewResult r = new LegalReviewResult();
                r.setInvolvesLegal(false);
                return r;
            }
            log.info("AI 法律检测完成: category={}, nature={}", result.getCategory(), result.getLegalNature());
            return result;
        } catch (Exception e) {
            log.error("AI 法律检测失败，降级为不提示: {}", e.getMessage());
            return null;
        }
    }
}
