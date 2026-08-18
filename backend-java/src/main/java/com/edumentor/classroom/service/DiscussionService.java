package com.edumentor.classroom.service;

import com.edumentor.classroom.dto.DiscussionReplyRequest;
import com.edumentor.classroom.dto.DiscussionReplyResponse;
import com.edumentor.engine.llm.LLMService;
import com.edumentor.engine.llm.LLMResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 小E 讨论点评服务：把「讨论话题 + 学生观点」交给 LLM，
 * 生成 AI同学小E 的真实回应（肯定亮点 + 引导深度思考）。
 */
@Service
public class DiscussionService {

    private static final Logger log = LoggerFactory.getLogger(DiscussionService.class);

    private final LLMService llmService;

    public DiscussionService(LLMService llmService) {
        this.llmService = llmService;
    }

    /** 小E 人设 + 输出约束 */
    private static final String SYSTEM_PROMPT = """
            你是「小E」，一位正在和同班同学一起上课的 AI 同学（不是老师，语气平辈、亲切）。
            当前课堂正在进行讨论环节，老师抛出了一个讨论话题，一位同学刚刚表达了自己的观点。
            请你以同学的口吻，对这位同学的观点做简短点评（40~90字）：
            1. 先肯定对方观点中一个具体的亮点（引用对方的话，不要泛泛而夸）；
            2. 然后从一个新的角度（可结合其他同学可能的想法）抛出 1 个追问或补充，引导讨论深入；
            3. 全程口语化、自然，像真的在课堂发言，不要用序号/列表/标题，不要评价"很好/很棒"这种空话。
            直接输出你的发言内容，不要输出任何其他文字。
            """;

    /**
     * 生成小E 回应。LLM 失败时返回兜底文案（不让学生看到错误）。
     */
    public DiscussionReplyResponse reply(DiscussionReplyRequest req) {
        String view = req.getStudentView() == null ? "" : req.getStudentView().trim();
        if (view.isEmpty()) {
            return DiscussionReplyResponse.builder()
                    .reply("我也在思考这个问题呢！要不你先说说你的想法，我们一起讨论～")
                    .build();
        }
        try {
            StringBuilder user = new StringBuilder();
            if (req.getTopic() != null && !req.getTopic().isBlank()) {
                user.append("老师抛出的讨论话题：").append(req.getTopic().trim()).append("\n");
            }
            if (req.getPrompt() != null && !req.getPrompt().isBlank()) {
                user.append("引导语：").append(req.getPrompt().trim()).append("\n");
            }
            user.append("这位同学的观点：\n“").append(view).append("”\n");
            if (req.getOptions() != null && !req.getOptions().isEmpty()) {
                user.append("\n其他同学可能持有的不同角度（可选择性参考，不要全部罗列）：\n");
                for (int i = 0; i < req.getOptions().size(); i++) {
                    user.append(i + 1).append(". ").append(req.getOptions().get(i)).append("\n");
                }
            }
            LLMResponse resp = llmService.ask(SYSTEM_PROMPT, user.toString());
            String content = resp == null ? null : resp.getContent();
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("LLM 返回空内容");
            }
            return DiscussionReplyResponse.builder()
                    .reply(content.trim())
                    .build();
        } catch (Exception e) {
            log.warn("小E 讨论点评失败，使用兜底文案: {}", e.getMessage());
            return DiscussionReplyResponse.builder()
                    .reply("你这个想法挺有意思的！不过我在想，如果从另一个角度看会不会有新发现？比如——考虑一下实际执行时会遇到什么困难？")
                    .build();
        }
    }
}
