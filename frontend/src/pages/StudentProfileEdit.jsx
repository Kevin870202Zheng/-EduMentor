import { useState, useEffect } from 'react';
import { Card, Form, Input, Select, Button, message, Spin, Typography, Divider } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import { studentAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

// 学段身份选项（PRD v4.0 §19：学生学段身份与学生账号绑定）
const STAGE_OPTIONS = [
  { label: '🏫 小学', value: 'PRIMARY' },
  { label: '📖 初中', value: 'JUNIOR' },
  { label: '🟢 高中', value: 'SENIOR' },
  { label: '🎓 大学', value: 'UNIVERSITY' },
];

export default function StudentProfileEdit() {
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const { user } = useAuth();
  const [form] = Form.useForm();

  useEffect(() => {
    loadProfile();
  }, [user?.id]);

  const loadProfile = async () => {
    if (!user?.id) return;
    setLoading(true);
    try {
      const res = await studentAPI.getProfile(user.id);
      const data = res?.data || res || {};
      form.setFieldsValue(data);
    } catch (e) {
      message.error('加载个人信息失败');
    }
    setLoading(false);
  };

  const handleSave = async (values) => {
    if (!user?.id) return;
    setSaving(true);
    try {
      await studentAPI.updateProfile(user.id, values);
      message.success('个人信息已更新');
    } catch (e) {
      message.error('保存失败');
    }
    setSaving(false);
  };

  if (loading) return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', marginTop: 100 }} />;

  return (
    <div style={{ maxWidth: 600, margin: '0 auto' }}>
      <Title level={4} style={{ marginBottom: 24 }}>
        <UserOutlined style={{ marginRight: 8 }} />个人信息
      </Title>

      <Card>
        <Form form={form} layout="vertical" onFinish={handleSave}>
          <Title level={5}>基本资料</Title>
          <Form.Item label="学段身份" name="stage" tooltip="你的教育阶段，决定学段主题学习中默认展示的内容">
            <Select options={STAGE_OPTIONS} placeholder="选择学段（如：大学）" />
          </Form.Item>
          <Form.Item label="年级" name="grade">
            <Input placeholder="如：大一" />
          </Form.Item>
          <Form.Item label="班级" name="className">
            <Input placeholder="如：计科2101班" />
          </Form.Item>
          <Form.Item label="专业" name="major">
            <Input placeholder="如：计算机科学与技术" />
          </Form.Item>
          <Form.Item label="系" name="department">
            <Input placeholder="如：计算机系" />
          </Form.Item>
          <Form.Item label="学院" name="college">
            <Input placeholder="如：信息与计算机工程学院" />
          </Form.Item>

          <Divider />
          <Title level={5}>学习设置</Title>
          <Form.Item label="学习风格" name="learningStyle">
            <Select>
              <Select.Option value="visual">视觉型</Select.Option>
              <Select.Option value="auditory">听觉型</Select.Option>
              <Select.Option value="reading">阅读型</Select.Option>
              <Select.Option value="kinesthetic">实践型</Select.Option>
            </Select>
          </Form.Item>
          <Form.Item label="每日学习时长（分钟）" name="dailyStudyMinutes">
            <Input type="number" min={0} max={600} placeholder="如：120" />
          </Form.Item>

          <Button type="primary" htmlType="submit" loading={saving} block>
            保存修改
          </Button>
        </Form>
      </Card>
    </div>
  );
}
