import { useState, useEffect } from 'react';
import { Card, Button, Tree, Input, Radio, message, Spin, Empty, Typography, Space, Tag } from 'antd';
import { SendOutlined } from '@ant-design/icons';
import { collabApi, storyApi, ROLE_CONFIG } from '../../api/collabApi';
import { knowledgeApi } from '../../api/knowledgeApi';

const { Text } = Typography;

/** 树 → antd Tree（仅叶子可选） */
function toTreeData(nodes, selectable = false) {
  return (nodes || []).map(n => ({
    title: n.name,
    key: n.id,
    disabled: selectable && n.type !== 'LEAF',
    children: n.children?.length ? toTreeData(n.children, selectable) : undefined,
  }));
}

function collectLeafIds(nodes, acc = []) {
  (nodes || []).forEach(n => {
    if (n.type === 'LEAF') acc.push(n.id);
    if (n.children?.length) collectLeafIds(n.children, acc);
  });
  return acc;
}

/**
 * 学生端 · 角色任务提交表单（设计文档 §5）
 * 按角色渲染：小学选故事 / 初中角色形象 / 高中台词 / 大学法律映射
 */
export default function StudentTaskForm({ projectId, task, project, onSubmitted }) {
  const [loading, setLoading] = useState(false);
  const [stories, setStories] = useState([]);
  const [storyId, setStoryId] = useState(null);
  const [reason, setReason] = useState('');
  const [characters, setCharacters] = useState('');
  const [script, setScript] = useState('');
  const [checkedKeys, setCheckedKeys] = useState([]);
  const [mapping, setMapping] = useState('');
  const [tree, setTree] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  const roleType = task?.roleType;

  // 已提交内容回填
  useEffect(() => {
    if (!task?.content) return;
    try {
      const c = JSON.parse(task.content);
      if (c.storyId) { setStoryId(c.storyId); setReason(c.reason || ''); }
      if (c.characters) setCharacters(c.characters);
      if (c.script) setScript(c.script);
      if (c.knowledgePointIds) setCheckedKeys(c.knowledgePointIds);
      if (c.mapping) setMapping(c.mapping);
    } catch (e) { /* ignore */ }
  }, [task?.content]);

  // 小学：加载故事库
  useEffect(() => {
    if (roleType === 'STORY_PICKER') {
      setLoading(true);
      storyApi.list().then(res => setStories(res || [])).catch(() => setStories([])).finally(() => setLoading(false));
    }
  }, [roleType]);

  // 大学：加载课程知识树
  useEffect(() => {
    if (roleType === 'LEGAL_MAPPER' && project?.courseId) {
      setLoading(true);
      knowledgeApi.getKnowledgePointTree(project.courseId)
        .then(data => setTree(data || []))
        .catch(() => setTree([]))
        .finally(() => setLoading(false));
    }
  }, [roleType, project?.courseId]);

  const handleSubmit = async () => {
    let content;
    if (roleType === 'STORY_PICKER') {
      if (!storyId) { message.warning('请先选定一个故事'); return; }
      content = { storyId, reason };
    } else if (roleType === 'CHARACTER_DESIGNER') {
      if (!characters.trim()) { message.warning('请填写角色形象设计'); return; }
      content = { characters };
    } else if (roleType === 'SCRIPT_WRITER') {
      if (!script.trim()) { message.warning('请填写台词脚本'); return; }
      content = { script };
    } else if (roleType === 'LEGAL_MAPPER') {
      if (checkedKeys.length === 0) { message.warning('请至少选择一个法律知识点'); return; }
      if (!mapping.trim()) { message.warning('请说明知识点与故事的映射关系'); return; }
      content = { knowledgePointIds: checkedKeys, mapping };
    }
    setSubmitting(true);
    try {
      await collabApi.submit(projectId, task.id, content);
      message.success('任务已提交，等待教师复核');
      onSubmitted();
    } catch (err) {
      message.error(err?.message || '提交失败');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <Spin />;

  const renderRoleForm = () => {
    if (roleType === 'STORY_PICKER') {
      return (
        <div>
          <Text type="secondary">请从中华传统故事库中选定一个故事（用于课堂主线）：</Text>
          <div style={{ marginTop: 8, maxHeight: 220, overflow: 'auto', display: 'flex', flexDirection: 'column', gap: 8 }}>
            {stories.length === 0 ? <Empty description="故事库暂无故事" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              : stories.map(s => (
                <Card key={s.id} size="small" hoverable
                  style={{ border: storyId === s.id ? '2px solid #1677ff' : '1px solid #f0f0f0' }}
                  onClick={() => setStoryId(s.id)}>
                  <Space direction="vertical" size={2} style={{ width: '100%' }}>
                    <Text strong>
                      <Radio checked={storyId === s.id} style={{ marginRight: 8 }} />📖 {s.title}
                      {s.dynasty && <Tag style={{ marginLeft: 8 }}>{s.dynasty}</Tag>}
                    </Text>
                    <Text type="secondary" style={{ fontSize: 12, display: 'block' }}>
                      {s.content?.substring(0, 90)}{s.content?.length > 90 ? '...' : ''}
                    </Text>
                  </Space>
                </Card>
              ))}
          </div>
          <Input.TextArea rows={2} style={{ marginTop: 8 }} placeholder="选择理由（可选）"
            value={reason} onChange={e => setReason(e.target.value)} maxLength={200} />
        </div>
      );
    }
    if (roleType === 'CHARACTER_DESIGNER') {
      return (
        <div>
          <Text type="secondary">请设计故事中的角色形象（姓名 / 性格 / 外观 / 背景）：</Text>
          <Input.TextArea rows={5} style={{ marginTop: 8 }} placeholder={'例：\n角色1 - 孔融\n· 性格：谦让、懂事、机智\n· 外观：四岁孩童，圆脸，穿素色汉服\n· 背景：孔家幼子，家中排行最小'} 
            value={characters} onChange={e => setCharacters(e.target.value)} maxLength={1000} />
        </div>
      );
    }
    if (roleType === 'SCRIPT_WRITER') {
      return (
        <div>
          <Text type="secondary">请创作关键场景的角色台词（对白 / 旁白）：</Text>
          <Input.TextArea rows={5} style={{ marginTop: 8 }} placeholder={'例：\n【开场·旁白】东汉年间，孔府庭院，梨香满园……\n孔融（踮脚挑梨）："我要这个最小的。"\n父亲（疑惑）："为何不要大梨？"\n孔融（认真）："我年纪最小，理当让着哥哥们。"'} 
            value={script} onChange={e => setScript(e.target.value)} maxLength={1000} />
        </div>
      );
    }
    if (roleType === 'LEGAL_MAPPER') {
      return (
        <div>
          <Text type="secondary">请从课程知识库中勾选与故事相关的法律知识点，并说明映射关系：</Text>
          <div style={{ border: '1px solid #f0f0f0', borderRadius: 8, padding: 8, marginTop: 8, maxHeight: 200, overflow: 'auto' }}>
            {tree.length === 0 ? <Empty description="加载知识树失败" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              : <Tree checkable defaultExpandAll treeData={toTreeData(tree, true)}
                  checkedKeys={checkedKeys} onCheck={k => setCheckedKeys(Array.isArray(k) ? k : k.checked || [])} />}
          </div>
          <Input.TextArea rows={3} style={{ marginTop: 8 }} placeholder="映射说明：如「孔融让梨 → 公民权利与义务：谦让是权利的边界意识……」"
            value={mapping} onChange={e => setMapping(e.target.value)} maxLength={500} />
        </div>
      );
    }
    return null;
  };

  const done = task?.status === 'COMPLETED' || task?.status === 'REVIEWED';

  return (
    <div>
      {renderRoleForm()}
      <div style={{ marginTop: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <Text type="secondary" style={{ fontSize: 12 }}>
          {done ? '✅ 已提交' : '提交后等待教师复核'}
        </Text>
        <Button type="primary" icon={<SendOutlined />} loading={submitting} onClick={handleSubmit}>
          {done ? '更新提交' : '提交任务'}
        </Button>
      </div>
    </div>
  );
}
