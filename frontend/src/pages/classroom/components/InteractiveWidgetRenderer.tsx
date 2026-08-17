import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Space, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import type { ActionDTO, WidgetPayload } from '../../../api/types';
import { actionToWidgetMessage, postWidgetMessage } from './WidgetActionBridge';

const { Text } = Typography;

const SUBTYPE_LABEL: Record<string, { text: string; color: string }> = {
  simulation: { text: '🔬 模拟实验', color: 'blue' },
  game: { text: '🎮 知识游戏', color: 'purple' },
  explore: { text: '🔍 点击探索', color: 'green' },
};

/**
 * 交互组件渲染器 — 将 LLM 生成的自包含 HTML 组件在
 * sandbox iframe + srcdoc 中隔离渲染，并通过 postMessage 桥
 * 接收 AI 教师的 widget_* 驱动动作（设计文档 §4.3 / §5.3）。
 */
interface InteractiveWidgetRendererProps {
  widget: WidgetPayload;
  /** 当前需要执行的 widget 驱动动作（到达时向 iframe 发消息） */
  pendingAction?: ActionDTO;
  /** iframe 就绪回调（父层持有引用，供后续动作复用） */
  onFrameReady?: (frame: HTMLIFrameElement | null) => void;
}

const InteractiveWidgetRenderer: React.FC<InteractiveWidgetRendererProps> = ({
  widget,
  pendingAction,
  onFrameReady,
}) => {
  const frameRef = useRef<HTMLIFrameElement>(null);
  const [ready, setReady] = useState(false);
  const [retryKey, setRetryKey] = useState(0);

  const html = widget?.html || '';
  const htmlValid =
    html.includes('</html>') && html.includes('<!DOCTYPE') && !/src=["']https?:/.test(html);

  const handleLoad = useCallback(() => {
    setReady(true);
    onFrameReady?.(frameRef.current);
  }, [onFrameReady]);

  // 组件加载失败重试
  const handleRetry = useCallback(() => {
    setReady(false);
    setRetryKey((k) => k + 1);
  }, []);

  // 到达 widget_* 动作时向 iframe 发送驱动消息
  useEffect(() => {
    if (!pendingAction || !ready) return;
    const msg = actionToWidgetMessage(pendingAction);
    if (!msg) return;
    postWidgetMessage(frameRef.current, msg);
  }, [pendingAction, ready]);

  const sub = SUBTYPE_LABEL[widget?.subtype] || SUBTYPE_LABEL.simulation;

  return (
    <div style={{ width: '100%', maxWidth: 860, margin: '0 auto' }}>
      {/* 组件标题栏 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '10px 16px',
          background: 'linear-gradient(135deg,#f0f5ff,#e6f7ff)',
          borderRadius: '12px 12px 0 0',
          border: '1px solid #d6e4ff',
          borderBottom: 'none',
        }}
      >
        <Space>
          <Tag color={sub.color} style={{ margin: 0 }}>{sub.text}</Tag>
          <Text strong style={{ fontSize: 15 }}>
            {widget?.title || '交互探索'}
          </Text>
        </Space>
        {ready && <Text type="secondary" style={{ fontSize: 12 }}>组件已就绪，可动手操作</Text>}
      </div>

      {/* iframe 组件区 */}
      <div
        style={{
          background: '#fff',
          borderRadius: '0 0 12px 12px',
          border: '1px solid #d6e4ff',
          overflow: 'hidden',
          minHeight: 380,
        }}
      >
        {!htmlValid ? (
          <div style={{ padding: 32, textAlign: 'center' }}>
            <Alert
              type="warning"
              showIcon
              message="交互组件格式异常"
              description="生成的组件 HTML 未通过安全校验，已降级为静态展示。请继续跟随 AI 教师的讲解。"
              style={{ marginBottom: 16 }}
            />
            <Button icon={<ReloadOutlined />} onClick={handleRetry}>重新加载</Button>
          </div>
        ) : (
          <iframe
            key={retryKey}
            ref={frameRef}
            title={widget?.title || 'interactive-widget'}
            sandbox="allow-scripts allow-same-origin"
            srcDoc={html}
            onLoad={handleLoad}
            style={{
              width: '100%',
              minHeight: 380,
              border: 'none',
              background: '#ffffff',
              display: 'block',
            }}
          />
        )}
      </div>

      <Text type="secondary" style={{ display: 'block', textAlign: 'center', fontSize: 12, marginTop: 8 }}>
        组件在沙箱中运行，仅支持本组件内的交互操作
      </Text>
    </div>
  );
};

export default InteractiveWidgetRenderer;
