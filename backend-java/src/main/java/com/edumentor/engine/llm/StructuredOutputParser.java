package com.edumentor.engine.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 结构化输出解析器 — 将 LLM 输出解析为 Java 对象。
 * <p>
 * LLM 输出可能包含各种格式变体（Markdown 代码块、前后说明文字等），
 * 此工具类提供鲁棒的解析能力，能从各种格式中提取结构化数据。
 * </p>
 *
 * <h3>支持的输入格式</h3>
 * <ul>
 *   <li>纯 JSON：{...}</li>
 *   <li>Markdown 代码块：```json ... ```</li>
 *   <li>带说明文字的 JSON：内容...{...}...内容</li>
 *   <li>YAML 格式（实验性）</li>
 * </ul>
 *
 * @author EduMentor Team
 */
@Component
public class StructuredOutputParser {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputParser.class);

    private final ObjectMapper objectMapper;

    /** 匹配 JSON 代码块的正则 */
    private static final Pattern JSON_BLOCK_PATTERN = Pattern.compile(
            "```(?:json)?\\s*\\n?([\\s\\S]*?)```", Pattern.MULTILINE);

    /** 匹配 JSON 对象/数组的正则 */
    private static final Pattern JSON_CONTENT_PATTERN = Pattern.compile(
            "(\\[.*\\]|\\{.*\\})", Pattern.DOTALL);

    public StructuredOutputParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 从 LLM 输出中解析出指定类型的对象。
     *
     * @param output      LLM 原始输出
     * @param targetClass 目标类型
     * @param <T>         类型参数
     * @return 解析后的对象
     * @throws LlmException 如果解析失败
     */
    public <T> T parse(String output, Class<T> targetClass) {
        String json = extractJson(output);
        try {
            return objectMapper.readValue(json, targetClass);
        } catch (JsonProcessingException e) {
            throw new LlmException("Failed to parse structured output to " +
                    targetClass.getSimpleName() + ": " + e.getMessage(),
                    LlmException.ErrorCategory.PARSE_ERROR, null, e);
        }
    }

    /**
     * 从 LLM 输出中解析出指定类型引用的对象（如泛型 List&lt;T&gt;）。
     *
     * @param output        LLM 原始输出
     * @param typeReference 类型引用
     * @param <T>           类型参数
     * @return 解析后的对象
     * @throws LlmException 如果解析失败
     */
    public <T> T parse(String output, TypeReference<T> typeReference) {
        String json = extractJson(output);
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (JsonProcessingException e) {
            throw new LlmException("Failed to parse structured output: " + e.getMessage(),
                    LlmException.ErrorCategory.PARSE_ERROR, null, e);
        }
    }

    /**
     * 解析为 Map。
     *
     * @param output LLM 原始输出
     * @return 解析后的 Map
     */
    public Map<String, Object> parseToMap(String output) {
        return parse(output, new TypeReference<Map<String, Object>>() {});
    }

    /**
     * 解析为 List。
     *
     * @param output LLM 原始输出
     * @return 解析后的 List
     */
    public List<Object> parseToList(String output) {
        return parse(output, new TypeReference<List<Object>>() {});
    }

    /**
     * 从 LLM 输出中提取 JSON 字符串。
     * 处理各种格式变体：
     * <ol>
     *   <li>Markdown 代码块 (```json ... ```)</li>
     *   <li>纯 JSON 文本 ({...})</li>
     *   <li>混合文本（提取第一个 JSON 对象或数组）</li>
     *   <li>XML 包裹的 JSON</li>
     * </ol>
     *
     * @param output LLM 原始输出
     * @return 提取的 JSON 字符串
     * @throws LlmException 如果未找到 JSON 内容
     */
    public String extractJson(String output) {
        if (output == null || output.isBlank()) {
            throw new LlmException("Empty output, cannot extract JSON",
                    LlmException.ErrorCategory.PARSE_ERROR, null);
        }

        // 1. 尝试匹配 Markdown 代码块
        Matcher blockMatcher = JSON_BLOCK_PATTERN.matcher(output);
        if (blockMatcher.find()) {
            String json = blockMatcher.group(1).trim();
            if (isValidJson(json)) {
                return json;
            }
        }

        // 2. 尝试直接提取 JSON 对象或数组
        Matcher contentMatcher = JSON_CONTENT_PATTERN.matcher(output);
        if (contentMatcher.find()) {
            String json = contentMatcher.group(1).trim();
            if (isValidJson(json)) {
                return json;
            }
        }

        // 3. 尝试修复常见格式问题后解析
        String cleaned = cleanJsonString(output);
        if (isValidJson(cleaned)) {
            return cleaned;
        }

        // 4. 尝试提取第一个 { 到最后一个 }
        int firstBrace = output.indexOf('{');
        int lastBrace = output.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String candidate = output.substring(firstBrace, lastBrace + 1);
            if (isValidJson(candidate)) {
                return candidate;
            }
        }

        // 5. 尝试提取第一个 [ 到最后一个 ]
        firstBrace = output.indexOf('[');
        lastBrace = output.lastIndexOf(']');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            String candidate = output.substring(firstBrace, lastBrace + 1);
            if (isValidJson(candidate)) {
                return candidate;
            }
        }

        throw new LlmException("No valid JSON found in LLM output: " + truncate(output, 200),
                LlmException.ErrorCategory.PARSE_ERROR, null);
    }

    /**
     * 校验字符串是否为合法 JSON。
     */
    private boolean isValidJson(String json) {
        if (json == null || json.isBlank()) return false;
        try {
            objectMapper.readTree(json);
            return true;
        } catch (JsonProcessingException e) {
            return false;
        }
    }

    /**
     * 清理常见的 JSON 格式问题。
     */
    private String cleanJsonString(String text) {
        if (text == null) return "";

        String cleaned = text.trim();

        // 移除 BOM
        if (cleaned.startsWith("\uFEFF")) {
            cleaned = cleaned.substring(1);
        }

        // 替换单引号为双引号（某些 LLM 会输出单引号）
        cleaned = cleaned.replace('\'', '"');

        // 修正未转义的控制字符
        cleaned = cleaned.replaceAll("[\\x00-\\x1F]", "");

        // 移除末尾多余的逗号（常见于最后一个属性后）
        cleaned = cleaned.replaceAll(",\\s*}", "}");
        cleaned = cleaned.replaceAll(",\\s*]", "]");

        // 修正 Python 风格的 True/False/None
        cleaned = cleaned.replaceAll("\\bTrue\\b", "true");
        cleaned = cleaned.replaceAll("\\bFalse\\b", "false");
        cleaned = cleaned.replaceAll("\\bNone\\b", "null");

        return cleaned;
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
