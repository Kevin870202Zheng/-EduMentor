import { useState } from 'react';
import { Card, Input, Button, Typography, Tag, Space, Spin, Divider, Empty } from 'antd';
import { SendOutlined, RobotOutlined, UserOutlined, BulbOutlined } from '@ant-design/icons';
import { qaAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;
const { TextArea } = Input;

const LEVEL_TAGS = {
  L1: { color: 'green', label: 'L1 提示引导' },
  L2: { color: 'blue', label: 'L2 分步引导' },
  L3: { color: 'orange', label: 'L3 结构化讲解' },
  L4: { color: 'red', label: 'L4 完整示范' },
  L5: { color: 'purple', label: 'L5 拓展延伸' },
};

const DEMO_RESPONSES = {
  '什么是梯度下降法？': `让我们用爬山来类比理解梯度下降法 🏔️

**第一步：理解核心思想**
想象你蒙着眼站在山上，想要走到山谷最低点。你该怎么做？
➡️ 每次沿着最陡的下坡方向迈一步
➡️ 这就是「梯度下降」的核心——沿着梯度（最陡方向）的反方向更新参数

**第二步：关键要素**
▸ **学习率**：每一步迈多大（步长）
▸ **梯度**：当前最陡的方向
▸ **迭代**：重复直到收敛

💡 你能用自己的话总结一下梯度下降的三个关键步骤吗？

🤔 **元认知反思**：想想看，学习率太大或太小会有什么问题？`,

  '为什么微积分基本定理这么重要？': `这是一个非常好的问题！让我从三个层面来讲解：

**1️⃣ 它连接了两个世界**
微分和积分原本是独立发展的，微积分基本定理告诉我们：微分和积分是互逆运算！

**2️⃣ 它让计算变得简单**
没有这个定理，求曲线下面积要做极限求和（黎曼和），有了它，只需要找到原函数代入上下限。

**3️⃣ 它开启了现代科学**
从物理学到经济学，这个定理是所有连续变化模型的数学基础。

📚 建议回顾：定积分的定义 → 原函数概念 → 牛顿-莱布尼茨公式`,

  '如何用矩阵解线性方程组？': `这是一个很好的线性代数应用问题！让我们一步步来看：

**第一步：将方程组转化为矩阵形式**
$$\\begin{cases} 2x + 3y = 8 \\\\ x - y = -1 \\end{cases}$$
可以写成：$A\\vec{x} = \\vec{b}$

**第二步：三种解法思路**
1️⃣ **逆矩阵法**：$\\vec{x} = A^{-1}\\vec{b}$（当A可逆时）
2️⃣ **高斯消元法**：将增广矩阵化为行阶梯形
3️⃣ **克拉默法则**：用行列式求解

**第三步：选择合适的方法**
▸ 小规模方程组 → 逆矩阵法最直接
▸ 大规模方程组 → 高斯消元法效率更高

💡 **思考题**：如果方程个数不等于未知数个数，该怎么处理？`,
};

export default function QATutoring() {
  const [question, setQuestion] = useState('');
  const [messages, setMessages] = useState([
    { role: 'assistant', content: '你好！我是智学导师 EduMentor。有什么学习问题需要我帮忙吗？我会引导你思考，而不是直接给答案哦！😊', level: null }
  ]);
  const [loading, setLoading] = useState(false);
  const { user } = useAuth();

  const sampleQuestions = [
    '什么是梯度下降法？',
    '为什么微积分基本定理这么重要？',
    '如何用矩阵解线性方程组？',
  ];

  const handleAsk = async (q) => {
    const query = q || question;
    if (!query.trim()) return;

    setMessages(prev => [...prev, { role: 'user', content: query }]);
    setQuestion('');
    setLoading(true);

    try {
      const res = await qaAPI.ask({ question: query, student_id: String(user?.id || 'demo'), course_id: 'demo' });
      const data = res.data || res;
      setMessages(prev => [...prev, {
        role: 'assistant',
        content: data.answer || '这是一个很好的问题！让我们一步步来分析...',
        level: data.level,
        related: data.related_knowledge
      }]);
    } catch (err) {
      // 如果 API 不可用，使用预置演示数据
      const answer = DEMO_RESPONSES[query] || `很好的问题！关于「${query}」，让我们一起来分析：

**第一步：梳理问题**
你觉得这个问题中，最关键的概念是什么？

**第二步：联系已知知识**
回想一下，之前学过的基础知识中有哪些与此相关？

**第三步：深入思考**
试试从不同的角度来理解这个问题...

💡 **提示**：先用自己的话复述一遍问题，看看是否有新的思路？

🤔 **元认知反思**：你目前对这个问题的理解处于什么水平？`;

      setMessages(prev => [...prev, {
        role: 'assistant',
        content: answer,
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
              marginBottom: 16,
              display: 'flex',
              flexDirection: msg.role === 'user' ? 'row-reverse' : 'row',
              gap: 8,
            }}>
              <div style={{
                maxWidth: '85%',
                padding: '12px 16px',
                borderRadius: 12,
                background: msg.role === 'user' ? '#1677ff' : '#f0f0f0',
                color: msg.role === 'user' ? '#fff' : '#333',
              }}>
                <div style={{ marginBottom: 4 }}>
                  {msg.role === 'user' ? <UserOutlined /> : <RobotOutlined style={{ color: '#1677ff' }} />}
                  <Text style={{
                    marginLeft: 6,
                    fontSize: 12,
                    color: msg.role === 'user' ? '#fff' : '#999'
                  }}>
                    {msg.role === 'user' ? '我' : 'EduMentor'}
                  </Text>
                  {msg.level && <Tag {...LEVEL_TAGS[msg.level]} style={{ marginLeft: 8 }} />}
                </div>
                <div style={{ whiteSpace: 'pre-wrap', lineHeight: 1.8 }}>
                  {msg.content}
                </div>
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

      <Space style={{ marginBottom: 12 }}>
        <BulbOutlined style={{ color: '#faad14' }} />
        <Text type="secondary">试试问这些问题：</Text>
        {sampleQuestions.map((q, i) => (
          <Button key={i} type="link" size="small" onClick={() => handleAsk(q)}>{q}</Button>
        ))}
      </Space>

      <div style={{ display: 'flex', gap: 8 }}>
        <TextArea
          value={question}
          onChange={e => setQuestion(e.target.value)}
          placeholder="输入你想问的学习问题..."
          rows={2}
          onPressEnter={(e) => { e.preventDefault(); handleAsk(); }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={() => handleAsk()}
          loading={loading}
          style={{ height: 52 }}
        >
          提问
        </Button>
      </div>
    </div>
  );
}
