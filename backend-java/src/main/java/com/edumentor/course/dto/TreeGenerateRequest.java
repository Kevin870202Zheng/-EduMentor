package com.edumentor.course.dto;

import lombok.Data;

/**
 * AI 生成树结构请求 DTO。
 * <p>
 * 包含生成粒度参数，支持按课程配置生成层级深度。
 * </p>
 *
 * @author EduMentor Team
 * @version 1.0
 */
@Data
public class TreeGenerateRequest {

    /**
     * 生成粒度：
     * <ul>
     *   <li><code>STANDARD</code> — 编 → 卷 → 章 → 节（默认，四层）</li>
     *   <li><code>COMPACT</code> — 章 → 节（两层，适合知识点较少的情况）</li>
     *   <li><code>FULL</code> — 编 → 卷 → 章 → 节 → 知识点（五层，适合大型课程）</li>
     * </ul>
     */
    private String granularity = "STANDARD";
}
