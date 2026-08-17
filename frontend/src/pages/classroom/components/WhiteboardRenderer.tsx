import React, { useMemo, lazy, Suspense } from 'react';
import { Card, Spin } from 'antd';
import { motion, AnimatePresence } from 'motion/react';
import type { ActionDTO } from '../../api/types';
import { fadeIn, writeIn } from '../animations';

/**
 * 判断内容是否包含 Markdown 语法
 */
function isMarkdownContent(text: string): boolean {
  return /(?:\*\*|__|#{1,6}\s|`{3}|\$\$|!\[.*?\]\(.*?\)|\* |\- |\d+\. )/.test(text);
}

/**
 * 白板渲染器（wb_draw_text / wb_draw_diagram）
 * 在白板区域展示公式、要点或图表
 * 支持三种渲染模式：
 * - 纯文本（旧式）：简单居中显示
 * - Markdown：使用 react-markdown 渲染，支持代码高亮、LaTeX 公式、图片
 * - Diagram：居中显示文本内容
 *
 * 附加入场动画：fadeIn + writeIn（书写效果）+ 扫描光束
 */

// 懒加载 Markdown 组件以减小初始包体积
const MarkdownContent = lazy(() => import('./MarkdownContent'));

const WhiteboardRenderer: React.FC<{ action: ActionDTO }> = ({ action }) => {
  const isDiagram = action.type === 'wb_draw_diagram';
  // 结构化 diagram（nodes+edges）优先渲染为 SVG 流程图；否则回退文本
  const diagram = isDiagram
    ? ((action.params?.diagram as DiagramData | undefined) ?? null)
    : null;
  const displayContent = action.content || action.wbContent || action.text || '';
  const useMarkdown = !isDiagram && isMarkdownContent(displayContent);

  const contentStyle: React.CSSProperties = useMemo(
    () => ({
      fontSize: isDiagram ? 18 : 22,
      fontWeight: isDiagram ? 400 : 600,
      color: '#1a1a2e',
      fontFamily: isDiagram
        ? '-apple-system, sans-serif'
        : '"Times New Roman", "CMU Serif", serif',
      whiteSpace: 'pre-wrap',
      lineHeight: 1.6,
      padding: '12px 0',
      textAlign: useMarkdown ? 'left' : 'center',
      width: '100%',
    }),
    [isDiagram, useMarkdown],
  );

  return (
    <AnimatePresence mode="wait">
      <motion.div
        key={displayContent.slice(0, 30) || 'wb'}
        variants={fadeIn}
        initial="hidden"
        animate="visible"
        exit="exit"
        style={{ padding: '16px', maxWidth: 800, margin: '0 auto' }}
      >
        <Card
          style={{
            background: '#fafafa',
            borderRadius: 12,
            minHeight: 200,
            border: '2px dashed #d9d9d9',
            position: 'relative',
            overflow: 'hidden',
          }}
          styles={{
            body: {
              display: 'flex',
              alignItems: useMarkdown ? 'stretch' : 'center',
              justifyContent: 'center',
              minHeight: 200,
              padding: useMarkdown ? '20px 24px' : undefined,
            },
          }}
        >
          <motion.div
            variants={writeIn}
            initial="hidden"
            animate="visible"
            style={contentStyle}
          >
            {diagram && diagram.nodes?.length ? (
              <DiagramSvg diagram={diagram} />
            ) : isDiagram ? (
              <div style={{ fontSize: 18, color: '#595959', whiteSpace: 'pre-wrap', textAlign: 'center' }}>
                {displayContent}
              </div>
            ) : useMarkdown ? (
              <Suspense
                fallback={
                  <div style={{ textAlign: 'center', padding: 24 }}>
                    <Spin size="small" />
                  </div>
                }
              >
                <MarkdownContent content={displayContent} />
              </Suspense>
            ) : (
              <div style={{ textAlign: 'center' }}>{displayContent}</div>
            )}
          </motion.div>

          {/* 白板书写光束扫描动画 */}
          <motion.div
            style={{
              position: 'absolute',
              inset: 0,
              background:
                'linear-gradient(90deg, transparent, rgba(22,119,255,0.06), transparent)',
              pointerEvents: 'none',
            }}
            initial={{ left: '-100%' }}
            animate={{ left: '200%' }}
            transition={{
              duration: 1.2,
              ease: 'easeInOut',
              repeat: Infinity,
              repeatDelay: 3,
            }}
          />
        </Card>
      </motion.div>
    </AnimatePresence>
  );
};

// ───────────────────────── 结构化流程图（Diagram） ─────────────────────────

interface DiagramNode {
  id: string;
  label?: string;
  x?: number;
  y?: number;
  w?: number;
  h?: number;
  color?: string;
  shape?: 'rect' | 'round' | 'circle';
}

interface DiagramEdge {
  id?: string;
  from?: [number, number];
  to?: [number, number];
  source?: string;
  target?: string;
  label?: string;
  color?: string;
}

interface DiagramData {
  nodes: DiagramNode[];
  edges?: DiagramEdge[];
  width?: number;
  height?: number;
}

/** 将结构化 diagram JSON 渲染为 SVG 流程图 */
const DiagramSvg: React.FC<{ diagram: DiagramData }> = ({ diagram }) => {
  const W = diagram.width || 760;
  const H = diagram.height || 360;
  const nodes = diagram.nodes;
  const byId = new Map(nodes.map((n) => [n.id, n]));

  const nodeBox = (n: DiagramNode) => {
    const w = n.w || 140;
    const h = n.h || 56;
    const x = n.x ?? 0;
    const y = n.y ?? 0;
    return { x, y, w, h };
  };

  const edges = (diagram.edges || []).map((e) => {
    if (e.from && e.to) return e;
    const s = e.source ? byId.get(e.source) : null;
    const t = e.target ? byId.get(e.target) : null;
    if (s && t) {
      const sb = nodeBox(s);
      const tb = nodeBox(t);
      return {
        ...e,
        from: [sb.x + sb.w, sb.y + sb.h / 2] as [number, number],
        to: [tb.x, tb.y + tb.h / 2] as [number, number],
      };
    }
    return e;
  });

  return (
    <svg
      width="100%"
      viewBox={`0 0 ${W} ${H}`}
      style={{ maxHeight: 360, maxWidth: 760 }}
    >
      {edges.map((e, i) => {
        if (!e.from || !e.to) return null;
        const [x1, y1] = e.from;
        const [x2, y2] = e.to;
        return (
          <g key={e.id || `e${i}`}>
            <line
              x1={x1} y1={y1} x2={x2} y2={y2}
              stroke={e.color || '#1677ff'}
              strokeWidth={2}
              markerEnd="url(#diagram-arrow)"
            />
            {e.label && (
              <text
                x={(x1 + x2) / 2} y={(y1 + y2) / 2 - 6}
                textAnchor="middle" fontSize={12} fill="#595959"
              >
                {e.label}
              </text>
            )}
          </g>
        );
      })}
      <defs>
        <marker id="diagram-arrow" markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
          <polygon points="0 0, 10 3.5, 0 7" fill="#1677ff" />
        </marker>
      </defs>
      {nodes.map((n) => {
        const { x, y, w, h } = nodeBox(n);
        const isCircle = n.shape === 'circle';
        return (
          <g key={n.id}>
            <rect
              x={x} y={y} width={isCircle ? h : w} height={h}
              rx={n.shape === 'round' || isCircle ? (isCircle ? h / 2 : 12) : 4}
              fill={n.color || '#f0f5ff'}
              stroke="#1677ff"
              strokeWidth={1.5}
            />
            <text
              x={x + (isCircle ? h : w) / 2}
              y={y + h / 2 + 5}
              textAnchor="middle"
              fontSize={14}
              fontWeight={600}
              fill="#1a1a2e"
            >
              {n.label || n.id}
            </text>
          </g>
        );
      })}
    </svg>
  );
};

export default WhiteboardRenderer;
