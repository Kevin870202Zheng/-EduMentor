## 场景信息

- **场景标题**：{{sceneTitle}}
- **场景描述**：{{sceneDescription}}
- **关键知识点**：{{keyPoints}}
- **教学目标**：{{teachingObjective}}

## 上下文信息

- **所属知识点**：{{knowledgePointName}}
- **难度等级**：{{difficulty}}（1-5）
- **所属课程**：{{courseName}}

## 知识点详细内容

以下是你讲解的知识依据，请确保讲稿内容与之一致：

{{knowledgePointContent}}

## 本章知识背景

{{aggregatedContent}}

## 任务

为本场景撰写**教师讲解稿**（阶段 A，只写讲稿，不写视觉）。

要求：
1. 严格按 system prompt 的 JSON 格式输出（script.sentences[]）
2. 每句 ≤ 40 字、口语化、带语义标签与关键词
3. 共 8~16 句，含 引入/概念/事实/设问/结论 等语义
4. 只输出纯 JSON，不要 markdown 代码块

