import { useState, useEffect, useCallback, useMemo } from 'react';
import { Card, Button, Modal, Input, Tree, Tag, Empty, message, Spin, Typography, Space, List } from 'antd';
import { EditOutlined, ArrowUpOutlined, ArrowDownOutlined, DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { pathApi } from '../../../api/pathApi';
import { knowledgeApi } from '../../../api/knowledgeApi';

const { Text } = Typography;

/** 将后端知识树转换为 antd Tree 数据 */
function toTreeData(nodes) {
  return (nodes || []).map(n => ({
    title: n.name,
    key: n.id,
    children: n.children?.length ? toTreeData(n.children) : undefined,
  }));
}

/** 收集树中所有 {id → name} 映射 */
function collectNames(nodes, map = {}) {
  (nodes || []).forEach(n => {
    map[n.id] = n.name;
    if (n.children?.length) collectNames(n.children, map);
  });
  return map;
}

/**
 * 手动勾选创建路径（CUSTOM）— 知识树勾选编辑器。
 */
export default function CustomPathSection({ courseId, studentId, onGenerated }) {
  const [open, setOpen] = useState(false);
  const [tree, setTree] = useState([]);
  const [name, setName] = useState('');
  const [checkedKeys, setCheckedKeys] = useState([]);
  const [selectedOrder, setSelectedOrder] = useState([]);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const nameMap = useMemo(() => collectNames(tree), [tree]);

  useEffect(() => {
    if (!open || !courseId) return;
    setLoading(true);
    setTree([]);
    setCheckedKeys([]);
    setSelectedOrder([]);
    setName('');
    knowledgeApi
      .getKnowledgePointTree(courseId)
      .then(data => setTree(data || []))
      .catch(() => message.error('加载知识树失败'))
      .finally(() => setLoading(false));
  }, [open, courseId]);

  const onCheck = (checked) => {
    const arr = Array.isArray(checked) ? checked : checked.checked || [];
    setCheckedKeys(arr);
    setSelectedOrder(prev => {
      const kept = prev.filter(k => arr.includes(k));
      const added = arr.filter(k => !prev.includes(k));
      return [...kept, ...added];
    });
  };

  const move = (index, delta) => {
    setSelectedOrder(prev => {
      const next = [...prev];
      const target = index + delta;
      if (target < 0 || target >= next.length) return prev;
      [next[index], next[target]] = [next[target], next[index]];
      return next;
    });
  };

  const remove = (index) => {
    const id = selectedOrder[index];
    setSelectedOrder(prev => prev.filter(k => k !== id));
    setCheckedKeys(prev => prev.filter(k => k !== id));
  };

  const save = async () => {
    if (!name.trim()) {
      message.warning('请为路径命名');
      return;
    }
    if (selectedOrder.length === 0) {
      message.warning('请至少勾选一个知识点');
      return;
    }
    setSaving(true);
    try {
      const path = await pathApi.createCustomPath({
        studentId,
        courseId,
        name: name.trim(),
        nodeIds: selectedOrder,
      });
      message.success(`自定义路径「${path.name}」已创建，共 ${path.totalNodes} 个节点`);
      setOpen(false);
      onGenerated?.(path);
    } catch (err) {
      message.error(err.message || '创建失败');
    }
    setSaving(false);
  };

  return (
    <Card size="small">
      <Button
        type="dashed"
        icon={<EditOutlined />}
        block
        onClick={() => setOpen(true)}
      >
        新建自定义路径（勾选知识点）
      </Button>
      <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 8 }}>
        从课程知识树中勾选知识点，自由排序，创建完全属于自己的学习路径。
      </Text>

      <Modal
        title="新建自定义路径"
        open={open}
        onCancel={() => setOpen(false)}
        onOk={save}
        okText="创建路径"
        confirmLoading={saving}
        width={760}
        okButtonProps={{ disabled: selectedOrder.length === 0 }}
      >
        <Input
          placeholder="路径名称，如：宪法专题进阶"
          value={name}
          onChange={e => setName(e.target.value)}
          maxLength={50}
          style={{ marginBottom: 12 }}
        />
        <div style={{ display: 'flex', gap: 16, height: 420 }}>
          {/* 知识树 */}
          <div style={{ flex: 1, border: '1px solid #f0f0f0', borderRadius: 8, padding: 8, overflow: 'auto' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>课程知识树（勾选知识点）</Text>
            {loading ? (
              <div style={{ textAlign: 'center', marginTop: 60 }}><Spin /></div>
            ) : tree.length === 0 ? (
              <Empty description="暂无知识点" image={Empty.PRESENTED_IMAGE_SIMPLE} />
            ) : (
              <Tree
                checkable
                defaultExpandAll
                treeData={toTreeData(tree)}
                checkedKeys={checkedKeys}
                onCheck={onCheck}
                selectable={false}
              />
            )}
          </div>
          {/* 已选列表 */}
          <div style={{ flex: 1, border: '1px solid #f0f0f0', borderRadius: 8, padding: 8, display: 'flex', flexDirection: 'column' }}>
            <Text type="secondary" style={{ fontSize: 12 }}>
              已选知识点（{selectedOrder.length}）· 拖动按钮调整顺序
            </Text>
            <div style={{ flex: 1, overflow: 'auto', marginTop: 8 }}>
              {selectedOrder.length === 0 ? (
                <Empty description="左侧勾选" image={Empty.PRESENTED_IMAGE_SIMPLE} />
              ) : (
                <List
                  size="small"
                  dataSource={selectedOrder}
                  renderItem={(id, idx) => (
                    <List.Item
                      style={{ padding: '4px 0' }}
                      actions={[
                        <Button key="up" size="small" type="text" icon={<ArrowUpOutlined />} disabled={idx === 0} onClick={() => move(idx, -1)} />,
                        <Button key="down" size="small" type="text" icon={<ArrowDownOutlined />} disabled={idx === selectedOrder.length - 1} onClick={() => move(idx, 1)} />,
                        <Button key="del" size="small" type="text" danger icon={<DeleteOutlined />} onClick={() => remove(idx)} />,
                      ]}
                    >
                      <Text style={{ fontSize: 13 }}>
                        {idx + 1}. {nameMap[id] || '未知知识点'}
                      </Text>
                    </List.Item>
                  )}
                />
              )}
            </div>
          </div>
        </div>
      </Modal>
    </Card>
  );
}
