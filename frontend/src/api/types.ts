// ================================================================
// EduMentor 前端 API 类型定义

// ─── 沉浸式课堂模块 ──────────────────────────────────

/** 教学动作类型 */
export type ActionType =
  | 'speech'
  | 'speech_with_highlight'
  | 'wb_draw_text'
  | 'wb_draw_diagram'
  | 'show_slide'
  | 'launch_widget'
  | 'widget_highlight'
  | 'widget_set_state'
  | 'widget_annotate'
  | 'widget_reveal'
  | 'quiz'
  | 'discussion'
  | 'scene_transition'
  | 'pause_for_thought'
  | 'code_demo';

/** 教学动作 DTO */
export interface ActionDTO {
  type: ActionType;
  text?: string;
  content?: string;
  position?: string;
  duration?: number;
  params?: Record<string, any>;
  // Quiz 专用
  question?: string;
  options?: string[];
  correctIndex?: number;
  explanation?: string;
  // 白板专用
  wbContent?: string;
  wbStyle?: string;
  // 幻灯片专用（show_slide）
  layoutId?: string;
  speech?: string;
  // 交互组件专用（launch_widget / widget_*）
  widgetKey?: string;
  intro?: string;
  target?: string;
  state?: Record<string, any>;
}

/** 幻灯片元素（PPT 卡片中的可视化元素） */
export interface SlideElement {
  id: string;
  kind: 'text' | 'shape' | 'line' | 'chart' | 'latex';
  x: number;
  y: number;
  w: number;
  h: number;
  // text
  content?: string;
  fontSize?: number;
  color?: string;
  align?: 'left' | 'center' | 'right';
  bold?: boolean;
  // shape
  shape?: 'rect' | 'circle' | 'round';
  fill?: string;
  radius?: number;
  label?: string;
  // line
  from?: [number, number];
  to?: [number, number];
  arrow?: boolean;
  dashed?: boolean;
  // chart
  chartType?: 'bar' | 'line' | 'pie' | 'radar';
  data?: { labels?: string[]; series?: number[][]; legends?: string[] };
  themeColors?: string[];
  // latex
  latex?: string;
}

/** 幻灯片布局（一页） */
export interface SlideLayout {
  layoutId: string;
  title?: string;
  elements: SlideElement[];
}

/** 交互组件配置 */
export interface WidgetConfig {
  variables?: Array<{ name: string; label: string; min?: number; max?: number; default?: number }>;
  targets?: string[];
}

/** 交互组件（interactive 场景） */
export interface WidgetPayload {
  subtype: 'simulation' | 'game' | 'explore';
  title?: string;
  config?: WidgetConfig;
  html: string;
}

/** 总结思维导图（review 场景） */
export interface SummaryMap {
  root: string;
  branches: Array<{ label: string; children?: string[]; color?: string }>;
}

/** 教学场景详情 */
export interface SceneDetailDto {
  id: string;
  classroomId: string;
  title: string;
  description?: string;
  sceneType: string;
  orderIndex: number;
  estimatedDurationSeconds?: number;
  actions: ActionDTO[];
  content?: Record<string, any>;
  createdAt?: string;
}

/** 课堂完整详情 */
export interface ClassroomDetailDto {
  id: string;
  courseId: string;
  knowledgePointId: string;
  title: string;
  description?: string;
  difficulty: number;
  totalDurationSeconds?: number;
  status: string;
  sceneCount: number;
  version: number;
  scenes: SceneDetailDto[];
  metadata?: Record<string, any>;
  createdAt?: string;
  updatedAt?: string;
}

/** 课堂进度 */
export interface ClassroomProgressDto {
  id: string;
  studentId: string;
  classroomId: string;
  status: string;
  currentSceneId?: string;
  currentActionOrder: number;
  scenesCompleted: number;
  totalScenes: number;
  quizCorrectCount: number;
  quizTotalCount: number;
  totalWatchSeconds: number;
  startedAt?: string;
  completedAt?: string;
  lastAccessedAt?: string;
}

/** Quiz 提交请求 */
export interface QuizSubmitRequest {
  sceneId: string;
  studentAnswer?: Record<string, any>;
  selectedIndex?: number;
  timeSpentSeconds?: number;
}

/** Quiz 提交响应 */
export interface QuizSubmitResponse {
  isCorrect: boolean;
  correctAnswer: string;
  explanation?: string;
  aiFeedback?: string;
  masteryDelta?: number;
  bktUpdate?: Record<string, any>;
  /** 关联的知识点名称 */
  knowledgePointName?: string;
}

/** 课后练习题 DTO */
export interface PracticeQuestionDto {
  id: string;
  questionContent: string;
  options: string[];
  correctIndex: number;
  explanation: string;
  knowledgePointId?: string;
  knowledgePointName?: string;
  relatedSceneId?: string;
  relatedSceneTitle?: string;
  difficulty: string;
}
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

/** 路径来源: AUTO(系统自动) / TEMPLATE(预设模板) / AI(AI 对话) / CUSTOM(手动自定义) */
export type PathSource = 'AUTO' | 'TEMPLATE' | 'AI' | 'CUSTOM';

export interface PathPlanRequest {
  studentId: string;
  courseId: string;
  name: string;
  description?: string;
  skipMastered?: boolean;
  dailyMinutes?: number;
  focusKpId?: string;
  examDaysLeft?: number;
  adaptStrategy?: 'REORDER' | 'SHORTEN' | 'FOCUS_WEAK' | 'EXPAND';
}

export interface PathAdaptRequest {
  pathId: string;
  adaptStrategy?: 'REORDER' | 'SHORTEN' | 'FOCUS_WEAK' | 'EXPAND';
  newKpIds?: string[];
}

export interface PathProgressUpdateRequest {
  pathId: string;
  nodeId: string;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';
  score?: number;
  timeSpentMinutes?: number;
}

export interface LearningPathNodeDto {
  id: string;
  learningPathId?: string;
  knowledgePointId: string;
  knowledgePointName: string;
  orderIndex: number;
  status: string;
  isRecommended?: boolean;
  estimatedMinutes: number;
  actualMinutes?: number;
  masteryThreshold?: number;
  aiReason?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface LearningPathDto {
  id: string;
  studentId: string;
  courseId?: string;
  createdBy?: string;
  name: string;
  description?: string;
  status: string;
  progress: number;
  totalNodes?: number;
  completedNodes?: number;
  dailyMinutes?: number;
  adaptStrategy?: string;
  source?: PathSource;
  templateId?: string;
  nodes?: LearningPathNodeDto[];
  createdAt?: string;
  updatedAt?: string;
}

/** 路径模板（推荐卡片） */
export interface PathTemplateDto {
  id: string;
  courseId: string;
  code: string;
  name: string;
  description?: string;
  icon?: string;
  totalMinutes?: number;
  nodeCount?: number;
  isVisible?: boolean;
  templateType: 'STATIC' | 'RULE_BY_STAGE';
  sortOrder?: number;
}

/** 模板节点 */
export interface PathTemplateNodeDto {
  id?: string;
  templateId?: string;
  knowledgePointId: string;
  knowledgePointName: string;
  orderIndex: number;
  estimatedMinutes: number;
  /** 动态模板（师范生备课）分课信息 */
  lessonIndex?: number;
  lessonTitle?: string;
}

/** 模板预览 */
export interface PathTemplatePreviewDto {
  templateId: string;
  code: string;
  name: string;
  description?: string;
  icon?: string;
  templateType: string;
  totalMinutes?: number;
  nodeCount: number;
  lessonCount?: number;
  nodes: PathTemplateNodeDto[];
  lessons?: Array<{
    lessonIndex: number;
    title: string;
    estimatedMinutes: number;
    nodes: PathTemplateNodeDto[];
  }>;
}

/** 从模板生成路径请求 */
export interface FromTemplateRequest {
  studentId: string;
  courseId: string;
  templateId: string;
  stage?: string;
  themeIds?: string[];
  skipMastered?: boolean;
}

/** 手动勾选创建路径请求（CUSTOM） */
export interface CustomPathRequest {
  studentId: string;
  courseId: string;
  name: string;
  description?: string;
  nodeIds: string[];
  dailyMinutes?: number;
}

/** 追加路径节点请求 */
export interface AddPathNodeRequest {
  knowledgePointId: string;
  orderIndex?: number;
}

/** 重排路径节点请求 */
export interface ReorderNodesRequest {
  nodeIds: string[];
}

/** AI 规划开启请求 */
export interface AiPlanStartRequest {
  studentId: string;
  courseId?: string;
  goal: string;
}

/** AI 规划对话请求 */
export interface AiPlanChatRequest {
  studentId: string;
  sessionId: string;
  message: string;
  generatePath?: boolean;
  courseId?: string;
}

/** AI 规划会话响应 */
export interface AiPlanResponse {
  sessionId: string;
  reply: string;
  path?: LearningPathDto;
  candidates?: PathTemplateNodeDto[];
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
