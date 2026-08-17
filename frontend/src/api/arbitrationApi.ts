// ================================================================
// 仲裁人案例分析（模拟仲裁）模块 API
// /api/arbitrations  — 设计文档 learning-directory-arbitration-design.html §4.8
// 每个知识点两阶段：PRE 课前 / POST 课后；学生=仲裁人，AI=普通老百姓原被告
// ================================================================

import apiClient from './apiClient';

// ── 类型 ─────────────────────────────────────────────────────────

export interface ArbitrationCase {
  caseTitle: string;
  fact: string;
  disputes: string[];
  legalPoints: string[];
  plaintiffName: string;
  plaintiffClaim: string;
  defendantName: string;
  defendantDefense: string;
  difficulty: number;
}

export interface ArbitrationMessageDto {
  id: string;
  sessionId: string;
  role: 'CLERK' | 'PLAINTIFF_AI' | 'DEFENDANT_AI' | 'ARBITER_STUDENT';
  content: string;
  roundSeq: number;
  createdAt: string;
}

export interface ArbitrationAwardData {
  result?: 'SUPPORT' | 'REJECT' | 'PARTIAL';
  reason?: string;
  phase?: 'PRE' | 'POST';
  raw?: string;
}

export interface ArbitrationSessionDto {
  id: string;
  courseId: string;
  knowledgePointId: string;
  studentId: string;
  phase: 'PRE' | 'POST';
  status:
    | 'CASE_GENERATING'
    | 'OPENING'
    | 'HEARING'
    | 'AWARD_READY'
    | 'AWARDED'
    | 'REPORTED';
  case: ArbitrationCase | null;
  messages: ArbitrationMessageDto[];
  awardData: ArbitrationAwardData | null;
  stageIndex: number;
  createdAt: string;
  updatedAt: string;
}

export interface ArbitrationStatusDto {
  knowledgePointId: string;
  pre: string;
  post: string;
  preAwarded: boolean;
  postAwarded: boolean;
  reportReady: boolean;
  report: string | null;
}

export interface ArbitrationReportDto {
  case: ArbitrationCase | null;
  preAward: ArbitrationAwardData | null;
  postAward: ArbitrationAwardData | null;
  report: string | null;
}

// ── API ─────────────────────────────────────────────────────────

export const arbitrationApi = {
  /** 启动（或获取）仲裁会话：PRE 首次进入生成案件 + 开庭；POST 复用课前案件 */
  async start(kpId: string, phase: 'PRE' | 'POST'): Promise<ArbitrationSessionDto> {
    return apiClient.post(`/arbitrations/${kpId}/start`, null, {
      params: { phase },
    });
  },

  /** 查询仲裁会话（含案件 + 庭审消息 + 裁决） */
  async getSession(kpId: string, phase: 'PRE' | 'POST'): Promise<ArbitrationSessionDto> {
    return apiClient.get(`/arbitrations/${kpId}/session`, {
      params: { phase },
    });
  },

  /** 仲裁人（学生）发言 → AI（老百姓原/被告）回应 */
  async sendMessage(
    kpId: string,
    phase: 'PRE' | 'POST',
    content: string,
  ): Promise<ArbitrationSessionDto> {
    return apiClient.post(
      `/arbitrations/${kpId}/message`,
      { content },
      { params: { phase } },
    );
  },

  /** 进入下一仲裁环节 */
  async nextStage(kpId: string, phase: 'PRE' | 'POST'): Promise<ArbitrationSessionDto> {
    return apiClient.post(`/arbitrations/${kpId}/next-stage`, null, {
      params: { phase },
    });
  },

  /** 提交裁决书 */
  async submitAward(
    kpId: string,
    request: { phase: 'PRE' | 'POST'; result: string; reason: string },
  ): Promise<ArbitrationSessionDto> {
    return apiClient.post(`/arbitrations/${kpId}/award`, request);
  },

  /** 生成分析报告（需 PRE + POST 两份裁决齐全） */
  async generateReport(kpId: string): Promise<{ report: string }> {
    return apiClient.post(`/arbitrations/${kpId}/report`);
  },

  /** 获取分析报告 */
  async getReport(kpId: string): Promise<ArbitrationReportDto> {
    return apiClient.get(`/arbitrations/${kpId}/report`);
  },

  /** 查询仲裁状态（入口卡片三态） */
  async getStatus(kpId: string): Promise<ArbitrationStatusDto> {
    return apiClient.get(`/arbitrations/${kpId}/status`);
  },
};
