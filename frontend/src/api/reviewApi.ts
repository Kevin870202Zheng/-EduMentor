// ================================================================
// 错题复盘模块 API
// /api/review/analysis, /review/plan, /review/errors, /review/records
// ================================================================

import apiClient, { extractPaginatedData } from './apiClient';
import type {
  ErrorAnalysisDto,
  ReviewPlanDto,
  ReviewRecordDto,
  ErrorRecordDto,
} from './types';

export const reviewApi = {
  /**
   * 错题分析
   * GET /api/review/analysis
   */
  async analyzeErrors(studentId?: string): Promise<ErrorAnalysisDto> {
    const params: Record<string, string> = {};
    if (studentId) params.studentId = studentId;
    return apiClient.get<unknown, ErrorAnalysisDto>('/review/analysis', { params });
  },

  /**
   * 获取复习计划
   * GET /api/review/plan
   */
  async getReviewPlan(studentId?: string): Promise<ReviewPlanDto> {
    const params: Record<string, string> = {};
    if (studentId) params.studentId = studentId;
    return apiClient.get<unknown, ReviewPlanDto>('/review/plan', { params });
  },

  /**
   * 完成复习
   * POST /api/review/records/{recordId}/complete
   */
  async completeReview(
    recordId: string,
    request: {
      masteryLevel: number;
      isCorrect: boolean;
      responseTimeRatio?: number;
      notes?: string;
    },
  ): Promise<ReviewRecordDto> {
    return apiClient.post<unknown, ReviewRecordDto>(
      `/review/records/${recordId}/complete`,
      request,
    );
  },

  /**
   * 获取错题列表（分页）
   * GET /api/review/errors
   */
  async getErrorRecords(
    studentId?: string,
    page: number = 1,
    size: number = 20,
  ): Promise<{
    items: ErrorRecordDto[];
    total: number;
    page: number;
    size: number;
  }> {
    const params: Record<string, string | number> = { page, size };
    if (studentId) params.studentId = studentId;
    const data = await apiClient.get<unknown, {
      items: ErrorRecordDto[];
      total: number;
      page: number;
      size: number;
      totalPages: number;
      hasMore: boolean;
    }>('/review/errors', { params });
    return extractPaginatedData(data);
  },

  /**
   * 记录错题
   * POST /api/review/errors
   */
  async recordError(request: {
    studentId?: string;
    questionId: string;
    knowledgePointId: string;
    answer: string;
    correctAnswer: string;
  }): Promise<ErrorRecordDto> {
    return apiClient.post<unknown, ErrorRecordDto>('/review/errors', request);
  },

  /**
   * 获取复习记录列表（分页）
   * GET /api/review/records
   */
  async getReviewRecords(
    studentId?: string,
    page: number = 1,
    size: number = 20,
  ): Promise<{
    items: ReviewRecordDto[];
    total: number;
    page: number;
    size: number;
  }> {
    const params: Record<string, string | number> = { page, size };
    if (studentId) params.studentId = studentId;
    const data = await apiClient.get<unknown, {
      items: ReviewRecordDto[];
      total: number;
      page: number;
      size: number;
      totalPages: number;
      hasMore: boolean;
    }>('/review/records', { params });
    return extractPaginatedData(data);
  },

  /**
   * 获取今日复习完成率
   * GET /api/review/today-completion
   */
  async getTodayCompletionRate(studentId?: string): Promise<number> {
    const params: Record<string, string> = {};
    if (studentId) params.studentId = studentId;
    return apiClient.get<unknown, number>('/review/today-completion', { params });
  },

  /**
   * 初始排程 — 为新知识点创建首次复习计划
   * POST /api/review/schedule
   */
  async scheduleInitialReview(request: {
    studentId?: string;
    knowledgePointId: string;
  }): Promise<ReviewRecordDto> {
    return apiClient.post<unknown, ReviewRecordDto>('/review/schedule', request);
  },
};
