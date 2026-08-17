// ================================================================
// 同学圈（学生朋友圈 + AI 法律风险提示）模块 API
// /api/moments — 设计文档 moments-legal-review-design.html §6
// 发布动态时同步 AI 法律检测；支持本地图片上传、点赞、评论
// ================================================================

import apiClient from './apiClient';

// ── 类型 ─────────────────────────────────────────────────────────

export interface LegalReviewResult {
  involvesLegal: boolean;
  category?: string;
  legalNature?: string;
  legalBasis?: string;
  riskTips?: string;
  suggestions?: string[];
  confidence?: 'high' | 'medium' | 'low';
}

export interface MomentAuthor {
  id: string;
  displayName: string;
  avatarUrl?: string | null;
  stage?: 'PRIMARY' | 'JUNIOR' | 'SENIOR' | 'UNIVERSITY' | null;
}

export interface MomentItem {
  id: string;
  authorId: string;
  content: string;
  images: string[];
  aiReview: LegalReviewResult | null;
  likeCount: number;
  commentCount: number;
  likedByMe: boolean;
  createdAt: string;
  author: MomentAuthor;
}

export interface MomentCommentItem {
  id: string;
  momentId: string;
  content: string;
  createdAt: string;
  author: MomentAuthor;
}

export interface MomentPage {
  items: MomentItem[];
  page: number;
  size: number;
  hasMore: boolean;
}

// ── API ─────────────────────────────────────────────────────────

export const momentApi = {
  /** 发布动态（同步 AI 法律检测） */
  async create(content: string, images?: string[]): Promise<MomentItem> {
    return apiClient.post('/moments', { content, images: images || [] });
  },

  /** 动态流（分页） */
  async list(page = 0, size = 10): Promise<MomentPage> {
    return apiClient.get('/moments', { params: { page, size } });
  },

  /** 删除动态（仅作者） */
  async remove(id: string): Promise<void> {
    return apiClient.delete(`/moments/${id}`);
  },

  /** 上传图片（返回 URL） */
  async upload(file: File): Promise<{ url: string }> {
    const formData = new FormData();
    formData.append('file', file);
    return apiClient.post('/moments/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  /** 点赞/取消点赞 */
  async toggleLike(id: string): Promise<{ liked: boolean; likeCount: number }> {
    return apiClient.post(`/moments/${id}/like`);
  },

  /** 评论列表 */
  async listComments(id: string): Promise<MomentCommentItem[]> {
    return apiClient.get(`/moments/${id}/comments`);
  },

  /** 发表评论 */
  async addComment(id: string, content: string): Promise<MomentCommentItem> {
    return apiClient.post(`/moments/${id}/comments`, { content });
  },

  /** 重新分析（AI 补检，仅作者） */
  async reReview(id: string): Promise<MomentItem> {
    return apiClient.post(`/moments/${id}/review`);
  },
};
