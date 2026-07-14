import { useState, useEffect } from 'react';
import { Card, Table, Button, Tag, Space, Modal, Input, message, Spin, Empty, Popconfirm } from 'antd';
import { ReloadOutlined, KeyOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons';
import { adminAPI } from '../services/api';

export default function AdminStudents() {
  const [students, setStudents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [resetOpen, setResetOpen] = useState(false);
  const [resetId, setResetId] = useState(null);
  const [resetUsername, setResetUsername] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [resetting, setResetting] = useState(false);

  useEffect(() => { loadStudents(); }, []);

  const loadStudents = async () => {
    setLoading(true);
    try {
      const res = await adminAPI.listUsers('STUDENT');
      setStudents(res?.data || res || []);
    } catch (err) {
      message.error('加载学生列表失败');
    }
    setLoading(false);
  };

  const handleToggleStatus = async (id, currentActive) => {
    try {
      await adminAPI.toggleStatus(id, !currentActive);
      message.success(!currentActive ? '学生已启用' : '学生已禁用');
      loadStudents();
    } catch (err) {
      message.error('操作失败: ' + (err.response?.data?.message || err.message));
    }
  };

  const openResetPwd = (record) => {
    setResetId(record.id);
    setResetUsername(record.username);
    setNewPassword('');
    setResetOpen(true);
  };

  const handleResetPwd = async () => {
    if (!newPassword || newPassword.length < 6) {
      message.warning('密码不少于6位');
      return;
    }
    setResetting(true);
    try {
      await adminAPI.resetPassword(resetId, newPassword);
      message.success('密码已重置');
      setResetOpen(false);
    } catch (err) {
      message.error('重置失败: ' + (err.response?.data?.message || err.message));
    }
    setResetting(false);
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
    { title: '姓名', dataIndex: 'displayName', key: 'displayName', width: 120 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 200,
      render: (v) => v || '-' },
    { title: '注册时间', dataIndex: 'createdAt', key: 'createdAt', width: 120,
      render: (v) => v ? new Date(v).toLocaleDateString('zh-CN') : '-' },
    { title: '最后登录', dataIndex: 'lastLoginAt', key: 'lastLoginAt', width: 160,
      render: (v) => v ? new Date(v).toLocaleString('zh-CN') : '从未登录' },
    { title: '状态', dataIndex: 'active', key: 'active', width: 80,
      render: (_, record) => record.isActive
        ? <Tag color="green">正常</Tag>
        : <Tag color="red">已禁用</Tag> },
    { title: '操作', key: 'action', width: 200,
      render: (_, record) => (
        <Space>
          <Button type="link" size="small" icon={<KeyOutlined />}
            onClick={() => openResetPwd(record)}>重置密码</Button>
          <Button type="link" size="small"
            onClick={() => handleToggleStatus(record.id, record.isActive)}>
            {record.isActive ? '禁用' : '启用'}
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card title="👥 学生管理" extra={
        <Button icon={<ReloadOutlined />} onClick={loadStudents}>刷新</Button>
      }>
        <Table dataSource={students} columns={columns} rowKey="id"
          loading={loading} size="small" pagination={{ pageSize: 20 }}
          locale={{ emptyText: <Empty description="暂无学生" /> }} />
      </Card>

      <Modal title={`重置密码 - ${resetUsername}`} open={resetOpen}
        onOk={handleResetPwd} onCancel={() => setResetOpen(false)}
        confirmLoading={resetting} okText="重置" cancelText="取消">
        <div style={{ marginBottom: 12 }}>
          <Input.Password placeholder="输入新密码（不少于6位）"
            value={newPassword} onChange={e => setNewPassword(e.target.value)} />
        </div>
      </Modal>
    </div>
  );
}
