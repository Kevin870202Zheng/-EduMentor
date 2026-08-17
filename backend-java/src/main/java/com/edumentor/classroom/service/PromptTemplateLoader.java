package com.edumentor.classroom.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Prompt 模板加载器。
 * <p>
 * 从 classpath 的 prompts/classroom/ 目录加载 .md 模板文件，
 * 并用传入的变量替换模板中的 {{variable}} 占位符。
 * </p>
 */
@Component
public class PromptTemplateLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateLoader.class);
    private static final String TEMPLATE_DIR = "prompts/classroom/";

    /**
     * 加载模板文件并用变量替换占位符。
     *
     * @param templateName 模板文件名（如 "outline-system.md"）
     * @param variables    替换变量映射
     * @return 渲染后的 prompt 文本
     */
    public String load(String templateName, Map<String, String> variables) {
        String template = readTemplate(templateName);
        if (template == null) {
            log.warn("Prompt template not found: {}, using empty fallback", templateName);
            return "";
        }
        return render(template, variables);
    }

    /**
     * 加载模板文件（不替换变量），适用于 system prompt。
     */
    public String loadRaw(String templateName) {
        String template = readTemplate(templateName);
        if (template == null) {
            log.warn("Prompt template not found: {}, using empty fallback", templateName);
            return "";
        }
        return template;
    }

    /**
     * 从 classpath 读取模板文件。
     */
    private String readTemplate(String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource(TEMPLATE_DIR + templateName);
            if (!resource.exists()) {
                log.warn("Template file not found: {}{}", TEMPLATE_DIR, templateName);
                return null;
            }
            try (InputStream is = resource.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            log.error("Failed to read template {}: {}", templateName, e.getMessage());
            return null;
        }
    }

    /**
     * 用变量替换模板中的 {{variable}} 占位符。
     */
    private String render(String template, Map<String, String> variables) {
        if (template == null) {
            return "";
        }
        String result = template;
        // 1. 递归展开 {{include:path}} 子模板引用（先于变量替换，避免子模板内变量无法替换）
        result = expandIncludes(result, 0);
        // 2. 变量替换
        if (variables != null && !variables.isEmpty()) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String value = entry.getValue() != null ? entry.getValue() : "";
                result = result.replace(placeholder, value);
            }
        }
        return result;
    }

    /**
     * 递归展开 {{include:path}} 占位符，将子模板（如 rules/slide-layout-rules.md）内联进主模板。
     */
    private String expandIncludes(String template, int depth) {
        if (template == null || depth > 8) {
            return template;
        }
        Matcher matcher = INCLUDE_PATTERN.matcher(template);
        StringBuffer sb = new StringBuffer();
        boolean found = false;
        while (matcher.find()) {
            found = true;
            String includePath = matcher.group(1);
            String included = readTemplate(includePath);
            if (included == null) {
                log.warn("Include template not found: {}", includePath);
                included = "";
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(included));
        }
        if (!found) {
            return template;
        }
        matcher.appendTail(sb);
        // 递归展开子模板内部的 include
        return expandIncludes(sb.toString(), depth + 1);
    }

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\{\\{include:([^}]+)}}");
}
