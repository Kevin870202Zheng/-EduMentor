import { useState, useCallback, useEffect } from 'react';
import { useNavigate, useOutletContext, useSearchParams } from 'react-router-dom';
import { Typography, message, Divider, Spin } from 'antd';
import StageSelector from '../components/stages/StageSelector';
import ThemeGrid from '../components/stages/ThemeGrid';
import KnowledgeLadder from '../components/stages/KnowledgeLadder';
import { courseAPI, studentAPI } from '../services/api';
import { useAuth } from '../context/AuthContext';

const { Title, Text } = Typography;

/**
 * 学段主题学习视图（PRD v4.0 §11）
 *
 * 「学段 × 主题」双维度导航：StageSelector → ThemeGrid → KnowledgeLadder。
 * 支持 URL 参数 ?stage=xxx&themeId=xxx 便于分享与刷新保持。
 * 入口：/student/courses 视图切换 或 独立路由 /student/stages。
 */
export default function StudentStageThemes() {
  const [searchParams, setSearchParams] = useSearchParams();
  const navigate = useNavigate();
  const { setSelectedCourseId } = useOutletContext() || {};
  const { user } = useAuth();

  const stage = searchParams.get('stage') || '';
  const themeId = searchParams.get('themeId') || null;
  const [selectedTheme, setSelectedTheme] = useState(null);
  // 主题列表（ThemeGrid 加载完成后同步，用于 URL 直达时解析主题名）
  const [themes, setThemes] = useState([]);
  const currentTheme = themes.find((t) => t.id === themeId) || selectedTheme;

  // 默认学段是否已确定（URL 有 stage，或已从学生档案拉取到学段身份）
  // 防止 StageSelector 在档案返回前抢先选中第一个学段（竞态）
  const [stageResolved, setStageResolved] = useState(!!stage);

  // 首次进入：URL 未指定学段时，默认选中学生的学段身份（PRD v4.0 §19）
  useEffect(() => {
    if (stage || !user?.id) {
      setStageResolved(true);
      return;
    }
    let mounted = true;
    studentAPI
      .getProfile(user.id)
      .then((res) => {
        if (!mounted) return;
        const profile = res?.data || res || {};
        if (profile.stage) {
          setSearchParams({ stage: profile.stage }, { replace: true });
        }
      })
      .catch(() => {
        // 拉取失败则交给 StageSelector 自动选第一个
      })
      .finally(() => {
        if (mounted) setStageResolved(true);
      });
    return () => { mounted = false; };
  }, [stage, user?.id, setSearchParams]);

  // 学段切换：重置主题
  const handleStageChange = useCallback((code) => {
    setSelectedTheme(null);
    setSearchParams({ stage: code }, { replace: true });
  }, [setSearchParams]);

  // 主题选中/取消
  const handleSelectTheme = useCallback((theme) => {
    setSelectedTheme(theme);
    const params = { stage };
    if (theme) params.themeId = theme.id;
    setSearchParams(params, { replace: true });
  }, [stage, setSearchParams]);

  // 进入学习：通过 courseId 拿 courseCode 后跳转学习页
  const handleEnterLearning = async (kp) => {
    if (!kp?.courseId) {
      message.info('该知识点暂未关联课程，无法进入学习');
      return;
    }
    try {
      const res = await courseAPI.get(kp.courseId);
      const course = res?.data || res;
      if (!course?.courseCode) {
        message.info('该知识点关联的课程尚未发布，无法进入学习');
        return;
      }
      if (setSelectedCourseId) setSelectedCourseId(course.id);
      navigate(`/student/learning/${course.courseCode}?kpId=${kp.id}`);
    } catch (e) {
      message.error(e?.message || '获取课程信息失败');
    }
  };

  return (
    <div>
      <div style={{ marginBottom: 8 }}>
        <Title level={4} style={{ margin: 0 }}>🧭 学段主题学习</Title>
        <Text type="secondary">按「学段 × 主题」浏览法律知识阶梯，同一主题在不同学段由浅入深</Text>
      </div>

      <div style={{ marginTop: 16 }}>
        {stageResolved ? (
          <StageSelector value={stage} onChange={handleStageChange} />
        ) : (
          <Spin size="small" style={{ display: 'block', margin: '8px 0' }} />
        )}
      </div>

      <ThemeGrid stage={stage} selectedThemeId={themeId} onSelectTheme={handleSelectTheme} onThemesLoaded={setThemes} />

      {stage && (themeId || selectedTheme) && (
        <>
          <Divider style={{ margin: '16px 0' }} />
          <Title level={5} style={{ marginBottom: 4 }}>
            🪜 知识阶梯：{currentTheme?.name || '主题'}
            {stage && <Text type="secondary" style={{ fontSize: 13, marginLeft: 8 }}>当前学段 · 按深度分层</Text>}
          </Title>
          <KnowledgeLadder
            themeId={themeId || selectedTheme?.id}
            stage={stage}
            onEnterLearning={handleEnterLearning}
          />
        </>
      )}
    </div>
  );
}
