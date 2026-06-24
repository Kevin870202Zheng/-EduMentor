// ================================================================
// 学习路径规划模块 API
// /api/paths/plan, /paths/{id}, /paths/adapt, /paths/node/progress
// ================================================================

import apiClient from './apiClient';
import type {
  PathPlanRequest,
  PathAdaptRequest,
  PathProgressUpdateRequest,
  LearningPathDto,
  LearningPathNodeDto,
  KnowledgeGraphDto,
} from './types';

export const pathApi = {
  /**
   * 生成个性化学习路径
   * POST /api/paths/plan
   */
  async planPath(request: PathPlanRequest): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>('/paths/plan', request);
  },

  /**
   * 获取路径详情
   * GET /api/paths/{id}
   */
  async getPath(id: string): Promise<LearningPathDto> {
    return apiClient.get<unknown, LearningPathDto>(`/paths/${id}`);
  },

  /**
   * 获取学生的所有学习路径
   * GET /api/paths?studentId={studentId}
   */
  async getStudentPaths(studentId: string): Promise<LearningPathDto[]> {
    return apiClient.get<unknown, LearningPathDto[]>('/paths', {
      params: { studentId },
    });
  },

  /**
   * 获取当前活跃路径
   * GET /api/paths/active?studentId={studentId}
   */
  async getActivePath(studentId: string): Promise<LearningPathDto | null> {
    return apiClient.get<unknown, LearningPathDto | null>('/paths/active', {
      params: { studentId },
    });
  },

  /**
   * 激活路径
   * POST /api/paths/{id}/activate
   */
  async activatePath(id: string): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>(`/paths/${id}/activate`);
  },

  /**
   * 暂停路径
   * POST /api/paths/{id}/pause
   */
  async pausePath(id: string): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>(`/paths/${id}/pause`);
  },

  /**
   * 完成路径
   * POST /api/paths/{id}/complete
   */
  async completePath(id: string): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>(`/paths/${id}/complete`);
  },

  /**
   * 更新节点进度
   * PUT /api/paths/node/progress
   */
  async updateNodeProgress(
    request: PathProgressUpdateRequest,
  ): Promise<LearningPathDto> {
    return apiClient.put<unknown, LearningPathDto>(
      '/paths/node/progress',
      request,
    );
  },

  /**
   * 获取下一个节点
   * GET /api/paths/{pathId}/next-node
   */
  async getNextNode(pathId: string): Promise<LearningPathNodeDto | null> {
    return apiClient.get<unknown, LearningPathNodeDto | null>(
      `/paths/${pathId}/next-node`,
    );
  },

  /**
   * 智能适配路径
   * POST /api/paths/adapt
   */
  async adaptPath(request: PathAdaptRequest): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>('/paths/adapt', request);
  },

  /**
   * 获取课程知识图谱
   * GET /api/paths/knowledge-graph?courseId={courseId}&studentId={studentId}
   */
  async getKnowledgeGraph(
    courseId: string,
    studentId?: string,
  ): Promise<KnowledgeGraphDto> {
    const params: Record<string, string> = { courseId };
    if (studentId) params.studentId = studentId;
    return apiClient.get<unknown, KnowledgeGraphDto>(
      '/paths/knowledge-graph',
      { params },
    );
  },
};
