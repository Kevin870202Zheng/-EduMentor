import { useState, useEffect, useCallback } from 'react';
import { Card, Row, Col, Button, Tag, Modal, Radio, Spin, Empty, message, Typography } from 'antd';
import { ThunderboltOutlined, BookOutlined, SafetyCertificateOutlined, ExperimentOutlined, ReadOutlined } from '@ant-design/icons';
import { pathApi } from '../../../api/pathApi';

const { Text } = Typography;

const STAGE_OPTIONS = [
  { value: 'PRIMARY', label: '小学' },
  { value: 'JUNIOR', label: '初中' },
  { value: 'SENIOR', label: '高中' },
  { value: 'UNIVERSITY', label: '大学' },
];

const ICON_MAP = {
  EXAM: <BookOutlined />,
  LITIGATION: <SafetyCertificateOutlined />,
  INTEREST: <ExperimentOutlined />,
  TEACHING: <ReadOutlined />,
};

const COLOR_MAP = {
  EXAM: '#1a73e8',
  LITIGATION: '#d93025',
  INTEREST: '#137333',
  TEACHING: '#6a1b9a',
};

/**
 * 推荐路径模板区 — 展示预设模板卡片。
 * 普通模板一键生成；师范生备课（TEACHING）先选目标学段 → 分课预览 → 确认生成。
 */
export default function PathTemplateSection({ courseId, studentId, onGenerated }) {
  const [templates, setTemplates] = useState([]);
  const [loading, setLoading] = useState(false);
  const [generatingId, setGeneratingId] = useState(null);

  // 师范生备课弹窗
  const [teachingTemplate, setTeachingTemplate] = useState(null);
  const [stage, setStage] = useState('PRIMARY');
  const [preview, setPreview] = useState(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  useEffect(() => {
    if (courseId) loadTemplates();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [courseId]);

  const loadTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const list = await pathApi.getTemplates(courseId);
      setTemplates(list || []);
    } catch {
      setTemplates([]);
    }
    setLoading(false);
  }, [courseId]);

  // 普通模板一键生成
  const generate = async (template) => {
    setGeneratingId(template.id);
    try {
      const path = await pathApi.createFromTemplate({
        studentId,
        courseId,
        templateId: template.id,
      });
      message.success(`已生成路径「${path.name}」，共 ${path.totalNodes} 个节点`);
      onGenerated?.(path);
    } catch (err) {
      message.error(err.message || '生成失败，请稍后重试');
    }
    setGeneratingId(null);
  };

  // 师范生备课：选学段 → 动态预览
  const openTeachingModal = (template) => {
    setTeachingTemplate(template);
    setStage('PRIMARY');
    setPreview(null);
  };

  const loadPreview = async (s) => {
    if (!teachingTemplate) return;
    setPreviewLoading(true);
    try {
      const p = await pathApi.previewTemplate(teachingTemplate.id, { stage: s });
      setPreview(p);
    } catch (err) {
      message.error(err.message || '预览失败');
      setPreview(null);
    }
    setPreviewLoading(false);
  };

  useEffect(() => {
    if (teachingTemplate) loadPreview(stage);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [teachingTemplate, stage]);

  const confirmTeaching = async () => {
    if (!teachingTemplate) return;
    setGeneratingId(teachingTemplate.id);
    try {
      const path = await pathApi.createFromTemplate({
        studentId,
        courseId,
        templateId: teachingTemplate.id,
        stage,
      });
      message.success(`已生成路径「${path.name}」，共 ${path.totalNodes} 个节点`);
      setTeachingTemplate(null);
      onGenerated?.(path);
    } catch (err) {
      message.error(err.message || '生成失败，请稍后重试');
    }
    setGeneratingId(null);
  };

  const renderLessonMeta = (template) => {
    if (template.templateType === 'RULE_BY_STAGE') {
      return '按学段动态分课 · 一课 4 课时';
    }
    if (!template.totalMinutes) {
      return '完整知识库 · 不设课时上限';
    }
    return `约 ${Math.round(template.totalMinutes / 60)} 课时`;
  };

  return (
    <Card
      title={<span><ThunderboltOutlined style={{ color: '#1677ff', marginRight: 8 }} />推荐路径模板</span>}
      size="small"
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 24 }}>
          <Spin />
        </div>
      ) : templates.length === 0 ? (
        <Empty description="暂无可用模板" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <Row gutter={[12, 12]}>
          {templates.map(t => (
            <Col xs={24} sm={12} lg={6} key={t.id}>
              <Card
                size="small"
                hoverable
                style={{ borderTop: `3px solid ${COLOR_MAP[t.code] || '#1677ff'}` }}
                styles={{ body: { padding: 16 } }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                  <span style={{ fontSize: 20, color: COLOR_MAP[t.code] || '#1677ff' }}>
                    {ICON_MAP[t.code] || <BookOutlined />}
                  </span>
                  <Text strong style={{ fontSize: 15 }}>{t.name}</Text>
                </div>
                <div style={{ minHeight: 40, marginBottom: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>{t.description}</Text>
                </div>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <Tag color={COLOR_MAP[t.code] || 'blue'} style={{ marginRight: 0 }}>
                    {renderLessonMeta(t)}
                  </Tag>
                </div>
                <Button
                  type="primary"
                  size="small"
                  block
                  style={{ marginTop: 10 }}
                  loading={generatingId === t.id}
                  disabled={!!generatingId}
                  onClick={() => t.templateType === 'RULE_BY_STAGE' ? openTeachingModal(t) : generate(t)}
                >
                  {t.templateType === 'RULE_BY_STAGE' ? '选择学段生成' : '一键生成'}
                </Button>
              </Card>
            </Col>
          ))}
        </Row>
      )}

      {/* 师范生备课：学段选择 + 分课预览 */}
      <Modal
        title="师范生备课 · 选择目标学段"
        open={!!teachingTemplate}
        onCancel={() => setTeachingTemplate(null)}
        onOk={confirmTeaching}
        okText="生成备课路径"
        confirmLoading={generatingId === teachingTemplate?.id}
        width={560}
      >
        <Radio.Group
          value={stage}
          onChange={e => setStage(e.target.value)}
          optionType="button"
          buttonStyle="solid"
          style={{ marginBottom: 16 }}
        >
          {STAGE_OPTIONS.map(s => (
            <Radio.Button key={s.value} value={s.value}>{s.label}</Radio.Button>
          ))}
        </Radio.Group>

        {previewLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : preview ? (
          <div>
            <Text type="secondary">
              共 <Text strong>{preview.nodeCount}</Text> 个知识点 ·
              <Text strong>{preview.lessonCount || 0}</Text> 课（每课 240 分钟）
              {preview.totalMinutes ? ` · 合计约 ${preview.totalMinutes} 分钟` : ''}
            </Text>
            <div style={{ maxHeight: 260, overflow: 'auto', marginTop: 12 }}>
              {(preview.lessons || []).map(lesson => (
                <div key={lesson.lessonIndex} style={{ marginBottom: 10, padding: 10, background: '#fafafa', borderRadius: 6 }}>
                  <Text strong style={{ fontSize: 13 }}>📖 第 {lesson.lessonIndex} 课 · {lesson.title}</Text>
                  <div style={{ marginTop: 4 }}>
                    {lesson.nodes.map(n => (
                      <Tag key={n.knowledgePointId} style={{ marginBottom: 4 }}>
                        {n.knowledgePointName}
                      </Tag>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          </div>
        ) : (
          <Empty description="暂无预览数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Modal>
    </Card>
  );
}
