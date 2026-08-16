import { useEffect, useState } from 'react';
import { Segmented, Spin } from 'antd';
import { stageAPI } from '../../services/api';

/**
 * 学段切换器（PRD v4.0 §11.2）
 *
 * 4 学段 Tab（小学/初中/高中/大学），数据来自 stageAPI.getAll()。
 * 首次加载自动选中第一个学段并回调 onChange。
 *
 * @param {string}  value     当前选中的学段代码
 * @param {(code:string)=>void} onChange 切换回调
 */
const STAGE_ICONS = {
  PRIMARY: '🏫',
  JUNIOR: '📖',
  SENIOR: '🟢',
  UNIVERSITY: '🎓',
};

export default function StageSelector({ value, onChange }) {
  const [stages, setStages] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    stageAPI
      .getAll()
      .then((res) => {
        const list = res?.data || res || [];
        if (!mounted) return;
        setStages(list);
        // 未选中时自动选中第一个学段
        if (list.length > 0 && !value && onChange) {
          onChange(list[0].code);
        }
      })
      .catch(() => {
        if (mounted) setStages([]);
      })
      .finally(() => {
        if (mounted) setLoading(false);
      });
    return () => { mounted = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  if (loading) {
    return <Spin size="small" style={{ display: 'block', margin: '8px 0' }} />;
  }

  if (stages.length === 0) {
    return null;
  }

  const options = stages.map((s) => ({
    label: `${STAGE_ICONS[s.code] || '📚'} ${s.name}`,
    value: s.code,
  }));

  return (
    <Segmented
      options={options}
      value={value}
      onChange={onChange}
      size="large"
      style={{ marginBottom: 16 }}
    />
  );
}
