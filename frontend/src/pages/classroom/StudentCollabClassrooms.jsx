import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Card, Button, Tag, Table, Space, Typography, message, Empty, Progress } from 'antd';
import { TeamOutlined, RightOutlined } from '@ant-design/icons';
import { collabApi, ROLE_CONFIG } from '../../api/collabApi';
import { useAuth } from '../../context/AuthContext';

const { Title, Text } = Typography;

const STATUS_META = {
  DRAFT: { color: 'default', label: '草稿' },
  INVITING: { color: 'blue', label: '邀请中' },
  COLLECTING: { color: 'processing', label: '收集中' },
  REVIEW: { color: 'orange', label: '审阅中' },
  GENERATING: { color: 'purple', label: '生成中' },
  PUBLISHED: { color: 'success', label: '已发布' },
};

/**
 * 学生端 · 学段协作课堂（我参与的协作任务）
 */
export default function StudentCollabClassrooms() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [projects, setProjects] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    collabApi.listMine()
      .then(res => setProjects(res || []))
      .catch(() => message.error('加载协作任务失败'))
      .finally(() => setLoading(false));
  }, []);

  // 我参与的项目（有我的任务）
  const myProjects = projects.filter(p => (p.tasks || []).some(t => t.assignedUserId === user?.id));

  // 找我的角色
  const myRole = (p) => (p.tasks || []).find(t => t.assignedUserId === user?.id)?.roleType;

  const columns = [
    { title: '协作项目', dataIndex: 'title', key: 'title', render: (t, r) => (
      <Space direction="vertical" size={0}>
        <Text strong>{t}</Text>
        <Text type="secondary" style={{ fontSize: 12 }}>{r.description || '—'}</Text>
      </Space>
    )},
    { title: '我的角色', key: 'role', width: 200, render: (_, r) => {
      const role = myRole(r);
      return role ? <Tag color="purple">{ROLE_CONFIG[role]?.label || role}</Tag> : <Tag>观察者</Tag>;
    }},
    { title: '状态', dataIndex: 'status', key: 'status', width: 110, render: (s) => {
      const meta = STATUS_META[s] || { color: 'default', label: s };
      return <Tag color={meta.color}>{meta.label}</Tag>;
    }},
    { title: '团队进度', key: 'progress', width: 170, render: (_, r) => {
      const tasks = r.tasks || [];
      const done = tasks.filter(t => t.status === 'COMPLETED' || t.status === 'REVIEWED').length;
      return (
        <Space>
          <Progress percent={Math.round((done / Math.max(tasks.length, 1)) * 100)} size="small" style={{ width: 80 }} />
          <Text type="secondary" style={{ fontSize: 12 }}>{done}/{tasks.length}</Text>
        </Space>
      );
    }},
    { title: '操作', key: 'action', width: 130, render: (_, r) => {
      const role = myRole(r);
      const task = (r.tasks || []).find(t => t.assignedUserId === user?.id);
      return (
        <Button type="primary" size="small" icon={<RightOutlined />}
          onClick={() => navigate(`/student/collab-classrooms/${r.id}`)}>
          {task?.status === 'COMPLETED' || task?.status === 'REVIEWED' ? '查看进度' : '去完成'}
        </Button>
      );
    }},
  ];

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <Title level={4} style={{ marginTop: 0 }}>
        <TeamOutlined /> 学段协作课堂
      </Title>
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        由教师发起、跨学段同学协作共创的智慧课堂。完成你的角色任务（小学选故事 / 初中角色 / 高中台词 / 大学法律映射）。
      </Text>

      <Card size="small">
        {loading ? <div style={{ textAlign: 'center', padding: 40 }}>加载中...</div>
          : myProjects.length === 0 ? (
            <Empty description="暂无协作任务，等待教师邀请你参与吧" />
          ) : (
            <Table rowKey="id" dataSource={myProjects} columns={columns} pagination={false} size="middle" />
          )}
      </Card>
    </div>
  );
}
