// ================================================================
// EduMentor API 统一导出入口
//
// 使用方式:
//   import { authApi, diagnosisApi, knowledgeApi } from '@/api';
//   const { items } = await knowledgeApi.listCourses(1, 20);
// ================================================================

export { apiClient, tokenManager, extractPaginatedData } from './apiClient';
export type { ApiResponse, PaginatedResponse, ApiFieldError } from './types';
export { ApiError } from './types';

export { authApi } from './authApi';
export { diagnosisApi } from './diagnosisApi';
export { pathApi } from './pathApi';
export { qaApi } from './qaApi';
export { reviewApi } from './reviewApi';
export { alertApi } from './alertApi';
export { dashboardApi } from './dashboardApi';
export { knowledgeApi } from './knowledgeApi';
export { classroomApi } from './classroomApi';
export { courtApi } from './courtApi';

export { wsClient, WebSocketClient } from './websocketClient';
export type { WsClientMessage, WsServerMessage, WsMessageHandler } from './websocketClient';

// ── 所有类型导出 ────────────────────────────────────────────

export type {
  // 认证
  LoginRequest,
  RegisterRequest,
  TokenResponse,
  RefreshTokenRequest,
  UserDto,
  StudentProfileDto,

  // 课程/知识
  CourseDto,
  CourseCreateRequest,
  KnowledgePointDto,
  KnowledgeRelationDto,
  KnowledgeGraphDto,

  // 诊断
  DiagnosisResultDto,
  CognitiveProfileDto,
  RadarChartDataDto,
  HeatmapDataDto,
  DiagnosisOverviewDto,

  // 路径
  PathPlanRequest,
  PathAdaptRequest,
  PathProgressUpdateRequest,
  LearningPathNodeDto,
  LearningPathDto,

  // 答疑
  ChatRequest,
  ChatResponse,
  ChatHistoryDto,

  // 错题复盘
  ErrorAnalysisDto,
  ReviewPlanDto,
  ErrorRecordDto,
  ReviewRecordDto,

  // 预警
  AlertDto,
  AlertHandleRequest,
  AlertStatisticsDto,

  // 驾驶舱
  ClassOverviewDto,
  StudentSummaryDto,
  WeakKnowledgeDto,
  DailyBriefDto,
  StrategySuggestionDto,

  // 学习会话
  StudySessionDto,

  // 沉浸式课堂
  ActionType,
  ActionDTO,
  SceneDetailDto,
  ClassroomDetailDto,
  ClassroomProgressDto,
  QuizSubmitRequest,
  QuizSubmitResponse,
  PracticeQuestionDto,
} from './types';
