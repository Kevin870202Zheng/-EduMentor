-- 法学概论课堂演示数据
INSERT INTO classrooms (id, course_id, knowledge_point_id, title, description, difficulty, total_duration_seconds, status, scene_count, version)
VALUES (
  'a1111111-1111-1111-1111-111111111111',
  '7e79c597-e907-4680-a7f3-be69bcd7eed8',
  '7fd77f71-4cf5-4190-a1ac-7b87e675c765',
  '法学概论导论',
  '了解法的基本概念、特征和分类，掌握法学的研究对象和方法',
  3, 900, 'published', 5, 1
);

INSERT INTO scenes (id, classroom_id, title, description, scene_type, order_index, estimated_duration_seconds, content_json)
VALUES (
  'a1111111-1111-1111-1111-111111111112',
  'a1111111-1111-1111-1111-111111111111',
  '法的概念与特征', '什么是法？法有哪些基本特征？',
  'slide', 0, 180,
  '{"key_points":["法是调整人们行为的社会规范","法由国家制定或认可","法具有国家强制力"],"learning_objectives":["理解法的基本概念","掌握法的三个基本特征"]}'
);

INSERT INTO scene_actions (scene_id, action_type, order_index, params_json, duration_ms) VALUES
('a1111111-1111-1111-1111-111111111112', 'scene_transition', 0, '{"title":"法的概念与特征","subtitle":"了解法的基本概念，掌握法的三个基本特征","icon":"⚖️","tags":["法学基础","法的特征","社会规范"]}', 3000),
('a1111111-1111-1111-1111-111111111112', 'speech', 1, '{"text":"同学们好！今天我们开始学习法学概论课程。首先让我们来了解法的基本概念。","prosody":{"rate":"0.9"}}', 8000),
('a1111111-1111-1111-1111-111111111112', 'speech_with_highlight', 2, '{"text":"法是 由国家制定或认可的 以权利义务为内容的 具有国家强制力的 社会规范。","highlights":[{"text":"国家制定或认可","color":"#FFD700"},{"text":"国家强制力","color":"#FF6B6B"}],"prosody":{"rate":"0.85"}}', 15000),
('a1111111-1111-1111-1111-111111111112', 'wb_draw_text', 3, '{"content":"法的三个基本特征：\n\n1. **规范性** — 法是调整人们行为的社会规范\n\n2. **国家意志性** — 法由国家制定或认可\n\n3. **国家强制性** — 法以国家强制力保证实施","text_type":"markdown"}', 12000),
('a1111111-1111-1111-1111-111111111112', 'pause_for_thought', 4, '{"text":"思考一下：法律与道德有什么不同？","duration_ms":5000}', 5000);

INSERT INTO scenes (id, classroom_id, title, description, scene_type, order_index, estimated_duration_seconds, content_json)
VALUES (
  'a1111111-1111-1111-1111-111111111113',
  'a1111111-1111-1111-1111-111111111111',
  '知识检测', '检测你对法学基础知识的掌握程度',
  'quiz', 1, 120,
  '{"quiz_type":"single_choice","difficulty":3}'
);

INSERT INTO scene_actions (scene_id, action_type, order_index, params_json, duration_ms) VALUES
('a1111111-1111-1111-1111-111111111113', 'scene_transition', 0, '{"title":"知识检测","subtitle":"来测试一下你对法的概念的理解","icon":"📝","tags":["课堂练习","知识巩固"]}', 3000),
('a1111111-1111-1111-1111-111111111113', 'speech', 1, '{"text":"让我们来做一道选择题，检验一下刚才的学习效果。","prosody":{"rate":"0.9"}}', 4000),
('a1111111-1111-1111-1111-111111111113', 'quiz', 2, '{"question":"下列哪一项是法的核心特征？","options":[{"label":"A","text":"法是约定俗成的行为规范"},{"label":"B","text":"法由国家制定或认可并具有国家强制力"},{"label":"C","text":"法仅适用于特定社会群体"},{"label":"D","text":"法是个人意志的体现"}],"correct_answer":"B","explanation":"法的核心特征在于由国家制定或认可，并以国家强制力保证实施。","difficulty":2}', 20000);

INSERT INTO scenes (id, classroom_id, title, description, scene_type, order_index, estimated_duration_seconds, content_json)
VALUES (
  'a1111111-1111-1111-1111-111111111114',
  'a1111111-1111-1111-1111-111111111111',
  '讨论：法与道德的关系', '思考法与其他社会规范的联系与区别',
  'discussion', 2, 180,
  '{"discussion_topic":"法在社会治理中的地位","guiding_questions":["法是万能的吗？","法与道德发生冲突时应该怎么办？"]}'
);

INSERT INTO scene_actions (scene_id, action_type, order_index, params_json, duration_ms) VALUES
('a1111111-1111-1111-1111-111111111114', 'scene_transition', 0, '{"title":"讨论：法与道德的关系","subtitle":"深入探讨法在社会治理中的作用","icon":"💭","tags":["讨论","法学思维","法治"]}', 3000),
('a1111111-1111-1111-1111-111111111114', 'speech', 1, '{"text":"接下来我们进入讨论环节。请思考：法是不是万能的？法和道德是什么关系？","prosody":{"rate":"0.9"}}', 8000),
('a1111111-1111-1111-1111-111111111114', 'discussion', 2, '{"topic":"法在社会治理中是否具有局限性？","prompt":"请结合你生活中的例子，谈谈对法和道德关系的理解。","think_time_ms":10000}', 20000),
('a1111111-1111-1111-1111-111111111114', 'speech', 3, '{"text":"很好的讨论！法虽然不是万能的，但它是现代社会不可或缺的治理工具。","prosody":{"rate":"0.9"}}', 10000);

INSERT INTO scenes (id, classroom_id, title, description, scene_type, order_index, estimated_duration_seconds, content_json)
VALUES (
  'a1111111-1111-1111-1111-111111111115',
  'a1111111-1111-1111-1111-111111111111',
  '法的分类与体系', '系统的法的分类方法及法律体系概述',
  'slide', 3, 200,
  '{"key_points":["根本法 vs 普通法","实体法 vs 程序法","一般法 vs 特别法"],"learning_objectives":["掌握法的分类","理解中国特色社会主义法律体系"]}'
);

INSERT INTO scene_actions (scene_id, action_type, order_index, params_json, duration_ms) VALUES
('a1111111-1111-1111-1111-111111111115', 'scene_transition', 0, '{"title":"法的分类与体系","subtitle":"系统地了解法的分类方法","icon":"🏛️","tags":["法律体系","法的分类","法治建设"]}', 3000),
('a1111111-1111-1111-1111-111111111115', 'speech', 1, '{"text":"下面我们来学习法的分类。法可以从不同角度进行分类。","prosody":{"rate":"0.9"}}', 5000),
('a1111111-1111-1111-1111-111111111115', 'wb_draw_text', 2, '{"content":"## 法的分类\n\n| 类型 | 例子 |\n|------|------|\n| 根本法/普通法 | 宪法 vs 民法典 |\n| 实体法/程序法 | 刑法 vs 刑事诉讼法 |\n| 一般法/特别法 | 合同法 vs 劳动合同法 |","text_type":"markdown"}', 15000),
('a1111111-1111-1111-1111-111111111115', 'pause_for_thought', 3, '{"text":"想一想：为什么需要区分法的不同类型？","duration_ms":4000}', 4000);

INSERT INTO scenes (id, classroom_id, title, description, scene_type, order_index, estimated_duration_seconds, content_json)
VALUES (
  'a1111111-1111-1111-1111-111111111116',
  'a1111111-1111-1111-1111-111111111111',
  '课程总结', '回顾本课的核心知识点',
  'review', 4, 120,
  '{"summary_points":["法是具有三大特征的社会规范","法的核心特征是国家强制力","法是社会治理不可或缺的工具","法可以从多角度进行分类"]}'
);

INSERT INTO scene_actions (scene_id, action_type, order_index, params_json, duration_ms) VALUES
('a1111111-1111-1111-1111-111111111116', 'scene_transition', 0, '{"title":"课程总结","subtitle":"回顾本节课的核心知识点","icon":"🎯","tags":["总结","回顾"]}', 3000),
('a1111111-1111-1111-1111-111111111116', 'speech', 1, '{"text":"让我们总结今天的学习。我们学习了法的概念、三大基本特征和分类方法。","prosody":{"rate":"0.9"}}', 8000),
('a1111111-1111-1111-1111-111111111116', 'wb_draw_text', 2, '{"content":"### 核心要点\n\n1. **法的概念**：由国家制定或认可的社会规范\n2. **三大特征**：规范性、国家意志性、国家强制性\n3. **法的分类**：根本法、普通法、实体法、程序法","text_type":"markdown"}', 10000),
('a1111111-1111-1111-1111-111111111116', 'speech', 3, '{"text":"今天的课就到这里，希望大家对法学有了系统的认识。下次再见！","prosody":{"rate":"0.9"}}', 10000);
