import { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate, useOutletContext } from 'react-router-dom';
import { Card, Typography, Button, Spin, Tag, Progress, Radio, Checkbox, Input, Space, Alert, Empty, List, message, Divider, Steps } from 'antd';
import { CheckCircleOutlined, CloseCircleOutlined, ArrowLeftOutlined, ArrowRightOutlined, BookOutlined, FileTextOutlined, RobotOutlined } from '@ant-design/icons';
import { courseAPI, learningAPI, answerAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text, Paragraph } = Typography;

const DIFFICULTY_COLORS = { 1: 'green', 2: 'cyan', 3: 'blue', 4: 'orange', 5: 'red' };

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
  const [answeredCount, setAnsweredCount] = useState(0);

  const currentKp = knowledgePoints[currentKpIndex];
  const prevCourseIdRef = useRef(null);

  // 🔗 联动：左侧切换课程时自动导航到新课程（跳过首次挂载）
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
  }, [selectedCourseId]);

  // 加载课程和学习数据
  useEffect(() => {
    if (!courseCode) return;
    loadData();
  }, [courseCode]);

  const loadData = async () => {
    setLoading(true);
    try {
      // 获取课程信息
      const infoRes = await courseAPI.getByCode(courseCode);
      const course = infoRes?.data || infoRes;
      setCourseInfo(course);

      if (course?.id) {
        // 并行加载知识点和诊断数据
        const [kpRes, diagRes] = await Promise.all([
          learningAPI.getKpsByCourse(course.id),
          user?.id ? learningAPI.getDiagnosisProfile(user.id, course.id) : Promise.resolve(null),
        ]);

        const kps = kpRes?.data || kpRes || [];
        setKnowledgePoints(kps);

        // 加载掌握度映射
        if (diagRes?.data?.knowledgeMasteries) {
          const map = {};
          diagRes.data.knowledgeMasteries.forEach(d => {
            if (d.knowledgePointId) map[d.knowledgePointId] = d.masteryLevel;
          });
          setMasteryMap(map);
        }

        // 加载第一个知识点的习题
        if (kps.length > 0) {
          loadQuestions(kps[0].id);
        }
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
      setAnsweredCount(0);
    } catch (err) {
      console.error('Failed to load questions:', err);
      setQuestions([]);
    }
  };

  // 切换知识点
  const switchKp = useCallback((index) => {
    if (index < 0 || index >= knowledgePoints.length) return;
    setCurrentKpIndex(index);
    const kp = knowledgePoints[index];
    if (kp?.id) loadQuestions(kp.id);
  }, [knowledgePoints]);

  // 选择/输入答案
  const handleSelectAnswer = (questionId, value) => {
    setSelectedAnswers(prev => ({ ...prev, [questionId]: value }));
  };

  // 处理多选
  const handleMultiSelect = (questionId, checkedValues) => {
    setSelectedAnswers(prev => ({ ...prev, [questionId]: checkedValues.sort().join(',') }));
  };

  // 加载掌握度状态（答题后刷新用）
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

  // 获取题目类型中文名
  const getTypeLabel = (type) => {
    const labels = {
      SINGLE_CHOICE: '单选题', MULTIPLE_CHOICE: '多选题', TRUE_FALSE: '判断题',
      FILL_BLANK: '填空题', SHORT_ANSWER: '简答题', ESSAY: '论述题',
    };
    return labels[type] || type;
  };

  // 判断题型是否需要选项
  const needsOptions = (type) => {
    return ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'].includes(type);
  };

  // 判断题型是否需要文本输入
  const isTextInput = (type) => {
    return ['FILL_BLANK', 'SHORT_ANSWER', 'ESSAY'].includes(type);
  };

  // 提交答案
  const handleSubmitAnswer = async (questionId, qType) => {
    const answer = selectedAnswers[questionId];
    if (!answer || (isTextInput(qType) && !answer.trim())) {
      message.warning('请先输入答案');
      return;
    }

    setSubmitting(true);
    try {
      const res = await answerAPI.submit({
        questionId,
        studentAnswer: answer,
        timeSpentSeconds: 0,
      });
      const result = res?.data || res;
      setSubmitResults(prev => ({ ...prev, [questionId]: result }));
      setAnsweredCount(prev => prev + 1);

      if (result?.correct) {
        message.success('✅ 回答正确！');
        // 答题正确后刷新掌握度
        if (courseInfo?.id) refreshMastery(courseInfo.id);
      } else {
        message.error('❌ 回答错误');
      }
    } catch (err) {
      message.error('提交失败: ' + (err.message || '未知错误'));
    }
    setSubmitting(false);
  };

  // 获取掌握度状态
  const getMasteryStatus = (kpId) => {
    const val = masteryMap[kpId];
    if (val == null) return { color: '#d9d9d9', label: '未学习', percent: 0 };
    if (val >= 0.8) return { color: '#52c41a', label: '已掌握', percent: Math.round(val * 100) };
    if (val >= 0.5) return { color: '#1677ff', label: '学习中', percent: Math.round(val * 100) };
    return { color: '#faad14', label: '待巩固', percent: Math.round(val * 100) };
  };

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 120 }} />;

  if (!courseInfo) {
    return <Alert type="error" message="课程不存在" description={`未找到课程 ${courseCode}`} showIcon />;
  }

  const allAnswered = questions.length > 0 && questions.every(q => submitResults[q.id]);
  const totalKps = knowledgePoints.length;
  const learnedKps = knowledgePoints.filter(kp => (masteryMap[kp.id] || 0) >= 0.5).length;

  return (
    <div style={{ maxWidth: 1100, margin: '0 auto' }}>
      {/* 返回 + 课程信息 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Space>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate('/student/dashboard')}>
            返回
          </Button>
          <Title level={4} style={{ margin: 0 }}>📚 {courseInfo.name}</Title>
          <Text type="secondary">编号: {courseInfo.courseCode}</Text>
        </Space>
        <Space>
          <Text>进度:</Text>
          <Progress percent={totalKps > 0 ? Math.round(learnedKps / totalKps * 100) : 0} size="small" style={{ width: 120 }} />
        </Space>
      </div>

      <div style={{ display: 'flex', gap: 16 }}>
        {/* 左侧：知识点导航 */}
        <Card size="small" style={{ width: 240, flexShrink: 0, maxHeight: 600, overflow: 'auto' }}>
          <Text strong style={{ display: 'block', marginBottom: 8 }}>📖 知识点列表</Text>
          <List
            size="small"
            dataSource={knowledgePoints}
            renderItem={(kp, idx) => {
              const status = getMasteryStatus(kp.id);
              return (
                <List.Item
                  key={kp.id}
                  onClick={() => switchKp(idx)}
                  style={{
                    cursor: 'pointer',
                    padding: '6px 8px',
                    borderRadius: 4,
                    background: idx === currentKpIndex ? '#e6f4ff' : 'transparent',
                    borderLeft: idx === currentKpIndex ? '3px solid #1677ff' : '3px solid transparent',
                  }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: 6, width: '100%' }}>
                    <Tag color={status.color} style={{ margin: 0, fontSize: 10, lineHeight: '16px', minWidth: 48, textAlign: 'center' }}>
                      {status.label}
                    </Tag>
                    <Text style={{ flex: 1, fontSize: 13 }} ellipsis={{ tooltip: kp.name }}>
                      {idx + 1}. {kp.name}
                    </Text>
                  </div>
                </List.Item>
              );
            }}
          />
        </Card>

        {/* 右侧：学习内容 */}
        <div style={{ flex: 1 }}>
          {currentKp ? (
            <div>
              {/* 当前知识点标题 */}
              <Card size="small" style={{ marginBottom: 12 }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <Title level={5} style={{ margin: 0 }}>
                      第 {currentKpIndex + 1} 课：{currentKp.name}
                    </Title>
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
                  <Progress
                    type="circle"
                    percent={getMasteryStatus(currentKp.id).percent}
                    size={48}
                    strokeColor={getMasteryStatus(currentKp.id).color}
                    format={p => p > 0 ? `${p}%` : '?'}
                  />
                </div>
              </Card>

              {/* 知识点内容 */}
              {currentKp.content && (
                <Card size="small" title="📖 学习内容" style={{ marginBottom: 12 }}>
                  <Paragraph style={{ whiteSpace: 'pre-wrap' }}>{currentKp.content}</Paragraph>
                </Card>
              )}

              {/* 练习题 */}
              <Card size="small" title={`📝 练习题（${questions.length} 题）`} style={{ marginBottom: 12 }}>
                {questions.length === 0 ? (
                  <Empty description="该知识点暂无练习题" />
                ) : (
                  questions.map((q, qIdx) => {
                    const result = submitResults[q.id];
                    const selected = selectedAnswers[q.id];
                    const disabled = !!result;

                    const qType = q.questionType || 'SINGLE_CHOICE';
                    // 解析选项
                    let options = [];
                    if (needsOptions(qType)) {
                      try {
                        let raw = typeof q.options === 'string' ? JSON.parse(q.options) : (q.options || []);
                        if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
                          options = Object.entries(raw).map(([k, v]) => ({ label: k, text: v }));
                        } else if (Array.isArray(raw)) {
                          options = raw.map(opt => {
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
                    }

                    return (
                      <div key={q.id} style={{ marginBottom: 16, padding: 12, background: '#fafafa', borderRadius: 8 }}>
                        <Text strong>
                          {qIdx + 1}. {q.content}
                          <Tag style={{ marginLeft: 6 }} color="blue">{getTypeLabel(qType)}</Tag>
                          {q.difficulty && <Tag color={DIFFICULTY_COLORS[q.difficulty]}>难度{q.difficulty}</Tag>}
                        </Text>

                        {/* 单选题: Radio */}
                        {qType === 'SINGLE_CHOICE' || qType === 'TRUE_FALSE' ? (
                          <Radio.Group
                            style={{ display: 'block', marginTop: 8 }}
                            value={selected}
                            onChange={e => handleSelectAnswer(q.id, e.target.value)}
                            disabled={disabled}
                          >
                            {options.map(opt => (
                              <Radio key={opt.label} value={opt.label} style={{ display: 'block', marginBottom: 4 }}>
                                <Text style={{
                                  color: result && opt.label === result.correctAnswer ? '#52c41a' :
                                         result && opt.label === result.studentAnswer && !result.correct ? '#ff4d4f' :
                                         'inherit',
                                  fontWeight: result && opt.label === result.correctAnswer ? 'bold' : 'normal',
                                }}>
                                  {opt.label}. {opt.text}
                                </Text>
                              </Radio>
                            ))}
                          </Radio.Group>
                        ) : qType === 'MULTIPLE_CHOICE' ? (
                          /* 多选题: Checkbox */
                          <Checkbox.Group
                            style={{ display: 'block', marginTop: 8 }}
                            value={selected ? selected.split(',') : []}
                            onChange={vals => handleMultiSelect(q.id, vals)}
                            disabled={disabled}
                          >
                            {options.map(opt => (
                              <Checkbox key={opt.label} value={opt.label} style={{ display: 'block', marginBottom: 4 }}>
                                <Text style={{
                                  color: result && result.correctAnswer && result.correctAnswer.split(',').includes(opt.label) ? '#52c41a' :
                                         result && result.studentAnswer && result.studentAnswer.split(',').includes(opt.label) && !result.correct ? '#ff4d4f' :
                                         'inherit',
                                }}>
                                  {opt.label}. {opt.text}
                                </Text>
                              </Checkbox>
                            ))}
                          </Checkbox.Group>
                        ) : qType === 'FILL_BLANK' ? (
                          /* 填空题: 文本输入框 */
                          <Input
                            style={{ marginTop: 8, maxWidth: 400 }}
                            placeholder="请输入答案"
                            value={selected || ''}
                            onChange={e => handleSelectAnswer(q.id, e.target.value)}
                            disabled={disabled}
                          />
                        ) : qType === 'SHORT_ANSWER' ? (
                          /* 简答题: 文本区域 */
                          <Input.TextArea
                            style={{ marginTop: 8 }}
                            rows={3}
                            placeholder="请输入答案"
                            value={selected || ''}
                            onChange={e => handleSelectAnswer(q.id, e.target.value)}
                            disabled={disabled}
                          />
                        ) : qType === 'ESSAY' ? (
                          /* 论述题: 大文本区域 */
                          <Input.TextArea
                            style={{ marginTop: 8 }}
                            rows={6}
                            placeholder="请详细论述你的观点..."
                            value={selected || ''}
                            onChange={e => handleSelectAnswer(q.id, e.target.value)}
                            disabled={disabled}
                          />
                        ) : null}

                        {/* 提交按钮 / 结果反馈 */}
                        {!disabled ? (
                          <Button
                            type="primary"
                            size="small"
                            style={{ marginTop: 8 }}
                            onClick={() => handleSubmitAnswer(q.id, qType)}
                            loading={submitting}
                            disabled={!selected}
                          >
                            提交答案
                          </Button>
                        ) : (
                          <Alert
                            style={{ marginTop: 8 }}
                            type={result?.correct ? 'success' : 'error'}
                            showIcon
                            icon={result?.correct ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
                            message={
                              <Space direction="vertical" size={2}>
                                <Text strong>{result?.correct ? '✅ 回答正确！' : '❌ 回答错误'}</Text>
                                <Text>正确答案：{result?.correctAnswer}</Text>
                                {result?.explanation && <Text type="secondary">解析：{result.explanation}</Text>}
                              </Space>
                            }
                          />
                        )}
                      </div>
                    );
                  })
                )}
              </Card>

              {/* 导航按钮 */}
              <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: 16 }}>
                <Button
                  icon={<ArrowLeftOutlined />}
                  disabled={currentKpIndex === 0}
                  onClick={() => switchKp(currentKpIndex - 1)}
                >
                  上一课
                </Button>
                <Button
                  type="primary"
                  icon={<ArrowRightOutlined />}
                  disabled={currentKpIndex >= totalKps - 1}
                  onClick={() => switchKp(currentKpIndex + 1)}
                >
                  下一课
                </Button>
              </div>
            </div>
          ) : (
            <Card>
              <Empty description="该课程暂无知识点内容" />
            </Card>
          )}
        </div>
      </div>
    </div>
  );
}
