# 教学场景大纲生成

## 课程与知识点信息

- **课程名称**：{{courseName}}
- **知识点名称**：{{knowledgePointName}}
- **知识点描述**：{{knowledgePointDescription}}
- **知识点详细内容**：{{knowledgePointContent}}
- **难度等级**：{{difficulty}}（1-5）
- **本章结构**：{{chapterStructure}}

## 本章知识点详细内容

{{aggregatedContent}}

## 教材原文参考

以下为本课程的教材原文节选，请基于此教材内容设计课堂，确保讲授内容与教材一致：

{{textbookExcerpt}}

## 本章参考习题

以下习题来自本章的题库，供你参考题目难度和风格（请生成类似难度和格式的课堂测验题）：

{{referenceQuestions}}

## 设计要求

1. 请围绕「{{knowledgePointName}}」这个知识点设计课堂
2. 难度等级 {{difficulty}} 决定了讲解深度和案例复杂度
3. 课堂面向中小学生，语言要通俗易懂
4. 结合实际案例或生活场景辅助讲解
5. 如果提供了教材原文，请确保课堂内容与教材原文保持一致
6. 如果提供了参考习题，请确保课堂测验题的难度和风格与之匹配

## 输出要求

请严格按照 system prompt 中定义的 JSON 格式输出，包含 `scenes` 数组和 `classroomTitle`、`classroomDescription` 字段。
