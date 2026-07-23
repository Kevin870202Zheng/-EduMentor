import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import { Card, Typography, Button, Spin, Tag, Progress, Radio, Checkbox, Input, Space, Alert, Empty, List, Tree, message, Collapse, Select, Form, Tabs, Modal, Table } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ArrowLeftOutlined, ArrowRightOutlined, RobotOutlined, BookOutlined, FolderOutlined, FileTextOutlined, FormOutlined, TeamOutlined, PlusOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import { courseAPI, learningAPI, answerAPI, questionAnalysisAPI, knowledgePointAPI, peerQuizAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text, Paragraph } = Typography;

const DIFFICULTY_COLORS = { 1: 'green', 2: 'cyan', 3: 'blue', 4: 'orange', 5: 'red' };

// ============================================================
// 纯工具函数
// ============================================================
const getTypeLabel = (type) => ({
  SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', ESSAY: '论述题',
}[type] || type);

const isChoiceType = (type) =>
  ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type);

const parseOptions = (q) => {
  const type = q.questionType || 'SINGLE_CHOICE';
  if (!isChoiceType(type)) return [];
  try {
    let raw = typeof q.options === 'string' ? JSON.parse(q.options) : (q.options || []);
    if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
      return Object.entries(raw).map(([k, v]) => ({ label: k, text: v }));
    } else if (Array.isArray(raw)) {
      return raw.map(opt => {
        if (typeof opt === 'object' && opt !== null && opt.label) return opt;
        if (typeof opt === 'string') {
          const m = opt.match(/^([A-Da-d])[)\.]\s*(.*)/);
          if (m) return { label: m[1].toUpperCase(), text: m[2] };
          return { label: String.fromCharCode(65 + raw.indexOf(opt)), text: opt };
        }
        return opt;
      });
    }
  } catch (e) { /* ignore */ }
  return [];
};

// ============================================================
// 子组件：AI 分析结果折叠面板
// ============================================================
const AnalysisCollapse = React.memo(({ result }) => {
  const items = [];
  if (result.knowledgePoint) {
    items.push({ key: 'kp', label: '📌 考察知识点', children: <Text>{result.knowledgePoint}</Text> });
  }
  if (result.optionAnalysis?.length > 0) {
    items.push({
      key: 'options', label: '📖 选项详解',
      children: result.optionAnalysis.map((opt, i) => (
        <div key={i} style={{ marginBottom: 6 }}>
          <Text strong style={{ color: opt.isCorrect ? '#52c41a' : '#ff4d4f' }}>{opt.label}. {opt.text}</Text>
          <br /><Text type="secondary">{opt.reason}</Text>
        </div>
      )),
    });
  }
  if (result.solutionSteps?.length > 0) {
    items.push({
      key: 'steps', label: '💡 解题思路',
      children: <ol style={{ margin: 0, paddingLeft: 20 }}>
        {result.solutionSteps.map((step, i) => <li key={i}><Text>{step}</Text></li>)}
      </ol>,
    });
  }
  if (result.commonMistakes?.length > 0) {
    items.push({
      key: 'mistakes', label: '⚠️ 常见错误',
      children: <ul style={{ margin: 0, paddingLeft: 20 }}>
        {result.commonMistakes.map((m, i) => <li key={i}><Text type="warning">{m}</Text></li>)}
      </ul>,
    });
  }
  if (result.relatedKnowledge?.length > 0) {
    items.push({
      key: 'related', label: '📚 相关知识',
      children: <div>{result.relatedKnowledge.map((k, i) => <Tag key={i} style={{ marginBottom: 4 }}>{k}</Tag>)}</div>,
    });
  }
  if (items.length === 0) return null;
  return (
    <Collapse size="small" items={items} defaultActiveKey={['kp', 'options']}
      style={{ marginTop: 8, background: '#fff' }} />
  );
});

// ============================================================
// 子组件：单道题目卡片（React.memo — 内部管理本地状态）
// 核心优化：选择题用本地 state 驱动（仅重渲染自身）
//          输入题用 DOM ref 非受控（零渲染开销）
// ============================================================
const QuestionCard = React.memo(({
  question, index, result, disabled,
  parsedOptions, submitting, analyzing, analysisResult,
  onSelectAnswer, onSubmitAnswer, onAnalyze,
}) => {
  const qId = question.id;
  const qType = question.questionType || 'SINGLE_CHOICE';
  const isChoice = isChoiceType(qType);

  // 选择题：本地 state 驱动选中显示（不依赖父组件重渲染）
  const [localValue, setLocalValue] = useState('');
  // 输入题：DOM ref 非受控
  const inputRef = useRef(null);

  // 切换题目时重置本地状态
  useEffect(() => { setLocalValue(''); }, [qId]);

  // 选择题选中
  const handleChoiceChange = (value) => {
    const finalValue = typeof value === 'object' && value?.target
      ? value.target.value : value;
    setLocalValue(finalValue);
    onSelectAnswer(qId, finalValue);
  };

  // 输入题：只写 ref，不触发渲染
  const handleInputChange = (e) => {
    onSelectAnswer(qId, e.target.value);
  };

  // 提交时获取最终答案
  const handleSubmit = () => {
    let answer;
    if (isChoice) {
      answer = localValue;
    } else {
      const el = inputRef.current;
      answer = el?.input?.value || el?.resizableTextArea?.textArea?.value || '';
    }
    if (!answer || (qType !== 'SINGLE_CHOICE' && qType !== 'MULTIPLE_CHOICE' && qType !== 'TRUE_FALSE' && !answer.trim())) {
      message.warning('请先输入答案');
      return;
    }
    onSubmitAnswer(qId, qType, answer);
  };

  return (
    <div style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 8 }}>
      <Text strong>
        {index + 1}. {question.content}
        <Tag style={{ marginLeft: 6 }} color="blue">{getTypeLabel(qType)}</Tag>
        {question.difficulty && <Tag color={DIFFICULTY_COLORS[question.difficulty]}>难度{question.difficulty}</Tag>}
      </Text>

      {/* 选择题：受控于本地 state，增量渲染 */}
      {isChoice && (
        <>
          {(qType === 'SINGLE_CHOICE' || qType === 'TRUE_FALSE') && (
            <Radio.Group style={{ display: 'block', marginTop: 8 }}
              value={localValue} onChange={e => handleChoiceChange(e.target.value)} disabled={disabled}>
              {parsedOptions.map(opt => (
                <Radio key={opt.label} value={opt.label} style={{ display: 'block', marginBottom: 4 }}>
                  <Text style={{
                    color: result && opt.label === result.correctAnswer ? '#52c41a' :
                           result && opt.label === result.studentAnswer && !result.correct ? '#ff4d4f' : 'inherit',
                    fontWeight: result && opt.label === result.correctAnswer ? 'bold' : 'normal',
                  }}>{opt.label}. {opt.text}</Text>
                </Radio>
              ))}
            </Radio.Group>
          )}
          {qType === 'MULTIPLE_CHOICE' && (
            <Checkbox.Group style={{ display: 'block', marginTop: 8 }}
              value={localValue ? localValue.split(',') : []}
              onChange={vals => handleChoiceChange(vals.sort().join(','))} disabled={disabled}>
              {parsedOptions.map(opt => (
                <Checkbox key={opt.label} value={opt.label} style={{ display: 'block', marginBottom: 4 }}>
                  <Text style={{
                    color: result && result.correctAnswer?.split(',').includes(opt.label) ? '#52c41a' :
                           result && result.studentAnswer?.split(',').includes(opt.label) && !result.correct ? '#ff4d4f' : 'inherit',
                  }}>{opt.label}. {opt.text}</Text>
                </Checkbox>
              ))}
            </Checkbox.Group>
          )}
        </>
      )}

      {/* 输入题：非受控，零渲染开销 */}
      {!isChoice && (
        <>
          {qType === 'FILL_BLANK' && (
            <Input ref={inputRef} style={{ marginTop: 8, maxWidth: 400 }}
              placeholder="请输入答案" defaultValue="" disabled={disabled} />
          )}
          {qType === 'SHORT_ANSWER' && (
            <Input.TextArea ref={inputRef} style={{ marginTop: 8 }} rows={3}
              placeholder="请输入答案" defaultValue="" disabled={disabled} />
          )}
          {qType === 'ESSAY' && (
            <Input.TextArea ref={inputRef} style={{ marginTop: 8 }} rows={6}
              placeholder="请详细论述你的观点..." defaultValue="" disabled={disabled} />
          )}
        </>
      )}

      {/* 提交 / 反馈 */}
      {!disabled ? (
        <div style={{ marginTop: 8 }}>
          <Button type="primary" size="small"
            onClick={handleSubmit}
            loading={submitting} disabled={isChoice && !localValue}>提交答案</Button>
          <Button size="small" icon={<RobotOutlined />} style={{ marginLeft: 8 }}
            onClick={() => onAnalyze(question, localValue)} loading={analyzing}>AI 分析</Button>
          {!analyzing && analysisResult && <AnalysisCollapse result={analysisResult} />}
        </div>
      ) : (
        <div>
          <Alert style={{ marginTop: 8 }} type={result?.correct ? 'success' : 'error'} showIcon
            icon={result?.correct ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
            message={
              <Space direction="vertical" size={2}>
                <Text strong>{result?.correct ? '✅ 回答正确！' : '❌ 回答错误'}</Text>
                <Text>正确答案：{result?.correctAnswer}</Text>
                {result?.explanation && <Text type="secondary">解析：{result.explanation}</Text>}
              </Space>
            } />
          <Button size="small" icon={<RobotOutlined />} style={{ marginTop: 8 }}
            onClick={() => onAnalyze(question, localValue)} loading={analyzing}>AI 分析</Button>
          {!analyzing && analysisResult && <AnalysisCollapse result={analysisResult} />}
        </div>
      )}
    </div>
  );
});

// ============================================================
// 主组件
// ============================================================
export default function StudentLearning() {
  const { courseCode } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const { selectedCourseId, studentCourses } = useOutletContext();

  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [courseInfo, setCourseInfo] = useState(null);
  const [knowledgePoints, setKnowledgePoints] = useState([]);
  const [treeNodes, setTreeNodes] = useState([]);
  const [currentKpIndex, setCurrentKpIndex] = useState(0);
  const [questions, setQuestions] = useState([]);
  const [masteryMap, setMasteryMap] = useState({});
  const [submitResults, setSubmitResults] = useState({});
  const [analysisResults, setAnalysisResults] = useState({});
  const [analyzingQuestions, setAnalyzingQuestions] = useState({});

  const currentKp = knowledgePoints[currentKpIndex];
  const prevCourseIdRef = useRef(null);

  // ─── ref 存储所有答案（不触发渲染） ───
  const answerRefs = useRef({});

  // ─── 出题考核状态 ───
  const [showCreateQuiz, setShowCreateQuiz] = useState(false);
  const [availableStudents, setAvailableStudents] = useState([]);
  const [quizTitle, setQuizTitle] = useState('');
  const [selectedParticipants, setSelectedParticipants] = useState([]);
  const [quizQuestions, setQuizQuestions] = useState([]);
  const [creatingQuiz, setCreatingQuiz] = useState(false);
  const [pendingQuizzes, setPendingQuizzes] = useState([]);
  const [createdQuizzes, setCreatedQuizzes] = useState([]);
  const [completedQuizzes, setCompletedQuizzes] = useState([]);
  const [activeTab, setActiveTab] = useState('exercises');
  const [selectedQuizDetail, setSelectedQuizDetail] = useState(null);
  const [selectedQuizResults, setSelectedQuizResults] = useState(null);
  const [quizAnswers, setQuizAnswers] = useState({});
  const [submittingQuiz, setSubmittingQuiz] = useState(false);
  const [loadingQuizzes, setLoadingQuizzes] = useState(false);

  // ─── 稳定的事件回调 ───

  const handleSelectAnswer = useCallback((questionId, value) => {
    answerRefs.current[questionId] = value;
  }, []);

  const refreshMastery = useCallback(async (courseId) => {
    if (!user?.id || !courseId) return;
    try {
      const diagRes = await learningAPI.getDiagnosisProfile(user.id, courseId);
      if (diagRes?.data?.knowledgeMasteries) {
        const map = {};
        diagRes.data.knowledgeMasteries.forEach(d => {
          if (d.knowledgePointId) map[d.knowledgePointId] = d.masteryLevel;
        });
        setMasteryMap(map);
      }
    } catch (e) { /* ignore */ }
  }, [user?.id]);

  const handleSubmitAnswer = useCallback(async (questionId, qType, answer) => {
    setSubmitting(true);
    try {
      const res = await answerAPI.submit({
        questionId, studentAnswer: answer, timeSpentSeconds: 0,
      });
      const result = res?.data || res;
      setSubmitResults(prev => ({ ...prev, [questionId]: result }));
      if (result?.correct) {
        message.success('✅ 回答正确！');
        if (courseInfo?.id) refreshMastery(courseInfo.id);
      } else {
        message.error('❌ 回答错误');
      }
    } catch (err) {
      message.error('提交失败: ' + (err.message || '未知错误'));
    }
    setSubmitting(false);
  }, [courseInfo?.id, refreshMastery]);

  const handleAnalyzeQuestion = useCallback(async (question, studentAnswer) => {
    const qId = question.id;
    setAnalyzingQuestions(prev => ({ ...prev, [qId]: true }));
    try {
      const res = await questionAnalysisAPI.analyze({
        questionId: qId,
        studentAnswer: studentAnswer || null,
        usage: studentAnswer ? 'post_answer' : 'pre_answer',
      });
      setAnalysisResults(prev => ({ ...prev, [qId]: res?.data || res }));
    } catch (err) {
      message.error('AI 分析失败: ' + (err.message || '未知错误'));
    }
    setAnalyzingQuestions(prev => ({ ...prev, [qId]: false }));
  }, []);

  // ─── 出题考核处理函数 ───

  const openCreateQuiz = useCallback(async () => {
    if (!courseInfo?.id) return;
    try {
      const res = await peerQuizAPI.getCourseMates(courseInfo.id);
      const students = res?.data || res || [];
      setAvailableStudents(students.filter(s => s.studentId !== user?.id));
    } catch (e) {
      setAvailableStudents([]);
    }
    setQuizTitle('考核 - ' + (currentKp?.name || '知识点'));
    setSelectedParticipants([]);
    setQuizQuestions([{ content: '', questionType: 'SINGLE_CHOICE', options: { A: '', B: '', C: '', D: '' }, correctAnswer: '', explanation: '' }]);
    setShowCreateQuiz(true);
  }, [courseInfo?.id, currentKp, user?.id]);

  const addQuestionToForm = () => {
    setQuizQuestions(prev => [...prev, { content: '', questionType: 'SINGLE_CHOICE', options: { A: '', B: '', C: '', D: '' }, correctAnswer: '', explanation: '' }]);
  };

  const removeQuestionFromForm = (idx) => {
    setQuizQuestions(prev => prev.filter((_, i) => i !== idx));
  };

  const updateQuestionField = (idx, field, value) => {
    setQuizQuestions(prev => prev.map((q, i) => i === idx ? { ...q, [field]: value } : q));
  };

  const updateQuestionOption = (idx, optKey, value) => {
    setQuizQuestions(prev => prev.map((q, i) => i === idx ? { ...q, options: { ...q.options, [optKey]: value } } : q));
  };

  const handleCreateQuiz = async () => {
    if (!quizTitle.trim()) { message.warning('请输入考核标题'); return; }
    if (selectedParticipants.length === 0) { message.warning('请选择被考核学生'); return; }
    const validQuestions = quizQuestions.filter(q => q.content.trim() && q.correctAnswer.trim());
    if (validQuestions.length === 0) { message.warning('请添加至少一道完整题目'); return; }
    setCreatingQuiz(true);
    try {
      await peerQuizAPI.create({
        quiz: {
          title: quizTitle,
          courseId: courseInfo.id,
          knowledgePointId: currentKp?.id || null,
          participantIds: selectedParticipants,
        },
        questions: validQuestions,
      });
      message.success('考核发布成功！');
      setShowCreateQuiz(false);
      setActiveTab('created');
      loadPeerQuizzes();
    } catch (err) {
      message.error('发布失败: ' + (err.message || '未知错误'));
    }
    setCreatingQuiz(false);
  };

  const loadPeerQuizzes = useCallback(async () => {
    if (!user?.id) return;
    setLoadingQuizzes(true);
    try {
      const [pendingRes, createdRes, completedRes] = await Promise.all([
        peerQuizAPI.getPending().catch(() => ({ data: [] })),
        peerQuizAPI.getMyCreated().catch(() => ({ data: [] })),
        peerQuizAPI.getCompleted().catch(() => ({ data: [] })),
      ]);
      setPendingQuizzes(pendingRes?.data || pendingRes || []);
      setCreatedQuizzes(createdRes?.data || createdRes || []);
      setCompletedQuizzes(completedRes?.data || completedRes || []);
    } catch (e) { /* ignore */ }
    setLoadingQuizzes(false);
  }, [user?.id]);

  const handleTakeQuiz = async (quizId) => {
    try {
      const res = await peerQuizAPI.getDetail(quizId);
      setSelectedQuizDetail(res?.data || res);
      setQuizAnswers({});
    } catch (err) {
      message.error('加载考核详情失败');
    }
  };

  const handleSubmitPeerQuiz = async (quizId) => {
    setSubmittingQuiz(true);
    try {
      await peerQuizAPI.submit(quizId);
      message.success('考核提交成功！');
      setSelectedQuizDetail(null);
      loadPeerQuizzes();
    } catch (err) {
      message.error('提交失败: ' + (err.message || '未知错误'));
    }
    setSubmittingQuiz(false);
  };

  const handleViewResults = async (quizId) => {
    try {
      const res = await peerQuizAPI.getResults(quizId);
      setSelectedQuizResults(res?.data || res);
    } catch (err) {
      message.error('加载结果失败');
    }
  };

  useEffect(() => {
    if (user?.id && courseInfo?.id) {
      loadPeerQuizzes();
    }
  }, [user?.id, courseInfo?.id, loadPeerQuizzes]);

  // ─── 数据加载 ───

  useEffect(() => {
    if (!selectedCourseId || !studentCourses.length) return;
    if (!prevCourseIdRef.current) {
      prevCourseIdRef.current = selectedCourseId;
      return;
    }
    prevCourseIdRef.current = selectedCourseId;
    const newCourse = studentCourses.find(c => c.courseId === selectedCourseId);
    if (newCourse && newCourse.courseCode !== courseCode) {
      navigate(`/student/learning/${newCourse.courseCode}`, { replace: true });
    }
  }, [selectedCourseId, studentCourses, courseCode, navigate]);

  useEffect(() => {
    if (!courseCode) return;
    loadData();
  }, [courseCode]);

  const loadData = async () => {
    setLoading(true);
    try {
      const infoRes = await courseAPI.getByCode(courseCode);
      const course = infoRes?.data || infoRes;
      setCourseInfo(course);
      if (course?.id) {
        const [kpRes, treeRes, diagRes] = await Promise.all([
          learningAPI.getKpsByCourse(course.id),
          knowledgePointAPI.getTree(course.id),
          user?.id ? learningAPI.getDiagnosisProfile(user.id, course.id) : Promise.resolve(null),
        ]);
        const kps = kpRes?.data || kpRes || [];
        const tree = treeRes?.data || treeRes || [];
        setKnowledgePoints(kps);
        setTreeNodes(tree);
        if (diagRes?.data?.knowledgeMasteries) {
          const map = {};
          diagRes.data.knowledgeMasteries.forEach(d => {
            if (d.knowledgePointId) map[d.knowledgePointId] = d.masteryLevel;
          });
          setMasteryMap(map);
        }
        if (kps.length > 0) loadQuestions(kps[0].id);
      }
    } catch (err) {
      console.error('Failed to load learning data:', err);
      message.error('加载课程数据失败');
    }
    setLoading(false);
  };

  const loadQuestions = async (kpId) => {
    try {
      const res = await learningAPI.getQuestionsByKp(kpId);
      const list = res?.data || res || [];
      setQuestions(list);
      answerRefs.current = {};
      setSubmitResults({});
      setAnalysisResults({});
      setAnalyzingQuestions({});
    } catch (err) {
      console.error('Failed to load questions:', err);
      setQuestions([]);
    }
  };

  const switchKp = useCallback((index) => {
    if (index < 0 || index >= knowledgePoints.length) return;
    setCurrentKpIndex(index);
    const kp = knowledgePoints[index];
    if (kp?.id) loadQuestions(kp.id);
  }, [knowledgePoints]);

  // ─── useMemo 缓存 ───

  // 构建树形导航数据
  const treeData = useMemo(() => {
    if (!treeNodes.length) return [];

    // 按 parentKpId 分组
    const childrenMap = {};
    treeNodes.forEach(n => {
      const pid = n.knowledgePoint.parentKpId || 'root';
      if (!childrenMap[pid]) childrenMap[pid] = [];
      childrenMap[pid].push(n);
    });

    // 获取掌握度状态
    const getMasteryStatus = (kpId) => {
      const val = masteryMap[kpId];
      if (val == null) return { color: '#d9d9d9', label: '未学习', percent: 0, count: 0, total: 0 };
      if (val >= 0.8) return { color: '#52c41a', label: '已掌握', percent: Math.round(val * 100), count: 1, total: 1 };
      if (val >= 0.5) return { color: '#1677ff', label: '学习中', percent: Math.round(val * 100), count: 0, total: 1 };
      return { color: '#faad14', label: '待巩固', percent: Math.round(val * 100), count: 0, total: 1 };
    };

    // 计算节点的聚合掌握度（递归）
    const calcAggregatedMastery = (node) => {
      const children = childrenMap[node.knowledgePoint.id] || [];
      if (children.length === 0) {
        return getMasteryStatus(node.knowledgePoint.id);
      }
      let total = 0, count = 0, leafCount = 0, masteredCount = 0;
      children.forEach(child => {
        const childStatus = calcAggregatedMastery(child);
        if (childStatus.percent > 0) { total += childStatus.percent; count++; }
        if (childStatus.total > 0) {
          leafCount += childStatus.total;
          masteredCount += childStatus.count || 0;
        }
      });
      // 统计叶子节点数
      const leafTotal = leafCount > 0 ? leafCount : children.length;
      const leafMastered = leafCount > 0 ? masteredCount : Math.round(count * (total / count / 100));

      if (count === 0) return { color: '#d9d9d9', label: '未学习', percent: 0, count: 0, total: leafTotal };
      const avg = Math.round(total / count);
      let color = '#d9d9d9', label = '未学习';
      if (avg >= 80) { color = '#52c41a'; label = '已掌握'; }
      else if (avg >= 50) { color = '#1677ff'; label = '学习中'; }
      else if (avg > 0) { color = '#faad14'; label = '待巩固'; }
      return { color, label, percent: avg, count: leafMastered, total: leafTotal };
    };

    // 节点类型图标
    const nodeIcon = (type) => {
      switch (type) {
        case 'VOLUME': return <BookOutlined style={{ color: '#722ed1' }} />;
        case 'PART': return <FolderOutlined style={{ color: '#2f54eb' }} />;
        case 'CHAPTER': return <FolderOutlined style={{ color: '#1677ff' }} />;
        case 'SECTION': return <FileTextOutlined style={{ color: '#13c2c2' }} />;
        default: return <FileTextOutlined style={{ color: '#52c41a' }} />;
      }
    };

    // 递归构建树节点（含层级编号）
    const buildChildren = (parentId, parentPath) => {
      const items = childrenMap[parentId] || [];
      return items
        .sort((a, b) => (a.knowledgePoint.orderIndex || 0) - (b.knowledgePoint.orderIndex || 0))
        .map((item, idx) => {
          const kp = item.knowledgePoint;
          const mastery = calcAggregatedMastery(item);
          const path = parentPath ? `${parentPath}.${idx + 1}` : `${idx + 1}`;
          return {
            key: kp.id,
            type: kp.type,
            isLeaf: !item.hasChild,
            title: (
              <div style={{ display: 'flex', alignItems: 'center', gap: 2, padding: '1px 0' }}>
                <span style={{ color: '#bbb', fontSize: 10, minWidth: 28, textAlign: 'right' }}>{path}</span>
                {nodeIcon(kp.type)}
                <span style={{
                  flex: 1, fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap',
                  color: currentKp?.id === kp.id ? '#1677ff' : 'inherit',
                  fontWeight: currentKp?.id === kp.id ? 600 : 'normal',
                  marginLeft: 2,
                }}>{kp.name}</span>
                {!item.isLeaf && mastery.total > 0 && (
                  <Text type="secondary" style={{ fontSize: 10, minWidth: 28 }}>
                    {mastery.count}/{mastery.total}
                  </Text>
                )}
                <Tag color={mastery.color} style={{
                  margin: 0, fontSize: 10, lineHeight: '14px', minWidth: 36, textAlign: 'center', padding: '0 4px',
                }}>{mastery.label}</Tag>
              </div>
            ),
            children: buildChildren(kp.id, path),
          };
        });
    };

    return buildChildren('root', '');
  }, [treeNodes, masteryMap, currentKp]);

  const parsedQuestions = useMemo(() => {
    return questions.map(q => ({ id: q.id, options: parseOptions(q) }));
  }, [questions]);

  const progressInfo = useMemo(() => {
    const total = knowledgePoints.length;
    const learned = knowledgePoints.filter(kp => (masteryMap[kp.id] || 0) >= 0.5).length;
    return { total, learned, percent: total > 0 ? Math.round(learned / total * 100) : 0 };
  }, [knowledgePoints, masteryMap]);

  // ─── Tree 选择处理 ───

  const handleTreeSelect = useCallback((selectedKeys) => {
    if (selectedKeys.length === 0) return;
    const kpId = selectedKeys[0];
    const idx = knowledgePoints.findIndex(k => k.id === kpId);
    if (idx >= 0) {
      setCurrentKpIndex(idx);
      loadQuestions(kpId);
    }
  }, [knowledgePoints]);

  // ─── 渲染 ───

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 120 }} />;
  if (!courseInfo) {
    return <Alert type="error" message="课程不存在" description={`未找到课程 ${courseCode}`} showIcon />;
  }

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Space>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/student/dashboard')}>返回</Button>
          <Title level={4} style={{ margin: 0 }}>📚 {courseInfo.name}</Title>
          <Text type="secondary">编号: {courseInfo.courseCode}</Text>
        </Space>
        <Space>
          <Text>进度:</Text>
          <Progress percent={progressInfo.percent} size="small" style={{ width: 120 }} />
        </Space>
      </div>

      <div style={{ display: 'flex', gap: 16 }}>
        <Card size="small" style={{ width: 320, flexShrink: 0, maxHeight: '70vh', overflow: 'auto' }}>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>📖 知识结构</Text>
          {treeData.length === 0 ? (
            <Empty description="暂无知识结构" image={Empty.PRESENTED_IMAGE_SIMPLE} />
          ) : (
            <Tree
              treeData={treeData}
              defaultExpandedKeys={treeData.slice(0, 2).map(n => n.key)}
              showIcon={false}
              onSelect={handleTreeSelect}
              selectedKeys={currentKp ? [currentKp.id] : []}
            />
          )}
        </Card>

        <div style={{ flex: 1 }}>
          {currentKp ? (
            <div>
              <Card size="small" style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Title level={5} style={{ margin: 0 }}>第 {currentKpIndex + 1} 课：{currentKp.name}</Title>
                    <Space style={{ marginTop: 4 }}>
                      <Text type="secondary">{currentKp.description || '暂无描述'}</Text>
                      <Tag color={DIFFICULTY_COLORS[currentKp.difficulty] || 'default'}>
                        难度: {'★'.repeat(currentKp.difficulty || 3)}
                      </Tag>
                      {currentKp.estimatedMinutes > 0 && (
                        <Text type="secondary">预计 {currentKp.estimatedMinutes} 分钟</Text>
                      )}
                    </Space>
                  </div>
                  <Progress type="circle" percent={currentKp ? Math.round((masteryMap[currentKp.id] || 0) * 100) : 0}
                    size={48} strokeColor={(() => {
                      const val = currentKp ? masteryMap[currentKp.id] : 0;
                      if (!val) return '#d9d9d9';
                      if (val >= 0.8) return '#52c41a';
                      if (val >= 0.5) return '#1677ff';
                      return '#faad14';
                    })()}
                    format={p => p > 0 ? `${p}%` : '?'} />
                </div>
              </Card>

              {currentKp.content && (
                <Card size="small" title="📖 学习内容" style={{ marginBottom: 12 }}>
                  <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{currentKp.content}</Paragraph>
                </Card>
              )}

              <Tabs activeKey={activeTab} onChange={setActiveTab} style={{ marginBottom: 0 }}
                items={[
                  {
                    key: 'exercises',
                    label: `📝 练习题（${questions.length} 题）`,
                    children: (
                      <div>
                        {questions.length === 0 ? (
                          <Empty description="该知识点暂无练习题" />
                        ) : (
                          questions.map((q, qIdx) => {
                            const parsed = parsedQuestions.find(p => p.id === q.id);
                            return (
                              <QuestionCard key={q.id}
                                question={q} index={qIdx}
                                result={submitResults[q.id]}
                                disabled={!!submitResults[q.id]}
                                parsedOptions={parsed?.options || []}
                                submitting={submitting}
                                analyzing={analyzingQuestions[q.id]}
                                analysisResult={analysisResults[q.id]}
                                onSelectAnswer={handleSelectAnswer}
                                onSubmitAnswer={handleSubmitAnswer}
                                onAnalyze={handleAnalyzeQuestion}
                              />
                            );
                          })
                        )}
                      </div>
                    ),
                  },
                  {
                    key: 'pending',
                    label: `⏳ 待考核（${pendingQuizzes.length}）`,
                    children: (
                      <div>
                        {loadingQuizzes ? <Spin style={{ display: 'block', margin: '20px auto' }} /> :
                         pendingQuizzes.length === 0 ? <Empty description="暂无待考核任务" /> :
                         pendingQuizzes.map(q => (
                           <Card key={q.id} size="small" style={{ marginBottom: 8 }}
                             hoverable onClick={() => handleTakeQuiz(q.id)}>
                             <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                               <div>
                                 <Text strong>{q.title}</Text>
                                 <br />
                                 <Text type="secondary">出题人: {q.creatorName} · {q.questionCount} 题</Text>
                               </div>
                               <Tag color="blue">待完成</Tag>
                             </div>
                           </Card>
                         ))
                        }
                      </div>
                    ),
                  },
                  {
                    key: 'created',
                    label: `📋 我出的题（${createdQuizzes.length}）`,
                    children: (
                      <div>
                        {loadingQuizzes ? <Spin style={{ display: 'block', margin: '20px auto' }} /> :
                         createdQuizzes.length === 0 ? <Empty description="你还没有出过题" /> :
                         createdQuizzes.map(q => (
                           <Card key={q.id} size="small" style={{ marginBottom: 8 }}>
                             <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                               <div>
                                 <Text strong>{q.title}</Text>
                                 <br />
                                 <Text type="secondary">{q.participantCount} 人参与 · {q.completedCount} 人完成 · {q.questionCount} 题</Text>
                               </div>
                               <Space>
                                 <Tag color={q.status === 'OPEN' ? 'green' : 'default'}>{q.status === 'OPEN' ? '进行中' : '已关闭'}</Tag>
                                 <Button size="small" icon={<EyeOutlined />}
                                   onClick={(e) => { e.stopPropagation(); handleViewResults(q.id); }}>查看结果</Button>
                               </Space>
                             </div>
                           </Card>
                         ))
                        }
                      </div>
                    ),
                  },
                ]}
              />

              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16 }}>
                <Button icon={<ArrowLeftOutlined />} disabled={currentKpIndex === 0}
                  onClick={() => switchKp(currentKpIndex - 1)}>上一课</Button>
                <Button type="primary" icon={<ArrowRightOutlined />}
                  disabled={currentKpIndex >= progressInfo.total - 1}
                  onClick={() => switchKp(currentKpIndex + 1)}>下一课</Button>
                <Button type="dashed" icon={<FormOutlined />}
                  onClick={openCreateQuiz}>出题考核</Button>
              </div>
            </div>
          ) : (
            <Card><Empty description="该课程暂无知识点内容" /></Card>
          )}
        </div>
      </div>

      {/* ─── 出题考核弹窗 ─── */}
      <Modal title="📝 出题考核" open={showCreateQuiz}
        onCancel={() => setShowCreateQuiz(false)}
        width={800} footer={null} destroyOnClose>
        <Form layout="vertical">
          <Form.Item label="考核标题">
            <Input value={quizTitle} onChange={e => setQuizTitle(e.target.value)} placeholder="输入考核标题" />
          </Form.Item>
          <Form.Item label="选择被考核学生（可多选）">
            <Select mode="multiple" style={{ width: '100%' }}
              placeholder="搜索并选择学生"
              value={selectedParticipants}
              onChange={setSelectedParticipants}
              options={availableStudents.map(s => ({ value: s.studentId, label: s.displayName }))}
            />
          </Form.Item>
          <Form.Item label={<Space>题目列表 <Button type="link" size="small" icon={<PlusOutlined />} onClick={addQuestionToForm}>添加题目</Button></Space>}>
            <div style={{ maxHeight: 400, overflow: 'auto' }}>
              {quizQuestions.map((q, idx) => (
                <Card key={idx} size="small" style={{ marginBottom: 8 }}
                  title={`第 ${idx + 1} 题`}
                  extra={quizQuestions.length > 1 && <Button type="text" danger icon={<DeleteOutlined />} onClick={() => removeQuestionFromForm(idx)} />}>
                  <Select style={{ width: 140, marginBottom: 8 }}
                    value={q.questionType}
                    onChange={v => updateQuestionField(idx, 'questionType', v)}
                    options={[
                      { value: 'SINGLE_CHOICE', label: '单选题' },
                      { value: 'MULTIPLE_CHOICE', label: '多选题' },
                      { value: 'TRUE_FALSE', label: '判断题' },
                      { value: 'FILL_BLANK', label: '填空题' },
                      { value: 'SHORT_ANSWER', label: '简答题' },
                    ]} />
                  <Input.TextArea rows={2} style={{ marginBottom: 8 }} placeholder="题目内容"
                    value={q.content} onChange={e => updateQuestionField(idx, 'content', e.target.value)} />
                  {(q.questionType === 'SINGLE_CHOICE' || q.questionType === 'MULTIPLE_CHOICE') && (
                    <div>
                      {['A','B','C','D'].map(k => (
                        <Input key={k} style={{ width: '48%', margin: '2px 1%' }} size="small"
                          placeholder={`选项 ${k}`}
                          value={q.options?.[k] || ''}
                          onChange={e => updateQuestionOption(idx, k, e.target.value)} />
                      ))}
                    </div>
                  )}
                  {q.questionType === 'TRUE_FALSE' && (
                    <Radio.Group value={q.correctAnswer} onChange={e => updateQuestionField(idx, 'correctAnswer', e.target.value)}>
                      <Radio value="A">正确</Radio>
                      <Radio value="B">错误</Radio>
                    </Radio.Group>
                  )}
                  <Input style={{ marginTop: 4 }} size="small" placeholder="正确答案"
                    value={q.correctAnswer} onChange={e => updateQuestionField(idx, 'correctAnswer', e.target.value)} />
                  <Input style={{ marginTop: 4 }} size="small" placeholder="解析（可选）"
                    value={q.explanation} onChange={e => updateQuestionField(idx, 'explanation', e.target.value)} />
                </Card>
              ))}
            </div>
          </Form.Item>
          <div style={{ textAlign: 'right' }}>
            <Button style={{ marginRight: 8 }} onClick={() => setShowCreateQuiz(false)}>取消</Button>
            <Button type="primary" onClick={handleCreateQuiz} loading={creatingQuiz}>发布考核</Button>
          </div>
        </Form>
      </Modal>

      {/* ─── 答题弹窗 ─── */}
      <Modal title={`📋 ${selectedQuizDetail?.title || '考核详情'}`}
        open={!!selectedQuizDetail}
        onCancel={() => setSelectedQuizDetail(null)}
        width={700}
        footer={selectedQuizDetail ? <Button type="primary" onClick={() => handleSubmitPeerQuiz(selectedQuizDetail.id)} loading={submittingQuiz}>提交答案</Button> : null}>
        {selectedQuizDetail?.questions?.map((q, idx) => (
          <div key={q.id} style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 8 }}>
            <Text strong>{idx + 1}. {q.content} <Tag color="blue">{getTypeLabel(q.questionType)}</Tag></Text>
            {q.options && ['SINGLE_CHOICE','MULTIPLE_CHOICE','TRUE_FALSE'].includes(q.questionType) && (
              <Radio.Group style={{ display: 'block', marginTop: 8 }}
                onChange={e => setQuizAnswers(prev => ({ ...prev, [q.id]: e.target.value }))}>
                {Object.entries(q.options).map(([k, v]) => (
                  <Radio key={k} value={k} style={{ display: 'block', marginBottom: 4 }}>
                    {k}. {typeof v === 'string' ? v : v?.text || v?.label || ''}
                  </Radio>
                ))}
              </Radio.Group>
            )}
            {['FILL_BLANK','SHORT_ANSWER','ESSAY'].includes(q.questionType) && (
              <Input.TextArea style={{ marginTop: 8 }} rows={3} placeholder="请输入答案"
                onChange={e => setQuizAnswers(prev => ({ ...prev, [q.id]: e.target.value }))} />
            )}
          </div>
        ))}
        {(!selectedQuizDetail?.questions || selectedQuizDetail.questions.length === 0) && <Empty description="暂无题目" />}
      </Modal>

      {/* ─── 考核结果弹窗 ─── */}
      <Modal title={selectedQuizResults?.title || '考核结果'}
        open={!!selectedQuizResults}
        onCancel={() => setSelectedQuizResults(null)}
        width={600} footer={null}>
        {selectedQuizResults?.participants?.length > 0 ? (
          <Table dataSource={selectedQuizResults.participants} rowKey="id" size="small" pagination={false}
            columns={[
              { title: '学生', dataIndex: 'studentName', key: 'name' },
              { title: '状态', dataIndex: 'status', key: 'status',
                render: s => s === 'COMPLETED' ? <Tag color="green">已完成</Tag> : <Tag color="orange">待完成</Tag>
              },
              { title: '得分', key: 'score',
                render: (_, r) => r.status === 'COMPLETED' ? `${r.score || 0} / ${r.totalQuestions || 0}` : '-'
              },
              { title: '完成时间', dataIndex: 'completedAt', key: 'completedAt',
                render: t => t ? new Date(t).toLocaleString('zh-CN') : '-'
              },
            ]} />
        ) : (
          <Empty description="暂无参与记录" />
        )}
      </Modal>
    </div>
  );
}
