import { apiClient } from './apiClient';

/**
 * 学段协作课堂 API 封装（设计文档 §5.4）
 */
export const collabApi = {
  /** 我的协作项目（发起的 + 参与的） */
  listMine: () => apiClient.get('/collab-classrooms'),

  /** 项目详情（含任务/学生/故事） */
  getDetail: (id: string) => apiClient.get(`/collab-classrooms/${id}`),

  /** 创建项目（教师） */
  create: (data: { title: string; description?: string; courseId?: string; difficulty?: number }) =>
    apiClient.post('/collab-classrooms', data),

  /** 更新项目（教师） */
  update: (id: string, data: { title?: string; description?: string; courseId?: string; difficulty?: number }) =>
    apiClient.put(`/collab-classrooms/${id}`, data),

  /** 该学段可邀学生 */
  candidates: (id: string, stage: string) =>
    apiClient.get(`/collab-classrooms/${id}/candidates`, { params: { stage } }),

  /** 邀请学生 */
  invite: (id: string, roleType: string, userId: string) =>
    apiClient.post(`/collab-classrooms/${id}/invite`, { roleType, userId }),

  /** 学生提交任务 */
  submit: (id: string, taskId: string, content: any) =>
    apiClient.post(`/collab-classrooms/${id}/tasks/${taskId}/submit`, { content: JSON.stringify(content) }),

  /** 教师复核/修改任务 */
  review: (id: string, taskId: string, content: any) =>
    apiClient.post(`/collab-classrooms/${id}/tasks/${taskId}/review`, { content: JSON.stringify(content) }),

  /** 确认生成课堂（教师） */
  generate: (id: string) => apiClient.post(`/collab-classrooms/${id}/generate`),
};

/** 故事库 API */
export const storyApi = {
  list: () => apiClient.get('/stories'),
  get: (id: string) => apiClient.get(`/stories/${id}`),
};

/** 角色 → 学段/职责 展示配置 */
export const ROLE_CONFIG = {
  STORY_PICKER: { stage: 'PRIMARY', label: '🏫 小学 · 故事选择者', desc: '从故事库选定一个中华传统故事' },
  CHARACTER_DESIGNER: { stage: 'JUNIOR', label: '📖 初中 · 角色形象设计师', desc: '设计故事角色的形象与性格' },
  SCRIPT_WRITER: { stage: 'SENIOR', label: '🟢 高中 · 台词编剧', desc: '创作关键场景的角色台词' },
  LEGAL_MAPPER: { stage: 'UNIVERSITY', label: '🎓 大学 · 法律知识映射师', desc: '从课程知识库映射法律知识点' },
};

export const STAGE_LABEL = {
  PRIMARY: '🏫 小学',
  JUNIOR: '📖 初中',
  SENIOR: '🟢 高中',
  UNIVERSITY: '🎓 大学',
};
