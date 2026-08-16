import { useEffect, useState } from 'react';
import { Card, Progress, Spin, Empty, Tag, List, Button, Space, Typography } from 'antd';
import { ReadOutlined, LockOutlined, CheckCircleOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { themeAPI } from '../../services/api';

const { Text } = Typography;

/**
 * 知识阶梯（PRD v4.0 §11.4）
 *
 * 主题内按 depth_level 分层的知识阶梯：每层一个进度条 + 知识点卡片列表。
 * 数据来自 themeAPI.getKpsByStageAndTheme(themeId, stage)。
 *
 * @param {string} themeId 主题 ID
 * @param {string} stage   学段代码
 * @param {(kp:object)=>void} onEnterLearning 点击「进入学习」回调（可选）
 */
const DEPTH_LABELS = {
  1: '深度1 · 识记',
  2: '深度2 · 理解',
  3: '深度3 · 应用',
  4: '深度4 · 分析',
  5: '深度5 · 评价',
};

export default function KnowledgeLadder({ themeId, stage, onEnterLearning }) {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!themeId) {
      setGroups([]);
      return;
    }
    let mounted = true;
    setLoading(true);
    themeAPI
      .getKpsByStageAndTheme(themeId, stage)
      .then((res) => {
        if (mounted) setGroups(res?.data || res || []);
      })
      .catch(() => {
        if (mounted) setGroups([]);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => { mounted = false; };
  }, [themeId, stage]);

  if (loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', margin: '48px 0' }} />;
  }

  if (groups.length === 0) {
    return <Empty description="暂无知识点，请联系教师补充" style={{ margin: '32px 0' }} />;
  }

  return (
    <div style={{ marginTop: 24 }}>
      {groups.map((group) => (
        <Card
          key={group.depthLevel}
          size="small"
          style={{ marginBottom: 16, borderLeft: '4px solid #1a73e8' }}
          title={
            <Space>
              <span>{DEPTH_LABELS[group.depthLevel] || `深度${group.depthLevel}`}</span>
              <Tag color="blue">{group.knowledgePoints?.length || 0} 个知识点</Tag>
            </Space>
          }
        >
          <List
            dataSource={group.knowledgePoints || []}
            split={false}
            renderItem={(kp) => (
              <List.Item
                style={{ padding: '8px 0' }}
                actions={[
                  <Button
                    key="learn"
                    type="primary"
                    size="small"
                    icon={<PlayCircleOutlined />}
                    onClick={() => onEnterLearning?.(kp)}
                  >
                    进入学习
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  avatar={kp.difficulty >= 4
                    ? <LockOutlined style={{ fontSize: 18, color: '#bbb' }} />
                    : <CheckCircleOutlined style={{ fontSize: 18, color: '#52c41a' }} />}
                  title={<Space>
                    {kp.name}
                    <Tag color={kp.difficulty >= 4 ? 'orange' : 'green'} style={{ fontSize: 11 }}>
                      难度 {kp.difficulty || 1}
                    </Tag>
                    {kp.courseId ? null : <Tag style={{ fontSize: 11 }}>未关联课程</Tag>}
                  </Space>}
                  description={
                    <Text type="secondary" style={{ fontSize: 13 }}>
                      {(kp.description || '').length > 80
                        ? `${kp.description.substring(0, 80)}...`
                        : kp.description || '暂无描述'}
                    </Text>
                  }
                />
              </List.Item>
            )}
          />
        </Card>
      ))}
    </div>
  );
}
