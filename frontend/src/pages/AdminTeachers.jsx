import { useState, useEffect } from 'react';
import { Card, Table, Button, Tag, Space, Modal, Form, Input, Popconfirm, message, Spin } from 'antd';
import { PlusOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { adminAPI } from '../services/api';

export default function AdminTeachers() {
  const [teachers, setTeachers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createForm] = Form.useForm();

  useEffect(() => { loadTeachers(); }, []);

  const loadTeachers = async () => {
    setLoading(true);
    try {
      const res = await adminAPI.listUsers('TEACHER');
      setTeachers(res?.data || res || []);
    } catch (err) {
      message.error('加载教师列表失败');
    }
    setLoading(false);
  };

  const handleCreate = async () => {
    const values = await createForm.validateFields();
    setCreating(true);
    try {
      await adminAPI.createTeacher(values);
      message.success('教师账号创建成功');
      setCreateOpen(false);
      createForm.resetFields();
      loadTeachers();
    } catch (err) {
      message.error('创建失败: ' + (err.response?.data?.message || err.message));
    }
    setCreating(false);
  };

  const handleDelete = async (id) => {
    try {
      await adminAPI.deleteUser(id);
      message.success('教师已删除');
      loadTeachers();
    } catch (err) {
      message.error('删除失败: ' + (err.response?.data?.message || err.message));
    }
  };

  const handleToggleStatus = async (id, currentActive) => {
    try {
      await adminAPI.toggleStatus(id, !currentActive);
      message.success(!currentActive ? '教师已启用' : '教师已禁用');
      loadTeachers();
    } catch (err) {
      message.error('操作失败: ' + (err.response?.data?.message || err.message));
    }
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
    { title: '姓名', dataIndex: 'displayName', key: 'displayName', width: 120 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 200 },
    { title: '注册时间', dataIndex: 'createdAt', key: 'createdAt', width: 160,
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
          <Button type="link" size="small"
            onClick={() => handleToggleStatus(record.id, record.isActive)}>
            {record.isActive ? '禁用' : '启用'}
          </Button>
          <Popconfirm title="确定删除该教师？此操作不可恢复。"
            onConfirm={() => handleDelete(record.id)} okText="确定" cancelText="取消">
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Card title="👨‍🏫 教师管理" extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadTeachers}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreateOpen(true)}>创建教师</Button>
        </Space>
      }>
        <Table dataSource={teachers} columns={columns} rowKey="id"
          loading={loading} size="small" pagination={{ pageSize: 20 }} />
      </Card>

      <Modal title="创建教师账号" open={createOpen}
        onOk={handleCreate} onCancel={() => setCreateOpen(false)}
        confirmLoading={creating} okText="创建" cancelText="取消">
        <Form form={createForm} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input placeholder="教师登录用的用户名" />
          </Form.Item>
          <Form.Item name="displayName" label="姓名">
            <Input placeholder="教师显示名称" />
          </Form.Item>
          <Form.Item name="email" label="邮箱">
            <Input placeholder="选填" />
          </Form.Item>
          <Form.Item name="password" label="密码"
            rules={[{ required: true, message: '请输入密码' }, { min: 6, message: '密码不少于6位' }]}>
            <Input.Password placeholder="不少于6位" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
