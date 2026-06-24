import { useState } from 'react';
import { Card, Input, Button, Typography, Tag, Space, Spin, Empty } from 'antd';
import { SendOutlined, RobotOutlined, UserOutlined, BulbOutlined } from '@ant-design/icons';
import { qaAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useOutletContext } from 'react-router-dom';

const { Title, Text } = Typography;
const { TextArea } = Input;

const LEVEL_TAGS = {
  L1: { color: 'green', label: 'L1 提示引导' },
  L2: { color: 'blue', label: 'L2 分步引导' },
  L3: { color: 'orange', label: 'L3 结构化讲解' },
  L4: { color: 'red', label: 'L4 完整示范' },
  L5: { color: 'purple', label: 'L5 拓展延伸' },
};

export default function QATutoring() {
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState([
    { role: 'assistant', content: '你好！我是智学导师 EduMentor。有什么学习问题需要我帮忙吗？我会引导你思考，而不是直接给答案哦！😊', level: null }
  ]);
  const [loading, setLoading] = useState(false);
  const { user } = useAuth();
  const { selectedCourseId } = useOutletContext();

  const handleAsk = async (q) => {
    const query = q || question;
    if (!query.trim()) return;

    setMessages(prev => [...prev, { role: 'user', content: query }]);
    setQuestion('');
    setLoading(true);

    try {
      const res = await qaAPI.ask({
        question: query,
        student_id: String(user?.id || ''),
        course_id: selectedCourseId || '',
      });
      const data = res.data || res;
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: data.answer || '这是一个很好的问题！让我们一步步来分析...',
        level: data.level,
        related: data.related_knowledge
      }]);
    } catch (err) {
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: `很好的问题！关于「${query}」，让我来分析一下当前的知识点体系。\n\n建议你先回顾一下相关的基础概念，然后逐步深入理解。如果遇到具体的难点，可以进一步提问。`,
        level: 'L2',
        related: []
      }]);
    }
    setLoading(false);
  };

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <Title level={4} style={{ marginBottom: 16 }}>🤖 智能答疑辅导</Title>

      <Card style={{ minHeight: 400, marginBottom: 16, display: 'flex', flexDirection: 'column' }}>
        <div style={{ flex: 1, overflow: 'auto', maxHeight: 450 }}>
          {messages.map((msg, idx) => (
            <div key={idx} style={{
              marginBottom: 16, display: 'flex',
              flexDirection: msg.role === 'user' ? 'row-reverse' : 'row', gap: 8,
            }}>
              <div style={{
                maxWidth: '85%', padding: '12px 16px', borderRadius: 12,
                background: msg.role === 'user' ? '#1677ff' : '#f0f0f0',
                color: msg.role === 'user' ? '#fff' : '#333',
              }}>
                <div style={{ marginBottom: 4 }}>
                  {msg.role === 'user' ? <UserOutlined /> : <RobotOutlined style={{ color: '#1677ff' }} />}
                  <Text style={{ marginLeft: 6, fontSize: 12, color: msg.role === 'user' ? '#fff' : '#999' }}>
                    {msg.role === 'user' ? '我' : 'EduMentor'}
                  </Text>
                  {msg.level && <Tag {...LEVEL_TAGS[msg.level]} style={{ marginLeft: 8 }} />}
                </div>
                <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.8 }}>{msg.content}</div>
                {msg.related?.length > 0 && (
                  <div style={{ marginTop: 8, padding: 8, background: '#e6f7ff', borderRadius: 6 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      📚 相关知识点：{msg.related.map(r => r.name).join('、')}
                    </Text>
                  </div>
                )}
              </div>
            </div>
          ))}
          {loading && <Spin style={{ display: 'flex', justifyContent: 'center' }} />}
          {messages.length === 1 && !loading && (
            <div style={{ textAlign: 'center', marginTop: 40 }}>
              <Empty description="开始你的第一个问题吧！" />
            </div>
          )}
        </div>
      </Card>

      <div style={{ display: 'flex', gap: 8 }}>
        <TextArea
          value={question}
          onChange={e => setQuestion(e.target.value)}
          placeholder="输入你想问的学习问题..."
          rows={2}
          onPressEnter={(e) => { e.preventDefault(); handleAsk(); }}
        />
        <Button type="primary" icon={<SendOutlined />} onClick={() => handleAsk()} loading={loading} style={{ height: 52 }}>
          提问
        </Button>
      </div>
    </div>
  );
}
