import React, { Suspense, lazy } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import { ConfigProvider, Spin } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { AuthProvider } from './context/AuthContext';
import ProtectedRoute from './components/common/ProtectedRoute';
import MainLayout from './components/common/MainLayout';

// ============ 路由级懒加载 ============
const Login = lazy(() => import('./pages/Login'));
const Register = lazy(() => import('./pages/Register'));
const StudentDashboard = lazy(() => import('./pages/StudentDashboard'));
const LearningPath = lazy(() => import('./pages/LearningPath'));
const QATutoring = lazy(() => import('./pages/QATutoring'));
const ErrorReview = lazy(() => import('./pages/ErrorReview'));
const StudentMoments = lazy(() => import('./pages/StudentMoments'));
const StudentCourses = lazy(() => import('./pages/StudentCourses'));
const StudentStageThemes = lazy(() => import('./pages/StudentStageThemes'));
const StudentProfileEdit = lazy(() => import('./pages/StudentProfileEdit'));
const StudentLearning = lazy(() => import('./pages/StudentLearning'));
const TimeMachine = lazy(() => import('./pages/TimeMachine'));
const TeacherDashboard = lazy(() => import('./pages/TeacherDashboard'));
const TeacherCourseManage = lazy(() => import('./pages/TeacherCourseManage'));
const TeacherCourseList = lazy(() => import('./pages/TeacherCourseList'));
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'));
const AdminTeachers = lazy(() => import('./pages/AdminTeachers'));
const AdminStudents = lazy(() => import('./pages/AdminStudents'));
const ClassroomPlayback = lazy(() => import('./pages/classroom/ClassroomPlayback'));
const TeacherClassrooms = lazy(() => import('./pages/classroom/TeacherClassrooms'));
const KnowledgePointPicker = lazy(() => import('./pages/classroom/KnowledgePointPicker'));
const TeacherCollabClassrooms = lazy(() => import('./pages/classroom/TeacherCollabClassrooms'));
const StudentCollabClassrooms = lazy(() => import('./pages/classroom/StudentCollabClassrooms'));
const CollabWorkbench = lazy(() => import('./pages/classroom/CollabWorkbench'));
const MootCourtRoom = lazy(() => import('./pages/classroom/MootCourtRoom'));
const MootCourtReport = lazy(() => import('./pages/classroom/MootCourtReport'));


function PageLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', flexDirection: 'column', gap: 16 }}>
      <Spin size="large" />
      <span style={{ color: '#999' }}>页面加载中...</span>
    </div>
  );
}

const theme = {
  token: {
    colorPrimary: '#1677ff',
    borderRadius: 8,
    fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
  },
};

function App() {
  return (
    <ConfigProvider locale={zhCN} theme={theme}>
      <AuthProvider>
        <Router>
          <Suspense fallback={<PageLoading />}>
            <Routes>
              {/* 公开路由 */}
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />

              {/* 根路径重定向 */}
              <Route path="/" element={<Navigate to="/student/dashboard" replace />} />

              {/* 学生路由 */}
              <Route
                path="/student"
                element={
                  <ProtectedRoute allowedRoles={['student']}>
                    <MainLayout role="student" />
                  </ProtectedRoute>
                }
              >
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<StudentDashboard />} />
                <Route path="learning-path" element={<LearningPath />} />
                <Route path="qa" element={<QATutoring />} />
                <Route path="error-review" element={<ErrorReview />} />
                <Route path="moments" element={<StudentMoments />} />
                <Route path="courses" element={<StudentCourses />} />
                <Route path="stages" element={<StudentStageThemes />} />
                <Route path="learning/:courseCode" element={<StudentLearning />} />
                <Route path="time-machine" element={<TimeMachine />} />
                {/* 旧路由兼容：智慧课堂 → 合作课堂；生成器入口并入课程学习 Tab（M3） */}
                <Route path="classrooms" element={<Navigate to="collab-classrooms" replace />} />
                <Route path="classroom-generator" element={<Navigate to="collab-classrooms" replace />} />
                <Route path="classroom-generator/select" element={<Navigate to="collab-classrooms" replace />} />
                <Route path="collab-classrooms" element={<StudentCollabClassrooms />} />
                <Route path="collab-classrooms/:id" element={<CollabWorkbench />} />
                <Route path="classroom/:classroomId" element={<ClassroomPlayback />} />
                <Route path="classroom/:classroomId/court" element={<MootCourtRoom />} />
                <Route path="classroom/:classroomId/court/report" element={<MootCourtReport />} />
                <Route path="profile" element={<StudentProfileEdit />} />
              </Route>

              {/* 教师路由 */}
              <Route
                path="/teacher"
                element={
                  <ProtectedRoute allowedRoles={['teacher']}>
                    <MainLayout role="teacher" />
                  </ProtectedRoute>
                }
              >
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<TeacherDashboard />} />
                <Route path="courses" element={<TeacherCourseList />} />
                <Route path="courses/:courseCode/manage" element={<TeacherCourseManage />} />
                <Route path="classrooms" element={<TeacherClassrooms />} />
                <Route path="classroom-generator" element={<Navigate to="collab-classrooms" replace />} />
                <Route path="classroom-generator/select" element={<KnowledgePointPicker />} />
                <Route path="collab-classrooms" element={<TeacherCollabClassrooms />} />
                <Route path="collab-classrooms/:id" element={<CollabWorkbench />} />
              </Route>

              {/* 管理员路由 */}
              <Route
                path="/admin"
                element={
                  <ProtectedRoute allowedRoles={['admin']}>
                    <MainLayout role="admin" />
                  </ProtectedRoute>
                }
              >
                <Route index element={<Navigate to="dashboard" replace />} />
                <Route path="dashboard" element={<AdminDashboard />} />
                <Route path="teachers" element={<AdminTeachers />} />
                <Route path="students" element={<AdminStudents />} />
              </Route>


              {/* 404 */}
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
          </Suspense>
        </Router>
      </AuthProvider>
    </ConfigProvider>
  );
}

export default App;
