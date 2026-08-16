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
  PathTemplateDto,
  PathTemplatePreviewDto,
  FromTemplateRequest,
  CustomPathRequest,
  AddPathNodeRequest,
  ReorderNodesRequest,
  AiPlanStartRequest,
  AiPlanChatRequest,
  AiPlanResponse,
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

  /**
   * 获取课程可用路径模板列表
   * GET /api/paths/templates?courseId={courseId}
   */
  async getTemplates(courseId: string): Promise<PathTemplateDto[]> {
    return apiClient.get<unknown, PathTemplateDto[]>('/paths/templates', {
      params: { courseId },
    });
  },

  /**
   * 预览模板节点内容
   * GET /api/paths/templates/{id}/preview?stage=&themeIds=
   */
  async previewTemplate(
    templateId: string,
    params?: { stage?: string; themeIds?: string[] },
  ): Promise<PathTemplatePreviewDto> {
    return apiClient.get<unknown, PathTemplatePreviewDto>(
      `/paths/templates/${templateId}/preview`,
      { params },
    );
  },

  /**
   * 从模板生成学生路径
   * POST /api/paths/from-template
   */
  async createFromTemplate(request: FromTemplateRequest): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>('/paths/from-template', request);
  },

  /**
   * 手动勾选创建路径（CUSTOM）
   * POST /api/paths/custom
   */
  async createCustomPath(request: CustomPathRequest): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>('/paths/custom', request);
  },

  /**
   * 向路径追加节点
   * POST /api/paths/{pathId}/nodes
   */
  async addNode(pathId: string, request: AddPathNodeRequest): Promise<LearningPathDto> {
    return apiClient.post<unknown, LearningPathDto>(`/paths/${pathId}/nodes`, request);
  },

  /**
   * 移除路径节点
   * DELETE /api/paths/{pathId}/nodes/{nodeId}
   */
  async removeNode(pathId: string, nodeId: string): Promise<LearningPathDto> {
    return apiClient.delete<unknown, LearningPathDto>(`/paths/${pathId}/nodes/${nodeId}`);
  },

  /**
   * 重排路径节点顺序
   * PUT /api/paths/{pathId}/nodes/reorder
   */
  async reorderNodes(pathId: string, request: ReorderNodesRequest): Promise<LearningPathDto> {
    return apiClient.put<unknown, LearningPathDto>(`/paths/${pathId}/nodes/reorder`, request);
  },

  /**
   * 开启 AI 规划会话
   * POST /api/paths/ai-plan/start
   */
  async aiPlanStart(request: AiPlanStartRequest): Promise<AiPlanResponse> {
    return apiClient.post<unknown, AiPlanResponse>('/paths/ai-plan/start', request);
  },

  /**
   * AI 规划多轮对话（generatePath=true 时生成路径）
   * POST /api/paths/ai-plan/chat
   */
  async aiPlanChat(request: AiPlanChatRequest): Promise<AiPlanResponse> {
    return apiClient.post<unknown, AiPlanResponse>('/paths/ai-plan/chat', request);
  },
};
