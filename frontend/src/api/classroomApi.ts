// ================================================================
// 沉浸式课堂模块 API
// /api/v2/classrooms
// ================================================================

import apiClient from './apiClient';
import type {
  ClassroomDetailDto,
  ClassroomProgressDto,
  QuizSubmitRequest,
  QuizSubmitResponse,
  PracticeQuestionDto,
} from './types';

export const classroomApi = {
  // ── 课堂生成 ──────────────────────────────────────────────

  /**
   * 基于知识点生成课堂（异步）
   * POST /api/v2/classrooms/generate
   */
  async generateClassroom(request: {
    courseCode: string;
    knowledgePointIds: string[];
    difficulty?: number;
    strategy?: string;
  }): Promise<{ jobId: string; status: string; message: string }> {
    return apiClient.post('/v2/classrooms/generate', request);
  },

  /**
   * 查询生成任务状态
   * GET /api/v2/classrooms/generate/{jobId}/status
   */
  async getGenerateStatus(jobId: string): Promise<{ jobId: string; status: string; message?: string }> {
    return apiClient.get(`/v2/classrooms/generate/${jobId}/status`);
  },

  // ── 课堂查询 ──────────────────────────────────────────────

  /**
   * 获取课堂详情（含场景和教学动作）
   * GET /api/v2/classrooms/{id}
   */
  async getClassroom(id: string): Promise<ClassroomDetailDto> {
    return apiClient.get(`/v2/classrooms/${id}`);
  },

  /**
   * 获取课程下的课堂列表
   * GET /api/v2/classrooms?courseId={courseId}
   */
  async listClassrooms(courseId: string): Promise<any[]> {
    return apiClient.get('/v2/classrooms', { params: { courseId } });
  },

  /**
   * 获取或生成课堂
   * POST /api/v2/classrooms/resolve?courseId=&knowledgePointId=&difficulty=
   */
  async resolveClassroom(
    courseId: string,
    knowledgePointId: string,
    difficulty: number = 3,
  ): Promise<any> {
    return apiClient.post('/v2/classrooms/resolve', null, {
      params: { courseId, knowledgePointId, difficulty },
    });
  },

  // ── 播放控制 ──────────────────────────────────────────────

  /**
   * 开始课堂学习
   * POST /api/v2/classrooms/{id}/start
   */
  async startClassroom(id: string): Promise<ClassroomProgressDto> {
    return apiClient.post(`/v2/classrooms/${id}/start`);
  },

  /**
   * 更新播放进度
   * POST /api/v2/classrooms/{id}/progress
   */
  async updateProgress(
    id: string,
    sceneId: string,
    actionOrder: number = 0,
  ): Promise<ClassroomProgressDto> {
    return apiClient.post(`/v2/classrooms/${id}/progress`, null, {
      params: { sceneId, actionOrder },
    });
  },

  /**
   * 获取播放进度
   * GET /api/v2/classrooms/{id}/progress
   */
  async getProgress(id: string): Promise<ClassroomProgressDto | null> {
    return apiClient.get(`/v2/classrooms/${id}/progress`);
  },

  /**
   * 完成课堂
   * POST /api/v2/classrooms/{id}/complete
   */
  async completeClassroom(id: string): Promise<ClassroomProgressDto> {
    return apiClient.post(`/v2/classrooms/${id}/complete`);
  },

  /**
   * 暂停课堂
   * POST /api/v2/classrooms/{id}/pause
   */
  async pauseClassroom(id: string): Promise<ClassroomProgressDto> {
    return apiClient.post(`/v2/classrooms/${id}/pause`);
  },

  // ── Quiz 提交 ─────────────────────────────────────────────

  /**
   * 提交课堂 Quiz 答案
   * POST /api/v2/classrooms/scenes/{sceneId}/quiz/submit
   */
  async submitQuiz(sceneId: string, request: QuizSubmitRequest): Promise<QuizSubmitResponse> {
    return apiClient.post(`/v2/classrooms/scenes/${sceneId}/quiz/submit`, request);
  },

  /**
   * 获取 Quiz 作答结果
   * GET /api/v2/classrooms/scenes/{sceneId}/quiz/result
   */
  async getQuizResult(sceneId: string): Promise<any[]> {
    return apiClient.get(`/v2/classrooms/scenes/${sceneId}/quiz/result`);
  },

  // ── 课后练习 ─────────────────────────────────────────────

  /**
   * 生成课后练习
   * POST /api/v2/classrooms/{id}/generate-practice
   */
  async generatePractice(
    id: string,
    questionCount: number = 5,
  ): Promise<PracticeQuestionDto[]> {
    return apiClient.post(`/v2/classrooms/${id}/generate-practice`, null, {
      params: { questionCount },
    });
  },

  /**
   * 获取课后练习题列表
   * GET /api/v2/classrooms/{id}/practice-questions
   */
  async getPracticeQuestions(id: string): Promise<PracticeQuestionDto[]> {
    return apiClient.get(`/v2/classrooms/${id}/practice-questions`);
  },
};
