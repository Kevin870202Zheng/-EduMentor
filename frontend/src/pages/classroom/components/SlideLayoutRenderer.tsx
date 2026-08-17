import React, { useMemo, useRef, useState, useEffect } from 'react';
import ReactECharts from 'echarts-for-react';
import katex from 'katex';
import 'katex/dist/katex.min.css';
import type { SlideLayout, SlideElement } from '../../../api/types';

const CANVAS_W = 960;
const CANVAS_H = 540;

/**
 * 幻灯片布局渲染器 — 将 LLM 生成的元素级布局（text/shape/line/chart/latex）
 * 渲染为 16:9 PPT 卡片。画布固定 960×540 逻辑像素，按容器宽度等比缩放。
 */
interface SlideLayoutRendererProps {
  slides: SlideLayout[];
  layoutId?: string;
}

const SlideLayoutRenderer: React.FC<SlideLayoutRendererProps> = ({ slides, layoutId }) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const [containerW, setContainerW] = useState(720);

  useEffect(() => {
    const measure = () => {
      if (containerRef.current) {
        setContainerW(containerRef.current.clientWidth);
      }
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

  if (!slide) return null;

  const scale = containerW / CANVAS_W;
  const canvasH = CANVAS_H * scale;

  return (
    <div ref={containerRef} style={{ width: '100%' }}>
      <div style={{ width: '100%', height: canvasH, position: 'relative', overflow: 'hidden' }}>
        <div
          style={{
            width: CANVAS_W,
            height: CANVAS_H,
            transform: `scale(${scale})`,
            transformOrigin: 'top left',
            position: 'relative',
            background: '#ffffff',
            borderRadius: 12,
            boxShadow: '0 4px 20px rgba(0,0,0,0.08)',
            overflow: 'hidden',
            border: '1px solid #eef0f4',
          }}
        >
          {slide.elements.map((el) => (
            <SlideElementView key={el.id} el={el} />
          ))}
        </div>
      </div>
    </div>
  );
};

/** 单个元素的绝对定位外壳 */
function wrap(el: SlideElement, children: React.ReactNode): React.ReactElement {
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
      }}
    >
      {children}
    </div>
  );
}

const SlideElementView: React.FC<{ el: SlideElement }> = ({ el }) => {
  switch (el.kind) {
    case 'text': {
      return wrap(
        el,
        <div
          style={{
            fontSize: el.fontSize || 20,
            fontWeight: el.bold ? 700 : 400,
            color: el.color || '#1a1a2e',
            textAlign: el.align || 'left',
            lineHeight: 1.4,
            width: '100%',
            padding: '0 8px',
            whiteSpace: 'pre-wrap',
          }}
        >
          {el.content}
        </div>,
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
            background: el.fill || '#f0f5ff',
            borderRadius: isCircle ? '50%' : shape === 'round' ? (el.radius || 12) : 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            border: shape === 'circle' ? '3px solid #1677ff' : undefined,
            color: '#1a1a2e',
            fontSize: 18,
            fontWeight: 600,
            padding: '0 12px',
            textAlign: 'center',
            boxSizing: 'border-box',
          }}
        >
          {el.label}
        </div>,
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
              <polygon points="0 0, 10 3.5, 0 7" fill={el.color || '#1677ff'} />
            </marker>
          </defs>
          <line
            x1={from[0]}
            y1={from[1]}
            x2={to[0]}
            y2={to[1]}
            stroke={el.color || '#1677ff'}
            strokeWidth={2.5}
            strokeDasharray={el.dashed ? '6 4' : undefined}
            markerEnd={el.arrow ? `url(#arrow-${el.id})` : undefined}
          />
        </svg>,
      );
    }
    case 'chart': {
      const option = buildChartOption(el);
      return wrap(
        el,
        <div style={{ width: '100%', height: '100%' }}>
          <ReactECharts option={option} style={{ width: '100%', height: '100%' }} notMerge />
        </div>,
      );
    }
    case 'latex': {
      const html = katex.renderToString(el.latex || '', { throwOnError: false });
      return wrap(
        el,
        <div
          dangerouslySetInnerHTML={{ __html: html }}
          style={{ fontSize: el.fontSize || 28, color: el.color || '#1a1a2e' }}
        />,
      );
    }
    default:
      return null;
  }
};

function buildChartOption(el: SlideElement) {
  const { chartType = 'bar', data, themeColors } = el;
  const labels = data?.labels || [];
  const series = data?.series || [];
  const legends = data?.legends || [];
  const colors = themeColors && themeColors.length ? themeColors : ['#1677ff', '#52c41a', '#faad14', '#ff4d4f'];

  if (chartType === 'pie') {
    const pieData = (series[0] || []).map((v, i) => ({ name: labels[i] || `项${i + 1}`, value: v }));
    return {
      color: colors,
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { fontSize: 12 } },
      series: [{ type: 'pie', radius: ['35%', '68%'], data: pieData, label: { show: true, fontSize: 13 } }],
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
      data: s,
    })),
  };
}

export default SlideLayoutRenderer;
