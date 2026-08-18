## 场景信息

- **场景标题**：{{sceneTitle}}
- **场景描述**：{{sceneDescription}}
- **关键知识点**：{{keyPoints}}
- **所属知识点**：{{knowledgePointName}}
- **难度等级**：{{difficulty}}（1-5）

## 已生成的教师讲解稿（逐句，阶段 A 产物）

以下是讲稿的每一句（含 seq、text、semantic 语义标签、keywords 关键词）。你的视觉设计必须严格对齐这些句子：

{{scriptSentences}}

## 任务

基于以上讲稿，设计本场景的**幻灯片 + 动作序列**（阶段 B）。

要求：
1. 按 system prompt 的版式模板库选型每页（cover/concept/data/compare/flow/summary）
2. 从 3 套配色系统选 1 套并保持一致
3. 每页元素内容引用对应句子的 keywords；highlightElementIds 指向即将讲解的元素
4. actions 的 speech.text 逐字使用讲稿原文；show_slide 在对应 speech 之前
5. 输出纯 JSON（含 type/content.slides/actions），不要 markdown 代码块

