import { useState, useEffect, useMemo } from 'react';
import { useNavigate, useOutletContext, useLocation } from 'react-router-dom';
import {
  Card, Button, Tree, Tag, Empty, message, Spin, Typography,
  Space, List, Input, Select, Radio, Alert, Progress,
} from 'antd';
import {
  ArrowLeftOutlined, ArrowUpOutlined, ArrowDownOutlined, DeleteOutlined,
  ThunderboltOutlined, PlayCircleOutlined,
} from '@ant-design/icons';
import { classroomApi } from '../../api/classroomApi';
import { knowledgeApi } from '../../api/knowledgeApi';
import { courseAPI } from '../../services/api';
import { useAuth } from '../../context/AuthContext';

const { Title, Text } = Typography;

/** 知识树 → antd Tree 数据
 * 兼容两种结构：
 *  - 嵌套结构 [{id, name, type, childrenCount, children}]
 *  - 扁平列表（后端 /courses/{courseId}/points/tree）[{knowledgePoint, level, hasChild}]
 */
function toTreeData(nodes) {
  // 扁平列表 → 按 parentKpId 分组递归构建嵌套树
  if (Array.isArray(nodes) && nodes.length > 0 && nodes[0]?.knowledgePoint) {
    const childrenMap = {};
    nodes.forEach(n => {
      const pid = n.knowledgePoint.parentKpId || 'root';
      if (!childrenMap[pid]) childrenMap[pid] = [];
      childrenMap[pid].push(n);
    });
    const build = (parentId) => {
      const items = childrenMap[parentId] || [];
      return items
        .sort((a, b) => (a.knowledgePoint.orderIndex || 0) - (b.knowledgePoint.orderIndex || 0))
        .map(n => {
          const kp = n.knowledgePoint;
          const childCount = (childrenMap[kp.id] || []).length;
          return {
            title: (
              <span>
                {kp.name}
                {childCount > 0 ? (
                  <Tag color="blue" style={{ marginLeft: 6, fontSize: 10 }}>{childCount}个知识点</Tag>
                ) : null}
              </span>
            ),
            key: kp.id,
            children: build(kp.id),
          };
        });
    };
    return build('root');
  }
  // 嵌套结构
  return (nodes || []).map(n => ({
    title: (
      <span>
        {n.name}
        {n.type === 'CHAPTER' && n.childrenCount ? (
          <Tag color="blue" style={{ marginLeft: 6, fontSize: 10 }}>{n.childrenCount}个知识点</Tag>
        ) : null}
      </span>
    ),
    key: n.id,
    children: n.children?.length ? toTreeData(n.children) : undefined,
  }));
}

/** 收集树中 {id → name} 映射（兼容扁平列表与嵌套结构） */
function collectNames(nodes, map = {}) {
  (nodes || []).forEach(n => {
    const kp = n.knowledgePoint || n;
    map[kp.id] = kp.name;
    if (n.children?.length) collectNames(n.children, map);
  });
  return map;
}

const AGGREGATE_LIMIT = 10;

/**
 * 🎓 场景一：知识点/章节勾选生成课堂
 * 从课程知识树勾选知识点或章节 → 聚合为一堂课（默认）或每知识点一课（批量）。
 *
 * 双模式：
 *  - 受控模式（传入 courseId）：课程学习中心 Tab3 使用，固定当前课程
 *  - 非受控模式：教师端独立页面使用，带课程下拉
 */
export default function KnowledgePointPicker({ courseId: fixedCourseId, courseName: fixedCourseName, onBack }) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const { selectedCourseId, studentCourses } = useOutletContext() || {};
  const isTeacher = user?.role === 'teacher' || user?.role === 'admin';
  const isFixed = !!fixedCourseId; // 受控模式

  const [courseId, setCourseId] = useState(fixedCourseId || selectedCourseId || '');
  const [courseOptions, setCourseOptions] = useState([]);
  const [tree, setTree] = useState([]);
  const [loading, setLoading] = useState(false);
  const [checkedKeys, setCheckedKeys] = useState([]);
  const [selectedOrder, setSelectedOrder] = useState([]);
  const [mode, setMode] = useState('aggregated');
  const [title, setTitle] = useState('');
  const [difficulty, setDifficulty] = useState(3);
  const [generating, setGenerating] = useState(false);
  const [batchJobId, setBatchJobId] = useState(null);
  const [batchStatus, setBatchStatus] = useState(null);

  const nameMap = useMemo(() => collectNames(tree), [tree]);
  const currentCourse = isFixed
    ? { courseId: fixedCourseId, courseName: fixedCourseName }
    : courseOptions.find(c => c.courseId === courseId);

  // 受控模式下固定课程
  useEffect(() => {
    if (fixedCourseId) setCourseId(fixedCourseId);
  }, [fixedCourseId]);

  // 课程数据源（仅非受控模式）：学生用已选课程；教师用全量课程列表
  useEffect(() => {
    if (isFixed) return;
    if (studentCourses?.length) {
      setCourseOptions(studentCourses.map(c => ({
        courseId: c.courseId,
        label: `${c.courseName || ''}（${c.courseCode || ''}）`,
        courseName: c.courseName,
      })));
      return;
    }
    if (isTeacher) {
      courseAPI.list({ publishedOnly: true }).then(res => {
        const items = res?.data?.items || res?.data || [];
        setCourseOptions(items.map(c => ({
          courseId: c.id,
          label: `${c.name || ''}（${c.courseCode || ''}）`,
          courseName: c.name,
        })));
      }).catch(() => setCourseOptions([]));
    }
  }, [studentCourses, isTeacher, isFixed]);

  useEffect(() => {
    if (selectedCourseId) setCourseId(selectedCourseId);
  }, [selectedCourseId]);

  // 从课程学习页跳转：携带预选课程与章节
  useEffect(() => {
    const state = location.state;
    if (!state) return;
    if (state.courseId) setCourseId(state.courseId);
    if (state.presetKpIds?.length) {
      setCheckedKeys(state.presetKpIds);
      setSelectedOrder(state.presetKpIds);
    }
    // 清除 state，避免刷新后重复
    window.history.replaceState({}, '');
  }, [location.state]);

  useEffect(() => {
    if (!courseId) return;
    setLoading(true);
    setTree([]);
    setCheckedKeys([]);
    setSelectedOrder([]);
    setTitle('');
    setBatchJobId(null);
    setBatchStatus(null);
    knowledgeApi
      .getKnowledgePointTree(courseId)
      .then(data => setTree(data || []))
      .catch(() => message.error('加载知识树失败'))
      .finally(() => setLoading(false));
  }, [courseId]);

  const onCheck = (checked) => {
    const arr = Array.isArray(checked) ? checked : checked.checked || [];
    setCheckedKeys(arr);
    setSelectedOrder(prev => {
      const kept = prev.filter(k => arr.includes(k));
      const added = arr.filter(k => !prev.includes(k));
      return [...kept, ...added];
    });
  };

  const move = (index, delta) => {
    setSelectedOrder(prev => {
      const next = [...prev];
      const target = index + delta;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const remove = (index) => {
    const id = selectedOrder[index];
    setSelectedOrder(prev => prev.filter(k => k !== id));
    setCheckedKeys(prev => prev.filter(k => k !== id));
  };

  const pollBatch = (jobId) => {
    const timer = setInterval(async () => {
      try {
        const res = await classroomApi.getGenerateStatus(jobId);
        setBatchStatus(res?.status || 'processing');
        if (res?.status === 'completed') {
          clearInterval(timer);
          setGenerating(false);
          message.success('全部课堂已生成完成');
          if (onBack) onBack();
          else navigate(isTeacher ? '/teacher/classrooms' : '/student/classrooms');
        } else if (res?.status === 'failed') {
          clearInterval(timer);
          setGenerating(false);
          message.error(res?.message || '批量生成失败');
        }
      } catch (e) {
        clearInterval(timer);
        setGenerating(false);
        message.error('查询生成状态失败');
      }
    }, 3000);
  };

  const handleGenerate = async () => {
    if (!courseId) { message.warning('请先选择课程'); return; }
    if (selectedOrder.length === 0) { message.warning('请至少勾选一个知识点或章节'); return; }
    if (mode === 'aggregated' && selectedOrder.length > AGGREGATE_LIMIT) {
      message.warning(`聚合模式建议不超过 ${AGGREGATE_LIMIT} 个知识点（当前 ${selectedOrder.length} 个），请改用批量模式或减少勾选`);
      return;
    }
    setGenerating(true);
    try {
      const res = await classroomApi.generateFromSelection({
        courseId,
        knowledgePointIds: selectedOrder,
        mode,
        title: mode === 'aggregated' ? title.trim() || undefined : undefined,
        difficulty,
        courseName: currentCourse?.courseName,
      });
      if (res?.mode === 'aggregated' && res?.id) {
        message.success('聚合课堂生成成功');
        navigate(`/student/classroom/${res.id}`);
      } else if (res?.mode === 'batch' && res?.jobId) {
        setBatchJobId(res.jobId);
        setBatchStatus('processing');
        message.info(`已提交 ${selectedOrder.length} 个课堂的批量生成任务`);
        pollBatch(res.jobId);
      } else {
        message.error('生成失败，请稍后重试');
        setGenerating(false);
      }
    } catch (err) {
      console.error('生成课堂失败:', err);
      message.error(err?.message || '生成课堂失败，请稍后重试');
      setGenerating(false);
    }
  };

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Space>
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => onBack ? onBack() : navigate(isTeacher ? '/teacher/classrooms' : '/student/classrooms')}>返回</Button>
          <Title level={4} style={{ margin: 0 }}>⚡ 课堂生成</Title>
        </Space>
      </div>

      {!isFixed && (
        <Card size="small" style={{ marginBottom: 12 }}>
          <Space wrap>
            <Text strong>课程：</Text>
            <Select
              style={{ width: 320 }}
              placeholder="选择课程"
              value={courseId || undefined}
              onChange={setCourseId}
              options={courseOptions.map(c => ({ label: c.label, value: c.courseId }))}
            />
            {currentCourse?.courseName && (
              <Text type="secondary">当前课程：{currentCourse.courseName}</Text>
            )}
          </Space>
        </Card>
      )}

      {!courseId ? (
        <Empty description="请先选择一门课程" />
      ) : (
        <Card size="small">
          <div style={{ display: 'flex', gap: 16, height: 480 }}>
            {/* 知识树 */}
            <div style={{ flex: 1.2, border: '1px solid #f0f0f0', borderRadius: 8, padding: 8, overflow: 'auto' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                课程知识树（勾选知识点或章节，章节=展开其下全部知识点）
              </Text>
              {loading ? (
                <div style={{ textAlign: 'center', marginTop: 60 }}><Spin /></div>
              ) : tree.length === 0 ? (
                <Empty description="暂无知识点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <Tree
                  checkable
                  defaultExpandAll
                  treeData={toTreeData(tree)}
                  checkedKeys={checkedKeys}
                  onCheck={onCheck}
                  selectable={false}
                />
              )}
            </div>
            {/* 已选列表 */}
            <div style={{ flex: 1, border: '1px solid #f0f0f0', borderRadius: 8, padding: 8, display: 'flex', flexDirection: 'column' }}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                已选（{selectedOrder.length} 个）· 可调整顺序
                {mode === 'aggregated' && selectedOrder.length > AGGREGATE_LIMIT && (
                  <Tag color="orange" style={{ marginLeft: 8 }}>超出聚合上限，建议切批量</Tag>
                )}
              </Text>
              <div style={{ flex: 1, overflow: 'auto', marginTop: 8 }}>
                {selectedOrder.length === 0 ? (
                  <Empty description="左侧勾选" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  <List
                    size="small"
                    dataSource={selectedOrder}
                    renderItem={(id, idx) => (
                      <List.Item
                        style={{ padding: '4px 0' }}
                        actions={[
                          <Button key="up" size="small" type="text" icon={<ArrowUpOutlined />} disabled={idx === 0} onClick={() => move(idx, -1)} />,
                          <Button key="down" size="small" type="text" icon={<ArrowDownOutlined />} disabled={idx === selectedOrder.length - 1} onClick={() => move(idx, 1)} />,
                          <Button key="del" size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => remove(idx)} />,
                        ]}
                      >
                        <Text style={{ fontSize: 13 }}>{idx + 1}. {nameMap[id] || '未知知识点'}</Text>
                      </List.Item>
                    )}
                  />
                )}
              </div>
            </div>
          </div>

          {/* 生成配置 */}
          <div style={{ marginTop: 16, paddingTop: 16, borderTop: '1px solid #f0f0f0' }}>
            <Space size="large" wrap align="center">
              <div>
                <Text strong style={{ marginRight: 8 }}>生成模式：</Text>
                <Radio.Group value={mode} onChange={e => setMode(e.target.value)}>
                  <Radio.Button value="aggregated">
                    <ThunderboltOutlined /> 聚合一堂课（{AGGREGATE_LIMIT} 个以内）
                  </Radio.Button>
                  <Radio.Button value="batch">📚 每知识点一课（批量）</Radio.Button>
                </Radio.Group>
              </div>
              {mode === 'aggregated' && (
                <Input
                  style={{ width: 280 }}
                  placeholder="课堂标题（留空自动生成）"
                  value={title}
                  onChange={e => setTitle(e.target.value)}
                  maxLength={50}
                />
              )}
              <div>
                <Text strong style={{ marginRight: 8 }}>难度：</Text>
                <Select value={difficulty} onChange={setDifficulty} style={{ width: 120 }}
                  options={[1, 2, 3, 4, 5].map(v => ({ value: v, label: `${'★'.repeat(v)}` }))} />
              </div>
              <Button type="primary" size="large" icon={<PlayCircleOutlined />}
                loading={generating} onClick={handleGenerate}
                disabled={selectedOrder.length === 0 || !!batchJobId}>
                生成课堂
              </Button>
            </Space>

            {batchJobId && (
              <div style={{ marginTop: 16 }}>
                <Text type="secondary">批量生成进度：</Text>
                <Progress
                  percent={batchStatus === 'completed' ? 100 : batchStatus === 'failed' ? 0 : 50}
                  status={batchStatus === 'failed' ? 'exception' : batchStatus === 'completed' ? 'success' : 'active'}
                  style={{ maxWidth: 400 }}
                />
              </div>
            )}

            {mode === 'batch' && (
              <Alert type="info" showIcon style={{ marginTop: 12 }}
                message="批量模式将为每个勾选的知识点（或章节）各生成一个课堂，可在「我的课堂」中查看全部结果。" />
            )}
          </div>
        </Card>
      )}
    </div>
  );
}
