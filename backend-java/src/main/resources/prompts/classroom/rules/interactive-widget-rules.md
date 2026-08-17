# HTML 交互组件生成规则（interactive 场景专用）

当场景类型为 interactive 时，生成 `content.widget`（含完整 HTML 文档），并通过 `launch_widget` 动作装载、`widget_*` 动作驱动。

---

## 组件子类型选择

| 子类型 | 适用知识点 | 交互形式 | 示例 |
|--------|-----------|----------|------|
| `simulation` 模拟实验 | 实验性规律（可调节变量 + 即时反馈） | 滑块调节参数 + 画布/SVG 实时反馈 | 车速→刹车距离、价格→供需曲线、角度→抛物线 |
| `game` 知识游戏 | 配对/分类/排序/记忆 | 拖拽、点击、计时挑战 + 计分 | 违法行为拖进责任类型、概念配对 |
| `explore` 可操作示意图 | 结构/流程/层级 | 点击节点展开详情 | 诉讼流程逐步展开、法院体系节点浏览 |

---

## HTML 硬性要求（违反即不合格）

1. **单文件自包含**：内联 CSS/JS，**禁止外链 CDN**（离线可用），标准 HTML5 结构（`<!DOCTYPE html>` 开头、单个 `</html>` 结尾）。
2. **必须实现 postMessage 监听**（AI 教师驱动组件的关键通道）：
   ```javascript
   window.addEventListener('message', function (event) {
     const { type, target, state, content } = event.data || {};
     switch (type) {
       case 'SET_WIDGET_STATE':   // 设置变量值
         if (state) Object.entries(state).forEach(([k, v]) => {
           const el = document.getElementById(k + '-slider') || document.getElementById(k)
             || document.querySelector('[data-var="' + k + '"]');
           if (el) { el.value = v; el.dispatchEvent(new Event('input', { bubbles: true })); }
           if (typeof window.setWidgetParam === 'function') window.setWidgetParam(k, v);
         });
         break;
       case 'HIGHLIGHT_ELEMENT':  // 高亮元素
         const h = document.querySelector(target);
         if (h) { h.style.outline = '3px solid rgba(139,92,246,0.8)'; h.style.outlineOffset = '4px';
           setTimeout(() => { h.style.outline = ''; h.style.outlineOffset = ''; }, 3000); }
         break;
       case 'ANNOTATE_ELEMENT':   // 标注气泡
         const a = document.querySelector(target);
         if (a && content) {
           const r = a.getBoundingClientRect();
           const tip = document.createElement('div');
           tip.textContent = content;
           tip.style.cssText = 'position:fixed;top:' + (r.top - 44) + 'px;left:' + r.left
             + 'px;background:rgba(139,92,246,0.95);color:#fff;padding:8px 12px;border-radius:8px;'
             + 'font-size:14px;z-index:1000;animation:fadeTip .3s;';
           document.body.appendChild(tip);
           setTimeout(() => tip.remove(), 4000);
         }
         break;
       case 'REVEAL_ELEMENT':     // 揭示隐藏元素
         const re = document.querySelector(target);
         if (re) { re.style.display = ''; re.style.opacity = '1'; }
         break;
     }
   });
   // 附带动画 keyframes（fadeTip、pulse 等）放入 <style>
   ```
3. **控件命名规范**（供 HIGHLIGHT/ANNOTATE 精确寻址）：
   - 滑块：`id="{var}-slider"`（如 `id="speed-slider"`）
   - 按钮：`id="{action}-btn"`（如 `id="start-btn"`、`id="reset-btn"`）
   - 显示区：`id="{var}-display"`（如 `id="distance-display"`）
   - 关键可交互元素加 `data-target` 属性
4. **动画可见性（CRITICAL）**：点击「开始/启动」后必须有明显视觉变化（对象移动/旋转/变色/粒子/进度条），**禁止"只有数字在变但画面静止"**。
5. **公平开局（game 子类型）**：初始状态安全，玩家在前 3~5 秒内不可能失败；默认参数即可存活 ≥10 秒。
6. **按钮状态机**：启动 → 暂停 → 继续 → 重新开始，按钮文本与行为必须一致；重置必须恢复**全部**初始状态。
7. **移动端适配**：控制区与画布不重叠（控制区可折叠），触摸目标 ≥ 44×44px。
8. **内容真实**：数值、法条、案例必须来自知识点上下文，禁止编造。
9. **性能**：动画用 `requestAnimationFrame`；不要在渲染循环内创建对象。

---

## widget JSON 结构

```json
{
  "subtype": "simulation",
  "title": "刹车距离模拟器",
  "config": {
    "variables": [
      { "name": "speed", "label": "车速 (km/h)", "min": 20, "max": 120, "default": 60 }
    ],
    "targets": ["#distance-display", "#speed-slider"]
  },
  "html": "<!DOCTYPE html>…</html>"
}
```

**html 字段内字符串一律用单引号（JS 内），JSON 中禁止出现未转义双引号导致的结构破坏。**

---

## 常见 Bug 规避

| Bug | 解决 |
|-----|------|
| 重置按钮点了没反应 | 重置函数必须恢复所有 state 变量并重绘 |
| 动画不明显 | 启动后对象必须实际移动/旋转/变色 |
| 移动端控件压住画布 | 用 flex 纵向布局，控制区 `max-height: 40vh` |
| 按钮行为混乱 | 用 `running/paused/ended` 状态机，按钮文本=点击后行为 |
| HTML 输出重复 | 只输出一个完整文档，以单个 `</html>` 结束 |

---

## 输出格式

只输出一个完整 HTML 文档（无 markdown 代码块包裹、无解释文字）。文档必须：
- 以 `<!DOCTYPE html>` 开头
- 内联 `<style>` 和 `<script>`
- 以单个 `</html>` 结束
- **禁止在 HTML 内部或外部重复输出第二份文档**
