## 场景信息

- **场景标题**：{{sceneTitle}}
- **场景类型**：{{sceneType}}
- **场景描述**：{{sceneDescription}}
- **关键知识点**：{{keyPoints}}
- **教学目标**：{{teachingObjective}}

## 上下文信息

- **所属知识点**：{{knowledgePointName}}
- **知识点详细内容**：{{knowledgePointContent}}
- **所属课程**：{{courseName}}
- **难度等级**：{{difficulty}}（1-5）

## 本章知识背景

以下是本章的知识点详细内容，请确保生成的讲解内容与之吻合：

{{aggregatedContent}}

## 生成要求

请根据以上信息，生成该场景的完整教学内容和教学动作序列。

### 如果是 slide 类型
请生成：
1. 一段完整的教师讲解词（口语化，适合教师朗读）
2. **1-2 页元素级幻灯片布局（content.slides[]）**，包含标题/要点/图表/公式/示意图等元素（严格遵循 system prompt 中的幻灯片布局规则）
3. 在适当时机提出的引导性问题
4. 教学动作序列中必须用 `show_slide` 动作逐页展示布局
5. 讲解内容必须与提供的知识点详细内容保持一致

### 如果是 interactive 类型
请生成：
1. 引入动手环节的引导语（introScript）
2. 操作说明讲解词（teacherScript）
3. **一个完整的自包含 HTML 交互组件（content.widget）**：含 `subtype`（simulation/game/explore）、`title`、`config`（variables/targets）、`html`（完整 HTML 文档，严格遵循 system prompt 中的交互组件规则）
4. 动作序列模式：`speech(引入) → launch_widget → widget_set_state(演示) → widget_highlight(指向) → speech(布置任务) → pause_for_thought(学生操作) → widget_annotate(总结) → speech(点评)`
5. widget_* 动作的 target 必须是组件 HTML 中真实存在的元素 id

### 如果是 quiz 类型
请生成：
1. 一段引入测验的引导语
2. 2 道高质量的选择题（每题4个选项，含正确索引和解析）
3. 每道题的详细解析
4. 测验相关的教学动作序列
5. **每个 quiz action 必须包含 question、options（4个字符串）、correctIndex（0-3）、explanation 四个字段，缺一不可**

### 如果是 discussion 类型
请生成：
1. 引入讨论的引导语
2. 一个核心讨论议题
3. 2-3 个支架性问题
4. 讨论相关的教学动作序列

### 如果是 review 类型
请生成：
1. 总结性的教师讲解词
2. **知识总结思维导图（content.summaryMap）**：root 中心主题 + branches 分支（label/children/color）
3. 一句让学生记住的话（takeawayMessage）
4. 教学动作序列中用 `show_slide` 动作展示思维导图

## 输出格式

严格按照 system prompt 中定义的 JSON 格式输出。不要 markdown 代码块，纯 JSON。
