package com.edumentor.classroom.service;

import com.edumentor.engine.llm.LLMService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * AI Agent 内容生成服务。
 * <p>
 * 根据角色（AI教师/AI同学/苏格拉底导师）生成教学内容文本。
 * 与 DirectorService 配合使用，Director 决定"谁说话"，AgentService 决定"说什么"。
 * </p>
 */
@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);

    private final LLMService llmService;

    public AgentService(LLMService llmService) {
        this.llmService = llmService;
    }

    /**
     * 根据Agent角色和任务生成内容。
     *
     * @param agentRole 角色: TEACHER / CLASSMATE / TUTOR
     * @param task      任务描述
     * @param context   上下文
     * @return 生成的文本内容
     */
    public String generate(String agentRole, String task, String context) {
        return switch (agentRole) {
            case "TEACHER" -> generateTeacher(task, context);
            case "CLASSMATE" -> generateClassmate(task, context);
            case "TUTOR" -> generateTutor(task, context);
            default -> generateTeacher(task, context);
        };
    }

    /**
     * AI教师内容生成。
     * 教学风格：专业、清晰、有耐心、善于举例
     */
    public String generateTeacher(String task, String context) {
        String systemPrompt = "你是一位经验丰富的AI教师，性格耐心、讲解清晰。" +
                "你善于用类比和实例帮助学生理解抽象概念。" +
                "请用通俗易懂的语言讲解，注意逻辑连贯，适合学生认知水平。";

        String prompt = String.format("""
                当前教学任务：%s
                教学上下文：%s
                
                请根据上述任务生成教学内容。
                """, task, context);

        return callLLM(systemPrompt, prompt);
    }

    /**
     * AI同学内容生成。
     * 风格：好奇、略带疑惑、真实反映学生学习时的思考
     */
    public String generateClassmate(String task, String context) {
        String systemPrompt = "你是一位名叫[小E]的AI同学，和真实学生一起上课。" +
                "你会在老师讲解完复杂概念后，提出一些学生可能也会有的疑问。" +
                "你的提问要自然、真实，不要问太简单或太刁钻的问题。" +
                "你的语气要像真正的学生一样，带有思考的过程。";

        String prompt = String.format("""
                课堂当前内容：%s
                你的任务：%s
                
                请以"小E"的口吻提出一个学习上的问题或疑惑。
                """, context, task);

        return callLLM(systemPrompt, prompt);
    }

    /**
     * 苏格拉底导师内容生成。
     * 风格：引导式、启发式、不直接给答案
     */
    public String generateTutor(String task, String context) {
        String systemPrompt = "你是一位苏格拉底导师，你的教学方法是[产婆术]——" +
                "通过巧妙的追问引导学生自己发现答案，而不是直接告诉他们。" +
                "你的问题要层层递进，从简单到复杂，帮助学生构建自己的理解。" +
                "即使学生答错了，也要先肯定他们的思考过程，再引导他们发现错误。";

        String prompt = String.format("""
                引导任务：%s
                学生的当前状态：%s
                
                请根据上述信息，生成2-3个启发式追问，引导学生自己得出正确答案。
                不要直接给出答案。
                """, task, context);

        return callLLM(systemPrompt, prompt);
    }

    private String callLLM(String systemPrompt, String prompt) {
        try {
            return llmService.ask(systemPrompt, prompt).getContent();
        } catch (Exception e) {
            log.warn("Agent LLM call failed: {}", e.getMessage());
            return "让我思考一下... " + prompt;
        }
    }
}
