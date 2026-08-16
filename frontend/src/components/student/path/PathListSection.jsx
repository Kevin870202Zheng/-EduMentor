import { useState, useCallback, useEffect } from 'react';
import {
  Card, List, Tag, Button, Empty, Spin, Drawer, Steps, Typography,
  Space, Progress, message, Radio, Select, Modal, Popconfirm,
} from 'antd';
import {
  CheckCircleOutlined, ClockCircleOutlined, PlayCircleOutlined,
  PauseCircleOutlined, RocketOutlined, ArrowUpOutlined, ArrowDownOutlined,
  DeleteOutlined, PlusOutlined, NodeIndexOutlined,
} from '@ant-design/icons';
import { pathApi } from '../../../api/pathApi';
import { knowledgeApi } from '../../../api/knowledgeApi';

const { Text, Title } = Typography;

const SOURCE_META = {
  TEMPLATE: { label: '模板', color: 'blue' },
  AI: { label: 'AI', color: 'purple' },
  CUSTOM: { label: '自定义', color: 'green' },
  AUTO: { label: '系统', color: 'default' },
};

const STATUS_META = {
  DRAFT: { label: '草稿', color: 'default' },
  ACTIVE: { label: '进行中', color: 'green' },
  COMPLETED: { label: '已完成', color: 'blue' },
  PAUSED: { label: '已暂停', color: 'orange' },
};

const STRATEGY_OPTIONS = [
  { value: 'REORDER', label: '均衡推荐' },
  { value: 'SHORTEN', label: '最短路径' },
  { value: 'FOCUS_WEAK', label: '薄弱优先' },
  { value: 'EXPAND', label: '拓展补充' },
];

const NODE_STATUS_ICON = {
  COMPLETED: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
  IN_PROGRESS: <RocketOutlined style={{ color: '#1677ff' }} />,
  SKIPPED: <ClockCircleOutlined style={{ color: '#faad14' }} />,
};

/**
 * 我的路径 — 路径列表 + 详情（节点操作 / 策略切换 / 状态流转）。
 */
export default function PathListSection({ paths, loading, studentId, onRefresh }) {
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [strategy, setStrategy] = useState('REORDER');
  const [addOpen, setAddOpen] = useState(false);
  const [addKpId, setAddKpId] = useState(null);
  const [kpOptions, setKpOptions] = useState([]);
  const [acting, setActing] = useState(false);

  const refreshDetail = useCallback(async (pathId) => {
    const d = await pathApi.getPath(pathId);
    setDetail(d);
    return d;
  }, []);

  const openDetail = async (path) => {
    setDetailLoading(true);
    try {
      const d = await refreshDetail(path.id);
      setStrategy(d.adaptStrategy || 'REORDER');
    } catch (err) {
      message.error(err.message || '加载详情失败');
    }
    setDetailLoading(false);
  };

  const act = async (fn, successMsg) => {
    if (!detail) return;
    setActing(true);
    try {
      await fn();
      message.success(successMsg);
      const d = await refreshDetail(detail.id);
      setStrategy(d.adaptStrategy || strategy);
      onRefresh?.();
    } catch (err) {
      message.error(err.message || '操作失败');
    }
    setActing(false);
  };

  const handleNodeStatus = (node, status) =>
    act(
      () => pathApi.updateNodeProgress({ pathId: detail.id, nodeId: node.id, status }),
      status === 'COMPLETED' ? '节点已标记完成' : '节点已跳过',
    );

  const moveNode = (index, delta) => {
    if (!detail?.nodes) return;
    const nodes = [...detail.nodes].sort((a, b) => a.orderIndex - b.orderIndex);
    const target = index + delta;
    if (target < 0 || target >= nodes.length) return;
    [nodes[index], nodes[target]] = [nodes[target], nodes[index]];
    act(
      () => pathApi.reorderNodes(detail.id, { nodeIds: nodes.map(n => n.id) }),
      '节点顺序已调整',
    );
  };

  const removeNode = (node) =>
    act(() => pathApi.removeNode(detail.id, node.id), '节点已移除');

  const loadKpOptions = async () => {
    if (!detail?.courseId) return;
    try {
      const list = await knowledgeApi.listKnowledgePoints(detail.courseId);
      setKpOptions((list || []).map(k => ({ label: k.name, value: k.id })));
    } catch {
      setKpOptions([]);
    }
  };

  const addNode = async () => {
    if (!addKpId) {
      message.warning('请选择知识点');
      return;
    }
    setActing(true);
    try {
      await pathApi.addNode(detail.id, { knowledgePointId: addKpId });
      message.success('节点已追加');
      setAddOpen(false);
      setAddKpId(null);
      await refreshDetail(detail.id);
      onRefresh?.();
    } catch (err) {
      message.error(err.message || '追加失败');
    }
    setActing(false);
  };

  const sortedNodes = [...(detail?.nodes || [])].sort((a, b) => a.orderIndex - b.orderIndex);
  const totalMinutes = sortedNodes.reduce((s, n) => s + (n.estimatedMinutes || 25), 0);

  return (
    <Card
      title={<span><NodeIndexOutlined style={{ color: '#1677ff', marginRight: 8 }} />我的路径</span>}
      size="small"
      extra={
        <Button size="small" onClick={onRefresh} icon={<span>🔄</span>}>
          刷新
        </Button>
      }
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
      ) : paths.length === 0 ? (
        <Empty description="还没有学习路径，从上方模板一键生成，或与 AI 共创吧" />
      ) : (
        <List
          dataSource={paths}
          renderItem={p => {
            const sm = SOURCE_META[p.source || 'AUTO'] || SOURCE_META.AUTO;
            const st = STATUS_META[p.status] || STATUS_META.DRAFT;
            return (
              <List.Item
                style={{ cursor: 'pointer' }}
                onClick={() => openDetail(p)}
                actions={[
                  <Button key="detail" type="link" size="small" onClick={e => { e.stopPropagation(); openDetail(p); }}>
                    查看详情
                  </Button>,
                ]}
              >
                <List.Item.Meta
                  title={
                    <Space>
                      <Text strong>{p.name}</Text>
                      <Tag color={sm.color}>{sm.label}</Tag>
                      <Tag color={st.color}>{st.label}</Tag>
                    </Space>
                  }
                  description={
                    <Space size="large">
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        共 {p.totalNodes || p.nodes?.length || 0} 个节点 · 完成 {p.completedNodes || 0}
                      </Text>
                      <Progress
                        percent={p.progress || 0}
                        size="small"
                        style={{ width: 140 }}
                        strokeColor={p.progress >= 100 ? '#52c41a' : '#1677ff'}
                      />
                    </Space>
                  }
                />
              </List.Item>
            );
          }}
        />
      )}

      {/* 路径详情 */}
      <Drawer
        title={detail ? (
          <Space>
            <Text strong>{detail.name}</Text>
            {detail.source && (
              <Tag color={SOURCE_META[detail.source]?.color}>
                {SOURCE_META[detail.source]?.label}
              </Tag>
            )}
            <Tag color={STATUS_META[detail.status]?.color}>{STATUS_META[detail.status]?.label}</Tag>
          </Space>
        ) : '路径详情'}
        open={!!detail}
        onClose={() => setDetail(null)}
        width={560}
      >
        {detailLoading ? (
          <div style={{ textAlign: 'center', padding: 60 }}><Spin /></div>
        ) : detail ? (
          <div>
            {/* 概要 */}
            <div style={{ marginBottom: 16 }}>
              <Progress percent={detail.progress || 0} />
              <Text type="secondary" style={{ fontSize: 13 }}>
                共 {detail.totalNodes || 0} 个知识点 · 预计总时长 {Math.round(totalMinutes / 60)} 小时
                {detail.dailyMinutes ? ` · 每日建议 ${detail.dailyMinutes} 分钟` : ''}
              </Text>
              {detail.description && (
                <div style={{ marginTop: 4 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>{detail.description}</Text>
                </div>
              )}
            </div>

            {/* 状态流转 */}
            <Space style={{ marginBottom: 16 }} wrap>
              {(detail.status === 'DRAFT' || detail.status === 'PAUSED') && (
                <Button
                  type="primary" size="small" icon={<PlayCircleOutlined />}
                  onClick={() => act(() => pathApi.activatePath(detail.id), '路径已激活')}
                  loading={acting}
                >
                  激活路径
                </Button>
              )}
              {detail.status === 'ACTIVE' && (
                <Button
                  size="small" icon={<PauseCircleOutlined />}
                  onClick={() => act(() => pathApi.pausePath(detail.id), '路径已暂停')}
                  loading={acting}
                >
                  暂停
                </Button>
              )}
              {detail.status !== 'COMPLETED' && (
                <Popconfirm title="将未完成节点全部跳过并完成路径？" onConfirm={() => act(() => pathApi.completePath(detail.id), '路径已完成')}>
                  <Button size="small" danger loading={acting}>完成路径</Button>
                </Popconfirm>
              )}
            </Space>

            {/* 策略切换 */}
            <div style={{ marginBottom: 16 }}>
              <Text type="secondary" style={{ fontSize: 12, marginRight: 8 }}>策略</Text>
              <Radio.Group
                size="small"
                value={strategy}
                optionType="button"
                buttonStyle="solid"
                onChange={e => {
                  const s = e.target.value;
                  act(() => pathApi.adaptPath({ pathId: detail.id, adaptStrategy: s }), '策略已切换');
                }}
                options={STRATEGY_OPTIONS}
                disabled={acting}
              />
            </div>

            {/* 追加节点 */}
            <Space style={{ marginBottom: 16 }}>
              <Button size="small" icon={<PlusOutlined />} onClick={() => { setAddOpen(true); loadKpOptions(); }}>
                追加知识点
              </Button>
              <Button size="small" icon={<span>🔄</span>} onClick={() => refreshDetail(detail.id)}>
                刷新详情
              </Button>
            </Space>

            {/* 节点 Steps */}
            <Steps
              direction="vertical"
              size="small"
              current={Math.max(0, sortedNodes.findIndex(n => n.status === 'IN_PROGRESS'))}
              items={sortedNodes.map((node, idx) => ({
                title: (
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 8 }}>
                    <Text strong style={{ fontSize: 13 }}>{node.knowledgePointName}</Text>
                    <Space size={2}>
                      {node.status !== 'COMPLETED' && node.status !== 'SKIPPED' && (
                        <Button size="small" type="text" icon={<CheckCircleOutlined style={{ color: '#52c41a' }} />}
                          onClick={() => handleNodeStatus(node, 'COMPLETED')} />
                      )}
                      {node.status === 'PENDING' && (
                        <Button size="small" type="text" icon={<ClockCircleOutlined style={{ color: '#faad14' }} />}
                          onClick={() => handleNodeStatus(node, 'SKIPPED')} />
                      )}
                      <Button size="small" type="text" icon={<ArrowUpOutlined />} disabled={idx === 0}
                        onClick={() => moveNode(idx, -1)} />
                      <Button size="small" type="text" icon={<ArrowDownOutlined />} disabled={idx === sortedNodes.length - 1}
                        onClick={() => moveNode(idx, 1)} />
                      <Popconfirm title="移除该节点？" onConfirm={() => removeNode(node)}>
                        <Button size="small" type="text" danger icon={<DeleteOutlined />} />
                      </Popconfirm>
                    </Space>
                  </div>
                ),
                description: (
                  <div>
                    <Tag style={{ fontSize: 12 }}>{node.estimatedMinutes || 25} 分钟</Tag>
                    {node.aiReason && (
                      <Text type="secondary" style={{ fontSize: 12 }}>🤖 {node.aiReason}</Text>
                    )}
                  </div>
                ),
                status: node.status === 'COMPLETED' ? 'finish'
                  : node.status === 'IN_PROGRESS' ? 'process'
                  : node.status === 'SKIPPED' ? 'error' : 'wait',
                icon: NODE_STATUS_ICON[node.status],
              }))}
            />
            {sortedNodes.length === 0 && <Empty description="路径暂无节点" />}
          </div>
        ) : null}
      </Drawer>

      {/* 追加知识点选择 */}
      <Modal
        title="追加知识点"
        open={addOpen}
        onCancel={() => setAddOpen(false)}
        onOk={addNode}
        okText="追加"
        confirmLoading={acting}
        width={440}
      >
        <Select
          style={{ width: '100%' }}
          placeholder="选择要追加的知识点"
          showSearch
          optionFilterProp="label"
          value={addKpId}
          onChange={setAddKpId}
          options={kpOptions}
        />
      </Modal>
    </Card>
  );
}
