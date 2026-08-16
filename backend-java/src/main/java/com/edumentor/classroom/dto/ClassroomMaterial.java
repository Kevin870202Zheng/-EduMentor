package com.edumentor.classroom.dto;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 课堂生成素材 — 生成管线的统一输入（设计文档 §4.3.1）。
 * <p>
 * 解耦生成管线对单一 KnowledgePoint 的强绑定：
 * <ul>
 *   <li>单知识点课堂：knowledgeContext 为该知识点聚合内容</li>
 *   <li>多知识点聚合课堂（场景一）：knowledgeContext 为勾选知识点聚合文本，knowledgePointIds 记录全部关联点</li>
 *   <li>学段协作课堂（场景二）：knowledgeContext 为故事原文+角色形象+台词+法律知识聚合文本，knowledgePointIds 记录大学映射的知识点</li>
 * </ul>
 * </p>
 */
@Data
@Builder
public class ClassroomMaterial {

    /** 所属课程 */
    private UUID courseId;

    /** 课程名称（用于 prompt） */
    private String courseName;

    /** 课堂标题（用户命名或自动生成） */
    private String title;

    /** 课堂描述 */
    private String description;

    /** 聚合内容文本（知识点聚合 / 合作素材聚合） */
    private String knowledgeContext;

    /** 教材原文节选（可选，单知识点课堂增强用） */
    private String textbookExcerpt;

    /** 参考习题（可选，单知识点课堂增强用） */
    private String referenceQuestions;

    /** 关联知识点 ID 列表（可为空列表） */
    private List<UUID> knowledgePointIds = new ArrayList<>();

    /** 生成来源：knowledge / multi_knowledge / collaborative */
    private String source = "knowledge";

    /** 扩展元数据 JSON（如协作项目 projectId） */
    private String metadata;

    /** 难度 1-5 */
    private int difficulty = 3;
}
