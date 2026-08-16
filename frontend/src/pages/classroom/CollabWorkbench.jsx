import { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Card, Button, Modal, Select, Tag, Typography, message, Spin, Empty,
  Space, Steps, Alert, List,
} from 'antd';
import {
  ArrowLeftOutlined, UserAddOutlined, CheckOutlined, EyeOutlined,
  ThunderboltOutlined, PlayCircleOutlined,
} from '@ant-design/icons';
import { collabApi, ROLE_CONFIG, STAGE_LABEL } from '../../api/collabApi';
import { useAuth } from '../../context/AuthContext';
import StudentTaskForm from './StudentTaskForm';

const { Title, Text, Paragraph } = Typography;

const STATUS_META = {
  DRAFT: { color: 'default', label: '草稿' },
  INVITING: { color: 'blue', label: '邀请中' },
  COLLECTING: { color: 'processing', label: '收集学生产出' },
  REVIEW: { color: 'orange', label: '审阅中' },
  GENERATING: { color: 'purple', label: 'AI 生成中' },
  PUBLISHED: { color: 'success', label: '已发布' },
};

/** 解析任务 content JSON → 展示对象 */
function parseContent(task) {
  if (!task?.content) return null;
  try { return JSON.parse(task.content); } catch (e) { return null; }
}

/** 角色内容展示 */
function renderTaskContent(task) {
  const c = parseContent(task);
  if (!c) return <Text type="secondary">暂无内容</Text>;
  if (task.roleType === 'STORY_PICKER') {
    return (
      <Space direction="vertical" size={2}>
        <Text strong>📖 选定故事</Text>
        {c.reason && <Text type="secondary">理由：{c.reason}</Text>}
      </Space>
    );
  }
  if (task.roleType === 'CHARACTER_DESIGNER') {
    return <Text style={{ whiteSpace: 'pre-wrap' }}>{c.characters}</Text>;
  }
  if (task.roleType === 'SCRIPT_WRITER') {
    return <Text style={{ whiteSpace: 'pre-wrap' }}>{c.script}</Text>;
  }
  if (task.roleType === 'LEGAL_MAPPER') {
    return (
      <Space direction="vertical" size={2}>
        <Text strong>⚖️ 法律知识点 {c.knowledgePointIds?.length || 0} 个</Text>
        <Text type="secondary">映射说明：{c.mapping}</Text>
      </Space>
    );
  }
  return <Text>{JSON.stringify(c)}</Text>;
}

/**
 * 学段协作课堂 · 工作台（设计文档 §5）
 * 教师视图：邀请/查看/复核/生成；学生视图：提交自己的角色任务
 */
export default function CollabWorkbench() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();
  const isTeacher = user?.role === 'teacher' || user?.role === 'admin';
  const backPath = isTeacher ? '/teacher/collab-classrooms' : '/student/collab-classrooms';

  const [project, setProject] = useState(null);
  const [loading, setLoading] = useState(true);
  const [inviteRole, setInviteRole] = useState(null);
  const [candidates, setCandidates] = useState([]);
  const [selectedStudent, setSelectedStudent] = useState(null);
  const [inviting, setInviting] = useState(false);
  const [reviewTask, setReviewTask] = useState(null);
  const [reviewContent, setReviewContent] = useState('');
  const [reviewing, setReviewing] = useState(false);
  const [generating, setGenerating] = useState(false);

  const load = () => {
    setLoading(true);
    collabApi.getDetail(id)
      .then(res => setProject(res))
      .catch(() => message.error('加载项目失败'))
      .finally(() => setLoading(false));
  };
  useEffect(() => { load(); }, [id]);

  const tasks = project?.tasks || [];
  const doneCount = tasks.filter(t => t.status === 'COMPLETED' || t.status === 'REVIEWED').length;
  const allReviewed = tasks.length === 4 && tasks.every(t => t.status === 'REVIEWED');
  const myTask = tasks.find(t => t.assignedUserId === user?.id);

  const openInvite = async (roleType) => {
    setInviteRole(roleType);
    setSelectedStudent(null);
    const cfg = ROLE_CONFIG[roleType];
    try {
      const res = await collabApi.candidates(id, cfg.stage);
      setCandidates(res || []);
    } catch (e) {
      message.error('加载候选学生失败');
      setCandidates([]);
    }
  };

  const handleInvite = async () => {
    if (!selectedStudent) { message.warning('请选择学生'); return; }
    setInviting(true);
    try {
      await collabApi.invite(id, inviteRole, selectedStudent);
      message.success('邀请成功');
      setInviteRole(null);
      load();
    } catch (err) {
      message.error(err?.message || '邀请失败');
    } finally {
      setInviting(false);
    }
  };

  const openReview = (task) => {
    setReviewTask(task);
    const c = parseContent(task);
    setReviewContent(c ? JSON.stringify(c, null, 2) : '');
  };

  const handleReview = async () => {
    if (!reviewContent.trim()) { message.warning('内容不能为空'); return; }
    setReviewing(true);
    try {
      await collabApi.review(id, reviewTask.id, JSON.parse(reviewContent));
      message.success('已复核');
      setReviewTask(null);
      load();
    } catch (err) {
      message.error(err?.message || '复核失败');
    } finally {
      setReviewing(false);
    }
  };

  const handleGenerate = async () => {
    setGenerating(true);
    try {
      const res = await collabApi.generate(id);
      message.success('协作课堂生成成功！');
      const classroomId = res?.id;
      load();
      if (classroomId) {
        setTimeout(() => navigate(isTeacher ? `/teacher/classrooms` : `/student/classroom/${classroomId}`), 800);
      }
    } catch (err) {
      message.error(err?.message || '生成失败');
    } finally {
      setGenerating(false);
    }
  };

  if (loading) return <div style={{ textAlign: 'center', padding: 80 }}><Spin size="large" /></div>;
  if (!project) return <Empty description="项目不存在" />;

  const stepIndex = ['DRAFT', 'INVITING', 'COLLECTING', 'REVIEW', 'GENERATING', 'PUBLISHED']
    .indexOf(project.status);

  return (
    <div style={{ maxWidth: 1000, margin: '0 auto' }}>
      {/* 头部 */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 16 }}>
        <Space align="start">
          <Button type="text" icon={<ArrowLeftOutlined />} onClick={() => navigate(backPath)}>返回</Button>
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {project.title}
              <Tag color={STATUS_META[project.status]?.color} style={{ marginLeft: 12 }}>
                {STATUS_META[project.status]?.label || project.status}
              </Tag>
            </Title>
            <Text type="secondary">
              {project.description || '暂无描述'}
              {project.courseId ? ` · 关联课程 ${project.courseId?.substring(0, 8)}…` : ''}
              {project.difficulty ? ` · 难度 ${'★'.repeat(project.difficulty)}` : ''}
            </Text>
          </div>
        </Space>
        {project.classroomId && (
          <Button type="primary" icon={<PlayCircleOutlined />}
            onClick={() => navigate(isTeacher ? `/teacher/classrooms` : `/student/classroom/${project.classroomId}`)}>
            查看已生成的课堂
          </Button>
        )}
      </div>

      {/* 状态进度 */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <Steps
          size="small"
          current={Math.max(stepIndex, 0)}
          items={[
            { title: '创建项目' },
            { title: '邀请学生' },
            { title: '收集产出' },
            { title: '教师审阅' },
            { title: 'AI 生成' },
            { title: '已发布' },
          ]}
        />
      </Card>

      {isTeacher ? (
        /* ══════════ 教师视图 ══════════ */
        <div>
          <Alert type="info" showIcon style={{ marginBottom: 16 }}
            message="协作流程：邀请各学段学生 → 等待全部提交 → 逐项查看/修改 → 确认生成 AI 课堂"
            description={`当前进度：${doneCount}/4 个角色任务已提交`} />

          {/* 角色任务卡片 */}
          <List
            grid={{ gutter: 12, xs: 1, sm: 2 }}
            dataSource={tasks}
            renderItem={task => {
              const cfg = ROLE_CONFIG[task.roleType];
              const c = parseContent(task);
              const assigned = task.assignedUserId != null;
              return (
                <List.Item>
                  <Card size="small" style={{ height: '100%' }}>
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                      <Space style={{ width: '100%', justifyContent: 'space-between' }}>
                        <Text strong>{cfg.label}</Text>
                        {!assigned && <Tag>未邀请</Tag>}
                        {assigned && task.status === 'PENDING' && <Tag color="blue">待提交</Tag>}
                        {task.status === 'COMPLETED' && <Tag color="processing">已提交</Tag>}
                        {task.status === 'REVIEWED' && <Tag color="success">已复核</Tag>}
                      </Space>

                      {assigned ? (
                        <>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            学生：{task.assignedName || '已邀请'}
                          </Text>
                          {task.content ? (
                            <div style={{ background: '#fafafa', borderRadius: 6, padding: 8, minHeight: 40 }}>
                              {renderTaskContent(task)}
                            </div>
                          ) : (
                            <Text type="secondary" style={{ fontSize: 12 }}>等待学生提交...</Text>
                          )}
                          {(task.status === 'COMPLETED' || task.status === 'REVIEWED') && (
                            <Button size="small" icon={<EyeOutlined />} onClick={() => openReview(task)}>
                              查看 / 修改
                            </Button>
                          )}
                        </>
                      ) : (
                        <Button size="small" type="primary" ghost icon={<UserAddOutlined />}
                          disabled={project.status !== 'DRAFT' && project.status !== 'INVITING'}
                          onClick={() => openInvite(task.roleType)}>
                          邀请学生（{STAGE_LABEL[cfg.stage]}）
                        </Button>
                      )}
                    </Space>
                  </Card>
                </List.Item>
              );
            }}
          />

          {/* 生成按钮 */}
          {project.status === 'PUBLISHED' ? (
            <Alert type="success" showIcon style={{ marginTop: 16 }}
              message="课堂已发布，可点击右上角查看并播放。" />
          ) : (
            <div style={{ marginTop: 16, textAlign: 'center' }}>
              <Button
                type="primary"
                size="large"
                icon={<ThunderboltOutlined />}
                disabled={!allReviewed}
                loading={generating}
                onClick={handleGenerate}
              >
                {allReviewed ? '✨ 确认生成智慧课堂' : `还需复核 ${4 - doneCount} 个任务`}
              </Button>
              {!allReviewed && (
                <div style={{ marginTop: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    全部任务复核（或学生已提交）后方可生成
                  </Text>
                </div>
              )}
            </div>
          )}
        </div>
      ) : (
        /* ══════════ 学生视图 ══════════ */
        <div>
          <Alert type="info" showIcon style={{ marginBottom: 16 }}
            message={project.status === 'PUBLISHED' ? '🎉 协作完成，课堂已发布' : '你的协作任务'}
            description={project.status === 'PUBLISHED'
              ? '所有同学已完成创作，教师已生成智慧课堂。'
              : '请完成你被分配的角色任务并提交，等待教师审阅后生成课堂。'} />

          {myTask ? (
            <Card
              size="small"
              title={<Space>{ROLE_CONFIG[myTask.roleType]?.label}<Tag color="blue">{myTask.status}</Tag></Space>}
            >
              <StudentTaskForm projectId={project.id} task={myTask} project={project} onSubmitted={load} />
            </Card>
          ) : (
            <Card size="small" title="你的任务">
              <Empty description="你尚未被分配任务" />
            </Card>
          )}

          {/* 其他角色进度（仅展示） */}
          <Card size="small" style={{ marginTop: 12 }} title="团队进度">
            <Space wrap>
              {tasks.map(t => {
                const cfg = ROLE_CONFIG[t.roleType];
                const st = t.status === 'REVIEWED' || t.status === 'COMPLETED'
                  ? <Tag color="success">完成</Tag> : <Tag>待提交</Tag>;
                return <span key={t.id}>{cfg.label} {st}</span>;
              })}
            </Space>
          </Card>
        </div>
      )}

      {/* 邀请 Modal */}
      <Modal title={`邀请学生 · ${inviteRole ? ROLE_CONFIG[inviteRole]?.label : ''}`}
        open={!!inviteRole} onCancel={() => setInviteRole(null)} footer={null} width={440}>
        {candidates.length === 0 ? (
          <Empty description={`该学段暂无学生（${inviteRole ? STAGE_LABEL[ROLE_CONFIG[inviteRole]?.stage] : ''}）`}
            image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <>
            <Select
              style={{ width: '100%' }}
              placeholder="选择要邀请的学生"
              value={selectedStudent}
              onChange={setSelectedStudent}
              options={candidates.map(c => ({
                label: `${c.displayName}（${c.grade || '未知年级'}${c.school ? ' · ' + c.school : ''}）`,
                value: c.userId,
              }))}
            />
            <Button type="primary" block style={{ marginTop: 12 }} loading={inviting}
              disabled={!selectedStudent} onClick={handleInvite}>
              确认邀请
            </Button>
          </>
        )}
      </Modal>

      {/* 复核 Modal */}
      <Modal title={`复核任务 · ${reviewTask ? ROLE_CONFIG[reviewTask.roleType]?.label : ''}`}
        open={!!reviewTask} onCancel={() => setReviewTask(null)} onOk={handleReview}
        confirmLoading={reviewing} okText="确认复核" width={620}>
        <Paragraph type="secondary" style={{ fontSize: 12 }}>
          可修改学生提交的内容（JSON 格式），确认后标记为已复核。
        </Paragraph>
        <textarea
          rows={10}
          style={{ width: '100%', fontFamily: 'monospace', fontSize: 13, border: '1px solid #d9d9d9', borderRadius: 6, padding: 8 }}
          value={reviewContent}
          onChange={e => setReviewContent(e.target.value)}
        />
      </Modal>
    </div>
  );
}
