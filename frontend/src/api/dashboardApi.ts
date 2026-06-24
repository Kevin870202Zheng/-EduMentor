// ================================================================
// 教师驾驶舱模块 API
// /api/dashboard/overview, /students, /weak-knowledge, /daily-brief, /suggestions
// ================================================================

import apiClient from './apiClient';
import type {
  ClassOverviewDto,
  StudentSummaryDto,
  WeakKnowledgeDto,
  DailyBriefDto,
  StrategySuggestionDto,
} from './types';

export const dashboardApi = {
  /**
   * 班级学情总览
   * GET /api/dashboard/overview
   */
  async getClassOverview(courseId?: string): Promise<ClassOverviewDto> {
    const params: Record<string, string> = {};
    if (courseId) params.courseId = courseId;
    return apiClient.get<unknown, ClassOverviewDto>('/dashboard/overview', { params });
  },

  /**
   * 学生列表（分页，带学情摘要）
   * GET /api/dashboard/students
   */
  async getStudentList(
    courseId?: string,
    page: number = 1,
    size: number = 20,
    sortBy: string = 'correctRate',
    sortDir: string = 'asc',
  ): Promise<{
    students: StudentSummaryDto[];
    total: number;
    page: number;
    size: number;
  }> {
    const params: Record<string, string | number> = {
      page,
      size,
      sortBy,
      sortDir,
    };
    if (courseId) params.courseId = courseId;
    return apiClient.get<unknown, {
      students: StudentSummaryDto[];
      total: number;
      page: number;
      size: number;
    }>('/dashboard/students', { params });
  },

  /**
   * 薄弱知识点列表
   * GET /api/dashboard/weak-knowledge
   */
  async getWeakKnowledgePoints(
    courseId?: string,
    threshold: number = 60,
    minStudents: number = 3,
  ): Promise<WeakKnowledgeDto[]> {
    const params: Record<string, string | number> = {
      threshold,
      minStudents,
    };
    if (courseId) params.courseId = courseId;
    return apiClient.get<unknown, WeakKnowledgeDto[]>('/dashboard/weak-knowledge', { params });
  },

  /**
   * 每日教学简报
   * GET /api/dashboard/daily-brief
   */
  async getDailyBrief(courseId?: string): Promise<DailyBriefDto> {
    const params: Record<string, string> = {};
    if (courseId) params.courseId = courseId;
    return apiClient.get<unknown, DailyBriefDto>('/dashboard/daily-brief', { params });
  },

  /**
   * 策略建议
   * GET /api/dashboard/suggestions
   */
  async getStrategySuggestions(
    courseId?: string,
    limit: number = 10,
  ): Promise<StrategySuggestionDto[]> {
    const params: Record<string, string | number> = { limit };
    if (courseId) params.courseId = courseId;
    return apiClient.get<unknown, StrategySuggestionDto[]>('/dashboard/suggestions', { params });
  },
};
