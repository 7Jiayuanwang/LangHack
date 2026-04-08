# LangHack 项目开发文档

## 📁 文档目录

### 🚀 开发指南
- **[DEVELOPMENT-GUIDE.md](./DEVELOPMENT-GUIDE.md)** ⭐ **推荐首读**
  - 分支管理策略（master/develop 双分支工作流）
  - 开发工作流程（功能开发/Bug修复/紧急修复/发布流程）
  - 代码规范（后端 Java + 前端 Vue 3）
  - 测试规范（Vitest + JUnit）
  - 常见问题解答（合并冲突、cherry-pick vs merge、部署问题等）

### 🔐 统一身份认证集成
- **[01-统一身份认证集成开发指南.md](./01-统一身份认证集成开发指南.md)**
  - 支持CAS 2.0和OAuth 2.0双协议
  - 基于memberOf的自动角色识别
  - 用户绑定和管理员确认机制
  - 开发周期：5-7个工作日

### 📊 学校数据接入
- **[02-学校数据接入开发指南.md](./02-学校数据接入开发指南.md)**
  - 支持API和ETL两种接入方式
  - 灵活的字段映射配置
  - 全量和增量数据同步
  - 开发周期：3-5个工作日

### 📚 其他文档
- **[DEPLOYMENT.md](./DEPLOYMENT.md)** - 部署指南与配置说明
- **[DEPLOYMENT-LESSONS-LEARNED.md](./DEPLOYMENT-LESSONS-LEARNED.md)** - 部署问题排查案例库
- **[TROUBLESHOOTING.md](./TROUBLESHOOTING.md)** - 常见问题与故障排除
- **[API接口文档.md](./API接口文档.md)** - REST API 端点详细说明
- **[DATABASE_SYNC_GUIDE.md](./DATABASE_SYNC_GUIDE.md)** - 数据库同步与迁移指南

---

## 🎯 开发目标

### 统一身份认证
- ✅ 实现单点登录功能
- ✅ 自动角色分配和识别
- ✅ 管理员确认机制
- ✅ 完整的错误处理

### 数据接入
- ✅ 用户信息同步
- ✅ 组织关系同步
- ✅ 字段映射和转换
- ✅ 监控和告警机制

---

## 🚀 快速开始

### 1. 环境准备
```bash
# 检查Java版本
java -version

# 检查Maven版本
mvn -version

# 克隆项目
git clone <repository-url>
cd LangHack
```

### 2. 配置环境变量
```bash
# 统一身份认证配置
export SSO_PROTOCOL=oauth2
export SSO_CLIENT_ID=your_client_id
export SSO_CLIENT_SECRET=your_client_secret

# 数据接入配置
export SCHOOL_DATA_SOURCE=api
export SCHOOL_API_TOKEN=your_api_token
```

### 3. 启动开发环境
```bash
# 启动后端服务
mvn spring-boot:run -Dspring.profiles.active=dev

# 启动前端服务
cd frontend
npm install
npm run dev
```

---

## 📋 开发流程

### 阶段一：统一身份认证（优先）
1. 阅读开发指南
2. 配置开发环境
3. 实现核心功能
4. 编写测试用例
5. 部署验证

### 阶段二：数据接入（后续）
1. 获取数据接入权限
2. 配置字段映射
3. 实现同步机制
4. 监控和告警
5. 数据质量验证

---

## 🔧 技术栈

### 后端
- **框架**：Spring Boot 2.7+
- **安全**：Spring Security
- **数据库**：MySQL 8.0+
- **缓存**：Redis
- **构建工具**：Maven

### 前端
- **框架**：Vue 3+
- **UI组件**：Element Plus
- **状态管理**：Pinia
- **构建工具**：Vite

---

## 📞 技术支持

### 常见问题
1. **SSO配置问题**：检查环境变量和回调URL
2. **数据同步失败**：查看同步日志和错误信息
3. **字段映射错误**：验证字段名称和类型匹配

### 联系方式
- **技术负责人**：[负责人信息]
- **文档维护**：[维护人员信息]
- **问题反馈**：[反馈渠道]

---

## 📝 更新日志

### v1.0.0 (2024-04-08)
- ✅ 创建统一身份认证集成开发指南
- ✅ 创建学校数据接入开发指南
- ✅ 完善项目文档结构

---

## 📄 许可证

本项目采用 [MIT License](../LICENSE) 许可证。

---

> 💡 **提示**：建议按照文档顺序进行开发，先完成统一身份认证，再实施数据接入功能。
