# Issue Tracking

问题跟踪文档，记录已知问题、待修复的 Bug、待实现的功能以及技术债务。

最后更新: 2026-04-18

## 环境配置

### 前端环境

技术栈:
- Vue 3.3.4
- Element Plus 2.3.9
- Vue Router 4.2.4
- Pinia 2.1.6
- Vite 4.4.9
- Node.js >= 16.x

安装依赖:
```bash
cd frontend
npm install
```

开发环境启动:
```bash
npm run dev
```

### 后端环境

技术栈:
- Spring Boot 2.7.14
- Java 17
- MyBatis Plus 3.5.3.1
- Spring Security
- JWT 0.11.5
- MySQL 8.0+

环境要求:
- JDK 17+
- Maven 3.6+
- MySQL 8.0+

配置文件:
- `backend/src/main/resources/application.yml` - 主配置文件
- 需要配置数据库连接、JWT密钥等

启动方式:
```bash
cd backend
/usr/bin/mvn spring-boot:run
```

---

## 已知问题

### 高优先级

#### 1. 学校数据 API 认证方式修正
- 状态: 已修复 (2026-04-18)
- 描述: 旧实现使用 `Authorization: Bearer <token>` 请求头，但官方接口文档规定认证方式为 `appId + appSecret + timestamp` 签名
- 影响: API 模式数据同步无法通过认证（学校数据集成不可用）
- 修复:
  - `SchoolDataProperties.ApiProperties`: 删除 `token` 字段，新增 `appId` + `appSecret` + `sign(timestamp)` 方法
  - `SchoolDataApiClientImpl.buildHeaders()`: 改为添加 `appId`/`timestamp`/`sign` 三个请求头
  - 签名公式: `sign = Base64(MD5(appId + appSecret + timestamp))`
  - 环境变量: `SCHOOL_API_TOKEN` → `SCHOOL_API_APP_ID` + `SCHOOL_API_APP_SECRET`
  - DB 配置: `api_token` → `api_app_id` + `api_app_secret`（V20 迁移）
  - 运维面板: Token 输入框 → App ID + App Secret 两个输入框
  - 部署脚本: `scripts/deploy.sh` 新增学校数据向导（交互式配置 + 功能开关自动激活）
- 相关文件:
  - `backend/src/main/java/com/english/config/SchoolDataProperties.java`
  - `backend/src/main/java/com/english/service/impl/SchoolDataApiClientImpl.java`
  - `backend/src/main/java/com/english/service/SchoolDataConfigRefreshService.java`
  - `backend/src/main/resources/db/migration/V20__school_data_api_auth_appid.sql`
  - `frontend/src/views/ops/SchoolDataConfig.vue`
  - `scripts/deploy.sh`, `docker/.env.example`, `docker-compose.yml`

#### 2. 待完成任务计数不准确
- 状态: 已修复
- 描述: 学生端待完成任务数显示不正确，已完成的任务仍被计入
- 影响: 学生端发现页面
- 修复: 后端过滤逻辑中添加状态和进度检查
- 相关文件: 
  - `backend/src/main/java/com/english/controller/StudentLearningController.java`
  - `frontend/src/views/student/Dashboard.vue`

#### 2. 课程进度条显示问题
- 状态: 已修复
- 描述: 学生端课程进度条百分比不准确，无法反映实际学习进度
- 影响: 学生端发现页面和课程页面
- 修复: 
  - 后端优化进度计算逻辑（单元进度60% + 单词进度40%）
  - 前端优先使用后端返回的progress字段
- 相关文件:
  - `backend/src/main/java/com/english/service/impl/StudentClassesServiceImpl.java`
  - `frontend/src/views/student/Dashboard.vue`

#### 3. 任务描述显示为JSON格式
- 状态: 已修复
- 描述: 任务描述字段存储为JSON格式，前端直接显示导致用户看到JSON字符串
- 影响: 学生端任务列表
- 修复: 前端添加JSON检测和解析逻辑
- 相关文件: `frontend/src/views/student/Assignments.vue`

### 中优先级

#### 4. 教师端任务界面截止时间不显示
- 状态: 已修复
- 描述: 教师端任务管理界面中，任务的截止时间字段显示为空
- 影响: 教师端任务管理
- 修复: 
  - 后端确保deadline字段正确保存
  - 前端处理null值显示
- 相关文件:
  - `backend/src/main/java/com/english/controller/AssignmentController.java`
  - `frontend/src/views/teacher/Assignments.vue`

#### 5. 班级界面所属课程和任课教师不显示
- 状态: 已修复
- 描述: 教师端班级管理界面中，所属课程和任课教师信息显示不正确
- 影响: 教师端班级管理
- 修复: 后端确保关联对象正确加载和返回
- 相关文件: `backend/src/main/java/com/english/service/impl/ClassServiceImpl.java`

### 低优先级

#### 6. 调试日志过多
- 状态: 已优化
- 描述: 代码中存在大量System.out.println调试日志
- 影响: 生产环境日志混乱
- 修复: 已将System.out.println替换为SLF4J Logger，使用logger.debug/warn/error进行分级日志记录
- 相关文件: 
  - `backend/src/main/java/com/english/controller/StudentLearningController.java`
  - `backend/src/main/java/com/english/service/impl/ScoreRecordServiceImpl.java`
  - `backend/src/main/java/com/english/service/impl/LearningRecordsServiceImpl.java`
  - `backend/src/main/java/com/english/service/impl/ClassServiceImpl.java`
  - `backend/src/main/java/com/english/controller/AssignmentController.java`

#### 7. 今日学习时长显示异常
- 状态: 已修复
- 描述: 学生端今日学习时长显示异常（如384分钟），可能包含异常数据
- 影响: 学生端发现页面
- 修复: 
  - 修复了学习时长聚合查询逻辑
  - 添加异常值过滤（单次学习超过2小时不计算）
  - 添加总时长合理性检查（单日超过12小时记录警告）
- 相关文件: `backend/src/main/java/com/english/service/impl/LearningRecordsServiceImpl.java`

#### 8. 教师导出成绩加权平均分计算错误
- 状态: 已修复
- 描述: 教师导出学生成绩时，加权平均分计算不正确，0分任务未被计入
- 影响: 教师端成绩导出功能
- 修复: 
  - 修复加权平均分计算公式：Σ(分数 × 权重) / Σ(权重)
  - 确保只计算有分数记录的任务（包括0分任务）
  - 未开始的任务不计入计算
- 相关文件: `backend/src/main/java/com/english/service/impl/ScoreRecordServiceImpl.java`

#### 9. 复习模式未实现
- 状态: 已实现
- 描述: 学生端课程页面中的复习模式功能
- 影响: 学生端课程学习
- 实现: 已实现单词复习功能，支持卡片翻转、进度跟踪和掌握程度更新
- 相关文件: `frontend/src/views/student/Courses.vue`

---

## 待实现功能

### 高优先级

#### 1. 单元页面完善
- 状态: 已实现
- 描述: 用户端单元页面功能
- 实现: 已创建UserUnits页面，显示单元列表、课文和单词数量，支持导航到单元内容
- 相关文件: 
  - `frontend/src/views/user/Units.vue` (新建)
  - `frontend/src/views/user/Courses.vue`
  - `frontend/src/router/index.js`

### 中优先级

#### 2. 智能学习功能
- 状态: 待实现
- 描述: 
  - 个性化推荐算法
  - 难度自适应
  - 学习路径规划
- 优先级: 低

#### 3. 交互功能增强
- 状态: 待实现
- 描述:
  - 单词点击查词功能
  - 语音播放功能（音频文件管理）
  - 例句展示功能
- 优先级: 低

#### 4. 测试评估系统
- 状态: 待实现
- 描述:
  - 单词测试系统（多种测试模式）
  - 学习效果评估
  - 进度报告生成
- 优先级: 低

### 低优先级

#### 5. 用户体验优化
- 状态: 部分完成
- 描述:
  - ✅ 响应式设计优化（移动端全面适配）
  - ❌ 国际化支持（多语言界面）
  - ❌ 无障碍功能（可访问性优化）
- 优先级: 低

#### 6. 性能优化
- 状态: 部分完成
- 描述:
  - ✅ Redis缓存机制（已集成 Redis 6 + Spring Cache）
  - ❌ 文件上传功能（音频文件管理）
  - ❌ 批量操作功能（批量导入导出）
  - ❌ 全文搜索功能
- 优先级: 低

#### 7. 系统管理功能
- 状态: 已实现
- 描述:
  - ✅ 操作日志系统（AccessLog + 访问统计）
  - ✅ 系统配置管理界面（运维面板 system_config 表）
  - ✅ 数据备份恢复功能（mysqldump 自动备份 + 增量备份）
  - ✅ 系统监控面板（运维面板 + AI 服务商状态监控）
- 优先级: 已完成

---

## 技术债务

### 代码质量

#### 1. 硬编码数据清理
- 状态: 已修复
- 描述: 之前存在硬编码的统计数据，现已全部替换为真实数据库查询
- 相关文件: 已全面修复

#### 2. 错误处理不统一
- 状态: 待改进
- 描述: 不同模块的错误处理方式不一致
- 建议: 统一错误处理机制，使用全局异常处理器

#### 3. API响应格式统一
- 状态: 部分完成
- 描述: 大部分API已使用统一的Result响应格式，但仍有部分需要检查
- 建议: 全面审查所有API接口，确保响应格式一致

### 数据库

#### 4. 数据库索引优化
- 状态: 待优化
- 描述: 部分查询可能存在性能问题，需要添加适当的数据库索引
- 建议: 分析慢查询日志，优化索引策略

#### 5. 数据库连接池配置
- 状态: 待检查
- 描述: 需要确认数据库连接池配置是否适合生产环境
- 建议: 根据实际负载调整连接池参数

### 安全性

#### 6. 敏感信息保护
- 状态: 部分改善
- 描述: .env 文件包含明文凭据，已在 .gitignore 中但需确保不被提交
- 建议: 生产环境使用 Docker secrets 或 Vault；.env 文件权限设为 600

#### 7. API权限验证
- 状态: 基本完成
- 描述: 大部分API已实现权限验证，但需要定期审查
- 建议: 定期进行安全审计

---

## 性能问题

### 1. 课程进度计算性能
- 状态: 已优化
- 描述: 课程进度计算涉及多表关联查询，可能影响性能
- 优化: 已优化查询逻辑，使用合理的索引

### 2. 任务进度自动刷新
- 状态: 已优化
- 描述: 教师端任务进度对话框每秒自动刷新，可能造成服务器压力
- 优化: 考虑增加刷新间隔或使用WebSocket推送

### 3. 前端资源加载
- 状态: 待优化
- 描述: 前端打包后文件较大，首次加载可能较慢
- 建议: 
  - 代码分割和懒加载
  - 资源压缩和CDN加速
  - 图片优化

---

## 安全问题

### 1. JWT Token过期处理
- 状态: 已实现
- 描述: JWT Token过期后的处理机制
- 验证: 需要确认前端是否正确处理Token过期情况

### 2. SQL注入防护
- 状态: 已防护
- 描述: 使用MyBatis参数化查询，已基本防护SQL注入
- 建议: 定期进行安全测试

### 3. XSS攻击防护
- 状态: 待检查
- 描述: 用户输入内容需要转义处理
- 建议: 使用Vue的内置转义机制，对富文本内容使用安全的渲染方式

---

## 代码注释和文档

### 1. API文档完善
- 状态: 部分完成
- 描述: 已有API_DOCUMENTATION.md，但需要持续更新
- 建议: 使用Swagger/OpenAPI自动生成API文档

### 2. 代码注释
- 状态: 待改进
- 描述: 部分复杂逻辑缺少注释
- 建议: 为关键业务逻辑添加详细注释

### 3. 开发文档
- 状态: 基本完成
- 描述: 已有README、TODO、OPTIMIZATION_DOCUMENTATION等文档
- 建议: 保持文档与代码同步更新

---

## 问题统计

| 类型 | 总数 | 已解决 | 进行中 | 待处理 |
|------|------|--------|--------|--------|
| 高优先级Bug | 3 | 3 | 0 | 0 |
| 中优先级Bug | 2 | 2 | 0 | 0 |
| 低优先级Bug | 4 | 3 | 0 | 1 |
| 待实现功能 | 7 | 4 | 0 | 3 |
| 技术债务 | 7 | 3 | 0 | 4 |
| 性能问题 | 3 | 2 | 0 | 1 |
| 安全问题 | 3 | 2 | 0 | 1 |

总计: 29个问题，已解决19个，待处理10个

---

## 更新日志

### 2026-04-18
- 全面排查项目问题，修复关键 Bug 和配置不一致
- 修复 application.yml YAML `//` 行内注释导致 CORS 配置失效
- 修复 V16 Flyway 迁移脚本 DELIMITER 不兼容问题（改用 PREPARED STATEMENT）
- 修复 auto-update.sh 中 `$DOCKER_DIR` 未定义导致健康检查静默失败
- 添加 frontend nginx.conf / nginx-ssl.conf 缺失的 /tutor 代理位置
- 同步根目录 docker-compose.yml 缺失的环境变量（SSO_CAS_SERVER_NAME, ADMIN_LOGIN_SECRET, VITE_ADMIN_*）
- 同步 docker/.env 缺失的 APP_BASE_URL 和 SCHOOL_DATA_* 变量
- 移动根目录散落文档（SCHOOL_DATA_*.MD → docs/），删除调试文件（111.txt）
- 更新 CLAUDE.md：修正 USER 路由、添加 SSO/学校数据/AI 功能文档
- 更新 TODO.md：标记已实现的 Docker/Redis/运维功能，添加后续新增功能

### 2025-12-29
- 修复今日学习时长计算异常问题
- 修复教师导出成绩加权平均分计算错误
- 优化调试日志，将System.out.println替换为SLF4J Logger
- 实现复习模式功能
- 完善单元页面功能
- 添加Logger导入修复（AssignmentController, LearningRecordsServiceImpl, ClassServiceImpl, ScoreRecordServiceImpl）
- 更新.gitignore，忽略backend/D:\JavaCode\mavnresp文件夹

### 历史修复记录
- 课程进度条显示问题已修复
- 待完成任务计数问题已修复
- 任务描述JSON显示问题已修复
- 教师端截止时间显示问题已修复
- 班级信息显示问题已修复
- 硬编码数据清理完成
- 今日学习时长显示异常已修复
- 教师导出成绩加权平均分计算错误已修复
- 调试日志优化完成
- 复习模式功能已实现
- 单元页面功能已完善

---

## 问题反馈

如发现新的问题或需要添加新的功能需求，请：
1. 在本文档中添加问题描述
2. 标注优先级和影响范围
3. 记录相关文件和代码位置
4. 更新问题统计表格

## 相关文档

- [README.md](../README.md) - 项目概述和快速开始
- [TODO.md](../TODO.md) - 待实现功能清单
- [CLAUDE.md](../CLAUDE.md) - AI 开发助手上下文
- [API接口文档.md](./API接口文档.md) - API接口文档
- [TROUBLESHOOTING.md](./TROUBLESHOOTING.md) - 故障排除指南
- [DEPLOYMENT.md](./DEPLOYMENT.md) - 部署指南
- [DATABASE_COLLABORATION_GUIDE.md](./DATABASE_COLLABORATION_GUIDE.md) - 数据库协作规范
