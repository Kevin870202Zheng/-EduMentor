import React from 'react';
import type { ActionDTO, QuizSubmitResponse } from '../../api/types';
import QuizRenderer from './QuizRenderer';
import DiscussionRenderer from './DiscussionRenderer';
import SceneTransitionRenderer from './SceneTransitionRenderer';
import PauseForThoughtRenderer from './PauseForThoughtRenderer';

interface ActionDispatcherProps {
  action: ActionDTO;
  onSubmitQuiz: (selectedIndex: number) => Promise<QuizSubmitResponse | null>;
  disabled?: boolean;
  /** 手动前进到下一个 Action */
  onAdvance?: () => void;
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
export function parseWidget(content?: Record<string, any>): any | null {
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

/**
 * 教学动作分发器 v4.0（双轨模型重构）
 *
 * 只处理「交互覆盖层」动作：
 *   - quiz / discussion / scene_transition / pause_for_thought
 * 其余动作（speech / show_slide / launch_widget / widget_* / wb_draw_*）
 * 已由 usePlayback 的视觉轨 + 语音轨处理，此处返回 null。
 */
const ActionDispatcher: React.FC<ActionDispatcherProps> = ({
  action,
  onSubmitQuiz,
  disabled,
  onAdvance,
}) => {
  switch (action.type) {
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

    // 以下动作由视觉轨（VisualCanvas）+ 语音轨（SubtitleBar）处理
    case 'speech':
    case 'speech_with_highlight':
    case 'code_demo':
    case 'show_slide':
    case 'launch_widget':
    case 'widget_highlight':
    case 'widget_set_state':
    case 'widget_annotate':
    case 'widget_reveal':
    case 'wb_draw_text':
    case 'wb_draw_diagram':
    default:
      return null;
  }
};

export default ActionDispatcher;

