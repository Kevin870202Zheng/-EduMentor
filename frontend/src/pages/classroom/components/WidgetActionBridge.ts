/**
 * WidgetActionBridge — 父页面 → iframe 组件的 postMessage 通信桥。
 * AI 教师通过 widget_* 动作驱动 HTML 交互组件（设计文档 §3.2 / §4.3）。
 */
export type WidgetMessageType =
  | 'SET_WIDGET_STATE'
  | 'HIGHLIGHT_ELEMENT'
  | 'ANNOTATE_ELEMENT'
  | 'REVEAL_ELEMENT';

export interface WidgetMessage {
  type: WidgetMessageType;
  target?: string;
  state?: Record<string, any>;
  content?: string;
}

/**
 * 向组件 iframe 发送驱动消息。
 * @returns 是否发送成功（iframe 未就绪时返回 false，由调用方决定是否重试）
 */
export function postWidgetMessage(
  frame: HTMLIFrameElement | null,
  msg: WidgetMessage,
): boolean {
  if (!frame?.contentWindow) return false;
  try {
    frame.contentWindow.postMessage(msg, '*');
    return true;
  } catch {
    return false;
  }
}

/** 将 ActionDTO 转换为对应的 WidgetMessage（非 widget_* 动作返回 null） */
export function actionToWidgetMessage(action: {
  type: string;
  target?: string;
  state?: Record<string, any>;
  content?: string;
}): WidgetMessage | null {
  switch (action.type) {
    case 'widget_set_state':
      return { type: 'SET_WIDGET_STATE', state: action.state };
    case 'widget_highlight':
      return { type: 'HIGHLIGHT_ELEMENT', target: action.target };
    case 'widget_annotate':
      return { type: 'ANNOTATE_ELEMENT', target: action.target, content: action.content };
    case 'widget_reveal':
      return { type: 'REVEAL_ELEMENT', target: action.target };
    default:
      return null;
  }
}
