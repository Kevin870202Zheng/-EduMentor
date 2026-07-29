import React from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeKatex from 'rehype-katex';
import remarkMath from 'remark-math';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { oneDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { Typography } from 'antd';

const { Text } = Typography;

/**
 * MarkdownContent — Markdown 渲染组件
 *
 * 功能：
 * - 标准 Markdown 渲染（标题、列表、粗体、链接等）
 * - LaTeX 数学公式（行内 $...$ 和块级 $$...$$）
 * - 代码语法高亮（支持所有 Prism 语言）
 * - 图片显示（自适应宽度 + 圆角）
 *
 * 注意：此组件通过 WhiteboardRenderer 的 Suspense 懒加载，
 * 仅在白板内容包含 Markdown 语法时才会被加载。
 */

interface MarkdownContentProps {
  content: string;
}

const MarkdownContent: React.FC<MarkdownContentProps> = ({ content }) => {
  return (
    <div className="edumentor-markdown">
      <ReactMarkdown
        remarkPlugins={[remarkMath]}
        rehypePlugins={[rehypeKatex]}
        components={{
          // 代码块：使用 SyntaxHighlighter 渲染
          code({ node, className, children, ...props }) {
            const match = /language-(\w+)/.exec(className || '');
            const isInline = !match && !className;

            if (isInline) {
              return (
                <code
                  style={{
                    background: '#f0f0f0',
                    padding: '2px 6px',
                    borderRadius: 4,
                    fontSize: '0.9em',
                    fontFamily:
                      "'JetBrains Mono', 'Fira Code', 'Courier New', monospace",
                  }}
                  {...props}
                >
                  {children}
                </code>
              );
            }

            const language = match ? match[1] : 'text';
            return (
              <div style={{ margin: '12px 0', borderRadius: 8, overflow: 'hidden', fontSize: 14 }}>
                {/* 代码头部语言标签 */}
                <div
                  style={{
                    padding: '2px 12px',
                    background: '#282c34',
                    color: '#abb2bf',
                    fontSize: 11,
                    borderBottom: '1px solid #3e4451',
                    fontFamily: 'monospace',
                  }}
                >
                  {language}
                </div>
                <SyntaxHighlighter
                  style={oneDark}
                  language={language}
                  PreTag="div"
                  customStyle={{ margin: 0, borderRadius: '0 0 8px 8px' }}
                  showLineNumbers
                >
                  {String(children).replace(/\n$/, '')}
                </SyntaxHighlighter>
              </div>
            );
          },

          // 图片：自适应宽度 + 加载
          img({ src, alt }) {
            return (
              <span style={{ display: 'block', margin: '12px 0', textAlign: 'center' }}>
                <img
                  src={src}
                  alt={alt || ''}
                  style={{
                    maxWidth: '100%',
                    borderRadius: 8,
                    maxHeight: 400,
                    objectFit: 'contain',
                  }}
                  loading="lazy"
                />
                {alt && (
                  <Text type="secondary" style={{ display: 'block', fontSize: 12, marginTop: 4 }}>
                    {alt}
                  </Text>
                )}
              </span>
            );
          },

          // 链接：新窗口打开
          a({ href, children }) {
            return (
              <a
                href={href}
                target="_blank"
                rel="noopener noreferrer"
                style={{ color: '#1677ff', textDecoration: 'underline' }}
              >
                {children}
              </a>
            );
          },

          // 标题样式
          h1: ({ children }) => (
            <h1 style={{ fontSize: 24, fontWeight: 700, margin: '16px 0 8px', color: '#1a1a2e' }}>
              {children}
            </h1>
          ),
          h2: ({ children }) => (
            <h2 style={{ fontSize: 20, fontWeight: 700, margin: '14px 0 6px', color: '#1a1a2e' }}>
              {children}
            </h2>
          ),
          h3: ({ children }) => (
            <h3 style={{ fontSize: 17, fontWeight: 600, margin: '12px 0 4px', color: '#1a1a2e' }}>
              {children}
            </h3>
          ),

          // 段落
          p: ({ children }) => (
            <p style={{ margin: '8px 0', lineHeight: 1.7, fontSize: 15, color: '#333' }}>
              {children}
            </p>
          ),

          // 列表
          ul: ({ children }) => (
            <ul style={{ paddingLeft: 24, margin: '8px 0', lineHeight: 2 }}>{children}</ul>
          ),
          ol: ({ children }) => (
            <ol style={{ paddingLeft: 24, margin: '8px 0', lineHeight: 2 }}>{children}</ol>
          ),

          // 引用
          blockquote: ({ children }) => (
            <blockquote
              style={{
                borderLeft: '3px solid #1677ff',
                margin: '12px 0',
                padding: '8px 16px',
                background: '#f0f5ff',
                borderRadius: '0 8px 8px 0',
                color: '#555',
              }}
            >
              {children}
            </blockquote>
          ),

          // 分割线
          hr: () => (
            <hr style={{ margin: '16px 0', border: 'none', borderTop: '1px solid #e8e8e8' }} />
          ),
        }}
      >
        {content}
      </ReactMarkdown>
    </div>
  );
};

export default MarkdownContent;
