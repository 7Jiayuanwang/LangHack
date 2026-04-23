# LangHack 项目文档

## 📁 文档目录

### 🚀 开发与协作
- **[DEVELOPMENT-GUIDE.md](./DEVELOPMENT-GUIDE.md)** ⭐ 推荐首读
  - 开发流程、代码规范、测试约定
  - 当前分支协作方式与发布习惯
  - 常见开发问题排查

### 🔐 统一身份认证
- **[01-统一身份认证集成开发指南.md](./01-统一身份认证集成开发指南.md)**
  - CAS / OAuth 2.0 集成背景
  - 角色映射、绑定与审核机制
- **[sso/](./sso/)**
  - SSO 快速参考、实现说明与问题定位

### 📊 学校数据接入
- **[02-学校数据接入开发指南.md](./02-学校数据接入开发指南.md)**
  - API / ETL 双模式接入
  - 字段映射、同步流程与运维配置
- **[SCHOOL_DATA_INTEGRATION.md](./SCHOOL_DATA_INTEGRATION.md)**
  - 学校数据整体架构与同步设计
- **[SCHOOL_DATA_API_SPEC.md](./SCHOOL_DATA_API_SPEC.md)**
  - API 字段、签名认证、端点规范
- **[SCHOOL_DATA_FIX.md](./SCHOOL_DATA_FIX.md)** / **[SCHOOL_DATA_API_FIX.md](./SCHOOL_DATA_API_FIX.md)**
  - 历史兼容性问题与修复记录

### 📚 其他文档
- **[DEPLOYMENT.md](./DEPLOYMENT.md)** - 部署、环境变量与运维指南
- **[DEPLOYMENT-LESSONS-LEARNED.md](./DEPLOYMENT-LESSONS-LEARNED.md)** - 部署案例与踩坑记录
- **[TROUBLESHOOTING.md](./TROUBLESHOOTING.md)** - 常见问题排查
- **[API接口文档.md](./API接口文档.md)** - REST API 说明
- **[DATABASE_SYNC_GUIDE.md](./DATABASE_SYNC_GUIDE.md)** - 数据库同步与迁移指南

---

## 🎯 开发目标

### 统一身份认证
- ✅ 实现单点登录功能
- ✅ 角色分配和识别
- ✅ 用户绑定、审核与错误处理
- ✅ 管理员入口和运维配置支持

### 数据接入
- ✅ 支持 API / ETL 双模式
- ✅ 支持字段映射与转换
- ✅ 支持全量 / 增量 / 链式拉取同步
- ✅ 支持监控、告警与运行时配置

---

## 🚀 快速开始

### 1. 环境准备
```bash
java -version
mvn -version
node -v
npm -v
```

### 2. 关键配置
```bash
# 学校数据 API 模式
export SCHOOL_DATA_SOURCE=api
export SCHOOL_API_APP_ID=your_app_id
export SCHOOL_API_APP_SECRET=your_app_secret
```

### 3. 启动开发环境
```bash
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```

---

## 📋 当前文档重点

1. **SSO**：已覆盖删除用户处理、审核流、错误展示与回调处理。
2. **学校数据**：认证方式已切换为 `appId + appSecret + timestamp` 签名，不再使用 Bearer Token。
3. **链式拉取**：已支持按学院链式同步课程 → 教师 → 学生 → 绑定，并支持自动授权。
4. **前端布局**：当前以移动端底部导航为主，admin / teacher / user / ops 共用 `MobileLayout`，student 独立布局。

---

## 🔧 技术栈

### 后端
- **框架**：Spring Boot 2.7+
- **安全**：Spring Security + JWT
- **数据库**：MySQL 8.0+
- **缓存**：Redis
- **构建工具**：Maven

### 前端
- **框架**：Vue 3
- **UI组件**：Element Plus
- **状态管理**：Pinia
- **构建工具**：Vite
- **测试**：Vitest + @vue/test-utils

---

## 📞 技术支持

### 常见问题方向
1. **SSO 配置问题**：检查回调 URL、角色映射和错误日志
2. **数据同步失败**：查看同步日志、字段映射与认证配置
3. **字段映射错误**：核对端点返回字段大小写与别名映射

---

## 📝 维护说明

更新文档时优先同步以下内容：
1. `README.md` 总览
2. `CLAUDE.md` 开发上下文
3. `docs/README.md` 索引
4. 对应专题文档（如 SSO / 学校数据 / 部署）

---

## 📄 许可证

本项目许可证请以仓库根目录中的实际许可证文件为准。
