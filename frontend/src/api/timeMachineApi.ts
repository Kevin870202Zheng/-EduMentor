// ================================================================
// 成长时光机 API
// /api/time-machine/*
// ================================================================

import apiClient from './apiClient';

export const timeMachineApi = {
  /** 总览：成长档案 + 信件 + 当前学段 */
  async overview(studentId: string): Promise<any> {
    return apiClient.get('/time-machine/overview', { params: { studentId } });
  },

  /** 信件列表 */
  async listLetters(studentId: string): Promise<any> {
    return apiClient.get('/time-machine/letters', { params: { studentId } });
  },

  /** 创建信件（question 留空则 AI 生成；generateOnly=true 仅预览不落库） */
  async createLetter(request: {
    studentId: string;
    stage?: string;
    courseId?: string;
    direction?: string;
    question?: string;
    generateOnly?: boolean;
  }): Promise<any> {
    return apiClient.post('/time-machine/letters', request);
  },

  /** 回答信件 */
  async answerLetter(id: string, answer: string): Promise<any> {
    return apiClient.post(`/time-machine/letters/${id}/answer`, { answer });
  },

  /** 手动归档成长快照 */
  async archive(request: { studentId: string; stage?: string; courseId?: string }): Promise<any> {
    return apiClient.post('/time-machine/archive', request);
  },

  /** 学段学习报告（AI 生成） */
  async stageReport(studentId: string, stage?: string): Promise<any> {
    return apiClient.get('/time-machine/stage-report', { params: { studentId, stage } });
  },
};
