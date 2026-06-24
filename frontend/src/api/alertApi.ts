// ================================================================
// 预警系统模块 API
// /api/alerts — 预警查询、处理、统计
// ================================================================

import apiClient, { extractPaginatedData } from './apiClient';
import type {
  AlertDto,
  AlertHandleRequest,
  AlertStatisticsDto,
} from './types';

export const alertApi = {
  /**
   * 获取预警列表（分页、筛选）
   * GET /api/alerts
   */
  async getAlerts(
    page: number = 1,
    size: number = 20,
    level?: string,
    handled?: boolean,
  ): Promise<{
    items: AlertDto[];
    total: number;
    page: number;
    size: number;
  }> {
    const params: Record<string, string | number | boolean> = { page, size };
    if (level) params.level = level;
    if (handled !== undefined) params.handled = handled;
    const data = await apiClient.get<unknown, {
      items: AlertDto[];
      total: number;
      page: number;
      size: number;
      totalPages: number;
      hasMore: boolean;
    }>('/alerts', { params });
    return extractPaginatedData(data);
  },

  /**
   * 获取预警详情
   * GET /api/alerts/{id}
   */
  async getAlertById(id: string): Promise<AlertDto> {
    return apiClient.get<unknown, AlertDto>(`/alerts/${id}`);
  },

  /**
   * 获取学生的预警列表
   * GET /api/alerts/student/{studentId}
   */
  async getAlertsByStudent(studentId: string): Promise<AlertDto[]> {
    return apiClient.get<unknown, AlertDto[]>(`/alerts/student/${studentId}`);
  },

  /**
   * 获取未处理预警
   * GET /api/alerts/unresolved
   */
  async getUnresolvedAlerts(): Promise<AlertDto[]> {
    return apiClient.get<unknown, AlertDto[]>('/alerts/unresolved');
  },

  /**
   * 标记已读
   * PUT /api/alerts/{id}/read
   */
  async markAsRead(id: string): Promise<AlertDto> {
    return apiClient.put<unknown, AlertDto>(`/alerts/${id}/read`);
  },

  /**
   * 处理预警
   * PUT /api/alerts/{id}/handle
   */
  async handleAlert(
    id: string,
    request: AlertHandleRequest,
  ): Promise<AlertDto> {
    return apiClient.put<unknown, AlertDto>(
      `/alerts/${id}/handle`,
      request,
    );
  },

  /**
   * 获取预警统计
   * GET /api/alerts/statistics
   */
  async getAlertStatistics(): Promise<AlertStatisticsDto> {
    return apiClient.get<unknown, AlertStatisticsDto>('/alerts/statistics');
  },
};
