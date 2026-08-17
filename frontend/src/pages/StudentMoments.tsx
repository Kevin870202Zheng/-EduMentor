import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Avatar,
  Button,
  Card,
  Divider,
  Dropdown,
  Empty,
  Image,
  Input,
  List,
  message,
  Modal,
  Popconfirm,
  Space,
  Spin,
  Tag,
  Typography,
  Upload,
} from 'antd';
import {
  DeleteOutlined,
  DownOutlined,
  HeartFilled,
  HeartOutlined,
  LoadingOutlined,
  MessageOutlined,
  MoreOutlined,
  PlusOutlined,
  RobotOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { useAuth } from '../context/AuthContext';
import { momentApi, MomentItem, MomentCommentItem } from '../api/momentApi';

const { TextArea } = Input;
const { Text, Paragraph } = Typography;

const STAGE_META: Record<string, { label: string; color: string }> = {
  PRIMARY: { label: '🏫 小学', color: 'green' },
  JUNIOR: { label: '📖 初中', color: 'blue' },
  SENIOR: { label: '🟢 高中', color: 'orange' },
  UNIVERSITY: { label: '🎓 大学', color: 'purple' },
};

/** 相对时间 */
function timeAgo(iso: string): string {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour} 小时前`;
  const day = Math.floor(hour / 24);
  if (day < 30) return `${day} 天前`;
  return new Date(iso).toLocaleDateString();
}

/**
 * 同学圈：学生朋友圈 + AI 法律风险提示
 * 设计文档: .youcoder/plans/moments-legal-review-design.html (v1.0)
 */
export default function StudentMoments() {
  const { user } = useAuth();
  const [moments, setMoments] = useState<MomentItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const pageRef = useRef(0);

  // 发布
  const [draft, setDraft] = useState('');
  const [publishing, setPublishing] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [images, setImages] = useState<string[]>([]);

  // 评论
  const [commentOpen, setCommentOpen] = useState<Record<string, boolean>>({});
  const [comments, setComments] = useState<Record<string, MomentCommentItem[]>>({});
  const [commentLoading, setCommentLoading] = useState<Record<string, boolean>>({});
  const [commentDrafts, setCommentDrafts] = useState<Record<string, string>>({});

  const loadMoments = useCallback(async (page = 0, append = false) => {
    if (page === 0) setLoading(true);
    else setLoadingMore(true);
    try {
      const data = await momentApi.list(page, 10);
      setMoments((prev) => (append ? [...prev, ...data.items] : data.items));
      setHasMore(data.hasMore);
      pageRef.current = page;
    } catch (e: any) {
      message.error(e?.message || '加载同学圈失败');
    } finally {
      setLoading(false);
      setLoadingMore(false);
    }
  }, []);

  useEffect(() => {
    loadMoments(0, false);
  }, [loadMoments]);

  // ── 发布 ──────────────────────────────────────────────────────
  const handlePublish = async () => {
    if (!draft.trim()) {
      message.warning('说点什么吧～');
      return;
    }
    setPublishing(true);
    try {
      const item = await momentApi.create(draft.trim(), images);
      setMoments((prev) => [item, ...prev]);
      setDraft('');
      setImages([]);
      message.success(item.aiReview?.involvesLegal
        ? '发布成功，AI 已识别到法律风险提示 ⚖️'
        : '发布成功');
    } catch (e: any) {
      message.error(e?.message || '发布失败');
    } finally {
      setPublishing(false);
    }
  };

  // 选择本地图片 → 上传 → 预览
  const handleUpload = async (file: File) => {
    if (images.length >= 9) {
      message.warning('最多上传 9 张图片');
      return false;
    }
    setUploading(true);
    try {
      const { url } = await momentApi.upload(file);
      setImages((prev) => [...prev, url]);
    } catch (e: any) {
      message.error(e?.message || '图片上传失败');
    } finally {
      setUploading(false);
    }
    return false; // 阻止 Upload 默认行为
  };

  // ── 点赞 ──────────────────────────────────────────────────────
  const handleLike = async (id: string) => {
    try {
      const { liked, likeCount } = await momentApi.toggleLike(id);
      setMoments((prev) => prev.map((m) => (m.id === id ? { ...m, likedByMe: liked, likeCount } : m)));
    } catch (e: any) {
      message.error(e?.message || '操作失败');
    }
  };

  // ── 评论 ──────────────────────────────────────────────────────
  const toggleComments = async (id: string) => {
    const open = !commentOpen[id];
    setCommentOpen((prev) => ({ ...prev, [id]: open }));
    if (open && !comments[id]) {
      setCommentLoading((prev) => ({ ...prev, [id]: true }));
      try {
        const list = await momentApi.listComments(id);
        setComments((prev) => ({ ...prev, [id]: list }));
      } catch (e: any) {
        message.error(e?.message || '加载评论失败');
      } finally {
        setCommentLoading((prev) => ({ ...prev, [id]: false }));
      }
    }
  };

  const handleComment = async (id: string) => {
    const content = (commentDrafts[id] || '').trim();
    if (!content) {
      message.warning('写点评论吧');
      return;
    }
    try {
      const comment = await momentApi.addComment(id, content);
      setComments((prev) => ({ ...prev, [id]: [...(prev[id] || []), comment] }));
      setCommentDrafts((prev) => ({ ...prev, [id]: '' }));
      setMoments((prev) => prev.map((m) => (m.id === id ? { ...m, commentCount: m.commentCount + 1 } : m)));
    } catch (e: any) {
      message.error(e?.message || '评论失败');
    }
  };

  // ── 删除 / 重新分析 ───────────────────────────────────────────
  const handleDelete = async (id: string) => {
    try {
      await momentApi.remove(id);
      setMoments((prev) => prev.filter((m) => m.id !== id));
      message.success('已删除');
    } catch (e: any) {
      message.error(e?.message || '删除失败');
    }
  };

  const handleReReview = async (id: string) => {
    try {
      const updated = await momentApi.reReview(id);
      setMoments((prev) => prev.map((m) => (m.id === id ? updated : m)));
      message.success(updated.aiReview?.involvesLegal ? '分析完成，发现法律风险 ⚖️' : '分析完成，未发现法律问题');
    } catch (e: any) {
      message.error(e?.message || '重新分析失败');
    }
  };

  const myId = user?.id;

  return (
    <div style={{ maxWidth: 680, margin: '0 auto' }}>
      {/* ── 发布区 ── */}
      <Card size="small" style={{ marginBottom: 16 }}>
        <TextArea
          rows={3}
          maxLength={500}
          placeholder="分享这一刻的想法……（AI 将自动检测是否涉及法律问题）"
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          showCount
        />
        {/* 图片预览 + 上传 */}
        <div style={{ display: 'flex', gap: 8, marginTop: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          {images.map((url) => (
            <div key={url} style={{ position: 'relative' }}>
              <Image src={url} width={64} height={64} style={{ borderRadius: 6, objectFit: 'cover' }} />
              <Button
                size="small"
                type="text"
                danger
                icon={<DeleteOutlined />}
                style={{ position: 'absolute', top: -6, right: -6, background: '#fff' }}
                onClick={() => setImages((prev) => prev.filter((u) => u !== url))}
              />
            </div>
          ))}
          {images.length < 9 && (
            <Upload accept="image/*" showUploadList={false} beforeUpload={handleUpload}>
              <Button icon={uploading ? <LoadingOutlined /> : <PlusOutlined />}>
                {uploading ? '上传中' : '图片'}
              </Button>
            </Upload>
          )}
          <div style={{ marginLeft: 'auto', display: 'flex', alignItems: 'center', gap: 8 }}>
            <Text type="secondary" style={{ fontSize: 12 }}>{draft.length}/500</Text>
            <Button type="primary" loading={publishing} onClick={handlePublish}>
              发布
            </Button>
          </div>
        </div>
      </Card>

      {/* ── 动态流 ── */}
      {loading ? (
        <div style={{ textAlign: 'center', padding: 48 }}>
          <Spin tip="加载同学圈..." />
        </div>
      ) : moments.length === 0 ? (
        <Empty description="还没有动态，来发布第一条吧！" style={{ padding: 48 }} />
      ) : (
        <List
          dataSource={moments}
          loadMore={
            hasMore ? (
              <div style={{ textAlign: 'center', margin: 16 }}>
                <Button onClick={() => loadMoments(pageRef.current + 1, true)} loading={loadingMore}>
                  加载更多
                </Button>
              </div>
            ) : (
              <Divider plain style={{ color: '#bbb', fontSize: 12 }}>已经到底啦</Divider>
            )
          }
          renderItem={(m) => (
            <Card
              size="small"
              style={{ marginBottom: 12 }}
              key={m.id}
            >
              {/* 头部：作者 */}
              <div style={{ display: 'flex', alignItems: 'flex-start', gap: 10 }}>
                <Avatar size={40} src={m.author?.avatarUrl || undefined} style={{ background: '#1677ff', flexShrink: 0 }}>
                  {(m.author?.displayName || '?').charAt(0)}
                </Avatar>
                <div style={{ flex: 1 }}>
                  <Space size={8}>
                    <Text strong>{m.author?.displayName || '同学'}</Text>
                    {m.author?.stage && STAGE_META[m.author.stage] && (
                      <Tag color={STAGE_META[m.author.stage].color} style={{ fontSize: 11, lineHeight: '16px' }}>
                        {STAGE_META[m.author.stage].label}
                      </Tag>
                    )}
                  </Space>
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>{timeAgo(m.createdAt)}</Text>
                  </div>
                </div>
                {m.authorId === myId && (
                  <Dropdown
                    menu={{
                      items: [
                        { key: 'review', label: '🔍 重新分析', onClick: () => handleReReview(m.id) },
                        {
                          key: 'delete',
                          label: '🗑️ 删除',
                          danger: true,
                          onClick: () => {
                            ModalConfirmDelete(m.id, handleDelete);
                          },
                        },
                      ],
                    }}
                  >
                    <Button size="small" type="text" icon={<MoreOutlined />} />
                  </Dropdown>
                )}
              </div>

              {/* 正文 */}
              <Paragraph style={{ marginTop: 10, marginBottom: m.images?.length ? 10 : 0, whiteSpace: 'pre-wrap' }}>
                {m.content}
              </Paragraph>

              {/* 图片 */}
              {m.images?.length > 0 && (
                <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: 10 }}>
                  <Image.PreviewGroup>
                    {m.images.map((url) => (
                      <Image key={url} src={url} width={96} height={96} style={{ borderRadius: 6, objectFit: 'cover' }} />
                    ))}
                  </Image.PreviewGroup>
                </div>
              )}

              {/* ⚖️ AI 法律风险提示卡 */}
              {m.aiReview?.involvesLegal && (
                <div
                  style={{
                    background: 'linear-gradient(135deg,#f9f0ff,#f4f8ff)',
                    border: '1px solid #d3adf7',
                    borderRadius: 8,
                    padding: '12px 14px',
                    marginBottom: 10,
                  }}
                >
                  <Space size={6} style={{ marginBottom: 6 }}>
                    <RobotOutlined style={{ color: '#722ed1' }} />
                    <Text strong style={{ fontSize: 13, color: '#722ed1' }}>
                      AI 法律风险提示
                    </Text>
                    {m.aiReview.confidence === 'low' && (
                      <Tag style={{ fontSize: 10, lineHeight: '14px' }} color="default">仅供参考</Tag>
                    )}
                  </Space>
                  <div style={{ marginBottom: 4 }}>
                    <Tag color="purple" style={{ fontSize: 12 }}>{m.aiReview.category || '法律问题'}</Tag>
                    <Tag color={m.aiReview.legalNature?.includes('刑事') ? 'red' : m.aiReview.legalNature?.includes('行政') ? 'orange' : 'blue'} style={{ fontSize: 12 }}>
                      定性：{m.aiReview.legalNature || '涉法行为'}
                    </Tag>
                  </div>
                  {m.aiReview.legalBasis && (
                    <Paragraph style={{ margin: '4px 0', fontSize: 13, color: '#4a3562' }}>
                      <Text strong style={{ fontSize: 13 }}>依据：</Text>
                      {m.aiReview.legalBasis}
                    </Paragraph>
                  )}
                  {m.aiReview.riskTips && (
                    <Paragraph style={{ margin: '4px 0', fontSize: 13, color: '#873800' }}>
                      <Text strong style={{ fontSize: 13 }}>风险：</Text>
                      {m.aiReview.riskTips}
                    </Paragraph>
                  )}
                  {m.aiReview.suggestions?.length > 0 && (
                    <div style={{ fontSize: 13, marginTop: 4 }}>
                      <Text strong style={{ fontSize: 13 }}>建议：</Text>
                      <ol style={{ margin: '4px 0 0 20px', padding: 0 }}>
                        {m.aiReview.suggestions.map((s, i) => (
                          <li key={i} style={{ color: '#4a3562' }}>{s}</li>
                        ))}
                      </ol>
                    </div>
                  )}
                </div>
              )}

              {/* 操作栏 */}
              <div style={{ display: 'flex', gap: 16, borderTop: '1px solid #f0f0f0', paddingTop: 8 }}>
                <Button
                  type="text"
                  size="small"
                  icon={m.likedByMe ? <HeartFilled style={{ color: '#ff4d4f' }} /> : <HeartOutlined />}
                  onClick={() => handleLike(m.id)}
                >
                  {m.likeCount || ''} 点赞
                </Button>
                <Button
                  type="text"
                  size="small"
                  icon={<MessageOutlined />}
                  onClick={() => toggleComments(m.id)}
                >
                  {m.commentCount || ''} 评论
                </Button>
              </div>

              {/* 评论区 */}
              {commentOpen[m.id] && (
                <div style={{ marginTop: 8, background: '#fafafa', borderRadius: 8, padding: 10 }}>
                  {commentLoading[m.id] ? (
                    <div style={{ textAlign: 'center', padding: 8 }}><Spin size="small" /></div>
                  ) : (
                    (comments[m.id] || []).map((c) => (
                      <div key={c.id} style={{ marginBottom: 6, fontSize: 13 }}>
                        <Text strong style={{ fontSize: 13 }}>
                          {c.author?.displayName || '同学'}
                          {c.author?.stage && STAGE_META[c.author.stage] ? `（${STAGE_META[c.author.stage].label}）` : ''}
                          ：
                        </Text>
                        <Text style={{ fontSize: 13 }}>{c.content}</Text>
                      </div>
                    ))
                  )}
                  {comments[m.id]?.length === 0 && (
                    <Text type="secondary" style={{ fontSize: 12 }}>暂无评论，来抢沙发～</Text>
                  )}
                  <Space.Compact style={{ width: '100%', marginTop: 8 }}>
                    <Input
                      size="small"
                      placeholder="写评论…"
                      maxLength={200}
                      value={commentDrafts[m.id] || ''}
                      onChange={(e) => setCommentDrafts((prev) => ({ ...prev, [m.id]: e.target.value }))}
                      onPressEnter={() => handleComment(m.id)}
                    />
                    <Button size="small" type="primary" icon={<SendOutlined />} onClick={() => handleComment(m.id)}>
                      发送
                    </Button>
                  </Space.Compact>
                </div>
              )}
            </Card>
          )}
        />
      )}
    </div>
  );
}

/** 删除确认（antd Modal.confirm 简单封装） */
function ModalConfirmDelete(id: string, onDelete: (id: string) => void) {
  Modal.confirm({
    title: '删除这条动态？',
    content: '删除后不可恢复',
    okText: '删除',
    okButtonProps: { danger: true },
    cancelText: '取消',
    onOk: () => onDelete(id),
  });
}
