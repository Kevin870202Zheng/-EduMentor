import { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import { Card, Typography, Button, Spin, Tag, Progress, Radio, Checkbox, Input, Space, Alert, Empty, List, message, Collapse } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ArrowLeftOutlined, ArrowRightOutlined, RobotOutlined } from '@ant-design/icons';
import { courseAPI, learningAPI, answerAPI, questionAnalysisAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text, Paragraph } = Typography;

const DIFFICULTY_COLORS = { 1: 'green', 2: 'cyan', 3: 'blue', 4: 'orange', 5: 'red' };

// ============================================================
// 纯工具函数（不依赖组件实例）
// ============================================================
const getTypeLabel = (type) => ({
  SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题',
  FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', ESSAY: '论述题',
}[type] || type);

const needsOptions = (type) =>
  ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type);

const parseOptions = (q) => {
  const type = q.questionType || 'SINGLE_CHOICE';
  if (!needsOptions(type)) return [];
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
// 子组件：AI 分析结果折叠面板（React.memo）
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
// 子组件：单道题目卡片（React.memo — 增量渲染核心）
// ============================================================
const QuestionCard = React.memo(({
  question, index, selected, result, disabled,
  parsedOptions, submitting, analyzing, analysisResult,
  onSelectAnswer, onSubmitAnswer, onAnalyze,
}) => {
  const qId = question.id;
  const qType = question.questionType || 'SINGLE_CHOICE';

  const renderOptions = (options) => {
    if (qType === 'SINGLE_CHOICE' || qType === 'TRUE_FALSE') {
      return (
        <Radio.Group style={{ display: 'block', marginTop: 8 }}
          value={selected} onChange={e => onSelectAnswer(qId, e.target.value)} disabled={disabled}>
          {options.map(opt => (
            <Radio key={opt.label} value={opt.label} style={{ display: 'block', marginBottom: 4 }}>
              <Text style={{
                color: result && opt.label === result.correctAnswer ? '#52c41a' :
                       result && opt.label === result.studentAnswer && !result.correct ? '#ff4d4f' : 'inherit',
                fontWeight: result && opt.label === result.correctAnswer ? 'bold' : 'normal',
              }}>{opt.label}. {opt.text}</Text>
            </Radio>
          ))}
        </Radio.Group>
      );
    }
    if (qType === 'MULTIPLE_CHOICE') {
      return (
        <Checkbox.Group style={{ display: 'block', marginTop: 8 }}
          value={selected ? selected.split(',') : []}
          onChange={vals => onSelectAnswer(qId, vals.sort().join(','))} disabled={disabled}>
          {options.map(opt => (
            <Checkbox key={opt.label} value={opt.label} style={{ display: 'block', marginBottom: 4 }}>
              <Text style={{
                color: result && result.correctAnswer?.split(',').includes(opt.label) ? '#52c41a' :
                       result && result.studentAnswer?.split(',').includes(opt.label) && !result.correct ? '#ff4d4f' : 'inherit',
              }}>{opt.label}. {opt.text}</Text>
            </Checkbox>
          ))}
        </Checkbox.Group>
      );
    }
    return null;
  };

  const renderInput = () => {
    if (qType === 'FILL_BLANK') {
      return <Input style={{ marginTop: 8, maxWidth: 400 }} placeholder="请输入答案"
        value={selected || ''} onChange={e => onSelectAnswer(qId, e.target.value)} disabled={disabled} />;
    }
    if (qType === 'SHORT_ANSWER') {
      return <Input.TextArea style={{ marginTop: 8 }} rows={3} placeholder="请输入答案"
        value={selected || ''} onChange={e => onSelectAnswer(qId, e.target.value)} disabled={disabled} />;
    }
    if (qType === 'ESSAY') {
      return <Input.TextArea style={{ marginTop: 8 }} rows={6} placeholder="请详细论述你的观点..."
        value={selected || ''} onChange={e => onSelectAnswer(qId, e.target.value)} disabled={disabled} />;
    }
    return null;
  };

  return (
    <div style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 8 }}>
      <Text strong>
        {index + 1}. {question.content}
        <Tag style={{ marginLeft: 6 }} color="blue">{getTypeLabel(qType)}</Tag>
        {question.difficulty && <Tag color={DIFFICULTY_COLORS[question.difficulty]}>难度{question.difficulty}</Tag>}
      </Text>

      {needsOptions(qType) ? renderOptions(parsedOptions) : renderInput()}

      {!disabled ? (
        <div style={{ marginTop: 8 }}>
          <Button type="primary" size="small"
            onClick={() => onSubmitAnswer(qId, qType, selected)}
            loading={submitting} disabled={!selected}>提交答案</Button>
          <Button size="small" icon={<RobotOutlined />} style={{ marginLeft: 8 }}
            onClick={() => onAnalyze(question, selected)} loading={analyzing}>AI 分析</Button>
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
            onClick={() => onAnalyze(question, result?.studentAnswer || selected)} loading={analyzing}>AI 分析</Button>
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
  const [currentKpIndex, setCurrentKpIndex] = useState(0);
  const [questions, setQuestions] = useState([]);
  const [masteryMap, setMasteryMap] = useState({});
  const [selectedAnswers, setSelectedAnswers] = useState({});
  const [submitResults, setSubmitResults] = useState({});
  const [analysisResults, setAnalysisResults] = useState({});
  const [analyzingQuestions, setAnalyzingQuestions] = useState({});

  const currentKp = knowledgePoints[currentKpIndex];
  const prevCourseIdRef = useRef(null);

  // ─── 稳定的事件回调（useCallback 确保 QuestionCard memo 生效） ───

  const handleSelectAnswer = useCallback((questionId, value) => {
    setSelectedAnswers(prev => ({ ...prev, [questionId]: value }));
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
    if (!answer || (qType !== 'SINGLE_CHOICE' && qType !== 'MULTIPLE_CHOICE' && qType !== 'TRUE_FALSE' && !answer.trim())) {
      if (!answer) { message.warning('请先输入答案'); return; }
    }
    if (!answer || ((qType === 'FILL_BLANK' || qType === 'SHORT_ANSWER' || qType === 'ESSAY') && !answer.trim())) {
      message.warning('请先输入答案');
      return;
    }
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
        const [kpRes, diagRes] = await Promise.all([
          learningAPI.getKpsByCourse(course.id),
          user?.id ? learningAPI.getDiagnosisProfile(user.id, course.id) : Promise.resolve(null),
        ]);
        const kps = kpRes?.data || kpRes || [];
        setKnowledgePoints(kps);
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
      setSelectedAnswers({});
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

  // ─── useMemo 缓存（输入/选择答案时不重算） ───

  // 左侧知识点列表
  const kpListItems = useMemo(() => {
    return knowledgePoints.map((kp, idx) => {
      const val = masteryMap[kp.id];
      let status;
      if (val == null) status = { color: '#d9d9d9', label: '未学习', percent: 0 };
      else if (val >= 0.8) status = { color: '#52c41a', label: '已掌握', percent: Math.round(val * 100) };
      else if (val >= 0.5) status = { color: '#1677ff', label: '学习中', percent: Math.round(val * 100) };
      else status = { color: '#faad14', label: '待巩固', percent: Math.round(val * 100) };
      return { kp, idx, status, isActive: idx === currentKpIndex };
    });
  }, [knowledgePoints, masteryMap, currentKpIndex]);

  // 选项解析结果
  const parsedQuestions = useMemo(() => {
    return questions.map(q => ({ id: q.id, options: parseOptions(q) }));
  }, [questions]);

  // 进度信息
  const progressInfo = useMemo(() => {
    const total = knowledgePoints.length;
    const learned = knowledgePoints.filter(kp => (masteryMap[kp.id] || 0) >= 0.5).length;
    return { total, learned, percent: total > 0 ? Math.round(learned / total * 100) : 0 };
  }, [knowledgePoints, masteryMap]);

  // ─── 渲染 ───

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 120 }} />;
  if (!courseInfo) {
    return <Alert type="error" message="课程不存在" description={`未找到课程 ${courseCode}`} showIcon />;
  }

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      {/* 顶部：返回 + 课程信息 + 进度 */}
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
        {/* 左侧：知识点导航（useMemo 缓存，输入答案时不重渲染） */}
        <Card size="small" style={{ width: 240, flexShrink: 0, maxHeight: 600, overflow: 'auto' }}>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>📖 知识点列表</Text>
          <List size="small" dataSource={kpListItems} renderItem={(item) => (
            <List.Item key={item.kp.id} onClick={() => switchKp(item.idx)}
              style={{
                cursor: 'pointer', padding: '6px 8px', borderRadius: 4,
                background: item.isActive ? '#e6f4ff' : 'transparent',
                borderLeft: item.isActive ? '3px solid #1677ff' : '3px solid transparent',
              }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, width: '100%' }}>
                <Tag color={item.status.color}
                  style={{ margin: 0, fontSize: 10, lineHeight: '16px', minWidth: 48, textAlign: 'center' }}>
                  {item.status.label}
                </Tag>
                <Text style={{ flex: 1, fontSize: 13 }} ellipsis={{ tooltip: item.kp.name }}>
                  {item.idx + 1}. {item.kp.name}
                </Text>
              </div>
            </List.Item>
          )} />
        </Card>

        {/* 右侧：学习内容 */}
        <div style={{ flex: 1 }}>
          {currentKp ? (
            <div>
              {/* 当前知识点标题 */}
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
                  <Progress type="circle" percent={kpListItems[currentKpIndex]?.status.percent || 0}
                    size={48} strokeColor={kpListItems[currentKpIndex]?.status.color || '#d9d9d9'}
                    format={p => p > 0 ? `${p}%` : '?'} />
                </div>
              </Card>

              {/* 知识点内容 */}
              {currentKp.content && (
                <Card size="small" title="📖 学习内容" style={{ marginBottom: 12 }}>
                  <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{currentKp.content}</Paragraph>
                </Card>
              )}

              {/* 练习题（React.memo QuestionCard → 只重渲染当前操作的题目） */}
              <Card size="small" title={`📝 练习题（${questions.length} 题）`} style={{ marginBottom: 12 }}>
                {questions.length === 0 ? (
                  <Empty description="该知识点暂无练习题" />
                ) : (
                  questions.map((q, qIdx) => {
                    const parsed = parsedQuestions.find(p => p.id === q.id);
                    return (
                      <QuestionCard key={q.id}
                        question={q} index={qIdx}
                        selected={selectedAnswers[q.id]}
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
              </Card>

              {/* 导航按钮 */}
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16 }}>
                <Button icon={<ArrowLeftOutlined />} disabled={currentKpIndex === 0}
                  onClick={() => switchKp(currentKpIndex - 1)}>上一课</Button>
                <Button type="primary" icon={<ArrowRightOutlined />}
                  disabled={currentKpIndex >= progressInfo.total - 1}
                  onClick={() => switchKp(currentKpIndex + 1)}>下一课</Button>
              </div>
            </div>
          ) : (
            <Card><Empty description="该课程暂无知识点内容" /></Card>
          )}
        </div>
      </div>
    </div>
  );
}
