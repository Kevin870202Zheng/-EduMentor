import { useNavigate } from 'react-router-dom';
import { Card, Typography, Button, Space, Tag } from 'antd';
import {
  CheckSquareOutlined, TeamOutlined, ArrowRightOutlined,
} from '@ant-design/icons';
import { useAuth } from '../../context/AuthContext';

const { Title, Paragraph, Text } = Typography;

/**
 * 🎓 课堂生成器（生成中心）— 智慧课堂模块
 * 场景一：知识点/章节勾选生成
 * 场景二：学段合作课堂（教师发起）
 */
export default function ClassroomGenerator() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const isTeacher = user?.role === 'teacher' || user?.role === 'admin';

  return (
    <div style={{ maxWidth: 960, margin: '0 auto' }}>
      <Title level={4} style={{ marginTop: 0 }}>🧪 课堂生成器</Title>
      <Paragraph type="secondary">
        根据你的学习需求，用两种方式生成全新的 AI 智慧课堂：
      </Paragraph>

      <Space direction="vertical" size="large" style={{ width: '100%', marginTop: 8 }}>
        {/* 场景一 */}
        <Card hoverable onClick={() => navigate('/student/classroom-generator/select')}
          style={{ cursor: 'pointer' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Space>
                <Tag color="blue" style={{ fontSize: 14, padding: '2px 10px' }}>
                  <CheckSquareOutlined /> 场景一
                </Tag>
                <Title level={5} style={{ margin: 0 }}>知识点选择生成</Title>
              </Space>
              <Paragraph style={{ margin: '12px 0 0', color: '#666' }}>
                从课程知识树中勾选你感兴趣的知识点或章节，AI 自动编排成
                一堂完整的智慧课堂（也可批量生成，每个知识点一课）。
              </Paragraph>
            </div>
            <Button type="primary" icon={<ArrowRightOutlined />} onClick={(e) => {
              e.stopPropagation();
              navigate('/student/classroom-generator/select');
            }}>
              开始勾选
            </Button>
          </div>
        </Card>

        {/* 场景二 */}
        <Card hoverable
          onClick={() => isTeacher
            ? navigate('/teacher/collab-classrooms')
            : navigate('/student/collab-classrooms')}
          style={{ cursor: 'pointer' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <Space>
                <Tag color="purple" style={{ fontSize: 14, padding: '2px 10px' }}>
                  <TeamOutlined /> 场景二
                </Tag>
                <Title level={5} style={{ margin: 0 }}>学段合作课堂</Title>
                {!isTeacher && <Text type="secondary" style={{ fontSize: 12 }}>（教师发起，学生参与协作）</Text>}
              </Space>
              <Paragraph style={{ margin: '12px 0 0', color: '#666' }}>
                基于中华传统故事库，跨学段学生协作共创：
                小学选故事 · 初中设计角色 · 高中创作台词 · 大学映射法律知识，
                教师审阅后由 AI 生成课堂。
              </Paragraph>
            </div>
            <Button type="primary" ghost icon={<ArrowRightOutlined />}
              onClick={(e) => {
                e.stopPropagation();
                navigate(isTeacher ? '/teacher/collab-classrooms' : '/student/collab-classrooms');
              }}>
              {isTeacher ? '进入协作工作台' : '查看协作任务'}
            </Button>
          </div>
        </Card>
      </Space>
    </div>
  );
}
