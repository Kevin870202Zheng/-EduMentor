// ================================================================
// 知识管理模块 API
// /api/knowledge/courses, /points, /relations, /graph
// ================================================================

import apiClient, { extractPaginatedData } from './apiClient';
import type {
  CourseDto,
  CourseCreateRequest,
  KnowledgePointDto,
  KnowledgeRelationDto,
  KnowledgeGraphDto,
  KnowledgePointDto as KnowledgePointTreeItem,
} from './types';

export const knowledgeApi = {
  // ── 课程管理 ──────────────────────────────────────────────

  /**
   * 创建课程
   * POST /api/knowledge/courses
   */
  async createCourse(request: CourseCreateRequest): Promise<CourseDto> {
    return apiClient.post<unknown, CourseDto>('/knowledge/courses', request);
  },

  /**
   * 更新课程
   * PUT /api/knowledge/courses/{id}
   */
  async updateCourse(
    id: string,
    request: Partial<CourseCreateRequest>,
  ): Promise<CourseDto> {
    return apiClient.put<unknown, CourseDto>(`/knowledge/courses/${id}`, request);
  },

  /**
   * 获取课程详情
   * GET /api/knowledge/courses/{id}
   */
  async getCourse(id: string): Promise<CourseDto> {
    return apiClient.get<unknown, CourseDto>(`/knowledge/courses/${id}`);
  },

  /**
   * 课程列表（分页）
   * GET /api/knowledge/courses
   */
  async listCourses(
    page: number = 1,
    size: number = 20,
  ): Promise<{
    items: CourseDto[];
    total: number;
    page: number;
    size: number;
  }> {
    const data = await apiClient.get<unknown, {
      items: CourseDto[];
      total: number;
      page: number;
      size: number;
      totalPages: number;
      hasMore: boolean;
    }>('/knowledge/courses', { params: { page, size } });
    return extractPaginatedData(data);
  },

  /**
   * 删除课程
   * DELETE /api/knowledge/courses/{id}
   */
  async deleteCourse(id: string): Promise<void> {
    return apiClient.delete<unknown, void>(`/knowledge/courses/${id}`);
  },

  /**
   * 发布课程
   * POST /api/knowledge/courses/{id}/publish
   */
  async publishCourse(id: string): Promise<CourseDto> {
    return apiClient.post<unknown, CourseDto>(`/knowledge/courses/${id}/publish`);
  },

  // ── 知识点管理 ────────────────────────────────────────────

  /**
   * 创建知识点
   * POST /api/knowledge/points
   */
  async createKnowledgePoint(request: {
    courseId: string;
    name: string;
    description?: string;
    difficulty?: number;
    orderIndex?: number;
    estimatedMinutes?: number;
    parentId?: string;
  }): Promise<KnowledgePointDto> {
    return apiClient.post<unknown, KnowledgePointDto>('/knowledge/points', request);
  },

  /**
   * 获取知识点详情
   * GET /api/knowledge/points/{id}
   */
  async getKnowledgePoint(id: string): Promise<KnowledgePointDto> {
    return apiClient.get<unknown, KnowledgePointDto>(`/knowledge/points/${id}`);
  },

  /**
   * 按课程查询知识点列表
   * GET /api/knowledge/points?courseId={courseId}
   */
  async listKnowledgePoints(courseId: string): Promise<KnowledgePointDto[]> {
    return apiClient.get<unknown, KnowledgePointDto[]>('/knowledge/points', {
      params: { courseId },
    });
  },

  /**
   * 知识点树形结构
   * GET /api/knowledge/points/tree?courseId={courseId}
   */
  async getKnowledgePointTree(
    courseId: string,
  ): Promise<KnowledgePointTreeItem[]> {
    return apiClient.get<unknown, KnowledgePointTreeItem[]>(
      '/knowledge/points/tree',
      { params: { courseId } },
    );
  },

  /**
   * 更新知识点
   * PUT /api/knowledge/points/{id}
   */
  async updateKnowledgePoint(
    id: string,
    request: Partial<{
      name: string;
      description: string;
      difficulty: number;
      orderIndex: number;
      estimatedMinutes: number;
      status: string;
    }>,
  ): Promise<KnowledgePointDto> {
    return apiClient.put<unknown, KnowledgePointDto>(
      `/knowledge/points/${id}`,
      request,
    );
  },

  /**
   * 删除知识点
   * DELETE /api/knowledge/points/{id}
   */
  async deleteKnowledgePoint(id: string): Promise<void> {
    return apiClient.delete<unknown, void>(`/knowledge/points/${id}`);
  },

  // ── 知识点关系管理 ────────────────────────────────────────

  /**
   * 创建知识点关系
   * POST /api/knowledge/relations
   */
  async createRelation(request: {
    sourcePointId: string;
    targetPointId: string;
    relationType: 'PREREQUISITE' | 'REINFORCEMENT' | 'RELATED';
    description?: string;
  }): Promise<KnowledgeRelationDto> {
    return apiClient.post<unknown, KnowledgeRelationDto>(
      '/knowledge/relations',
      request,
    );
  },

  /**
   * 删除知识点关系
   * DELETE /api/knowledge/relations/{id}
   */
  async deleteRelation(id: string): Promise<void> {
    return apiClient.delete<unknown, void>(`/knowledge/relations/${id}`);
  },

  /**
   * 获取指定知识点的关联关系
   * GET /api/knowledge/points/{id}/relations
   */
  async getRelationsForPoint(
    id: string,
  ): Promise<KnowledgeRelationDto[]> {
    return apiClient.get<unknown, KnowledgeRelationDto[]>(
      `/knowledge/points/${id}/relations`,
    );
  },

  // ── 知识图谱 ──────────────────────────────────────────────

  /**
   * 获取课程知识图谱
   * GET /api/knowledge/courses/{courseId}/graph
   */
  async getKnowledgeGraph(courseId: string): Promise<KnowledgeGraphDto> {
    return apiClient.get<unknown, KnowledgeGraphDto>(
      `/knowledge/courses/${courseId}/graph`,
    );
  },
};
