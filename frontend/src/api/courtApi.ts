// ================================================================
// 模拟法庭模块 API
// /api/moot-courts
// ================================================================

import apiClient from './apiClient';

// ── 类型 ────────────────────────────────────────────────────────

export interface MootCourtCase {
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

export interface MootCourtMessageDto {
  id: string;
  role: 'CLERK' | 'PLAINTIFF_AI' | 'DEFENDANT_AI' | 'JUDGE_STUDENT';
  content: string;
  roundSeq: number;
  createdAt: string;
}

export interface MootCourtJudgmentData {
  result: 'SUPPORT' | 'REJECT' | 'PARTIAL' | string;
  reason: string;
  phase?: 'PRE' | 'POST';
}

export interface MootCourtSessionDto {
  id: string;
  classroomId: string;
  phase: 'PRE' | 'POST';
  status:
    | 'CASE_GENERATING'
    | 'OPENING'
    | 'HEARING'
    | 'JUDGMENT_READY'
    | 'JUDGED'
    | 'REPORTED';
  case: MootCourtCase | null;
  caseContent?: string | null;
  messages: MootCourtMessageDto[];
  judgment?: string | null;
  judgmentData?: MootCourtJudgmentData | null;
  report?: string | null;
  stageIndex: number;
}

export interface MootCourtReportDto {
  case: MootCourtCase | null;
  preJudgment: MootCourtJudgmentData | null;
  postJudgment: MootCourtJudgmentData | null;
  report: string | null;
}

// ── API ─────────────────────────────────────────────────────────

export const courtApi = {
  /** 启动（或获取）法庭会话：首次进入触发案例生成 + 开庭 */
  async start(classroomId: string, phase: 'PRE' | 'POST'): Promise<MootCourtSessionDto> {
    return apiClient.post(`/moot-courts/${classroomId}/start`, null, {
      params: { phase },
    });
  },

  /** 查询法庭会话（含案件 + 庭审消息） */
  async getSession(classroomId: string, phase: 'PRE' | 'POST'): Promise<MootCourtSessionDto> {
    return apiClient.get(`/moot-courts/${classroomId}/session`, {
      params: { phase },
    });
  },

  /** 法官（学生）发言 → AI 原/被告回应 */
  async sendMessage(
    classroomId: string,
    phase: 'PRE' | 'POST',
    content: string,
  ): Promise<MootCourtSessionDto> {
    return apiClient.post(
      `/moot-courts/${classroomId}/message`,
      { content },
      { params: { phase } },
    );
  },

  /** 进入下一庭审环节 */
  async nextStage(classroomId: string, phase: 'PRE' | 'POST'): Promise<MootCourtSessionDto> {
    return apiClient.post(`/moot-courts/${classroomId}/next-stage`, null, {
      params: { phase },
    });
  },

  /** 提交判决 */
  async submitJudgment(
    classroomId: string,
    request: { phase: 'PRE' | 'POST'; result: string; reason: string },
  ): Promise<MootCourtSessionDto> {
    return apiClient.post(`/moot-courts/${classroomId}/judgment`, request);
  },

  /** 生成分析报告（需 PRE + POST 两份判决齐全） */
  async generateReport(classroomId: string): Promise<{ report: string }> {
    return apiClient.post(`/moot-courts/${classroomId}/report`);
  },

  /** 获取分析报告 */
  async getReport(classroomId: string): Promise<MootCourtReportDto> {
    return apiClient.get(`/moot-courts/${classroomId}/report`);
  },
};

export default courtApi;
