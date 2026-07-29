import React from 'react';
import type { ActionDTO, QuizSubmitResponse } from '../../api/types';
import SpeechRenderer from './SpeechRenderer';
import WhiteboardRenderer from './WhiteboardRenderer';
import QuizRenderer from './QuizRenderer';
import DiscussionRenderer from './DiscussionRenderer';
import SceneTransitionRenderer from './SceneTransitionRenderer';
import PauseForThoughtRenderer from './PauseForThoughtRenderer';

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
}

const ActionDispatcher: React.FC<ActionDispatcherProps> = ({
  action,
  onSubmitQuiz,
  disabled,
  isSpeaking = false,
  onAdvance,
}) => {
  const type = action.type;

  switch (type) {
    case 'speech':
    case 'speech_with_highlight':
      return <SpeechRenderer action={action} isSpeaking={isSpeaking} />;

    case 'wb_draw_text':
    case 'wb_draw_diagram':
      return <WhiteboardRenderer action={action} />;

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
