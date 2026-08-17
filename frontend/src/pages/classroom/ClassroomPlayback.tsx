import React, { useEffect, useRef, useState } from 'react';
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
import TeacherAvatar from './components/TeacherAvatar';
import { PRESET_TEACHERS } from './components/TeacherAvatar';
import SceneSidebar from './components/SceneSidebar';
import type { PlaybackState } from './usePlayback';
import type { WidgetPayload } from '../../api/types';

const { Text, Title } = Typography;

/**
 * 沉浸式课堂播放器主页面 v2.0
 * 布局：顶部标题栏 + 左侧场景侧边栏 + 右侧内容区
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
    pause,
    resume,
    nextAction,
    prevAction,
    goToScene,
    submitQuiz,
    loadClassroom,
    ttsState,
  } = playback;

  const [sidebarCollapsed, setSidebarCollapsed] = useState(false);

  // 当前场景已装载的交互组件（widget_* 动作复用，场景切换时重置）
  const [activeWidget, setActiveWidget] = useState<WidgetPayload | null>(null);
  const widgetFrameRef = useRef<HTMLIFrameElement | null>(null);

  // 切换场景时重置交互组件状态
  useEffect(() => {
    setActiveWidget(null);
    widgetFrameRef.current = null;
  }, [currentSceneIndex]);

  // 判断当前 speech 是否正在 TTS 播放中（用于波形动画）
  const isSpeaking =
    ttsState === 'playing' &&
    (currentAction?.type === 'speech' ||
      currentAction?.type === 'speech_with_highlight' ||
      currentAction?.type === 'code_demo');

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

          {/* 主要内容 */}
          <div style={{
            flex: 1,
            overflowY: 'auto',
            padding: '8px 0',
            display: 'flex',
            flexDirection: 'column',
          }}>
            {/* 当前场景标题 */}
            {currentScene && (
              <div style={{ padding: '8px 24px', textAlign: 'center' }}>
                <Tag color="blue" style={{ fontSize: 13, padding: '2px 12px' }}>
                  场景 {currentSceneIndex + 1}/{totalScenes}：{currentScene.title}
                </Tag>
              </div>
            )}

            {/* 当前Action */}
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'center' }}>
              {currentAction ? (
                <ActionDispatcher
                  key={`action-${currentSceneIndex}-${currentActionIndex}`}
                  action={currentAction}
                  onSubmitQuiz={submitQuiz}
                  disabled={state !== 'playing'}
                  isSpeaking={isSpeaking}
                  onAdvance={nextAction}
                  sceneContent={currentScene?.content}
                  activeWidget={activeWidget}
                  widgetFrameRef={widgetFrameRef}
                  onWidgetLaunched={setActiveWidget}
                />
              ) : (
                <div style={{ textAlign: 'center', padding: 48, color: '#999' }}>
                  <Text>没有可播放的内容</Text>
                </div>
              )}
            </div>

            {/* 播放控制（播放中/暂停时显示） */}
            {(state === 'playing' || state === 'paused' || state === 'idle') && (
              <div style={{
                padding: '12px 24px',
                textAlign: 'center',
                borderTop: '1px solid #f0f0f0',
                background: '#fff',
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

                <div style={{ marginTop: 8 }}>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    动作 {currentActionIndex + 1}/{currentScene?.actions?.length || 0}
                    {' | '}
                    {currentAction?.type === 'speech' ? '讲解' :
                     currentAction?.type === 'quiz' ? '练习' :
                     currentAction?.type === 'wb_draw_text' ? '白板' :
                     currentAction?.type === 'discussion' ? '讨论' :
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
    </div>
  );
};

export default ClassroomPlayback;
