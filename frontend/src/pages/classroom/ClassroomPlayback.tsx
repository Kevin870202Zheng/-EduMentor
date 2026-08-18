import React, { useMemo, useRef, useState } from 'react';
import { useParams } from 'react-router-dom';
import {
  Button,
  Space,
  Typography,
  Progress,
  Spin,
  Tag,
  Tooltip,
} from 'antd';
import {
  PlayCircleOutlined,
  PauseCircleOutlined,
  StepForwardOutlined,
  StepBackwardOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import { usePlayback } from './usePlayback';
import ActionDispatcher from './components/ActionDispatcher';
import VisualCanvas from './components/VisualCanvas';
import SubtitleBar from './components/SubtitleBar';
import TeacherAvatar from './components/TeacherAvatar';
import { PRESET_TEACHERS } from './components/TeacherAvatar';
import SceneSidebar from './components/SceneSidebar';
import type { PlaybackState } from './usePlayback';
import type { WidgetPayload } from '../../api/types';

const { Text, Title } = Typography;

/**
 * 沉浸式课堂播放器主页面 v4.0
 * 布局：顶部标题栏 + 左侧场景侧边栏 + 右侧内容区
 * 双轨模型：视觉画布（常驻）+ 字幕条（语音轨）+ 交互覆盖层（quiz/discussion）
 */
const ClassroomPlayback: React.FC = () => {
  const { classroomId } = useParams<{ classroomId: string }>();
  const playback = usePlayback(classroomId || '');

  const {
    classroom,
    state,
    loading,
    error,
    currentScene,
    currentSceneIndex,
    currentAction,
    currentActionIndex,
    totalScenes,
    scenesCompleted,
    visualState,
    pendingWidgetAction,
    gotoSlidePage,
    pause,
    resume,
    nextAction,
    prevAction,
    goToScene,
    submitQuiz,
    loadClassroom,
    ttsState,
    ttsProgress,
  } = playback;

  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);
  const widgetFrameRef = useRef<HTMLIFrameElement | null>(null);

  // ── 语音轨：字幕条内容（speech / show_slide.speech / widget_*.content） ──
  const subtitleAction = useMemo(() => {
    const a = currentAction;
    if (!a) return null;
    if (a.type === 'speech' || a.type === 'speech_with_highlight' || a.type === 'code_demo') {
      return a;
    }
    if (a.type === 'show_slide' && a.speech?.trim()) {
      return { ...a, type: 'speech' as const, text: a.speech };
    }
    if (
      (a.type === 'widget_highlight' || a.type === 'widget_set_state'
        || a.type === 'widget_annotate' || a.type === 'widget_reveal')
      && a.content?.trim()
    ) {
      return { ...a, type: 'speech' as const, text: a.content };
    }
    return null;
  }, [currentAction]);

  // 是否正在 TTS 播放（波形动画）
  const isSpeaking = ttsState === 'playing' && !!subtitleAction;

  // ── 交互覆盖层：quiz / discussion / scene_transition ──
  const isOverlay =
    currentAction?.type === 'quiz'
    || currentAction?.type === 'discussion'
    || currentAction?.type === 'scene_transition';

  // 无语音时的轻提示（pause_for_thought）
  const subtitleHint = currentAction?.type === 'pause_for_thought'
    ? '💭 思考一下…'
    : null;

  // 加载中
  if (loading) {
    return (
      <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
        <Spin size="large" tip="加载课堂中..." />
      </div>
    );
  }

  // 错误
  if (error) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Title level={4} type="danger">加载失败</Title>
        <Text type="secondary">{error}</Text>
        <br /><br />
        <Button type="primary" onClick={loadClassroom}>重试</Button>
      </div>
    );
  }

  // 无数据
  if (!classroom) {
    return (
      <div style={{ textAlign: 'center', padding: 48 }}>
        <Title level={4}>课堂不存在</Title>
        <Button type="primary" onClick={() => window.history.back()}>返回</Button>
      </div>
    );
  }

  const progressPercent = totalScenes > 0
    ? Math.round((scenesCompleted / totalScenes) * 100)
    : 0;

  const stateLabels: Record<PlaybackState, { text: string; color: string }> = {
    idle: { text: '准备开始', color: 'default' },
    playing: { text: '播放中', color: 'processing' },
    paused: { text: '已暂停', color: 'warning' },
    live: { text: '讨论中', color: 'success' },
  };

  const stateInfo = stateLabels[state];

  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      height: 'calc(100vh - 140px)',
      background: '#fafafa',
      borderRadius: 12,
      overflow: 'hidden',
    }}>
      {/* ========== 顶部：课堂标题 + 状态 + AI讲师头像 ========== */}
      <div style={{
        padding: '12px 24px',
        background: '#fff',
        borderBottom: '1px solid #f0f0f0',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        flexShrink: 0,
      }}>
        <div>
          <Title level={4} style={{ margin: 0 }}>{classroom.title}</Title>
          <Text type="secondary" style={{ fontSize: 13 }}>
            {classroom.description}
          </Text>
        </div>
        <Space size={16}>
          <Space>
            <Tag color={stateInfo.color}>{stateInfo.text}</Tag>
            <Text type="secondary">
              {scenesCompleted}/{totalScenes} 场景
            </Text>
          </Space>
          {/* AI 讲师头像 */}
          <TeacherAvatar
            role={PRESET_TEACHERS.teacher}
            isSpeaking={isSpeaking}
            sceneTitle={currentScene?.title}
          />
        </Space>
      </div>

      {/* ========== 主体区：侧边栏 + 内容区 ========== */}
      <div style={{
        display: 'flex',
        flex: 1,
        overflow: 'hidden',
      }}>
        {/* 场景侧边栏 */}
        <SceneSidebar
          scenes={classroom.scenes}
          currentSceneIndex={currentSceneIndex}
          completedCount={scenesCompleted}
          collapsed={sidebarCollapsed}
          onCollapseChange={setSidebarCollapsed}
          onSceneSelect={goToScene}
        />

        {/* 内容区 */}
        <div style={{
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}>
          {/* 进度条 */}
          <Progress
            percent={progressPercent}
            showInfo={false}
            strokeColor="#1677ff"
            size="small"
            style={{ margin: 0 }}
          />

          {/* 场景标题 */}
          {currentScene && (
            <div style={{ padding: '8px 24px 4px', textAlign: 'center', flexShrink: 0 }}>
              <Tag color="blue" style={{ fontSize: 13, padding: '2px 12px' }}>
                场景 {currentSceneIndex + 1}/{totalScenes}：{currentScene.title}
              </Tag>
            </div>
          )}

          {/* ===== 主区域：视觉画布（常驻）+ 字幕条 + 交互覆盖层 ===== */}
          <div style={{
            flex: 1,
            minHeight: 0,
            padding: '4px 16px 8px',
            display: 'flex',
            flexDirection: 'column',
            gap: 8,
            position: 'relative',
          }}>
            {/* 视觉画布（常驻） */}
            <VisualCanvas
              visualState={visualState}
              sceneContent={currentScene?.content}
              sceneTitle={currentScene?.title}
              onGotoPage={gotoSlidePage}
              pendingWidgetAction={pendingWidgetAction}
              widgetFrameRef={widgetFrameRef}
              highlightElementIds={
                currentAction?.type === 'show_slide'
                  ? currentAction.highlightElementIds
                  : undefined
              }
            />

            {/* 字幕条（语音轨） */}
            <SubtitleBar
              action={subtitleAction}
              isSpeaking={isSpeaking}
              ttsProgress={ttsProgress}
              hint={subtitleHint}
            />

            {/* 交互覆盖层：quiz / discussion / scene_transition */}
            {isOverlay && (
              <div style={{
                position: 'absolute',
                inset: 0,
                zIndex: 20,
                background: 'rgba(255,255,255,0.97)',
                overflow: 'auto',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 12,
              }}>
                <div style={{ width: '100%', maxWidth: 720, padding: '16px 8px' }}>
                  <ActionDispatcher
                    key={`action-${currentSceneIndex}-${currentActionIndex}`}
                    action={currentAction}
                    onSubmitQuiz={submitQuiz}
                    disabled={state !== 'playing'}
                    onAdvance={nextAction}
                  />
                </div>
              </div>
            )}
          </div>

          {/* 播放控制（播放中/暂停时显示） */}
          {(state === 'playing' || state === 'paused' || state === 'idle') && (
            <div style={{
              padding: '10px 24px',
              textAlign: 'center',
              borderTop: '1px solid #f0f0f0',
              background: '#fff',
              flexShrink: 0,
            }}>
              <Space size={16}>
                <Tooltip title="上一动作">
                  <Button
                    icon={<StepBackwardOutlined />}
                    onClick={prevAction}
                    disabled={currentSceneIndex === 0 && currentActionIndex === 0}
                    shape="circle"
                    size="large"
                  />
                </Tooltip>

                {state === 'idle' || state === 'paused' ? (
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={resume}
                    size="large"
                    style={{ minWidth: 120, borderRadius: 24 }}
                  >
                    继续播放
                  </Button>
                ) : (
                  <Button
                    type="primary"
                    icon={<PauseCircleOutlined />}
                    onClick={pause}
                    size="large"
                    style={{ minWidth: 120, borderRadius: 24 }}
                  >
                    暂停
                  </Button>
                )}

                <Tooltip title="下一动作">
                  <Button
                    icon={<StepForwardOutlined />}
                    onClick={nextAction}
                    shape="circle"
                    size="large"
                  />
                </Tooltip>
              </Space>

              <div style={{ marginTop: 6 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  动作 {currentActionIndex + 1}/{currentScene?.actions?.length || 0}
                  {' | '}
                  {currentAction?.type === 'speech' ? '讲解' :
                   currentAction?.type === 'quiz' ? '练习' :
                   currentAction?.type === 'wb_draw_text' ? '白板' :
                   currentAction?.type === 'discussion' ? '讨论' :
                   currentAction?.type === 'show_slide' ? '换页' :
                   currentAction?.type || '动作'}
                </Text>
              </div>
            </div>
          )}

          {/* Live模式：讨论状态 */}
          {state === 'live' && (
            <div style={{
              padding: '16px 24px',
              textAlign: 'center',
              borderTop: '1px solid #d4adfc',
              background: '#f9f0ff',
            }}>
              <Space>
                <BulbOutlined style={{ color: '#722ed1', fontSize: 16 }} />
                <Text strong style={{ color: '#722ed1' }}>讨论模式</Text>
                <Button
                  type="primary"
                  ghost
                  onClick={resume}
                  size="small"
                >
                  继续课堂
                </Button>
              </Space>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ClassroomPlayback;

