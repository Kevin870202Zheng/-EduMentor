import { useEffect, useState } from 'react';
import { Card, Col, Row, Spin, Empty } from 'antd';
import { BookOutlined } from '@ant-design/icons';
import { themeAPI } from '../../services/api';

/**
 * 主题卡片网格（PRD v4.0 §11.3）
 *
 * 展示当前学段可见的 8 个法律主题卡片，点击卡片展开/选中知识阶梯。
 * 数据来自 themeAPI.getAll(stage)，后端按学段过滤并附带知识点计数。
 *
 * @param {string}  stage       当前学段代码
 * @param {string|null} selectedThemeId 当前选中的主题 ID
 * @param {(theme:object)=>void} onSelectTheme 主题选中回调
 * @param {(themes:array)=>void} onThemesLoaded 主题列表加载完成回调（可选，供父组件解析主题名）
 */
export default function ThemeGrid({ stage, selectedThemeId, onSelectTheme, onThemesLoaded }) {
  const [themes, setThemes] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!stage) {
      setThemes([]);
      onThemesLoaded?.([]);
      return;
    }
    let mounted = true;
    setLoading(true);
    themeAPI
      .getAll(stage)
      .then((res) => {
        const list = res?.data || res || [];
        if (mounted) setThemes(list);
        onThemesLoaded?.(list);
      })
      .catch(() => {
        if (mounted) setThemes([]);
        onThemesLoaded?.([]);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => { mounted = false; };
  }, [stage]);

  if (loading) {
    return <Spin size="large" style={{ display: 'flex', justifyContent: 'center', margin: '48px 0' }} />;
  }

  if (themes.length === 0) {
    return (
      <Empty
        description="该学段课程建设中，敬请期待"
        style={{ margin: '48px 0' }}
      >
        <span style={{ color: '#999', fontSize: 13 }}>可切换到其他学段查看已开设的主题</span>
      </Empty>
    );
  }

  return (
    <Row gutter={[16, 16]}>
      {themes.map((theme) => {
        const active = selectedThemeId === theme.id;
        return (
          <Col xs={24} sm={12} md={8} lg={6} key={theme.id}>
            <Card
              hoverable
              onClick={() => onSelectTheme?.(theme)}
              style={{
                borderColor: active ? '#1a73e8' : undefined,
                boxShadow: active ? '0 0 0 2px rgba(26,115,232,0.3)' : undefined,
              }}
            >
              <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 8 }}>
                <span style={{ fontSize: 26 }}>{theme.icon || '📚'}</span>
                <strong>{theme.name}</strong>
              </div>
              <div style={{ color: '#666', fontSize: 13, minHeight: 40 }}>
                {(theme.description || '').length > 42
                  ? `${theme.description.substring(0, 42)}...`
                  : theme.description || ''}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginTop: 8, color: '#888', fontSize: 12 }}>
                <BookOutlined />
                <span>{theme.kpCount || 0} 个知识点</span>
                {active && <span style={{ color: '#1a73e8', marginLeft: 'auto' }}>▼ 已展开</span>}
              </div>
            </Card>
          </Col>
        );
      })}
    </Row>
  );
}
