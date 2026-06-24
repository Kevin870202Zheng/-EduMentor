// ================================================================
// EduMentor 前端 API 类型定义
// ================================================================

// ─── 通用响应类型 ──────────────────────────────────────────

/** Spring Boot ApiResponse<T> 原始响应结构 */
export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  timestamp: string;
}

/** Spring Boot PaginatedResponse<T> 分页结构 */
export interface PaginatedResponse<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
  hasMore: boolean;
  timestamp: string;
}

/** Spring Boot ErrorResponse 字段级错误 */
export interface ApiFieldError {
  field: string;
  message: string;
  rejectedValue?: unknown;
}

/** ApiError 前端异常类 */
export class ApiError extends Error {
  constructor(
    public code: number,
    message: string,
    public errors?: ApiFieldError[],
  ) {
    super(message);
    this.name = 'ApiError';
  }

  get isUnauthorized(): boolean {
    return this.code === 401;
  }

  get isForbidden(): boolean {
    return this.code === 403;
  }

  get isNotFound(): boolean {
    return this.code === 404;
  }

  get isValidationError(): boolean {
    return this.code === 400;
  }

  get isExternalServiceError(): boolean {
    return this.code === 502;
  }
}

// ─── 认证模块 ──────────────────────────────────────────────

/** 登录请求 */
export interface LoginRequest {
  username: string;
  password: string;
}

/** 注册请求 */
export interface RegisterRequest {
  username: string;
  password: string;
  email: string;
  role?: 'STUDENT' | 'TEACHER';
  displayName?: string;
}

/** Token 响应 */
export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  tokenType: string;
}

/** 刷新 Token 请求 */
export interface RefreshTokenRequest {
  refreshToken: string;
}

// ─── 用户模块 ──────────────────────────────────────────────

export interface UserDto {
  id: string;
  username: string;
  email: string;
  role: string;
  displayName: string;
  avatar?: string;
  isActive: boolean;
  lastLoginAt?: string;
  createdAt: string;
}

export interface StudentProfileDto {
  id: string;
  userId: string;
  grade?: string;
  school?: string;
  learningStyle?: string;
  strengths?: string[];
  weaknesses?: string[];
  createdAt: string;
  updatedAt: string;
}

// ─── 课程/知识管理模块 ────────────────────────────────────

export interface CourseDto {
  id: string;
  title: string;
  description?: string;
  subject: string;
  grade?: string;
  coverImage?: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  knowledgePointCount: number;
  createdAt: string;
  updatedAt: string;
}

export interface CourseCreateRequest {
  title: string;
  description?: string;
  subject: string;
  grade?: string;
}

export interface KnowledgePointDto {
  id: string;
  courseId: string;
  name: string;
  description?: string;
  difficulty: number;
  orderIndex: number;
  estimatedMinutes: number;
  status: 'ACTIVE' | 'ARCHIVED';
  children?: KnowledgePointDto[];
}

export interface KnowledgeRelationDto {
  id: string;
  sourcePointId: string;
  targetPointId: string;
  relationType: 'PREREQUISITE' | 'REINFORCEMENT' | 'RELATED';
  description?: string;
}

export interface KnowledgeGraphDto {
  nodes: KnowledgePointDto[];
  edges: KnowledgeRelationDto[];
}

// ─── 学情诊断模块 ──────────────────────────────────────────

export interface DiagnosisResultDto {
  studentId: string;
  overallMastery: number;
  knowledgeMastery: Record<string, number>;
  weakPoints: string[];
  strongPoints: string[];
  recommendedActions: string[];
  analyzedAt: string;
}

export interface CognitiveProfileDto {
  memoryScore: number;
  comprehensionScore: number;
  applicationScore: number;
  analysisScore: number;
  totalQuestions: number;
  correctRate: number;
  lastActiveDate: string;
}

export interface RadarChartDataDto {
  dimensions: string[];
  values: number[];
  averageValues?: number[];
}

export interface HeatmapDataDto {
  knowledgePoints: string[];
  dates: string[];
  data: number[][];
}

export interface DiagnosisOverviewDto {
  studentId: string;
  totalQuestions: number;
  correctCount: number;
  correctRate: number;
  weakKnowledgePoints: string[];
  strongKnowledgePoints: string[];
  lastDiagnosisAt?: string;
}

// ─── 学习路径模块 ──────────────────────────────────────────

export interface PathPlanRequest {
  studentId: string;
  courseId: string;
  goal?: string;
  preferredDifficulty?: number;
  maxNodes?: number;
}

export interface PathAdaptRequest {
  pathId: string;
  studentId: string;
  reason?: string;
}

export interface PathProgressUpdateRequest {
  pathId: string;
  nodeId: string;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
  score?: number;
  timeSpentMinutes?: number;
}

export interface LearningPathNodeDto {
  id: string;
  knowledgePointId: string;
  knowledgePointName: string;
  orderIndex: number;
  status: string;
  estimatedMinutes: number;
  masteryThreshold: number;
  completedAt?: string;
}

export interface LearningPathDto {
  id: string;
  studentId: string;
  courseId: string;
  title: string;
  status: string;
  nodes: LearningPathNodeDto[];
  progress: number;
  createdAt: string;
  updatedAt: string;
}

// ─── 智能答疑模块 ──────────────────────────────────────────

export interface ChatRequest {
  content: string;
  sessionId?: string;
  messageType?: 'QUESTION' | 'ANSWER' | 'FEEDBACK' | 'OTHER';
  relatedKpId?: string;
  stream?: boolean;
}

export interface ChatResponse {
  sessionId: string;
  content: string;
  messageType: string;
  relatedKpId?: string;
  tokenUsage?: {
    promptTokens: number;
    completionTokens: number;
    totalTokens: number;
  };
  createdAt: string;
}

export interface ChatHistoryDto {
  id: string;
  sessionId: string;
  userId: string;
  role: 'USER' | 'ASSISTANT' | 'SYSTEM';
  content: string;
  messageType: string;
  relatedKpId?: string;
  llmModel?: string;
  tokensUsed?: number;
  createdAt: string;
}

// ─── 错题复盘模块 ──────────────────────────────────────────

export interface ErrorAnalysisDto {
  studentId: string;
  totalErrors: number;
  errorRate: number;
  errorByType: Record<string, number>;
  errorByKnowledgePoint: Record<string, number>;
  trending: Array<{ date: string; count: number }>;
  topMistakes: Array<{ knowledgePoint: string; count: number; rate: number }>;
  suggestions: string[];
}

export interface ReviewPlanDto {
  studentId: string;
  date: string;
  todayTasks: Array<{
    recordId: string;
    knowledgePointId: string;
    knowledgePointName: string;
    scheduledDate: string;
    masteryLevel: number;
    priority: 'HIGH' | 'MEDIUM' | 'LOW';
  }>;
  upcomingDays: Array<{
    date: string;
    taskCount: number;
  }>;
  completionRate: number;
}

export interface ErrorRecordDto {
  id: string;
  studentId: string;
  questionId: string;
  knowledgePointId: string;
  knowledgePointName: string;
  errorType: string;
  answer: string;
  correctAnswer: string;
  analysis?: string;
  createdAt: string;
}

export interface ReviewRecordDto {
  id: string;
  studentId: string;
  knowledgePointId: string;
  knowledgePointName: string;
  scheduledDate: string;
  masteryLevel: number;
  reviewCount: number;
  nextReviewDate: string;
  status: string;
  completedAt?: string;
}

// ─── 预警系统模块 ──────────────────────────────────────────

export interface AlertDto {
  id: string;
  userId: string;
  userName?: string;
  alertLevel: 'BLUE' | 'YELLOW' | 'RED';
  alertType: string;
  title: string;
  description: string;
  isRead: boolean;
  isHandled: boolean;
  handleAction?: string;
  handleNote?: string;
  handledBy?: string;
  handledAt?: string;
  createdAt: string;
}

export interface AlertHandleRequest {
  action: 'RESOLVE' | 'DISMISS' | 'ESCALATE';
  note?: string;
}

export interface AlertStatisticsDto {
  totalAlerts: number;
  unresolvedCount: number;
  blueCount: number;
  yellowCount: number;
  redCount: number;
  todayNewCount: number;
  byType: Record<string, number>;
}

// ─── 教师驾驶舱模块 ──────────────────────────────────────

export interface ClassOverviewDto {
  totalStudents: number;
  activeStudents: number;
  averageCorrectRate: number;
  totalAlerts: number;
  unresolvedAlerts: number;
  weakKnowledgePoints: Array<{
    knowledgePointId: string;
    knowledgePointName: string;
    correctRate: number;
    affectedStudents: number;
  }>;
  todayActivity: {
    totalSessions: number;
    totalQuestions: number;
    activeStudents: number;
  };
}

export interface StudentSummaryDto {
  id: string;
  name: string;
  totalAnswers: number;
  correctRate: number;
  weakPointsCount: number;
  alertCount: number;
  lastActiveDate: string;
  trend: 'UP' | 'DOWN' | 'STABLE';
}

export interface WeakKnowledgeDto {
  knowledgePointId: string;
  knowledgePointName: string;
  courseName: string;
  correctRate: number;
  totalAnswers: number;
  affectedStudents: number;
  trend: 'UP' | 'DOWN' | 'STABLE';
}

export interface DailyBriefDto {
  date: string;
  activeStudents: number;
  totalSessions: number;
  totalQuestions: number;
  averageCorrectRate: number;
  newAlerts: number;
  topWeakPoints: string[];
  fastestImprovingStudents: Array<{
    studentId: string;
    studentName: string;
    improvement: number;
  }>;
}

export interface StrategySuggestionDto {
  type: 'FOCUS' | 'REVIEW' | 'INTERVENTION' | 'REWARD';
  priority: 'HIGH' | 'MEDIUM' | 'LOW';
  title: string;
  description: string;
  relatedKnowledgePoints?: string[];
  relatedStudents?: string[];
}

// ─── 学习会话模块 ──────────────────────────────────────────

export interface StudySessionDto {
  id: string;
  studentId: string;
  learningPathNodeId?: string;
  status: string;
  startTime: string;
  endTime?: string;
  durationMinutes?: number;
  questionsAnswered: number;
  correctCount: number;
  focusScore?: number;
}
