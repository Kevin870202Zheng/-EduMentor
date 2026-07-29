// =============================================================================
// EduMentor Classroom Mock API Server
// Provides mock responses for /api/v2/classrooms/* endpoints
// Start: node mock-server.js
// Port: 3099
// =============================================================================

const http = require('http');

const LAW101_COURSE_ID = '7e79c597-e907-4680-a7f3-be69bcd7eed8';
const CLASSROOM_ID = 'a1111111-1111-1111-1111-111111111111';

const MOCK_CLASSROOMS = [
  {
    id: CLASSROOM_ID,
    courseId: LAW101_COURSE_ID,
    knowledgePointId: '7fd77f71-4cf5-4190-a1ac-7b87e675c765',
    title: '法学概论导论',
    description: '了解法的基本概念、特征和分类，掌握法学的研究对象和方法',
    difficulty: 3,
    totalDurationSeconds: 900,
    status: 'published',
    sceneCount: 5,
  },
];

const SCENE_ACTIONS_DATA = [
  // Scene 1: 法的概念与特征
  { sceneIdx: 0, sceneId: 'a1111111-1111-1111-1111-111111111112', title: '法的概念与特征', desc: '什么是法？法有哪些基本特征？', type: 'slide', actions: [
    { actionType: 'scene_transition', orderIndex: 0, params: { title: '法的概念与特征', subtitle: '了解法的基本概念，掌握法的三个基本特征', icon: '⚖️', tags: ['法学基础', '法的特征', '社会规范'] }, durationMs: 3000 },
    { actionType: 'speech', orderIndex: 1, params: { text: '同学们好！今天我们开始学习法学概论课程。首先让我们来了解法的基本概念。', prosody: { rate: '0.9', pitch: 'medium' } }, durationMs: 8000 },
    { actionType: 'speech_with_highlight', orderIndex: 2, params: { text: '法是 由国家制定或认可的 以权利义务为内容的 具有国家强制力的 社会规范。', highlights: [{ text: '国家制定或认可', color: '#FFD700' }, { text: '国家强制力', color: '#FF6B6B' }], prosody: { rate: '0.85', pitch: 'medium' } }, durationMs: 15000 },
    { actionType: 'wb_draw_text', orderIndex: 3, params: { content: '法的三个基本特征：\n\n1. **规范性** — 法是调整人们行为的社会规范\n\n2. **国家意志性** — 法由国家制定或认可\n\n3. **国家强制性** — 法以国家强制力保证实施', text_type: 'markdown' }, durationMs: 12000 },
    { actionType: 'pause_for_thought', orderIndex: 4, params: { text: '思考一下：法律与道德有什么不同？', duration_ms: 5000 }, durationMs: 5000 },
  ]},
  // Scene 2: Quiz
  { sceneIdx: 1, sceneId: 'a1111111-1111-1111-1111-111111111113', title: '知识检测', desc: '检测你对法学基础知识的掌握程度', type: 'quiz', actions: [
    { actionType: 'scene_transition', orderIndex: 0, params: { title: '知识检测', subtitle: '来测试一下你对法的概念的理解', icon: '📝', tags: ['课堂练习', '知识巩固'] }, durationMs: 3000 },
    { actionType: 'speech', orderIndex: 1, params: { text: '让我们来做一道选择题，检验一下刚才的学习效果。', prosody: { rate: '0.9' } }, durationMs: 4000 },
    { actionType: 'quiz', orderIndex: 2, params: { question: '下列哪一项是法的核心特征？', options: [{ label: 'A', text: '法是约定俗成的行为规范' }, { label: 'B', text: '法由国家制定或认可并具有国家强制力' }, { label: 'C', text: '法仅适用于特定社会群体' }, { label: 'D', text: '法是个人意志的体现' }], correctAnswer: 'B', explanation: '法的核心特征在于由国家制定或认可，并以国家强制力保证实施。这是法与其他社会规范（如道德、习惯）的根本区别。', difficulty: 2 }, durationMs: 20000 },
  ]},
  // Scene 3: Discussion
  { sceneIdx: 2, sceneId: 'a1111111-1111-1111-1111-111111111114', title: '讨论：法与道德的关系', desc: '思考法与其他社会规范的联系与区别', type: 'discussion', actions: [
    { actionType: 'scene_transition', orderIndex: 0, params: { title: '讨论：法与道德的关系', subtitle: '深入探讨法在社会治理中的作用', icon: '💭', tags: ['讨论', '法学思维', '法治'] }, durationMs: 3000 },
    { actionType: 'speech', orderIndex: 1, params: { text: '接下来我们进入讨论环节。请思考：法是不是万能的？法和道德是什么关系？', prosody: { rate: '0.9' } }, durationMs: 8000 },
    { actionType: 'discussion', orderIndex: 2, params: { topic: '法在社会治理中是否具有局限性？', prompt: '请结合你生活中的例子，谈谈对法和道德关系的理解。', think_time_ms: 10000 }, durationMs: 20000 },
    { actionType: 'speech', orderIndex: 3, params: { text: '很好的讨论！法虽然不是万能的，但它是现代社会不可或缺的治理工具。法与道德相互补充。', prosody: { rate: '0.9' } }, durationMs: 10000 },
  ]},
  // Scene 4: 白板
  { sceneIdx: 3, sceneId: 'a1111111-1111-1111-1111-111111111115', title: '法的分类与体系', desc: '系统的法的分类方法及法律体系概述', type: 'slide', actions: [
    { actionType: 'scene_transition', orderIndex: 0, params: { title: '法的分类与体系', subtitle: '系统地了解法的分类方法', icon: '🏛️', tags: ['法律体系', '法的分类', '法治建设'] }, durationMs: 3000 },
    { actionType: 'speech', orderIndex: 1, params: { text: '下面我们来学习法的分类。法可以从不同角度进行分类。', prosody: { rate: '0.9' } }, durationMs: 5000 },
    { actionType: 'wb_draw_text', orderIndex: 2, params: { content: '## 法的分类\n\n| 类型 | 例子 |\n|------|------|\n| 根本法/普通法 | 宪法 vs 民法典 |\n| 实体法/程序法 | 刑法 vs 刑事诉讼法 |\n| 一般法/特别法 | 合同法 vs 劳动合同法 |', text_type: 'markdown' }, durationMs: 15000 },
    { actionType: 'pause_for_thought', orderIndex: 3, params: { text: '想一想：为什么需要区分法的不同类型？', duration_ms: 4000 }, durationMs: 4000 },
  ]},
  // Scene 5: 总结
  { sceneIdx: 4, sceneId: 'a1111111-1111-1111-1111-111111111116', title: '课程总结', desc: '回顾本课的核心知识点', type: 'review', actions: [
    { actionType: 'scene_transition', orderIndex: 0, params: { title: '课程总结', subtitle: '回顾本节课的核心知识点', icon: '🎯', tags: ['总结', '回顾'] }, durationMs: 3000 },
    { actionType: 'speech', orderIndex: 1, params: { text: '让我们总结今天的学习。我们学习了法的概念、三大基本特征和分类方法。', prosody: { rate: '0.9' } }, durationMs: 8000 },
    { actionType: 'wb_draw_text', orderIndex: 2, params: { content: '### 核心要点\n\n1. **法的概念**：由国家制定或认可的社会规范\n2. **三大特征**：规范性、国家意志性、国家强制性\n3. **法的分类**：根本法、普通法、实体法、程序法', text_type: 'markdown' }, durationMs: 10000 },
    { actionType: 'speech', orderIndex: 3, params: { text: '今天的课就到这里，希望大家对法学有了系统的认识。下次再见！', prosody: { rate: '0.9' } }, durationMs: 10000 },
  ]},
];

function ok(data) {
  return JSON.stringify({ code: 200, success: true, message: 'success', data, timestamp: new Date().toISOString() });
}

function fail(code, msg) {
  return JSON.stringify({ code, success: false, message: msg, timestamp: new Date().toISOString() });
}

const server = http.createServer((req, res) => {
  res.setHeader('Content-Type', 'application/json');
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization');

  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const url = new URL(req.url, 'http://localhost:3099');
  const path = url.pathname;

  console.log(`[Mock] ${req.method} ${path}`);

  // GET /v2/classrooms?courseId=xxx
  if (req.method === 'GET' && path === '/v2/classrooms' && url.searchParams.has('courseId')) {
    res.writeHead(200);
    res.end(ok(MOCK_CLASSROOMS));
    return;
  }

  // GET /v2/classrooms/:id (classroom detail with scenes+actions)
  const matchDetail = path.match(/^\/v2\/classrooms\/([^/]+)$/);
  if (req.method === 'GET' && matchDetail) {
    const classroomId = matchDetail[1];
    const scenes = SCENE_ACTIONS_DATA.map(s => ({
      id: s.sceneId,
      classroomId,
      title: s.title,
      description: s.desc,
      sceneType: s.type,
      orderIndex: s.sceneIdx,
      estimatedDurationSeconds: Math.round(s.actions.reduce((sum, a) => sum + a.durationMs, 0) / 1000),
      actions: s.actions.map(a => ({
        id: `action-${s.sceneIdx}-${a.orderIndex}`,
        sceneId: s.sceneId,
        actionType: a.actionType,
        orderIndex: a.orderIndex,
        paramsJson: JSON.stringify(a.params),
        durationMs: a.durationMs,
      })),
    }));

    const classroom = {
      ...MOCK_CLASSROOMS[0],
      scenes,
    };
    res.writeHead(200);
    res.end(ok(classroom));
    return;
  }

  // POST /v2/classrooms/:id/start
  const matchStart = path.match(/^\/v2\/classrooms\/([^/]+)\/start$/);
  if (req.method === 'POST' && matchStart) {
    res.writeHead(200);
    res.end(ok({ status: 'in_progress', currentSceneOrder: 0, currentActionOrder: 0 }));
    return;
  }

  // POST /v2/classrooms/:id/progress
  const matchProgress = path.match(/^\/v2\/classrooms\/([^/]+)\/progress$/);
  if (req.method === 'POST' && matchProgress) {
    res.writeHead(200);
    res.end(ok({ status: 'in_progress' }));
    return;
  }

  // POST /v2/classrooms/:id/complete
  const matchComplete = path.match(/^\/v2\/classrooms\/([^/]+)\/complete$/);
  if (req.method === 'POST' && matchComplete) {
    res.writeHead(200);
    res.end(ok({ status: 'completed' }));
    return;
  }

  // POST /v2/classrooms/:id/pause
  const matchPause = path.match(/^\/v2\/classrooms\/([^/]+)\/pause$/);
  if (req.method === 'POST' && matchPause) {
    res.writeHead(200);
    res.end(ok({ status: 'paused' }));
    return;
  }

  // POST /v2/classrooms/scenes/:sceneId/quiz/submit
  const matchQuiz = path.match(/^\/v2\/classrooms\/scenes\/([^/]+)\/quiz\/submit$/);
  if (req.method === 'POST' && matchQuiz) {
    res.writeHead(200);
    res.end(ok({ isCorrect: true, feedback: '回答正确！法的核心特征就是国家制定或认可并以国家强制力保证实施。', explanation: '法的核心特征在于由国家制定或认可，并以国家强制力保证实施。' }));
    return;
  }

  // Fallback — proxy to real backend for other requests
  console.log(`[Mock] ⚠️  Unhandled: ${req.method} ${path}`);
  res.writeHead(404);
  res.end(fail(404, 'Not found in mock'));
});

const PORT = 3099;
server.listen(PORT, () => {
  console.log(`✅ Mock API server running on http://localhost:${PORT}`);
  console.log(`   Endpoints:`);
  console.log(`   GET  /v2/classrooms?courseId=xxx`);
  console.log(`   GET  /v2/classrooms/:id  (with scenes + actions)`);
  console.log(`   POST /v2/classrooms/:id/start`);
  console.log(`   POST /v2/classrooms/:id/progress`);
  console.log(`   POST /v2/classrooms/:id/complete`);
  console.log(`   POST /v2/classrooms/:id/pause`);
  console.log(`   POST /v2/classrooms/scenes/:sceneId/quiz/submit`);
});
