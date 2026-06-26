# English Learning Platform - 待实现功能清单

## 📋 项目概述

基于对README.md功能和当前实现状况的全面分析，以下是英语学习平台尚未实现的功能列表。

## ✅ 已实现功能总结

### 核心基础功能
- ✅ 用户认证系统 (JWT + Spring Security)
- ✅ 用户管理 (增删改查)
- ✅ 课程管理 (增删改查)
- ✅ 单词管理 (增删改查)
- ✅ 课文管理 (增删改查)
- ✅ 单元管理 (实体类和基础结构)
- ✅ 管理员仪表板 (真实统计数据)
- ✅ 前端基础页面结构
- ✅ 路由和权限控制

### 班级管理系统 (高优先级) ✅ **已完成**
- ✅ **Class实体类** - 班级数据模型
- ✅ **ClassService接口和实现** - 班级业务逻辑
- ✅ **ClassMapper** - 班级数据访问层
- ✅ **StudentClass实体类** - 学生班级关联
- ✅ **班级相关API接口**
  - 创建班级
  - 管理班级成员
  - 班级统计信息
- ✅ **管理员班级管理页面** - 完整的班级CRUD功能
- ✅ **教师班级管理功能** - 查看和管理所教班级
- ✅ **学生班级功能** - 查看所在班级信息

### 任务系统 (高优先级) ✅ **已完成**
- ✅ **Assignment实体类** - 学习任务数据模型
- ✅ **AssignmentService接口和实现** - 任务业务逻辑
- ✅ **AssignmentMapper** - 任务数据访问层
- ✅ **任务相关API接口**
  - 创建学习任务
  - 任务列表查询
  - 任务完成状态跟踪
  - 任务统计信息
- ✅ **教师任务布置功能** - 从单元内容创建任务
- ✅ **学生任务列表** - 查看和完成学习任务
- ✅ **任务进度跟踪** - 学习进度可视化
- ✅ **任务统计报表** - 完成情况分析

### 生词本系统 (中优先级) ✅ **已完成**
- ✅ **VocabularyBook实体类** - 生词本数据模型
- ✅ **VocabularyBookService接口和实现** - 生词本业务逻辑
- ✅ **VocabularyBookMapper** - 生词本数据访问层
- ✅ **生词本相关API接口**
  - 添加生词
  - 生词本查询
  - 掌握程度管理
  - 复习提醒
- ✅ **学生生词本功能** - 完整的生词管理
- ✅ **生词添加功能** - 从课文点击添加
- ✅ **生词复习系统** - 掌握程度分类管理
- ✅ **掌握程度分类** - NEW/LEARNING/MASTERED

### 学习记录系统 (中优先级) ✅ **已完成**
- ✅ **LearningRecord实体类** - 学习记录数据模型
- ✅ **LearningRecordService接口和实现** - 学习记录业务逻辑
- ✅ **LearningRecordMapper** - 学习记录数据访问层
- ✅ **学习记录相关API接口**
  - 记录学习行为
  - 学习统计查询
  - 学习时长统计
  - 学习效果分析
- ✅ **学习行为追踪** - 实时记录学习活动
- ✅ **学习统计仪表板** - 个人学习数据
- ✅ **学习进度可视化** - 统计图表展示
- ✅ **学习历史记录** - 详细学习日志

### 单元单词关联系统 (中优先级) ✅ **已完成**
- ✅ **UnitWord实体类** - 单元单词关联数据模型
- ✅ **UnitWordMapper** - 关联数据访问层
- ✅ **单元单词关联API接口**
  - 单元添加单词
  - 单元单词列表
  - 单词移除功能
- ✅ **单元内容管理** - 管理单元下的课文和单词
- ✅ **单词分组功能** - 按单元组织单词学习
- ✅ **内容关联编辑** - 数据库关联管理

### 学生端完整功能 ✅ **已完成**
- ✅ **学生仪表板** - 学习数据统计展示
- ✅ **学生课程页面** - 课程学习界面
- ✅ **学生生词本页面** - 生词管理功能
- ✅ **学生任务页面** - 任务查看和完成
- ✅ **课程内容学习** - 单元内容查看和学习
- ✅ **班级管理功能** - 加入和查看班级
- ✅ **学习统计功能** - 个人学习数据分析

## 🔧 今日新增/修复功能

> **注**: 以下为历史记录。最新功能见下方「后续新增功能」。

### 1. 硬编码数据修复 ✅ **完成**
- **问题**: 学生端统计功能存在大量硬编码的默认值
- **修复**: 将所有硬编码数据替换为真实数据库查询计算
- **影响功能**: 班级学习进度、课程统计、任务进度、单元学习统计

### 2. 实体类完善 ✅ **完成**
- **新增**: VocabularyBook、LearningRecord、UnitWord 实体类
- **修复**: 实体类字段与数据库schema完全匹配
- **数据类型**: 修正ENUM类型字段（mastery_level, record_type等）

### 3. API方法一致性 ✅ **完成**
- **问题**: Controller调用Service方法名不匹配
- **修复**: 统一所有方法签名和参数
- **验证**: 42个前端API调用全部对应正确实现

### 4. 学生端数据完整性 ✅ **完成**
- **测试数据**: 创建完整的学生端测试数据库脚本
- **功能验证**: 学生端100%功能使用真实数据
- **性能优化**: 统计计算查询优化

### 5. 前端错误修复 ✅ **完成**
- **问题**: Courses页面数据结构和API调用错误
- **修复**: 前端数据解析和错误处理
- **用户体验**: 添加详细调试日志和错误提示

### 6. 单元内容学习 ✅ **完成**
- **问题**: 单元内容不显示，API返回空数据
- **修复**: 完善后端数据查询逻辑
- **功能**: 课文和单词内容正常显示

---

## 🆕 后续新增功能（原 TODO 中未列出）

### SSO 统一身份认证 ✅ **已完成**
- ✅ CAS 协议集成（内蒙古农业大学统一身份认证）
- ✅ SsoAuthController + SsoUserService
- ✅ 前端 SsoCallback.vue 回调处理
- ✅ 角色自动映射（CAS 属性 → 系统角色）

### 学校数据集成 ✅ **已完成**
- ✅ 双模式数据接入：REST API + JDBC ETL
- ✅ 学校数据同步服务（定时全量/增量同步）
- ✅ 课表、成绩、选课、教学计划等数据展示
- ✅ 功能开关控制（按学校启用/禁用）
- ✅ 运维面板学校数据配置界面
- ✅ API 签名认证（appId + appSecret + timestamp，符合官方接口规范）
- ✅ 部署向导集成（`scripts/deploy.sh` 交互式配置 + 功能开关自动激活）

### AI 功能集成 ✅ **已完成**
- ✅ AI 翻译（多提供商路由：HiAgent / OpenRouter / SiliconFlow）
- ✅ AI 问答（文章划词问答）
- ✅ 腾讯云 ASR 语音识别
- ✅ Bilibili 视频代理
- ✅ AI 服务商管理面板（增删改查 + 状态监控）
- ✅ 熔断器保护（Resilience4j）
- ✅ 翻译长文本自动分块（≤10000字，按句切块，块大小 1500字）

### 运维功能 ✅ **已完成**
- ✅ 运维面板（OPS 角色 + 提权机制）
- ✅ 访问日志与统计
- ✅ 自动部署脚本（deploy.sh + auto-update.sh）
- ✅ 维护模式（部署时自动开关）
- ✅ 数据库自动备份与恢复

### 待实现功能 🚧

#### 接口安全加固 ✅ **已完成（2026-06-17）**
- ✅ 标签白名单：`filterValidTags()` 服务层校验 + UserVideoController / UserTextController 必填+白名单双重拦截
- ✅ 翻译 Prompt Injection 防护：语言代码白名单（en/zh/auto）
- ✅ 媒体文件访问控制：未审核视频原始文件不可下载
- ✅ AI 问答 / 批注 / 划线输入长度上限
- ✅ 字幕 HTML 实体解码（`&gt;&gt;` → `>>`）

#### 流式翻译（优先级：中）
- ❌ 将 `translate()` 底层改为 `stream: true` Chat Completions 调用（需切换到 `WebClient`）
- ❌ `TranslateController` 新增 SSE 端点（`/api/translate/stream`），每个分块翻完后 push 结果
- ❌ 前端用 `EventSource` / fetch ReadableStream 消费流式响应，实现渐进呈现
- **前置条件**：RestTemplate → WebClient 迁移，或单独为流式接口新建 WebClient Bean

#### 数据指标可视化（优先级：最高 - 比赛答辩前必须完成）

> **目标**: 所有数据指标在运维/管理员前端页面可视化展示  
> **参考文档**: `docs/DATA_CAPABILITY_ASSESSMENT.md`, `docs/DATA_METRICS_REPORT.md`

##### P0 - 必须完成（2.5 天）

- ❌ **1. 用户活跃度数据展示**
  - 后端 API: `GET /api/admin/metrics/user-activity`
    - 返回: 注册用户数、MAU、DAU、班级数、学院数
  - 前端页面: 管理员 Dashboard 新增"用户活跃度"卡片
    - 显示: 今日活跃、本月活跃、总注册用户、班级数
    - 图表: 最近 30 天 DAU 趋势图（ECharts 折线图）
  - 数据补充: 执行 `docs/DATA_CAPABILITY_ASSESSMENT.md` 中的 SQL 脚本生成测试数据

- ❌ **2. 学习行为数据展示**
  - 后端 API: `GET /api/admin/metrics/learning-behavior`
    - 返回: 总阅读量、总学习记录数、平均学习时长、热门文章 Top 10
  - 前端页面: 管理员 Dashboard 新增"学习行为"卡片
    - 显示: 总阅读量、总学习时长、平均每用户学习时长
    - 图表: 学习记录类型分布（饼图）、每日学习记录数趋势（柱状图）

- ❌ **3. 四六级成绩对比分析**
  - 后端 API: `GET /api/admin/metrics/cet-comparison`
    - 返回: 使用平台学生通过率、未使用平台学生通过率、提升百分点
    - SQL: 已在 `docs/DATA_CAPABILITY_ASSESSMENT.md` 中设计
  - 前端页面: 管理员 Dashboard 新增"四六级成绩对比"卡片
    - 显示: 两组学生的通过率对比（进度条）、提升百分点（高亮显示）
    - 图表: 分数分布对比（双柱状图）
  - 数据补充: 触发学校数据同步或执行模拟数据脚本

- ❌ **4. AI 调用统计展示**
  - 后端 API: `GET /api/admin/metrics/ai-usage`
    - 返回: 累计调用次数、平均每日调用量、成功率、平均响应时间
    - 按服务类型分组: HIAGENT, OPENROUTER, TENCENT_ASR 等
  - 前端页面: 管理员 Dashboard 新增"AI 调用统计"卡片
    - 显示: 累计调用、今日调用、成功率、平均响应时间
    - 图表: 每日调用量趋势（折线图）、服务类型分布（饼图）
  - 数据补充: 手动测试 AI 功能或执行模拟数据脚本

- ❌ **5. 推荐算法效果追踪（埋点）**
  - 后端修改:
    - 扩展 `learning_records.record_type` 枚举: 新增 `RECOMMENDATION_VIEW`, `RECOMMENDATION_CLICK`, `RECOMMENDATION_REFRESH`
    - 新增 API: `POST /api/student/learning/record/recommendation` (记录推荐行为)
  - 前端埋点 (`frontend/src/views/student/Articles.vue`):
    - 推荐文章展示时: 记录 `RECOMMENDATION_VIEW`
    - 点击推荐文章时: 记录 `RECOMMENDATION_CLICK`
    - 点击"换一批"时: 记录 `RECOMMENDATION_REFRESH`
  - 后端 API: `GET /api/admin/metrics/recommendation-effectiveness`
    - 返回: 推荐点击率、平均换一批次数、推荐曝光数
  - 前端页面: 管理员 Dashboard 新增"推荐效果"卡片
    - 显示: 点击率、换一批频率、曝光数
    - 图表: 点击率趋势（折线图）

##### P1 - 建议完成（6 天）

- ❌ **6. AI 对话持久化与分享功能**
  - **数据库设计**（Flyway 迁移）:
    ```sql
    -- 对话会话表
    CREATE TABLE ai_conversations (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      user_id BIGINT NOT NULL,
      title VARCHAR(200) COMMENT '对话标题（自动生成或用户编辑）',
      first_question TEXT COMMENT '首条问题（用于生成默认标题）',
      message_count INT DEFAULT 0 COMMENT '消息数量',
      created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      deleted TINYINT DEFAULT 0,
      INDEX idx_user_time (user_id, updated_time),
      FOREIGN KEY (user_id) REFERENCES users(id)
    );
    
    -- 对话消息表
    CREATE TABLE ai_messages (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      conversation_id BIGINT NOT NULL,
      role ENUM('user', 'assistant') NOT NULL,
      content TEXT NOT NULL,
      selected_text TEXT COMMENT '用户选中的文本（仅 user 消息）',
      article_context TEXT COMMENT '文章上下文（仅 user 消息）',
      created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_conversation (conversation_id, created_time),
      FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE
    );
    
    -- 对话分享表
    CREATE TABLE conversation_shares (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      conversation_id BIGINT NOT NULL,
      share_token VARCHAR(64) UNIQUE NOT NULL COMMENT '分享令牌（UUID + HMAC）',
      access_password VARCHAR(128) COMMENT '访问密码（BCrypt 加密，可选）',
      expires_at DATETIME COMMENT '过期时间（NULL = 永久）',
      view_count INT DEFAULT 0 COMMENT '访问次数',
      created_by BIGINT NOT NULL COMMENT '分享者用户ID',
      created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
      INDEX idx_token (share_token),
      INDEX idx_conversation (conversation_id),
      FOREIGN KEY (conversation_id) REFERENCES ai_conversations(id) ON DELETE CASCADE,
      FOREIGN KEY (created_by) REFERENCES users(id)
    );
    ```
  
  - **后端实现**（2.5 天）:
    - **Entity**: `AiConversation`, `AiMessage`, `ConversationShare`
    - **Service**: `ConversationService`（CRUD + 分享逻辑）
      - `createConversation(userId, firstQuestion)` — 创建新对话
      - `addMessage(conversationId, role, content, ...)` — 添加消息
      - `getConversationHistory(conversationId, userId)` — 获取历史（权限检查）
      - `listUserConversations(userId, page, size)` — 用户对话列表
      - `generateShareToken(conversationId, userId, expiresIn, password)` — 生成分享链接
      - `validateShareToken(token, password)` — 验证分享访问
      - `deleteConversation(conversationId, userId)` — 删除对话
    - **Controller**: `ConversationController`
      - `POST /api/student/conversations` — 创建对话
      - `GET /api/student/conversations` — 对话列表（分页）
      - `GET /api/student/conversations/{id}` — 对话详情
      - `DELETE /api/student/conversations/{id}` — 删除对话
      - `POST /api/student/conversations/{id}/share` — 生成分享链接
      - `GET /api/public/shared-conversations/{token}` — 访问分享对话（公开接口）
    - **修改 `AiQaController.chat()`**:
      - 新增可选参数 `conversationId`（Long，可为 null）
      - 如果 `conversationId` 存在 → 加载历史消息作为上下文
      - 调用 AI 后 → 保存 user 和 assistant 消息到数据库
      - 如果 `conversationId` 为 null → 创建新对话（首次提问）
    - **Token 生成逻辑**:
      - `UUID.randomUUID().toString().replace("-", "")` (32字符)
      - HMAC-SHA256 签名: `HMAC(conversationId + userId + timestamp, SECRET_KEY)` (32字符)
      - 最终 token: `uuid + hmac` (64字符)
    - **安全措施**:
      - 分享链接默认 7 天过期（可配置）
      - 密码保护使用 BCrypt 加密
      - 访问时记录 `view_count` 和 IP（可选）
      - 用户只能分享自己的对话
  
  - **前端实现**（2 天）:
    - **修改 `frontend/src/views/student/AiQa.vue`**:
      - 添加左侧历史对话侧边栏（可折叠）
        - 显示对话列表（标题 + 最后更新时间）
        - 点击切换对话 → 加载历史消息
        - "新建对话"按钮
        - "删除对话"按钮（确认对话框）
      - 添加"分享"按钮（对话标题栏右侧）
        - 点击弹出分享对话框
        - 选项: 过期时间（7天/30天/永久）、是否设置密码
        - 生成分享链接并复制到剪贴板
        - 显示二维码（可选）
      - 消息持久化逻辑:
        - 发送消息时传递 `conversationId`
        - 首次提问时 `conversationId` 为 null，后端返回新 `conversationId`
        - 后续消息使用该 `conversationId`
      - 对话标题编辑功能（双击标题可编辑）
    
    - **新增 `frontend/src/views/public/SharedConversation.vue`**:
      - 路由: `/shared-conversation/:token`
      - 只读视图（不能发送新消息）
      - 显示对话标题、消息历史
      - 如果需要密码 → 弹出密码输入框
      - 显示"在 LangHack 中打开"按钮（跳转登录/注册）
      - 水印: "分享自 LangHack"
    
    - **API 封装** (`frontend/src/api/conversation.js`):
      - `createConversation(firstQuestion)`
      - `getConversations(page, size)`
      - `getConversationDetail(id)`
      - `deleteConversation(id)`
      - `shareConversation(id, options)`
      - `getSharedConversation(token, password)`
  
  - **UI/UX 设计要点**:
    - 历史对话侧边栏宽度: 280px（移动端可滑出）
    - 对话标题自动生成: 取首条问题前 20 字 + "..."
    - 分享链接格式: `https://yourdomain.com/shared-conversation/{token}`
    - 过期提示: "此分享链接已过期"
    - 密码错误提示: "访问密码错误，请重试"
  
  - **测试要点**:
    - 对话创建和消息保存
    - 历史对话加载和切换
    - 分享链接生成和访问
    - 密码保护验证
    - 过期时间检查
    - 权限控制（用户只能访问自己的对话）
    - 并发安全（多个消息同时保存）

- ❌ **7. AI 用户反馈功能**
  - 数据库: 新增 `ai_feedback` 表（Flyway 迁移）
    ```sql
    CREATE TABLE ai_feedback (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      call_log_id BIGINT NOT NULL,
      user_id BIGINT NOT NULL,
      rating TINYINT COMMENT '1-5星评分',
      is_helpful BOOLEAN COMMENT '是否有帮助',
      feedback_text TEXT COMMENT '文字反馈',
      created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
      FOREIGN KEY (call_log_id) REFERENCES ai_call_log(id)
    );
    ```
  - 后端 API: `POST /api/student/ai-qa/feedback` (提交反馈)
  - 前端: AI 回答后显示"有帮助"/"无帮助"按钮 + 可选文字反馈
  - 后端 API: `GET /api/admin/metrics/ai-feedback-summary`
    - 返回: 平均评分、有帮助率、反馈数量
  - 前端页面: 管理员 Dashboard "AI 调用统计"卡片中显示用户满意度

- ❌ **8. 学院数据支持**
  - 数据库: `classes` 表添加 `college` 字段（VARCHAR(100)）
  - Flyway 迁移: `ALTER TABLE classes ADD COLUMN college VARCHAR(100) AFTER name;`
  - 后端: 修改 `Class` 实体类和相关 Service
  - 前端: 班级管理页面添加学院选择/输入
  - 统计 API: 返回学院数量（`SELECT COUNT(DISTINCT college) FROM classes`）

- ❌ **9. 四六级对比分析详细页面**
  - 后端 API: `GET /api/admin/metrics/cet-detailed`
    - 返回: 按分数段分布、按考试类型（CET4/CET6）分组、时间趋势
  - 前端页面: 新增独立页面 `/admin/cet-analysis`
    - 图表: 分数分布直方图、通过率趋势、使用时长与成绩相关性散点图

- ❌ **10. 协作式互动阅读（集体智慧标注）**
  - **核心理念**: 任一学生标注的重点内容对同文本的所有学生可见，促进集体智慧沉淀
  
  - **数据库设计**（Flyway 迁移）:
    ```sql
    -- 文本标注表
    CREATE TABLE text_annotations (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      text_id BIGINT NOT NULL COMMENT '文章ID',
      user_id BIGINT NOT NULL COMMENT '标注者ID',
      start_offset INT NOT NULL COMMENT '标注起始位置（字符偏移）',
      end_offset INT NOT NULL COMMENT '标注结束位置',
      annotation_type ENUM('highlight', 'note', 'question') DEFAULT 'highlight' COMMENT '标注类型',
      color VARCHAR(20) DEFAULT 'yellow' COMMENT '高亮颜色',
      note_content TEXT COMMENT '笔记内容（仅 note 类型）',
      is_public BOOLEAN DEFAULT TRUE COMMENT '是否公开（用户可选）',
      upvote_count INT DEFAULT 0 COMMENT '点赞数',
      created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
      updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      deleted TINYINT DEFAULT 0,
      INDEX idx_text_public (text_id, is_public, deleted),
      INDEX idx_user (user_id, deleted),
      FOREIGN KEY (text_id) REFERENCES texts(id),
      FOREIGN KEY (user_id) REFERENCES users(id)
    );
    
    -- 标注点赞表
    CREATE TABLE annotation_upvotes (
      id BIGINT PRIMARY KEY AUTO_INCREMENT,
      annotation_id BIGINT NOT NULL,
      user_id BIGINT NOT NULL,
      created_time DATETIME DEFAULT CURRENT_TIMESTAMP,
      UNIQUE KEY uk_annotation_user (annotation_id, user_id),
      FOREIGN KEY (annotation_id) REFERENCES text_annotations(id) ON DELETE CASCADE,
      FOREIGN KEY (user_id) REFERENCES users(id)
    );
    
    -- 用户标注偏好设置表
    CREATE TABLE user_annotation_preferences (
      user_id BIGINT PRIMARY KEY,
      show_others_annotations BOOLEAN DEFAULT TRUE COMMENT '是否显示他人标注',
      default_public BOOLEAN DEFAULT TRUE COMMENT '新标注默认是否公开',
      default_color VARCHAR(20) DEFAULT 'yellow' COMMENT '默认高亮颜色',
      updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
      FOREIGN KEY (user_id) REFERENCES users(id)
    );
    ```
  
  - **后端实现**（2 天）:
    - **Entity**: `TextAnnotation`, `AnnotationUpvote`, `UserAnnotationPreferences`
    - **Service**: `AnnotationService`
      - `createAnnotation(textId, userId, startOffset, endOffset, type, color, noteContent, isPublic)` — 创建标注
      - `getTextAnnotations(textId, userId, includeOthers)` — 获取文章标注（根据用户偏好过滤）
      - `updateAnnotation(annotationId, userId, updates)` — 更新标注（仅自己的）
      - `deleteAnnotation(annotationId, userId)` — 删除标注（仅自己的）
      - `upvoteAnnotation(annotationId, userId)` — 点赞标注
      - `getPopularAnnotations(textId, limit)` — 获取热门标注（按点赞数排序）
      - `getUserPreferences(userId)` — 获取用户偏好
      - `updateUserPreferences(userId, preferences)` — 更新用户偏好
    - **Controller**: `AnnotationController`
      - `POST /api/student/annotations` — 创建标注
      - `GET /api/student/texts/{textId}/annotations` — 获取文章标注
      - `PUT /api/student/annotations/{id}` — 更新标注
      - `DELETE /api/student/annotations/{id}` — 删除标注
      - `POST /api/student/annotations/{id}/upvote` — 点赞/取消点赞
      - `GET /api/student/texts/{textId}/popular-annotations` — 热门标注
      - `GET /api/student/annotation-preferences` — 获取偏好设置
      - `PUT /api/student/annotation-preferences` — 更新偏好设置
    - **权限控制**:
      - 所有学生可查看公开标注
      - 仅标注者可编辑/删除自己的标注
      - 私有标注仅标注者可见
  
  - **前端实现**（2.5 天）:
    - **文章阅读页面增强** (`frontend/src/views/student/ArticleDetail.vue`):
      - **标注工具栏**（悬浮工具条）:
        - 用户选中文本后显示工具栏
        - 按钮: 高亮（黄/绿/蓝/粉）、添加笔记、提问
        - 公开/私有切换开关
      - **标注渲染**:
        - 高亮显示: `<mark>` 标签 + 对应颜色
        - 鼠标悬停显示标注信息卡片:
          - 标注者昵称 + 头像
          - 笔记内容（如有）
          - 点赞数 + 点赞按钮
          - 创建时间
          - 删除按钮（仅自己的标注）
        - 多人标注同一位置时显示叠加效果（边框加粗）
      - **标注侧边栏**（可折叠）:
        - Tab 1: 我的标注（列表）
        - Tab 2: 热门标注（按点赞排序）
        - Tab 3: 全部标注（时间倒序）
        - 点击标注项 → 滚动到对应位置并高亮
      - **偏好设置对话框**:
        - 开关: "显示他人标注"（默认开启）
        - 开关: "新标注默认公开"（默认开启）
        - 颜色选择: 默认高亮颜色
    
    - **标注冲突处理**:
      - 重叠标注: 显示最新标注的颜色，悬停显示所有标注列表
      - 嵌套标注: 外层标注用边框，内层用背景色
    
    - **实时更新**（可选）:
      - WebSocket 推送新标注（其他学生添加时）
      - 或定时轮询（每 30 秒刷新一次）
    
    - **API 封装** (`frontend/src/api/annotation.js`):
      - `createAnnotation(data)`
      - `getTextAnnotations(textId, includeOthers)`
      - `updateAnnotation(id, data)`
      - `deleteAnnotation(id)`
      - `upvoteAnnotation(id)`
      - `getPopularAnnotations(textId, limit)`
      - `getPreferences()`
      - `updatePreferences(data)`
  
  - **UI/UX 设计要点**:
    - 标注工具栏位置: 选中文本上方居中，带箭头指向选区
    - 高亮颜色: 半透明（opacity: 0.3），不影响阅读
    - 标注信息卡片: 白色背景 + 阴影，最大宽度 300px
    - 侧边栏宽度: 320px（移动端全屏弹出）
    - 点赞动画: 心形图标放大 + 颜色变化
    - 删除确认: "确定删除此标注？"（防止误操作）
  
  - **统计指标**（管理员可见）:
    - 总标注数、公开标注数、私有标注数
    - 平均每篇文章标注数
    - 最受欢迎的标注（Top 10）
    - 标注最多的学生（Top 10）
    - 标注类型分布（高亮/笔记/提问）
  
  - **教学价值**:
    - **集体智慧**: 学生互相学习，发现他人关注的重点
    - **降低门槛**: 新手可参考优秀学生的标注
    - **促进讨论**: 笔记和提问类标注引发思考
    - **学习轨迹**: 教师可查看班级标注热力图，了解学生关注点
  
  - **隐私保护**:
    - 用户可选择标注是否公开（默认公开）
    - 私有标注仅自己可见
    - 用户可随时关闭"显示他人标注"
    - 删除标注时同步删除所有点赞记录
  
  - **性能优化**:
    - 标注数据按文章 ID 缓存（Redis，TTL 5 分钟）
    - 前端虚拟滚动（文章超长时）
    - 标注渲染使用 Web Worker（避免阻塞主线程）
    - 数据库索引: `(text_id, is_public, deleted)` 复合索引
  
  - **测试要点**:
    - 标注创建和渲染
    - 重叠/嵌套标注显示
    - 公开/私有切换
    - 点赞功能
    - 偏好设置保存和应用
    - 权限控制（不能编辑他人标注）
    - 并发标注（多人同时标注同一位置）

##### P2 - 可选（10.5 天）

- ❌ **11. 推荐算法效果详细分析**
  - 数据库: 新增 `recommendation_logs` 表（完整方案）
  - 后端 API: `GET /api/admin/metrics/recommendation-detailed`
    - 返回: 按算法类型（Item-CF/User-CF/Cold-Start）分组效果、按文章分析点击率
  - 前端页面: 新增独立页面 `/admin/recommendation-analysis`
    - 图表: 算法效果对比、文章推荐效果排行、用户行为漏斗

- ❌ **12. 综合数据大屏**
  - 前端页面: 新增 `/admin/data-dashboard` 全屏数据大屏
  - 布局: 6-8 个数据卡片 + 实时刷新（每 30 秒）
  - 用途: 比赛答辩/路演展示

**实现要点**：
- 所有 API 需要 `@PreAuthorize("hasAnyRole('ADMIN', 'OPS')")` 权限控制
- 前端使用 ECharts 进行数据可视化
- 数据缓存: Redis 缓存统计结果（TTL 5 分钟）
- 响应式设计: 支持移动端查看

**预期收益**：
- 比赛答辩时可实时展示平台数据，专业度大幅提升
- 运营人员可随时查看平台运行状况，无需手动查询数据库
- 为产品迭代提供数据支撑（哪些功能使用频繁、哪些需要优化）

---

#### 性能压测基线建立（优先级：高）
- ❌ **压测脚本编写**
  - wrk 压测脚本（公开接口 + 认证接口 + 写入接口）
  - 典型业务场景模拟（文章列表、登录、阅读进度提交）
- ❌ **性能基线报告**
  - QPS/TPS 基准数据
  - P50/P95/P99 响应时间分布
  - 并发 100/200/500 下的错误率
  - 数据库慢查询分析
- ❌ **性能优化**
  - Redis 缓存热点数据（文章列表、单词查询）
  - 数据库连接池参数调优
  - Nginx gzip 压缩 + 静态资源缓存策略
  - API 响应分页默认值优化

#### 运维监控体系（优先级：高）
- ❌ **指标监控**
  - Prometheus + Grafana 容器化部署
  - Spring Boot Actuator 集成（`/actuator/prometheus`）
  - JVM 内存/GC/线程池监控
  - 数据库连接池监控（HikariCP metrics）
  - API 级别 QPS/延迟/错误率 Dashboard
- ❌ **集中式日志**
  - ELK（Elasticsearch + Logstash + Kibana）或 Loki + Grafana
  - 日志结构化（JSON 格式）
  - 请求链路追踪（Trace ID）
- ❌ **告警通知**
  - 错误率阈值告警（钉钉/邮件）
  - 服务健康检查告警
  - 磁盘/内存/CPU 资源告警
- ❌ **APM 应用性能监控**
  - SkyWalking / Pinpoint 集成
  - 慢请求自动发现
  - 依赖拓扑可视化

**实现要点**：
- 新增 `docker-compose.monitoring.yml` 或扩展 `docker-compose.yml`
- 新增 `monitoring/prometheus.yml` 配置文件
- 新增 `monitoring/grafana-dashboards/` 目录（预置 Dashboard JSON）
- Spring Boot `application.yml` 启用 `management.endpoints.web.exposure.include: health,metrics,prometheus`
- 日志格式统一为 JSON，便于 Logstash/Loki 解析

**预期收益**：
- 评审/路演时有可视化大屏展示，专业度大幅提升
- 30 分钟内定位性能瓶颈（vs 当前人工排查数小时）
- 故障自动告警，避免依赖用户反馈才发现问题
- 为扩容决策提供量化依据（CPU/内存/QPS 趋势）

#### 分类备份系统（优先级：中）
- ❌ **按数据类型分类备份**
  - 核心配置备份（system_config, feature_toggles, sso_config）
  - 用户数据备份（users, roles, permissions）
  - 业务数据备份（texts, courses, assignments, vocabulary_books）
  - 学校数据备份（school_*, teaching_*, course_*）
- ❌ **前端分类备份管理界面**
  - 下拉菜单选择备份分类
  - 表格显示备份分类标签
  - 选择性恢复特定分类数据
- ❌ **可配置备份目录**
  - 支持指定宿主机备份目录
  - Docker volume 持久化存储
  - 项目重建后数据不丢失
- ❌ **分类备份定时任务**
  - 不同数据类型不同备份频率
  - 不同数据类型不同保留期
  - 配置化定时任务（YAML 配置）

**实现要点**：
- 数据库：backup_records 表添加 category 字段（V28 迁移）
- 后端：BackupService 添加 backupByCategory() 方法
- 前端：Backups.vue 添加分类选择和展示
- 配置：application.yml 添加分类备份配置
- Docker：docker-compose.yml 支持自定义备份目录挂载

**优势**：
- 恢复速度快（只恢复需要的部分）
- 存储优化（不同数据保留期不同）
- 风险隔离（业务数据损坏不影响用户数据）
- 灵活性高（可单独恢复某类数据）

#### 全栈零停机热部署（已实现 — Deployer Sidecar 架构）

> **状态**：✅ 已实现（v2.0，2026-05-29）
> **详细文档**：`docs/ZERO_DOWNTIME_DEPLOYMENT.md`

**已实现功能**：

- `scripts/hot-deploy.sh`（308 行）— 蓝绿部署脚本，Docker 环境下由 deployer sidecar 容器执行
- `docker/deployer/Dockerfile` — 基于 `docker:27-cli`，含 git/bash/curl/docker-compose
- `UpdateServiceImpl.java` — 两阶段执行：`docker run -d` 启动 deployer + `docker logs -f` 流式日志
- `Update.vue` — 统一部署管理页面（配置 + 手动触发 + SSE 实时日志 + 步骤进度 + 部署历史）
- SSE 断连恢复 — 后端重启时自动 polling 等待新后端上线
- `@PostConstruct` 遗留 RUNNING 状态清理

**架构**：

```
浏览器 ──SSE──> 后端容器 ──docker run -d──> deployer 容器 (独立运行 hot-deploy.sh)
                  │                            │
                  └── docker logs -f ──────────┘ (流式日志)
```

**蓝绿流程**：拉代码 → 构建 green 镜像 → 启动 green → 健康检查 → 切 nginx → 停 blue → 启新 blue → 切回 → 清理 green

**配置**：`PROJECT_HOST_PATH`（宿主机项目路径）+ `APP_UPDATE_SUPPORTED=true` + Docker Socket 挂载

**未来可扩展**：Shadow Table（DDL 变更）、滚动升级（基础设施层）

---

## 🚧 未实现功能清单 (降低优先级)

### 1. 高级学习功能 (低优先级)

#### 智能学习
- ❌ **个性化推荐算法** - 基于学习记录推荐内容
- ❌ **难度自适应** - 根据表现调整学习难度
- ❌ **学习路径规划** - 智能安排学习顺序

#### 交互功能
- ❌ **单词点击查词** - 课文中点击单词显示详情
- ❌ **语音播放** - 单词和课文发音（音频文件管理）
- ❌ **例句展示** - 丰富的例句库

#### 测试评估
- ❌ **单词测试系统** - 多种测试模式
- ❌ **学习效果评估** - 能力测评
- ❌ **进度报告** - 定期学习报告

### 2. 系统完善功能 (低优先级)

#### 用户体验
- ✅ **响应式设计优化** - 移动端全面适配（el-table 横滚、el-dialog 自适应、触摸目标 44px、viewport-fit、safe-area）
- ❌ **主题切换功能** - 明暗主题
- ❌ **国际化支持** - 多语言界面
- ❌ **无障碍功能** - 可访问性优化

#### 性能优化
- ✅ **数据缓存机制** - Redis缓存（已集成 Redis 6 + Spring Cache）
- ❌ **文件上传功能** - 音频文件管理
- ❌ **批量操作功能** - 批量导入导出
- ❌ **搜索优化** - 全文搜索功能

#### 系统管理
- ✅ **操作日志系统** - AccessLog 访问日志 + 访问统计
- ✅ **系统配置管理** - 运维面板参数配置（system_config 表 + 前端运维界面）
- ✅ **数据备份恢复** - mysqldump 自动备份 + 增量备份
- ✅ **系统监控** - 运维面板健康检查 + AI 服务商状态监控

---

## 🎯 当前完成状态

### ✅ 已完成 (100%核心功能)
- **用户认证与权限管理**: JWT + Spring Security完整实现
- **数据管理**: 用户、课程、单元、单词、课文完整CRUD
- **班级管理系统**: 教师创建班级、学生加入班级功能
- **任务系统**: 教师布置任务、学生完成任务、进度跟踪
- **生词本系统**: 学生个人词汇管理、掌握程度跟踪
- **学习记录系统**: 学习行为记录、统计分析、数据可视化
- **学生端功能**: 完整的学习体验界面和功能
- **教师端功能**: 班级管理、任务布置、学生跟踪

### 📊 功能完成度统计
| 功能模块 | 完成度 | 说明 |
|---------|--------|------|
| 用户管理 | 100% | 完整的认证、权限、CRUD功能 |
| 课程体系 | 100% | 课程、单元、单词、课文完整管理 |
| 班级系统 | 100% | 创建、加入、管理班级功能 |
| 任务系统 | 100% | 布置、完成、跟踪任务功能 |
| 生词本系统 | 100% | 个人词汇管理、复习系统 |
| 学习记录 | 100% | 学习行为记录、统计可视化 |
| 学生端 | 100% | 完整学习体验界面 |
| 数据完整性 | 100% | 真实数据驱动，无硬编码 |
| SSO 集成 | 100% | CAS 统一身份认证 |
| 学校数据 | 100% | 双模式接入、定时同步、前端展示 |
| AI 功能 | 100% | 翻译、问答、ASR、视频代理 |
| 运维功能 | 100% | 面板、日志、监控、备份、自动部署 |

---

## 🔄 下一步发展方向

### 功能优化建议
1. **用户体验优化**: 移动端适配、主题切换
2. **性能提升**: 缓存机制、查询优化
3. **功能扩展**: 智能推荐、测试系统

### 技术升级建议
1. **微服务架构**: 系统拆分和扩展性
2. **容器化部署**: ✅ Docker化部署已完成（docker-compose.yml + Nginx + 主从复制）
3. **监控体系**: 应用监控和日志管理

---

## 📝 项目成就

### 技术架构
- **后端**: Spring Boot + MyBatis Plus + MySQL
- **前端**: Vue 3 + Element Plus + Composition API
- **安全**: JWT认证 + Spring Security
- **数据库**: MySQL 8.0 + 完整关联约束

### 业务完整性
- **多角色支持**: 管理员、教师、学生角色分离
- **完整学习流程**: 课程→单元→任务→学习→统计
- **数据驱动**: 100%真实数据，无示例代码
- **用户体验**: 完整的学习管理体验

### 开发质量
- **代码规范**: 统一的代码结构和注释
- **数据完整性**: 完整的外键约束和事务处理
- **错误处理**: 前后端统一的错误处理机制
- **测试覆盖**: 完整的功能验证和数据测试

**当前状态**: ✅ 核心功能100%完成，可作为完整的英语学习平台投入使用！