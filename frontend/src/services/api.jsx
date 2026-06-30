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

// ============ 模块十二：学习中心 ============
export const learningAPI = {
  getKpsByCourse: (courseId) => api.get(`/knowledge/courses/${courseId}/points`),
  getQuestionsByKp: (kpId) => api.get(`/v1/questions`, { params: { knowledgePointId: kpId } }),
  getGraph: (courseId) => api.get(`/knowledge/courses/${courseId}/graph`),
  getDiagnosisProfile: (studentId, courseId) =>
    api.get('/diagnosis/profile', { params: { studentId, courseId } }),
};

export default api;
