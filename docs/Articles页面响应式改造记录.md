# Articles 页面响应式改造记录

> 更新日期：2026-04-08/09/10/11

---

## 一、改造概述

本次改造对以下页面进行了响应式布局优化、交互体验提升和功能完善：

- `frontend/src/views/public/Articles.vue` — 公开文章页（`/articles`）
- `frontend/src/views/student/Articles.vue` — 学生端文章页（`/student/articles`）
- `frontend/src/views/student/AiQa.vue` — AI 助教页（`/student/ai-qa`）

---

## 二、改造内容

### 2.1 响应式布局（PC 端）

两个文章页面采用一致的响应式策略：

| 断点 | 布局 | 说明 |
|------|------|------|
| < 768px（移动端） | 单列列表 | 保持原有设计不变 |
| ≥ 768px（平板） | 2 列网格 | 内容区宽度 960px |
| ≥ 1200px（大屏） | 3 列网格 | 内容区宽度 960px |

关键实现：
- `.articles-mobile-page` 在 PC 端取消 `max-width` 限制，全屏显示
- `.articles-feed` 使用 CSS Grid 实现多列布局
- 添加 `.page-content-wrapper` 内容包装器，统一管理最大宽度和内边距

### 2.2 PC 端卡片高度固定

为避免文章内容长短不一导致卡片高度参差不齐，PC 端做了以下约束：

| 属性 | 移动端 | PC 端 |
|------|--------|-------|
| 卡片最小高度 | 自适应 | `280px` |
| 封面图高度 | `170px` | `140px` |
| 内容区内边距 | `16px` | `12px`（上下减小） |

实现方式：
- `.article-card` 设置 `min-height: 280px`
- `.article-content` 设置 `flex: 1`，自动填充剩余空间
- 封面图 `.article-cover` 固定高度 `140px`

### 2.3 顶部筛选栏选中样式

筛选栏（全部、科技、商务...）选中状态样式优化：

| 属性 | 修改前 | 修改后 |
|------|--------|--------|
| 背景色 | `#137fec`（浅蓝） | `#1d4ed8`（深蓝） |
| 圆角 | `999px`（胶囊） | `50px`（椭圆） |
| 阴影 | `rgba(19,127,236,0.3)` | `rgba(29,78,216,0.45)`（更深） |

### 2.4 分页功能

纯前端分页，每页显示 **5 篇文章**：

- 切换分类时自动回到第 1 页
- 切换页码时平滑滚动到顶部
- 选中页码为蓝色圆角按钮，与筛选栏风格统一
- PC 端（≥768px）自动隐藏（因为是网格布局，不需要分页）

分页组件包含左右箭头按钮和数字页码。

### 2.5 底部导航栏（PC 端修复）

修复了公开文章页在 PC 全屏模式下底部导航栏消失的问题：

- 移动端：固定在底部，居中显示，最大宽度 600px
- PC 端：全宽铺开，`border-radius: 0`，底部占位 `.bottom-spacer` 恢复为 80px，`page-main` 加 `padding-bottom: 100px` 避免内容被遮挡

### 2.6 点击高亮效果移除

为 `.filter-pill`、`.article-card`、`.page-btn` 添加了 `-webkit-tap-highlight-color: transparent`，消除移动端点击时的浅蓝色闪烁。

### 2.7 页面顶部标题移除

- **公开文章页**：移除"发现文章"和"Daily Picks"标题区域（含登录图标）
- **学生端文章页**：移除"发现文章"和"Daily Picks"标题区域，保留筛选/搜索/通知按钮并靠右显示
- **AI 助教页**：移除"AI 助教"标题，保留返回按钮和头像图标

筛选栏在移除顶部标题后，`.filter-bar` 的 `top` 值从 `60px` 调整为 `max(env(safe-area-inset-top, 0px), 0px)`，紧贴顶部。

### 2.8 学生端文章页搜索功能

实现了完整的文章搜索功能：

- 点击搜索图标显示/隐藏搜索框（带折叠动画）
- 输入关键词实时过滤文章（匹配标题或内容）
- 关闭搜索框自动清空搜索词
- 搜索和分类筛选可以叠加使用
- 无结果时显示空状态提示

搜索使用 Element Plus 的 `el-input` 组件，风格与筛选栏统一。

### 2.9 Safari 底部导航栏被地址栏遮挡修复

修复 iOS Safari 环境下底部导航栏被浏览器地址栏遮挡的问题：

- **容器高度改为 `100dvh`**：将 `.trending-app` 和 `.mobile-layout` 的 `height: 100vh` 改为 `height: 100dvh`（dynamic viewport height），Safari 会自动响应其动态工具栏的显示/隐藏状态
- **移除 `overflow: hidden`**：原 `overflow: hidden` 会干扰 Safari 的动态视口计算，移除后 `100dvh` 能正确响应地址栏变化
- **`.trend-nav` / `.mobile-nav`**：改为 `position: fixed; bottom: 0; left: 0; right: 0;`
- **`.main-content` / `.mobile-main`**：添加底部 padding 防止内容被底部导航栏遮挡
- 使用 `env(safe-area-inset-bottom)` 适配 iPhone 刘海屏和安全区域

### 2.10 ElMessage 提示位置偏移

为 ElMessage 提示消息添加全局样式，使其往下偏移，避免被页面顶部元素遮挡：

- 在 `InteractiveText.vue` 的 `<style>` 中使用 `:global(.el-message) { top: 100px !important; }`
- 不使用独立的 CSS 文件，直接在组件样式中声明

### 2.11 单词本分页与圆形按钮

`/student/vocabulary` 单词本页面分页改造：

- **移动端**：每页 10 个单词，显示分页器
- **PC 端**：所有单词一次显示完，不显示分页器
- **动态适配**：监听 `window.innerWidth`，屏幕宽度 < 768px 切换到移动端模式，≥ 768px 切换到 PC 模式，切换时自动回到第 1 页
- **分页按钮圆形**：`el-pager li` 添加 `border-radius: 50%` 样式

---

## 三、修改文件清单

| 文件路径 | 修改内容 |
|----------|----------|
| `frontend/src/views/public/Articles.vue` | 响应式布局、分页、筛选样式、底部导航修复、点击高亮、移除顶部标题 |
| `frontend/src/views/student/Articles.vue` | 响应式布局、分页、筛选样式、点击高亮、移除顶部标题、搜索功能 |
| `frontend/src/views/student/AiQa.vue` | 移除"AI 助教"标题 |
| `frontend/src/views/student/Layout.vue` | Safari 底部导航栏遮挡修复（100dvh + position: fixed + overflow 移除） |
| `frontend/src/views/shared/MobileLayout.vue` | Safari 底部导航栏遮挡修复（100dvh + position: fixed + overflow 移除） |
| `frontend/src/views/student/InteractiveText.vue` | ElMessage 提示往下偏移 100px（:global CSS） |
| `frontend/src/views/student/Vocabulary.vue` | 单词本分页（移动端10条/PC端全显示）、圆形分页按钮 |

---

## 四、公开文章页 vs 学生端文章页 vs AI 助教页

| 功能 | 公开页 | 学生端 |
|------|--------|--------|
| 响应式布局 | ✅ | ✅ |
| PC 端 2/3 列网格 | ✅ | ✅ |
| 分页（5篇/页） | ✅ | ✅ |
| 筛选栏选中样式（深蓝椭圆） | ✅ | ✅ |
| 点击高亮消除 | ✅ | ✅ |
| 内容包装器（`.page-content-wrapper`） | ✅ | ✅ |
| 移除顶部标题 | ✅（全部移除） | ✅（保留操作按钮靠右） |
| 搜索功能 | ❌ | ✅ |
| 底部导航栏 | ✅（PC 全屏显示） | ❌（使用 Layout 导航） |

---

## 五、相关 API

推荐文章接口：`GET /api/public/texts/featured?count=12`

返回数据说明：
- 查询 `texts` 表中 `deleted=0` 且 `is_featured=1` 的记录
- 按 `featured_priority` 和 `created_at` 降序排序
- 当前数据库中 `is_featured=1` 的记录为 0，需管理员在后台标记后才会有数据
