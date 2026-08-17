import React from 'react';
import type { ActionDTO, QuizSubmitResponse, WidgetPayload } from '../../api/types';
import SpeechRenderer from './SpeechRenderer';
import WhiteboardRenderer from './WhiteboardRenderer';
import QuizRenderer from './QuizRenderer';
import DiscussionRenderer from './DiscussionRenderer';
import SceneTransitionRenderer from './SceneTransitionRenderer';
import PauseForThoughtRenderer from './PauseForThoughtRenderer';
import SlideLayoutRenderer from './SlideLayoutRenderer';
import InteractiveWidgetRenderer from './InteractiveWidgetRenderer';

/**
 * 教学动作分发器
 * 根据 action.type 渲染对应的动作组件
 */
interface ActionDispatcherProps {
  action: ActionDTO;
  onSubmitQuiz: (selectedIndex: number) => Promise<QuizSubmitResponse | null>;
  disabled?: boolean;
  /** 当前是否正在 TTS 播放（SpeechRenderer 用于显示波形） */
  isSpeaking?: boolean;
  /** 手动前进到下一个 Action */
  onAdvance?: () => void;
  /** 当前场景的 content（含 slides/widget/summaryMap，供 show_slide / launch_widget 使用） */
  sceneContent?: Record<string, any>;
  /** 当前场景已装载的交互组件（widget_* 动作复用，避免 iframe 重建） */
  activeWidget?: WidgetPayload | null;
  /** iframe 引用（父层持有，供后续 widget_* 动作复用） */
  widgetFrameRef?: React.MutableRefObject<HTMLIFrameElement | null>;
  /** launch_widget 动作装载组件时回调 */
  onWidgetLaunched?: (widget: WidgetPayload) => void;
}

/**
 * 解析场景 content 中的 slides（后端可能以 JSON 字符串存储；
 * 兼容嵌套结构：后端序列化 SceneContent 对象时会把整个 contentJson
 * 放进 content.contentJson，slides 实际在嵌套层）。
 */
export function parseSlides(content?: Record<string, any>) {
  const src = content?.slides ?? content?.contentJson?.slides;
  if (!src) return [];
  try {
    return typeof src === 'string' ? JSON.parse(src) : src;
  } catch {
    return [];
  }
}

/** 解析场景 content 中的 widget（兼容嵌套 contentJson） */
export function parseWidget(content?: Record<string, any>): WidgetPayload | null {
  const src = content?.widget ?? content?.contentJson?.widget;
  if (!src) return null;
  try {
    return typeof src === 'string' ? JSON.parse(src) : src;
  } catch {
    return null;
  }
}

/** 解析场景 content 中的 summaryMap（兼容嵌套 contentJson） */
export function parseSummaryMap(content?: Record<string, any>) {
  const src = content?.summaryMap ?? content?.contentJson?.summaryMap;
  if (!src) return null;
  try {
    return typeof src === 'string' ? JSON.parse(src) : src;
  } catch {
    return null;
  }
}

const ActionDispatcher: React.FC<ActionDispatcherProps> = ({
  action,
  onSubmitQuiz,
  disabled,
  isSpeaking = false,
  onAdvance,
  sceneContent,
  activeWidget,
  widgetFrameRef,
  onWidgetLaunched,
}) => {
  const type = action.type;

  switch (type) {
    case 'speech':
    case 'speech_with_highlight':
      return <SpeechRenderer action={action} isSpeaking={isSpeaking} />;

    case 'wb_draw_text':
    case 'wb_draw_diagram':
      return <WhiteboardRenderer action={action} />;

    case 'show_slide': {
      const slides = parseSlides(sceneContent);
      if (slides.length > 0 && action.layoutId) {
        return (
          <div style={{ padding: '8px 24px' }}>
            {action.speech && (
              <SpeechRenderer action={{ ...action, type: 'speech', text: action.speech }} isSpeaking={isSpeaking} />
            )}
            <SlideLayoutRenderer slides={slides} layoutId={action.layoutId} />
          </div>
        );
      }
      // 降级：无 slides 布局时退回白板/讲解
      return <WhiteboardRenderer action={action} />;
    }

    case 'launch_widget': {
      const widget = parseWidget(sceneContent);
      if (widget) {
        if (onWidgetLaunched) onWidgetLaunched(widget);
        return (
          <InteractiveWidgetRenderer
            widget={widget}
            onFrameReady={(frame) => {
              if (widgetFrameRef) widgetFrameRef.current = frame;
            }}
          />
        );
      }
      return <SpeechRenderer action={action} isSpeaking={isSpeaking} />;
    }

    case 'widget_highlight':
    case 'widget_set_state':
    case 'widget_annotate':
    case 'widget_reveal': {
      // 组件已在当前场景装载 → 复用并发送驱动消息
      if (activeWidget) {
        return (
          <InteractiveWidgetRenderer
            widget={activeWidget}
            pendingAction={action}
            onFrameReady={(frame) => {
              if (widgetFrameRef) widgetFrameRef.current = frame;
            }}
          />
        );
      }
      // 降级：组件未装载时展示讲解
      return <SpeechRenderer action={action} isSpeaking={isSpeaking} />;
    }

    case 'quiz':
      return (
        <QuizRenderer
          action={action}
          onSubmit={onSubmitQuiz}
          disabled={disabled}
          onAdvance={onAdvance}
        />
      );

    case 'discussion':
      return <DiscussionRenderer action={action} />;

    case 'scene_transition':
      return <SceneTransitionRenderer action={action} />;

    case 'pause_for_thought':
      return <PauseForThoughtRenderer action={action} />;

    case 'code_demo':
      // code_demo 暂时未实现，用 speech 替代
      return <SpeechRenderer action={action} isSpeaking={isSpeaking} />;

    default:
      return <SpeechRenderer action={action} isSpeaking={isSpeaking} />;
  }
};

export default ActionDispatcher;
