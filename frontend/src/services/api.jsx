import axios from 'axios';

const STRATEGY_BACKEND = { balanced: 'REORDER', shortest: 'SHORTEN', explore: 'FOCUS_WEAK' };

const API_BASE = import.meta.env.VITE_API_BASE || '/api';

const api = axios.create({
  baseURL: API_BASE,
  timeout: 600000,  // 10分钟（大文件章节拆分提取需要）
  headers: { 'Content-Type': 'application/json' },
});

// ============ 请求拦截器：注入 Token ============
api.interceptors.request.use(
  config => {
    const token = localStorage.getItem('edumentor_access_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  error => Promise.reject(error)
);

// ============ 响应拦截器：统一错误处理 + Token 刷新 ============
let isRefreshing = false;
let refreshSubscribers = [];

function onRefreshed(newToken) {
  refreshSubscribers.forEach(callback => callback(newToken));
  refreshSubscribers = [];
}

function addRefreshSubscriber(callback) {
  refreshSubscribers.push(callback);
}

api.interceptors.response.use(
  response => response.data,
  async error => {
    const originalRequest = error.config;
    const status = error.response?.status;

    // 401 未授权 - 尝试刷新令牌
    if (status === 401 && !originalRequest._retry) {
      const refreshToken = localStorage.getItem('edumentor_refresh_token');

      if (refreshToken && !isRefreshing) {
        originalRequest._retry = true;
        isRefreshing = true;

        try {
          const res = await axios.post(`${API_BASE}/auth/refresh`, {
            refreshToken: refreshToken,
          });
          const newToken = res.data?.data?.accessToken || res.data?.accessToken;

          if (newToken) {
            localStorage.setItem('edumentor_access_token', newToken);
            onRefreshed(newToken);
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            return api(originalRequest);
          }
        } catch (refreshError) {
          // 刷新失败 -> 清除登录状态
          localStorage.removeItem('edumentor_access_token');
          localStorage.removeItem('edumentor_refresh_token');
          localStorage.removeItem('edumentor_user');
          window.location.href = '/login';
          return Promise.reject({ message: '登录已过期，请重新登录', status: 401 });
        } finally {
          isRefreshing = false;
        }
      } else if (refreshToken && isRefreshing) {
        // 正在刷新中，排队等待
        return new Promise(resolve => {
          addRefreshSubscriber(newToken => {
            originalRequest.headers.Authorization = `Bearer ${newToken}`;
            resolve(api(originalRequest));
          });
        });
      } else {
        // 没有 refresh token，跳转登录
        localStorage.removeItem('edumentor_access_token');
        localStorage.removeItem('edumentor_user');
        window.location.href = '/login';
      }
    }

    const message = error.response?.data?.message || error.message || '请求失败';
    console.error('API Error:', message);
    return Promise.reject({ message, status });
  }
);

// ============ 模块零：认证 ============
export const authAPI = {
  login: (data) => api.post('/auth/login', data),
  register: (data) => api.post('/auth/register', data),
  refresh: (refreshToken) => api.post('/auth/refresh', { refreshToken }),
  getProfile: (userId) => api.get('/auth/profile', { params: { user_id: userId } }),
  updateProfile: (data) => api.put('/auth/profile', data),
};

// ============ 模块一：学情分析诊断 ============
// 后端 DiagnosisController — 全部使用 GET，参数为 query params
export const diagnosisAPI = {
  analyze: (params) => api.get('/diagnosis/analyze', { params: { studentId: params.student_id, courseId: params.course_id } }),
  getRadar: (studentId, courseId) =>
    api.get('/diagnosis/radar', { params: { studentId, courseId } }),
  getHeatmap: (studentId, courseId) =>
    api.get('/diagnosis/heatmap', { params: { studentId, courseId } }),
  getProfile: (studentId) =>
    api.get('/diagnosis/profile', { params: { studentId } }),
};

// ============ 模块二：学习路径规划 ============
export const pathAPI = {
  getPlan: (studentId, courseId, strategy = 'balanced') => {
    const adaptStrategy = STRATEGY_BACKEND[strategy] || 'REORDER';
    return api.post('/paths/plan', {
      studentId,
      courseId,
      name: '默认学习路径',
      skipMastered: strategy !== 'explore',
      adaptStrategy,
    });
  },
  getNext: (studentId, courseId) =>
    api.get('/path/next', { params: { student_id: studentId, course_id: courseId } }),
  getKnowledgeGraph: (courseId) =>
    api.get(`/path/knowledge-graph/${courseId}`),
  adapt: (pathId, strategy) =>
    api.post('/paths/adapt', { pathId, adaptStrategy: strategy }),
};

// ============ 模块三：智能答疑辅导 ============
export const qaAPI = {
  ask: (data) => api.post('/qa/ask', data),
  getLevels: () => api.get('/qa/levels'),

  /**
   * SSE 流式问答 — 通过 fetch + ReadableStream 消费后端 SSE 端点。
   *
   * @param {{question:string, courseId?:string, sessionId?:string}} data  请求参数
   * @param {(content:string)=>void}  onChunk  内容块回调（逐段追加）
   * @param {(result:object)=>void}   onDone   流完成回调
   * @param {(error:string)=>void}    onError  出错回调
   * @returns {AbortController}  用于取消请求
   */
  askStream: (data, onChunk, onDone, onError) => {
    const token = localStorage.getItem('edumentor_access_token');
    const params = new URLSearchParams({ question: data.question });
    if (data.courseId) params.append('courseId', data.courseId);
    if (data.sessionId) params.append('sessionId', data.sessionId);

    const controller = new AbortController();

    // SSE 流式请求直连后端，避免 Vite 代理缓冲
    const sseUrl = import.meta.env.DEV
      ? `http://localhost:8080/api/qa/ask/stream?${params}`
      : `${API_BASE}/qa/ask/stream?${params}`;

    (async () => {
      try {
        const response = await fetch(sseUrl, {
          headers: { Authorization: `Bearer ${token}` },
          signal: controller.signal,
        });

        // SSE 流式响应，无额外日志

        if (!response.ok) {
          onError(`请求失败 (${response.status})`);
          return;
        }

        if (!response.body) {
          console.error('SSE: response.body is null - ReadableStream not supported');
          onError('浏览器不支持流式读取');
          return;
        }

        const reader = response.body.getReader();
        const decoder = new TextDecoder();
        let buffer = '';
        let currentEvent = '';
        let chunkCount = 0;

        while (true) {
          const { done, value } = await reader.read();
          if (done) {
            // 流式传输完成
            break;
          }

          chunkCount++;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop(); // 不完整的行留到下次

          for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith('event:')) {
              currentEvent = trimmed.slice(6).trim();
            } else if (trimmed.startsWith('data:')) {
              // data: 后可能有空格（兼容不同 SSE 实现）
              const jsonStr = trimmed.startsWith('data: ') ? trimmed.slice(6) : trimmed.slice(5);
              try {
                const parsed = JSON.parse(jsonStr);
                console.log('SSE parsed event:', currentEvent, 'content type:', typeof parsed.content, 'len:', parsed.content?.length);
                if (currentEvent === 'done') {
                  onDone?.(parsed);
                } else if (currentEvent === 'error') {
                  onError?.(parsed.message || '未知错误');
                } else {
                  // chunk 事件 / 默认当作内容块
                  onChunk?.(parsed.content || '');
                }
              } catch (_) { /* SSE 解析异常（忽略） */ }
            }
            // 空行重置 event（SSE 规范：空行分隔事件）
            if (trimmed === '') currentEvent = '';
          }
        }
      } catch (err) {
        if (err.name !== 'AbortError') {
          onError?.(err.message || '网络异常');
        }
      }
    })();

    return controller;
  },
};

// ============ 模块四：错题复盘与反思 ============
export const errorAPI = {
  analyze: (data) => api.post('/review/error-analysis', data),
  getRecords: (studentId, params = {}) =>
    api.get('/reviews/errors', { params: { studentId, ...params } }),
  getDetail: (recordId) => api.get(`/reviews/errors/${recordId}`),
  submitReview: (recordId, data) => api.put(`/reviews/errors/${recordId}/review`, data),
  getSchedule: (studentId) => api.get(`/review/schedule/${studentId}`),
  getReflectionGuide: () => api.get('/review/reflection-guide'),
};

// ============ 模块五：教师驾驶舱 ============
export const dashboardAPI = {
  getSummary: (courseId) =>
    api.get('/dashboard/overview', { params: { courseId } }),
  getStudentList: (courseId, page = 1) =>
    api.get('/dashboard/students', { params: { courseId, page, size: 20, sortBy: 'correctRate', sortDir: 'asc' } }),
  getWeakKnowledge: (courseId) =>
    api.get('/dashboard/weak-knowledge', { params: { courseId } }),
  getDailyBrief: (courseId) =>
    api.get('/dashboard/daily-brief', { params: { courseId } }),
  getSuggestions: (courseId) =>
    api.get('/dashboard/strategy-suggestions', { params: { courseId } }),
};

// ============ 模块六：课程内容管理（教师端） ============
export const courseContentAPI = {
  // 获取课程信息（按 courseCode）
  getCourseInfo: (courseCode) => api.get(`/courses/${courseCode}`),

  // 获取课程资料列表
  listMaterials: (courseCode) => api.get(`/courses/${courseCode}/materials`),

  // 上传课程资料
  uploadMaterial: (courseCode, file) => {
    const formData = new FormData();
    formData.append('file', file);
    return api.post(`/courses/${courseCode}/materials`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
  },

  // AI 提取
  extractMaterial: (courseCode, materialId) =>
    api.post(`/courses/${courseCode}/materials/${materialId}/extract`),

  // 获取提取结果
  getExtractionResult: (courseCode, materialId) =>
    api.get(`/courses/${courseCode}/materials/${materialId}/extraction`),

  // 发布提取结果
  publishExtraction: (courseCode, materialId) =>
    api.post(`/courses/${courseCode}/materials/${materialId}/publish`),

  // 删除资料
  deleteMaterial: (courseCode, materialId) =>
    api.delete(`/courses/${courseCode}/materials/${materialId}`),
};

// ============ 模块七：课程管理（通用） ============
export const courseAPI = {
  list: (params) => api.get('/knowledge/courses', { params }),
  get: (id) => api.get(`/knowledge/courses/${id}`),
  create: (data) => api.post('/knowledge/courses', data),
  update: (id, data) => api.put(`/knowledge/courses/${id}`, data),
  delete: (id) => api.delete(`/knowledge/courses/${id}`),
  publish: (id, published) => api.put(`/knowledge/courses/${id}/publish`, null, { params: { published } }),
  listByTeacher: (teacherId) => api.get(`/knowledge/courses/teacher/${teacherId}`),
  getByCode: (courseCode) => api.get(`/courses/${courseCode}`),
};

// ============ 模块八：选课管理 ============
export const enrollmentAPI = {
  enroll: (data) => api.post('/enrollments', data),
  drop: (id) => api.delete(`/enrollments/${id}`),
  listByStudent: (studentId) => api.get(`/enrollments/student/${studentId}`),
  listDropped: (studentId) => api.get(`/enrollments/student/${studentId}/dropped`),
  countByCourse: (courseId) => api.get(`/enrollments/course/${courseId}/count`),
};

// ============ 模块九：学生信息 ============
export const studentAPI = {
  getProfile: (userId) => api.get(`/students/${userId}/profile`),
  updateProfile: (userId, data) => api.put(`/students/${userId}/profile`, data),
};

// ============ 模块十：课程教师分配 ============
export const courseTeacherAPI = {
  listTeachers: (courseId) => api.get(`/course-teachers/course/${courseId}`),
  assignTeacher: (data) => api.post('/course-teachers', data),
  removeTeacher: (id) => api.delete(`/course-teachers/${id}`),
  listAvailable: () => api.get('/course-teachers/available'),
};

// ============ 模块十一：答题提交 ============
export const answerAPI = {
  submit: (data) => api.post('/v1/answers', data),
};

// ============ 模块十二：知识点管理（教师用CRUD） ============
export const knowledgePointAPI = {
  get: (id) => api.get(`/knowledge/points/${id}`),
  create: (data) => api.post('/knowledge/points', data),
  update: (id, data) => api.put(`/knowledge/points/${id}`, data),
  delete: (id) => api.delete(`/knowledge/points/${id}`),
  listByCourse: (courseId) => api.get(`/knowledge/courses/${courseId}/points`),
  getTree: (courseId) => api.get(`/knowledge/courses/${courseId}/points/tree`),
  generateTree: (courseId, data) => api.post(`/knowledge/courses/${courseId}/points/tree/generate`, data),
  moveNode: (id, params) => api.put(`/knowledge/points/${id}/move`, null, { params }),
};

// ============ 模块十三：习题管理（教师用CRUD） ============
export const questionManageAPI = {
  get: (id) => api.get(`/v1/questions/${id}`),
  create: (data) => api.post('/v1/questions', data),
  update: (id, data) => api.put(`/v1/questions/${id}`, data),
  delete: (id) => api.delete(`/v1/questions/${id}`),
  listByKp: (kpId) => api.get('/v1/questions', { params: { knowledgePointId: kpId } }),
  generate: (data) => api.post('/v1/questions/generate', data),
};

// ============ 模块十三-A：题目分析（AI 智能分析） ============
export const questionAnalysisAPI = {
  analyze: (data) => api.post('/v1/questions/analyze', data),
};

// ============ 模块十四：学习中心 ============
export const learningAPI = {
  getKpsByCourse: (courseId) => api.get(`/knowledge/courses/${courseId}/points`),
  getQuestionsByKp: (kpId) => api.get(`/v1/questions`, { params: { knowledgePointId: kpId } }),
  getGraph: (courseId) => api.get(`/knowledge/courses/${courseId}/graph`),
  getDiagnosisProfile: (studentId, courseId) =>
    api.get('/diagnosis/profile', { params: { studentId, courseId } }),
};

// ============ 模块十五：管理员 ============
export const adminAPI = {
  getStats: () => api.get('/admin/stats'),
  listUsers: (role) => api.get('/admin/users', { params: { role } }),
  getUser: (id) => api.get(`/admin/users/${id}`),
  createTeacher: (data) => api.post('/admin/users/teacher', data),
  deleteUser: (id) => api.delete(`/admin/users/${id}`),
  toggleStatus: (id, active) => api.put(`/admin/users/${id}/status`, { active }),
  resetPassword: (id, newPassword) => api.put(`/admin/users/${id}/reset-password`, { newPassword }),
};

// export default api;

// ============ 模块十六：学生互出题考核 ============
export const peerQuizAPI = {
  create: (data) => api.post('/v1/peer-quizzes', data),
  getPending: () => api.get('/v1/peer-quizzes/pending'),
  getCompleted: () => api.get('/v1/peer-quizzes/completed'),
  getMyCreated: () => api.get('/v1/peer-quizzes/created'),
  getDetail: (quizId) => api.get(`/v1/peer-quizzes/${quizId}`),
  submit: (quizId) => api.post(`/v1/peer-quizzes/${quizId}/submit`),
  close: (quizId) => api.put(`/v1/peer-quizzes/${quizId}/close`),
  getResults: (quizId) => api.get(`/v1/peer-quizzes/${quizId}/results`),
  getQuestionResults: (quizId, questionId) =>
    api.get(`/v1/peer-quizzes/${quizId}/questions/${questionId}/results`),
  getCourseMates: (courseId) => api.get(`/v1/peer-quizzes/students`, { params: { courseId } }),
};

export default api;
