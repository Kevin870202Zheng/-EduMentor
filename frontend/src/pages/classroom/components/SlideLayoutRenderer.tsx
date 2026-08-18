import React, { useMemo, useRef, useState, useEffect } from 'react';
import ReactECharts from 'echarts-for-react';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import type { SlideLayout, SlideElement } from '../../../api/types';

const CANVAS_W = 960;
const CANVAS_H = 540;

/** 主题色板（PPT 设计大师：3 套配色系统） */
const THEMES: Record<string, {
  primary: string; secondary: string; light: string; bg: string; text: string;
}> = {
  academic: {
    primary: '#1677ff', secondary: '#69b1ff', light: '#e6f4ff',
    bg: 'linear-gradient(145deg,#f5faff 0%,#eef6ff 55%,#e6f4ff 100%)',
    text: '#1a1a2e',
  },
  morandi: {
    primary: '#52c41a', secondary: '#95de64', light: '#f6ffed',
    bg: 'linear-gradient(145deg,#fbfef6 0%,#f4fbe9 55%,#f6ffed 100%)',
    text: '#1f2d1a',
  },
  minimal: {
    primary: '#1a1a2e', secondary: '#8c8c8c', light: '#f5f5f5',
    bg: 'linear-gradient(145deg,#ffffff 0%,#fafafa 55%,#f2f2f2 100%)',
    text: '#1a1a2e',
  },
};
const DEFAULT_THEME = THEMES.academic;

interface SlideLayoutRendererProps {
  slides: SlideLayout[];
  layoutId?: string;
  /** 句-页联动：当前讲解句对应的高亮元素 ID（M4） */
  highlightElementIds?: string[];
  /** 章节序号（页脚装饰） */
  pageNumber?: number;
  totalPages?: number;
}

/**
 * 幻灯片布局渲染器 v2（PPT 设计大师）
 * - 主题色板：academic / morandi / minimal，整套页面统一取色
 * - 精致度：卡片圆角 12px + 柔和阴影、渐变背景、标题装饰条、章节号页脚
 * - 句-页联动：highlightElementIds 元素发光描边动画
 */
const SlideLayoutRenderer: React.FC<SlideLayoutRendererProps> = ({
  slides,
  layoutId,
  highlightElementIds,
  pageNumber,
  totalPages,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [containerW, setContainerW] = useState(720);

  useEffect(() => {
    const measure = () => {
      if (containerRef.current) setContainerW(containerRef.current.clientWidth);
    };
    measure();
    const observer = new ResizeObserver(measure);
    if (containerRef.current) observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  const slide = useMemo(
    () => slides.find((s) => s.layoutId === layoutId) || slides[0] || null,
    [slides, layoutId],
  );

  const theme = THEMES[slide?.theme || ''] || DEFAULT_THEME;
  const highlightSet = useMemo(
    () => new Set(highlightElementIds || []),
    [highlightElementIds],
  );

  if (!slide) return null;

  const scale = containerW / CANVAS_W;
  const canvasH = CANVAS_H * scale;

  return (
    <div ref={containerRef} style={{ width: '100%' }}>
      <div style={{ width: '100%', height: canvasH, position: 'relative', overflow: 'hidden' }}>
        {/* 高亮动画 keyframes */}
        <style>{`
          @keyframes emHighlight {
            0% { box-shadow: 0 0 0 0 rgba(250, 173, 20, 0); }
            30% { box-shadow: 0 0 0 6px rgba(250, 173, 20, 0.35); }
            70% { box-shadow: 0 0 0 4px rgba(250, 173, 20, 0.28); }
            100% { box-shadow: 0 0 0 0 rgba(250, 173, 20, 0); }
          }
          @keyframes emPulse {
            0%, 100% { opacity: 1; }
            50% { opacity: 0.75; }
          }
        `}</style>
        <div
          style={{
            width: CANVAS_W,
            height: CANVAS_H,
            transform: `scale(${scale})`,
            transformOrigin: 'top left',
            position: 'relative',
            background: theme.bg,
            borderRadius: 12,
            boxShadow: '0 6px 24px rgba(0,0,0,0.10)',
            overflow: 'hidden',
            border: `1px solid ${theme.secondary}33`,
            fontFamily: "'PingFang SC','Microsoft YaHei',sans-serif",
          }}
        >
          {/* 顶部装饰条 */}
          <div style={{
            position: 'absolute', top: 0, left: 0, right: 0, height: 6,
            background: `linear-gradient(90deg, ${theme.primary}, ${theme.secondary})`,
          }} />

          {/* 章节序号徽标 */}
          {typeof pageNumber === 'number' && (
            <div style={{
              position: 'absolute', top: 22, right: 28,
              fontSize: 13, fontWeight: 600, color: theme.primary,
              background: `${theme.primary}14`, borderRadius: 20,
              padding: '4px 14px', letterSpacing: 1,
            }}>
              {String(pageNumber + 1).padStart(2, '0')} / {String(totalPages || 1).padStart(2, '0')}
            </div>
          )}

          {/* 页标题（若有） */}
          {slide.title && (
            <div style={{
              position: 'absolute', top: 26, left: 36,
              fontSize: 26, fontWeight: 700, color: theme.text,
              display: 'flex', alignItems: 'center', gap: 12,
            }}>
              <span style={{
                width: 6, height: 26, borderRadius: 3,
                background: `linear-gradient(180deg, ${theme.primary}, ${theme.secondary})`,
                display: 'inline-block',
              }} />
              {slide.title}
            </div>
          )}

          {/* 元素 */}
          {slide.elements.map((el) => (
            <SlideElementView
              key={el.id}
              el={el}
              theme={theme}
              highlighted={highlightSet.has(el.id)}
            />
          ))}

          {/* 页脚装饰点 */}
          <div style={{
            position: 'absolute', bottom: 14, left: 36, right: 36,
            display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: 4,
          }}>
            {[0, 1, 2].map((i) => (
              <span key={i} style={{
                width: 5, height: 5, borderRadius: '50%',
                background: i === 0 ? theme.primary : `${theme.secondary}66`,
              }} />
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};

/** 单个元素的绝对定位外壳（含高亮动画） */
function wrap(
  el: SlideElement,
  children: React.ReactNode,
  highlighted: boolean,
): React.ReactElement {
  return (
    <div
      style={{
        position: 'absolute',
        left: el.x,
        top: el.y,
        width: el.w,
        height: el.h,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        overflow: 'hidden',
        animation: highlighted ? 'emHighlight 1.6s ease-in-out infinite, emPulse 1.6s ease-in-out infinite' : undefined,
        borderRadius: 12,
        zIndex: highlighted ? 10 : undefined,
      }}
    >
      {children}
    </div>
  );
}

const SlideElementView: React.FC<{
  el: SlideElement;
  theme: typeof DEFAULT_THEME;
  highlighted: boolean;
}> = ({ el, theme, highlighted }) => {
  const isCard = el.variant === 'card' || el.variant === 'conclusion' || el.variant === 'highlight';

  // 卡片/结论条外壳
  if (isCard) {
    const conclusion = el.variant === 'conclusion';
    return wrap(
      el,
      <div
        style={{
          width: '100%',
          height: '100%',
          background: conclusion
            ? `linear-gradient(135deg, ${theme.primary}1a, ${theme.light})`
            : (el.bg || `${theme.light}cc`),
          borderRadius: 12,
          border: `1px solid ${theme.secondary}55`,
          borderLeft: conclusion ? `6px solid ${theme.primary}` : `3px solid ${theme.secondary}88`,
          boxShadow: '0 4px 14px rgba(0,0,0,0.07)',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '0 16px',
          boxSizing: 'border-box',
        }}
      >
        {conclusion && (
          <div style={{
            fontSize: 12, fontWeight: 700, color: theme.primary,
            letterSpacing: 2, marginBottom: 4, textTransform: 'uppercase',
          }}>
            ⚡ 结论
          </div>
        )}
        <div style={{
          fontSize: el.fontSize || 18,
          fontWeight: conclusion ? 700 : 600,
          color: conclusion ? theme.primary : theme.text,
          textAlign: el.align || 'center',
          lineHeight: 1.5,
        }}>
          {el.label || el.content}
        </div>
      </div>,
      highlighted,
    );
  }

  switch (el.kind) {
    case 'text': {
      return wrap(
        el,
        <div
          style={{
            fontSize: el.fontSize || 20,
            fontWeight: el.bold ? 700 : 400,
            color: el.color || theme.text,
            textAlign: el.align || 'left',
            lineHeight: 1.4,
            width: '100%',
            padding: '0 8px',
            whiteSpace: 'pre-wrap',
            textShadow: el.bold ? `0 1px 2px ${theme.secondary}22` : undefined,
          }}
        >
          {el.content}
        </div>,
        highlighted,
      );
    }
    case 'shape': {
      const shape = el.shape || 'rect';
      const isCircle = shape === 'circle';
      return wrap(
        el,
        <div
          style={{
            width: '100%',
            height: '100%',
            background: el.fill || `${theme.light}cc`,
            borderRadius: isCircle ? '50%' : shape === 'round' ? (el.radius || 12) : 12,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: isCircle ? `3px solid ${theme.primary}` : `1px solid ${theme.secondary}55`,
            boxShadow: '0 4px 14px rgba(0,0,0,0.07)',
            color: theme.text,
            fontSize: 18,
            fontWeight: 600,
            padding: '0 12px',
            textAlign: 'center',
            boxSizing: 'border-box',
          }}
        >
          {el.label}
        </div>,
        highlighted,
      );
    }
    case 'line': {
      const from = el.from || [0, 0];
      const to = el.to || [el.w, el.h];
      return wrap(
        el,
        <svg width={el.w} height={el.h} style={{ overflow: 'visible' }}>
          <defs>
            <marker id={`arrow-${el.id}`} markerWidth="10" markerHeight="7" refX="9" refY="3.5" orient="auto">
              <polygon points="0 0, 10 3.5, 0 7" fill={el.color || theme.primary} />
            </marker>
          </defs>
          <line
            x1={from[0]}
            y1={from[1]}
            x2={to[0]}
            y2={to[1]}
            stroke={el.color || theme.primary}
            strokeWidth={2.5}
            strokeDasharray={el.dashed ? '6 4' : undefined}
            markerEnd={el.arrow ? `url(#arrow-${el.id})` : undefined}
          />
        </svg>,
        highlighted,
      );
    }
    case 'chart': {
      const option = buildChartOption(el, theme);
      return wrap(
        el,
        <div style={{ width: '100%', height: '100%' }}>
          <ReactECharts option={option} style={{ width: '100%', height: '100%' }} notMerge />
        </div>,
        highlighted,
      );
    }
    case 'latex': {
      const html = katex.renderToString(el.latex || '', { throwOnError: false });
      return wrap(
        el,
        <div
          dangerouslySetInnerHTML={{ __html: html }}
          style={{ fontSize: el.fontSize || 28, color: el.color || theme.text }}
        />,
        highlighted,
      );
    }
    default:
      return null;
  }
};

function buildChartOption(el: SlideElement, theme: typeof DEFAULT_THEME) {
  const { chartType = 'bar', data, themeColors } = el;
  const labels = data?.labels || [];
  const series = data?.series || [];
  const legends = data?.legends || [];
  const colors = themeColors && themeColors.length
    ? themeColors
    : [theme.primary, theme.secondary, '#faad14', '#ff4d4f', '#722ed1'];

  if (chartType === 'pie') {
    const pieData = (series[0] || []).map((v, i) => ({ name: labels[i] || `项${i + 1}`, value: v }));
    return {
      color: colors,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{
        type: 'pie', radius: ['35%', '68%'],
        itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
        data: pieData, label: { show: true, fontSize: 13 },
      }],
    };
  }
  if (chartType === 'radar') {
    const flat = series.flat();
    const max = flat.length ? Math.max(...flat) * 1.2 : 100;
    return {
      color: colors,
      tooltip: {},
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      radar: { indicator: labels.map((l) => ({ name: l, max })) },
      series: [{ type: 'radar', data: legends.map((lg, i) => ({ name: lg, value: series[i] || [] })) }],
    };
  }
  const isLine = chartType === 'line';
  return {
    color: colors,
    tooltip: { trigger: 'axis' },
    legend: { data: legends, textStyle: { fontSize: 12 } },
    grid: { left: 42, right: 16, top: 32, bottom: 30 },
    xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 12 } },
    yAxis: { type: 'value', axisLabel: { fontSize: 12 } },
    series: series.map((s, i) => ({
      name: legends[i] || `系列${i + 1}`,
      type: isLine ? 'line' : 'bar',
      smooth: isLine,
      barMaxWidth: 36,
      itemStyle: { borderRadius: isLine ? 0 : [4, 4, 0, 0] },
      data: s,
    })),
  };
}

export default SlideLayoutRenderer;

