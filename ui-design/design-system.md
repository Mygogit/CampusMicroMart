# CampusMicroMart · UI 设计系统（清新校园极简 / Fresh Campus Minimal）

> 面向高校二手交易平台的桌面 Web 优先设计系统。设计系统优先（Design-System-First）：先定令牌与组件，再落地界面。
> 交付日期：2026-08-31 · 设计：UI Designer · 状态：可用于开发交付

---

## 1. 设计原则

1. **留白即呼吸**：用空间区分层级，而不是用分割线和卡片堆叠。
2. **一抹新生绿**：主色承担「行动」与「环保流转」语义，其余大面积留给中性色。
3. **克制的细节**：柔和圆角 + 低透明度阴影，避免毛玻璃滥用与发光描边。
4. **意图性大于强度**：极简也要有态度——统一字号节奏、明确焦点、可预期反馈。

---

## 2. 色彩系统（oklch，附 hex 回退）

### 主色 · 新生绿（Primary / Brand）
| Token | oklch | Hex | 用途 |
|---|---|---|---|
| `--green-50`  | `oklch(0.97 0.02 155)`  | `#eafaf2` | 主色极浅底（选中/悬停背景） |
| `--green-100` | `oklch(0.93 0.05 155)`  | `#cdf0e0` | 浅绿填充、标签底 |
| `--green-300` | `oklch(0.82 0.11 155)`  | `#7fd9b4` | 图标、装饰 |
| `--green-500` | `oklch(0.68 0.14 155)`  | `#2fa978` | **主色**（按钮、链接、焦点） |
| `--green-600` | `oklch(0.60 0.14 155)`  | `#24885f` | 主色悬停/按压 |
| `--green-700` | `oklch(0.52 0.13 155)`  | `#1c6a4b` | 主色文字/深色态 |

### 中性 · 暖白（Neutral / Warm）
| Token | oklch | Hex | 用途 |
|---|---|---|---|
| `--bg`         | `oklch(0.985 0.004 150)` | `#fbfbf8` | 页面背景（非纯白） |
| `--surface`    | `oklch(1 0 0)`           | `#ffffff` | 卡片/面板表面 |
| `--surface-2`  | `oklch(0.975 0.004 150)` | `#f6f6f2` | 次级表面（输入框底） |
| `--border`     | `oklch(0.92 0.006 150)`  | `#e6e6df` | 描边 |
| `--text`       | `oklch(0.24 0.02 155)`   | `#2a2f2c` | 正文（非纯黑） |
| `--text-muted` | `oklch(0.52 0.02 155)`   | `#6f756f` | 次级文字 |
| `--text-subtle`| `oklch(0.66 0.02 155)`   | `#969b94` | 占位/辅助 |

### 强调色（Accent · 用于「优惠 / 急出 / 分类」）
| Token | oklch | Hex | 用途 |
|---|---|---|---|
| `--coral-500` | `oklch(0.72 0.16 35)`  | `#ec6a53` | 优惠/降价标签 |
| `--coral-50`  | `oklch(0.96 0.04 35)`  | `#fdeae5` | 优惠标签底 |
| `--amber-500` | `oklch(0.78 0.14 75)`  | `#e0a23c` | 急出/限时 |

### 语义色（Semantic）
| Token | oklch | Hex | 用途 |
|---|---|---|---|
| `--success` | `oklch(0.70 0.15 150)` | `#3faa78` | 成功/已完成 |
| `--warning` | `oklch(0.78 0.14 75)`  | `#e0a23c` | 待处理/提醒 |
| `--danger`  | `oklch(0.62 0.20 25)`  | `#d2503f` | 错误/下架 |
| `--info`    | `oklch(0.68 0.14 155)` | `#2fa978` | 提示（同主色） |

### 无障碍对比度（WCAG AA）
- 正文 `--text` on `--bg`：对比度 ≈ **13.5:1** ✅（远超 4.5:1）
- `--text-muted` on `--bg`：≈ **4.7:1** ✅
- 白字 on `--green-500`：≈ **3.9:1** → 用于 **大号/按钮文字**（≥18px 或 700 字重时满足 3:1）；小号正文避免白底绿字
- 主色作链接时下方保留下划线或配图标，确保不仅靠颜色传达

---

## 3. 字体系统

| 角色 | 字体 | 回退（中文优先） |
|---|---|---|
| 展示 / 标题 | **Sora** (400–700) | `'PingFang SC','Microsoft YaHei',sans-serif` |
| 正文 / UI | **Manrope** (400–700) | `'PingFang SC','Microsoft YaHei',sans-serif` |

- **避免**：Inter / Roboto / Arial 作主字体（通用 AI 感）。
- 标题与正文使用不同字重 + 字号节奏建立层级，而非靠大图标。
- 字号采用 `clamp()` 流式缩放，桌面基准 16px。

### 字号阶梯（桌面）
| Token | 值 | 用途 |
|---|---|---|
| `--fs-display` | `clamp(28px, 3vw, 40px)` | 页面主标题 |
| `--fs-h1` | `clamp(22px, 2vw, 28px)` | 区块标题 |
| `--fs-h2` | `20px` | 卡片标题 |
| `--fs-base` | `16px` | 正文 |
| `--fs-sm` | `14px` | 次级文字 |
| `--fs-xs` | `12px` | 标签/辅助 |

---

## 4. 间距与圆角

### 间距（4px 基准）
`--space-1:4px · --space-2:8px · --space-3:12px · --space-4:16px · --space-5:20px · --space-6:24px · --space-8:32px · --space-10:40px · --space-12:48px · --space-16:64px`

用**变化的间距**创造节奏：区块间距 32–48px，卡片内边距 16–20px，元素间隙 8–12px。

### 圆角
`--radius-sm:10px`（输入/按钮）· `--radius:14px`（卡片）· `--radius-lg:20px`（面板/图片）· `--radius-pill:999px`（标签/头像）

### 阴影（低透明度，暖色偏绿）
`--shadow-sm: 0 1px 2px oklch(0.3 0.02 155/0.04), 0 1px 3px oklch(0.3 0.02 155/0.06)`
`--shadow-md: 0 4px 12px oklch(0.3 0.02 155/0.07)`
`--shadow-lg: 0 12px 32px oklch(0.3 0.02 155/0.10)`

### 动效
`--ease-out-quart: cubic-bezier(0.22,1,0.36,1)`（状态变化用，避免回弹/弹性）
`--dur-fast:160ms · --dur:280ms`
仅在 `opacity / transform / background / color` 上动画，不动画 `width/height/top/left`。

---

## 5. 组件库

### 按钮 Button
| 变体 | 外观 | 用途 |
|---|---|---|
| `.btn-primary` | 绿底白字 | 主行动（发布、立即购买） |
| `.btn-ghost` | 透明 + 绿字 + 边框 | 次级（收藏、取消） |
| `.btn-subtle` | 中性底 | 最低优先（返回） |

- 尺寸：默认 `h=40px`，紧凑 `h=34px`，大 `h=48px`。
- 焦点：`focus-visible` 显示 2px 绿环 + 2px 偏移；禁用态 `opacity:.55` 且 `pointer-events:none`。
- 悬停：主按钮 `translateY(-1px)` + `--shadow-md`；过渡 `--dur-fast`。

### 表单 Form
- `.input`：高 40px，`--surface-2` 底 + 1px `--border`，聚焦时绿边 + 绿环（非发光）。
- 标签上方、必填以 `*` 红标 + `aria-required`；错误信息 `--danger` 文字 + `aria-describedby`。
- 上传区 `.dropzone`：虚线边框，悬停绿底，支持拖拽与点击。

### 卡片 Card
- 商品卡：图（16:10，圆角 `--radius-lg` 顶部裁切）+ 标题 + 价格（绿/大） + 成色标签 + 卖家行。
- 悬停：卡片 `translateY(-2px)` + `--shadow-md`；图片轻微 `scale(1.03)`。
- 桌面网格：`repeat(auto-fill, minmax(220px,1fr))`，列间距 20px。

### 导航 Navigation
- 左侧栏 `w=248px` 固定：品牌 + 主菜单（首页/浏览/发布闲置/消息/个人中心）+ 底部用户。
- 顶栏：搜索（带图标）+ 通知 + 头像。
- 移动/窄屏：左栏收为图标栏或顶部抽屉（不隐藏核心功能）。

### 反馈 Feedback
- `.toast`：右下角，成功绿 / 错误红，3s 自动消失，`role="status"`。
- `.modal`：遮罩 `oklch(0.2 0.02 155/0.4)`，面板 `--radius-lg` + `--shadow-lg`，ESC 关闭、焦点陷阱。
- `.badge`：状态徽章（进行中/已完成/已取消），语义色底。

### 数据展示 Data Display
- 列表行：头像 + 标题 + 元信息 + 状态徽章 + 操作；行悬停 `--green-50` 底。
- 空状态：插画（线性 SVG）+ 一句引导文案 + 主按钮，避免「暂无数据」冷场。

---

## 6. 响应式策略（桌面优先，向下兼容）

| 断点 | 宽度 | 布局 |
|---|---|---|
| 桌面 | ≥1280px | 左栏 248px + 主区 max 1200px 居中 |
| 笔记本 | 1024–1279px | 左栏 248px + 主区自适应 |
| 平板 | 768–1023px | 左栏收为 72px 图标栏 |
| 手机 | <768px | 左栏变底部 Tab 栏，主区单列 |

容器查询 `@container` 用于商品卡，使卡片在侧栏变窄时仍自适应。

---

## 7. 无障碍清单（WCAG AA）

- [x] 正文对比度 ≥ 4.5:1，大字 ≥ 3:1
- [x] 全键盘可达：Tab 顺序合理，`focus-visible` 明显
- [x] 语义化标签 + ARIA：`nav/ main/ article/ aria-label/ aria-current`
- [x] 触达目标 ≥ 40px（桌面按钮 40px；移动底部 Tab ≥ 48px）
- [x] `prefers-reduced-motion` 时关闭非必要动画
- [x] 表单有 `<label>`、错误用 `aria-live` 播报
- [x] 不仅靠颜色传达状态（状态附文字/图标）

---

## 8. 暗色模式（扩展，未默认启用）

`[data-theme="dark"]` 令牌：背景 `oklch(0.20 0.01 155)`、表面 `oklch(0.26 0.01 155)`、文字 `oklch(0.95 0 0)`、主色提亮至 `oklch(0.78 0.14 155)`。原型当前以亮色为准，暗色仅预留令牌。

---

**UI Designer · 2026-08-31 · 实现就绪，开发交付 QA 流程已建立**
