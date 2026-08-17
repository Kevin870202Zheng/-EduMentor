import React, { useRef, useCallback, useState, useEffect } from 'react';
import { Typography, Space } from 'antd';
import { motion } from 'motion/react';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  CheckCircleFilled,
} from '@ant-design/icons';
import type { SceneDetailDto, ActionDTO } from '../../api/types';
import SceneThumbnail from './SceneThumbnail';

const { Text } = Typography;

/**
 * SceneSidebar — 场景侧边栏组件
 *
 * 功能：
 * - 显示场景列表（每项含序号、标题、缩略图、类型图标）
 * - 拖拽调整宽度（170px ~ 400px，默认 220px）
 * - 可折叠/展开
 * - 当前场景高亮，已完成场景勾选标记
 * - 生成中的场景显示骨架屏
 * - 点击场景跳转
 */

interface SceneSidebarProps {
  /** 所有场景 */
  scenes: SceneDetailDto[];
  /** 当前场景索引 */
  currentSceneIndex: number;
  /** 已完成的场景数 */
  completedCount: number;
  /** 是否折叠 */
  collapsed: boolean;
  /** 折叠状态变更回调 */
  onCollapseChange: (collapsed: boolean) => void;
  /** 点击场景回调（传入索引） */
  onSceneSelect: (index: number) => void;
}

const DEFAULT_WIDTH = 220;
const MIN_WIDTH = 170;
const MAX_WIDTH = 400;

// 场景类型图标映射
const getSceneTypeEmoji = (scene: SceneDetailDto): string => {
  const mainAction = (scene.actions || []).find(
    (a: ActionDTO) => !['scene_transition', 'pause_for_thought'].includes(a.type),
  );
  const type = mainAction?.type || 'speech';
  const map: Record<string, string> = {
    speech: '💬',
    speech_with_highlight: '⭐',
    wb_draw_text: '📝',
    wb_draw_diagram: '📐',
    show_slide: '📽️',
    launch_widget: '🧪',
    widget_highlight: '🔦',
    widget_set_state: '🎚️',
    widget_annotate: '🏷️',
    widget_reveal: '👁️',
    quiz: '✍️',
    discussion: '💭',
    scene_transition: '🔄',
    pause_for_thought: '🤔',
    code_demo: '💻',
  };
  return map[type] || '📖';
};

const SceneSidebar: React.FC<SceneSidebarProps> = ({
  scenes,
  currentSceneIndex,
  completedCount,
  collapsed,
  onCollapseChange,
  onSceneSelect,
}) => {
  const [sidebarWidth, setSidebarWidth] = useState(DEFAULT_WIDTH);
  const isDraggingRef = useRef(false);
  const sidebarRef = useRef<HTMLDivElement>(null);
  const activeItemRef = useRef<HTMLDivElement>(null);

  // 拖拽逻辑
  const handleDragStart = useCallback(
    (e: React.MouseEvent) => {
      e.preventDefault();
      isDraggingRef.current = true;
      const startX = e.clientX;
      const startWidth = sidebarWidth;

      const handleMouseMove = (me: MouseEvent) => {
        const delta = me.clientX - startX;
        const newWidth = Math.min(
          MAX_WIDTH,
          Math.max(MIN_WIDTH, startWidth + delta),
        );
        setSidebarWidth(newWidth);
      };

      const handleMouseUp = () => {
        isDraggingRef.current = false;
        document.removeEventListener('mousemove', handleMouseMove);
        document.removeEventListener('mouseup', handleMouseUp);
        document.body.style.cursor = '';
        document.body.style.userSelect = '';
      };

      document.body.style.cursor = 'col-resize';
      document.body.style.userSelect = 'none';
      document.addEventListener('mousemove', handleMouseMove);
      document.addEventListener('mouseup', handleMouseUp);
    },
    [sidebarWidth],
  );

  // 自动滚动到当前激活的场景
  useEffect(() => {
    if (activeItemRef.current) {
      activeItemRef.current.scrollIntoView({
        block: 'nearest',
        behavior: 'smooth',
      });
    }
  }, [currentSceneIndex]);

  const displayWidth = collapsed ? 0 : sidebarWidth;

  return (
    <motion.div
      ref={sidebarRef}
      animate={{ width: displayWidth }}
      transition={
        isDraggingRef.current
          ? { duration: 0 }
          : { type: 'spring', stiffness: 300, damping: 30 }
      }
      style={{
        position: 'relative',
        background: '#fafafa',
        borderRight: '1px solid #f0f0f0',
        display: 'flex',
        flexDirection: 'column',
        flexShrink: 0,
        overflow: 'hidden',
        height: '100%',
      }}
    >
      {/* 折叠状态下的展开按钮 */}
      {collapsed ? (
        <div
          style={{
            padding: '12px 8px',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12,
          }}
        >
          <div
            onClick={() => onCollapseChange(false)}
            style={{
              cursor: 'pointer',
              padding: 6,
              borderRadius: 6,
              color: '#999',
              fontSize: 18,
            }}
          >
            <MenuUnfoldOutlined />
          </div>

          {/* 折叠状态显示小序号圆点 */}
          {scenes.map((scene, i) => (
            <div
              key={scene.id}
              onClick={() => onSceneSelect(i)}
              style={{
                width: 24,
                height: 24,
                borderRadius: '50%',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: 11,
                fontWeight: 700,
                cursor: 'pointer',
                background:
                  i === currentSceneIndex
                    ? '#1677ff'
                    : i < completedCount
                      ? '#52c41a'
                      : '#f0f0f0',
                color:
                  i === currentSceneIndex || i < completedCount
                    ? '#fff'
                    : '#999',
              }}
            >
              {i < completedCount ? (
                <CheckCircleFilled style={{ fontSize: 14 }} />
              ) : (
                i + 1
              )}
            </div>
          ))}
        </div>
      ) : (
        <>
          {/* 侧边栏头部 */}
          <div
            style={{
              padding: '12px 16px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              borderBottom: '1px solid #f0f0f0',
            }}
          >
            <Text strong style={{ fontSize: 14 }}>
              教学场景
            </Text>
            <Space size={4}>
              <Text type="secondary" style={{ fontSize: 12 }}>
                {completedCount}/{scenes.length}
              </Text>
              <div
                onClick={() => onCollapseChange(true)}
                style={{
                  cursor: 'pointer',
                  padding: '2px 6px',
                  borderRadius: 4,
                  color: '#999',
                  fontSize: 14,
                }}
              >
                <MenuFoldOutlined />
              </div>
            </Space>
          </div>

          {/* 场景列表 */}
          <div
            style={{
              flex: 1,
              overflowY: 'auto',
              overflowX: 'hidden',
              padding: '8px',
              display: 'flex',
              flexDirection: 'column',
              gap: 6,
            }}
          >
            {scenes.map((scene, index) => {
              const isCurrent = index === currentSceneIndex;
              const isCompleted = index < completedCount;
              const isFuture = index > currentSceneIndex;

              return (
                <motion.div
                  key={scene.id}
                  ref={isCurrent ? activeItemRef : null}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: index * 0.03, duration: 0.2 }}
                  onClick={() => onSceneSelect(index)}
                  style={{
                    borderRadius: 8,
                    padding: 8,
                    cursor: 'pointer',
                    background: isCurrent
                      ? '#e6f4ff'
                      : isCompleted
                        ? '#f6ffed'
                        : 'transparent',
                    border: isCurrent
                      ? '1px solid #91d5ff'
                      : '1px solid transparent',
                    transition: 'all 0.2s',
                  }}
                  whileHover={
                    !isCurrent
                      ? { background: '#f5f5f5', scale: 1.01 }
                      : undefined
                  }
                  whileTap={{ scale: 0.98 }}
                >
                  {/* 场景头部：序号 + 标题 + 完成标记 */}
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: 6,
                      marginBottom: 6,
                    }}
                  >
                    <div
                      style={{
                        width: 20,
                        height: 20,
                        borderRadius: '50%',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: 10,
                        fontWeight: 700,
                        flexShrink: 0,
                        background: isCurrent
                          ? '#1677ff'
                          : isCompleted
                            ? '#52c41a'
                            : '#f0f0f0',
                        color:
                          isCurrent || isCompleted ? '#fff' : '#999',
                      }}
                    >
                      {isCompleted ? (
                        <CheckCircleFilled style={{ fontSize: 13 }} />
                      ) : (
                        index + 1
                      )}
                    </div>
                    <span
                      style={{
                        fontSize: 12,
                        fontWeight: isCurrent ? 700 : 500,
                        color: isCurrent
                          ? '#1677ff'
                          : isFuture
                            ? '#bbb'
                            : '#333',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        flex: 1,
                      }}
                    >
                      {scene.title}
                    </span>
                    <span style={{ fontSize: 12, opacity: 0.5 }}>
                      {getSceneTypeEmoji(scene)}
                    </span>
                  </div>

                  {/* 缩略图 */}
                  <SceneThumbnail scene={scene} />
                </motion.div>
              );
            })}

            {/* 底部留白 */}
            <div style={{ height: 16 }} />
          </div>

          {/* 拖拽手柄 */}
          <div
            onMouseDown={handleDragStart}
            style={{
              position: 'absolute',
              right: 0,
              top: 0,
              bottom: 0,
              width: 5,
              cursor: 'col-resize',
              zIndex: 10,
            }}
          >
            <div
              style={{
                position: 'absolute',
                top: '50%',
                right: 1,
                width: 2,
                height: 32,
                borderRadius: 1,
                background: '#d9d9d9',
                transform: 'translateY(-50%)',
                pointerEvents: 'none',
                transition: 'height 0.2s',
              }}
            />
          </div>
        </>
      )}
    </motion.div>
  );
};

export default SceneSidebar;
