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
            {isDiagram ? (
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

export default WhiteboardRenderer;
