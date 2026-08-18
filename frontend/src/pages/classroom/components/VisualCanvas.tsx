import React, { useMemo, useRef, useState } from 'react';
import { Button, Space, Tag, Typography, Tooltip } from 'antd';
import {
  LeftOutlined,
  RightOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  ExperimentOutlined,
} from '@ant-design/icons';
import type { ActionDTO, WidgetPayload } from '../../../api/types';
import type { VisualState } from '../usePlayback';
import SlideLayoutRenderer from './SlideLayoutRenderer';
import InteractiveWidgetRenderer from './InteractiveWidgetRenderer';
import WhiteboardRenderer from './WhiteboardRenderer';
import { parseSlides, parseSummaryMap } from './ActionDispatcher';

const { Text } = Typography;

interface VisualCanvasProps {
  visualState: VisualState;
  /** 当前场景的 content（含 slides/widget/summaryMap） */
  sceneContent?: Record<string, any>;
  /** 当前场景标题（kind='none' 时展示占位） */
  sceneTitle?: string;
  /** 手动翻页（不打断播放） */
  onGotoPage: (index: number) => void;
  /** 待执行的 widget 驱动动作（widget_* 到达时传给常驻组件） */
  pendingWidgetAction?: ActionDTO | null;
  /** iframe 引用（父层持有，供后续 widget_* 动作复用） */
  widgetFrameRef?: React.MutableRefObject<HTMLIFrameElement | null>;
  /** 句-页联动：当前讲解句对应的高亮元素 ID（M4） */
  highlightElementIds?: string[];
}

/**
 * 视觉画布 — 常驻渲染视觉轨（v4.0 双轨模型）
 *
 * 承载四种视觉形态：
 *   - slides：PPT 分页常驻（支持手动翻页 / 全屏）
 *   - widget：交互组件常驻（接收 widget_* 驱动）
 *   - summaryMap：知识地图常驻
 *   - whiteboard：白板内容常驻
 *   - none：场景标题占位（等第一个视觉动作）
 */
const VisualCanvas: React.FC<VisualCanvasProps> = ({
  visualState,
  sceneContent,
  sceneTitle,
  onGotoPage,
  pendingWidgetAction,
  widgetFrameRef,
  highlightElementIds,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [fullscreen, setFullscreen] = useState(false);

  const slides = useMemo(() => parseSlides(sceneContent), [sceneContent]);
  const summaryMap = useMemo(() => parseSummaryMap(sceneContent), [sceneContent]);
  const slidesTotal = slides.length;
  const currentPage = Math.min(visualState.pageIndex, Math.max(0, slidesTotal - 1));

  const toggleFullscreen = async () => {
    try {
      if (!document.fullscreenElement && containerRef.current) {
        await containerRef.current.requestFullscreen();
        setFullscreen(true);
      } else {
        await document.exitFullscreen();
        setFullscreen(false);
      }
    } catch {
      /* 浏览器限制时静默失败 */
    }
  };

  // ── 渲染各视觉形态 ──
  const renderCanvas = () => {
    switch (visualState.kind) {
      case 'slides': {
        if (!slides.length) {
          return <EmptyPlaceholder sceneTitle={sceneTitle} />;
        }
        const slide = slides[currentPage] || slides[0];
        return (
          <div style={{ display: 'flex', flexDirection: 'column', height: '100%' }}>
            {/* 页指示器 + 翻页 */}
            <div
              style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '4px 24px 8px',
              }}
            >
              <Text type="secondary" style={{ fontSize: 13 }}>
                📽️ {slide.title || `第 ${currentPage + 1} 页`}
                <span style={{ marginLeft: 8, opacity: 0.7 }}>
                  {currentPage + 1} / {slidesTotal}
                </span>
              </Text>
              <Space size={4}>
                <Tooltip title="上一页（不打断讲解）">
                  <Button
                    size="small"
                    type="text"
                    icon={<LeftOutlined />}
                    disabled={currentPage <= 0}
                    onClick={() => onGotoPage(currentPage - 1)}
                  />
                </Tooltip>
                <Tooltip title="下一页（不打断讲解）">
                  <Button
                    size="small"
                    type="text"
                    icon={<RightOutlined />}
                    disabled={currentPage >= slidesTotal - 1}
                    onClick={() => onGotoPage(currentPage + 1)}
                  />
                </Tooltip>
                <Tooltip title={fullscreen ? '退出全屏' : '全屏'}>
                  <Button
                    size="small"
                    type="text"
                    icon={fullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
                    onClick={toggleFullscreen}
                  />
                </Tooltip>
              </Space>
            </div>
            {/* PPT 画布 */}
            <div
              style={{
                flex: 1,
                overflow: 'auto',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '0 24px 16px',
                background: '#f0f2f5',
              }}
            >
              <div style={{ width: '100%', maxWidth: 920 }}>
                <SlideLayoutRenderer
                  slides={slides}
                  layoutId={slide.layoutId}
                  highlightElementIds={highlightElementIds}
                  pageNumber={currentPage}
                  totalPages={slidesTotal}
                />
              </div>
            </div>
          </div>
        );
      }

      case 'widget': {
        if (!visualState.widget) {
          return <EmptyPlaceholder sceneTitle={sceneTitle} />;
        }
        return (
          <div style={{ padding: '8px 24px 16px', overflow: 'auto', height: '100%' }}>
            <InteractiveWidgetRenderer
              widget={visualState.widget}
              pendingAction={pendingWidgetAction || undefined}
              onFrameReady={(frame) => {
                if (widgetFrameRef) widgetFrameRef.current = frame;
              }}
            />
          </div>
        );
      }

      case 'summaryMap': {
        if (!summaryMap) {
          return <EmptyPlaceholder sceneTitle={sceneTitle} />;
        }
        return <SummaryMapView root={summaryMap.root} branches={summaryMap.branches} />;
      }

      case 'whiteboard': {
        return (
          <div style={{ padding: '8px 24px 16px', overflow: 'auto', height: '100%' }}>
            <WhiteboardRenderer
              action={{
                type: 'wb_draw_text',
                content: visualState.whiteboardContent || '',
                position: visualState.whiteboardPosition,
              }}
            />
          </div>
        );
      }

      case 'none':
      default:
        return <EmptyPlaceholder sceneTitle={sceneTitle} />;
    }
  };

  return (
    <div
      ref={containerRef}
      style={{
        flex: 1,
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
        background: '#fff',
        borderRadius: 12,
        overflow: 'hidden',
        border: '1px solid #eef0f4',
      }}
    >
      {renderCanvas()}
    </div>
  );
};

/** 空画布占位（等视觉动作 / 无视觉内容场景） */
const EmptyPlaceholder: React.FC<{ sceneTitle?: string }> = ({ sceneTitle }) => (
  <div
    style={{
      flex: 1,
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      minHeight: 240,
      background: 'linear-gradient(135deg,#fafafa,#f0f5ff)',
    }}
  >
    <ExperimentOutlined style={{ fontSize: 36, color: '#bfc9d9', marginBottom: 12 }} />
    <Text strong style={{ fontSize: 16, color: '#8c9cb0' }}>
      {sceneTitle || '本场景以讲解为主'}
    </Text>
    <Text type="secondary" style={{ fontSize: 13, marginTop: 4 }}>
      视觉内容将随教学进度呈现
    </Text>
  </div>
);

/** 知识地图（review 场景 summaryMap 常驻渲染） */
const SummaryMapView: React.FC<{
  root: string;
  branches: Array<{ label: string; children?: string[]; color?: string }>;
}> = ({ root, branches }) => {
  return (
    <div style={{ padding: '16px 24px', overflow: 'auto', height: '100%' }}>
      <div style={{ textAlign: 'center', marginBottom: 16 }}>
        <Tag color="purple" style={{ fontSize: 16, padding: '6px 20px', borderRadius: 20 }}>
          🧠 {root}
        </Tag>
      </div>
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: `repeat(${Math.min(branches.length, 4)}, 1fr)`,
          gap: 14,
        }}
      >
        {branches.map((b, i) => (
          <div
            key={i}
            style={{
              background: '#fafafa',
              borderRadius: 12,
              border: `1px solid ${b.color || '#d6e4ff'}`,
              borderTop: `4px solid ${b.color || '#1677ff'}`,
              padding: '12px 14px',
            }}
          >
            <Text strong style={{ fontSize: 14, color: '#1a1a2e', display: 'block', marginBottom: 8 }}>
              {b.label}
            </Text>
            {(b.children || []).map((c, j) => (
              <div
                key={j}
                style={{
                  fontSize: 13,
                  color: '#555',
                  padding: '3px 0 3px 14px',
                  borderLeft: `2px solid ${(b.color || '#1677ff')}44`,
                  marginBottom: 2,
                }}
              >
                {c}
              </div>
            ))}
          </div>
        ))}
      </div>
    </div>
  );
};

export default VisualCanvas;

