import { useState, useEffect, useCallback } from 'react';
import { Typography, Row, Col, Divider, Empty, message } from 'antd';
import { useOutletContext } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { pathApi } from '../api/pathApi';
import PathTemplateSection from '../components/student/path/PathTemplateSection';
import AiPlanSection from '../components/student/path/AiPlanSection';
import CustomPathSection from '../components/student/path/CustomPathSection';
import PathListSection from '../components/student/path/PathListSection';

const { Title } = Typography;

/**
 * 🧭 学习路径 — 模板一键生成 / AI 共创 / 手动自定义 / 系统自动 四入口。
 * 生成后统一落入「我的路径」，支持节点编辑与状态流转。
 */
export default function LearningPath() {
  const { user } = useAuth();
  const { selectedCourseId, studentCourses } = useOutletContext();
  const studentId = user?.id;

  const [paths, setPaths] = useState([]);
  const [loadingPaths, setLoadingPaths] = useState(false);

  const loadPaths = useCallback(async () => {
    if (!studentId) return;
    setLoadingPaths(true);
    try {
      const list = await pathApi.getStudentPaths(studentId);
      setPaths(list || []);
    } catch {
      setPaths([]);
    }
    setLoadingPaths(false);
  }, [studentId]);

  useEffect(() => {
    loadPaths();
  }, [loadPaths, selectedCourseId]);

  const handleGenerated = (path) => {
    loadPaths();
    message.success(`路径「${path.name}」已生成`);
  };

  const currentCourse = studentCourses?.find(c => c.courseId === selectedCourseId);

  if (!selectedCourseId) {
    return (
      <div>
        <Title level={4} style={{ marginBottom: 16 }}>🧭 学习路径</Title>
        <Empty description="请先在左侧选择一门课程，再规划学习路径" />
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <Title level={4} style={{ margin: 0 }}>🧭 学习路径</Title>
        <span style={{ fontSize: 13, color: '#5f6368' }}>
          当前课程：<b>{currentCourse?.courseName || '未知课程'}</b>
        </span>
      </div>

      <Row gutter={[16, 16]}>
        {/* ① 推荐路径（模板） */}
        <Col span={24}>
          <PathTemplateSection
            courseId={selectedCourseId}
            studentId={studentId}
            onGenerated={handleGenerated}
          />
        </Col>

        {/* ② AI 共创 */}
        <Col xs={24} lg={14}>
          <AiPlanSection
            studentId={studentId}
            courseId={selectedCourseId}
            onGenerated={handleGenerated}
          />
        </Col>

        {/* ③ 手动自定义 */}
        <Col xs={24} lg={10}>
          <CustomPathSection
            courseId={selectedCourseId}
            studentId={studentId}
            onGenerated={handleGenerated}
          />
        </Col>
      </Row>

      <Divider style={{ margin: '16px 0' }} />

      {/* ④ 我的路径 */}
      <PathListSection
        paths={paths}
        loading={loadingPaths}
        studentId={studentId}
        onRefresh={loadPaths}
      />
    </div>
  );
}
