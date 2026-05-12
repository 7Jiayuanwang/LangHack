# LangHack 文档索引

本目录包含 LangHack 项目的所有技术文档。

---

## 📋 核心文档

### 项目文档
- [API 接口文档](API接口文档.md) - 后端 REST API 接口说明
- [部署文档](DEPLOYMENT.md) - Docker Compose 部署指南
- [故障排查](TROUBLESHOOTING.md) - 常见问题排查
- [数据库协作规范](DATABASE_COLLABORATION_GUIDE.md) - 数据库表登记与变更规范

### AI 集成
- [AI 模型提供商集成文档](AI模型提供商集成文档.md) - HiAgent / OpenRouter / SiliconFlow / 腾讯云 ASR

### SSO 集成
- [SSO 集成文档](sso/) - CAS 统一身份认证集成（5 份文档）

---

## 🔧 最新修复（2026-05-05）

### 完整修复报告
- **[生产环境问题修复完整报告](FIXES_2026-05-05_COMPLETE.md)** ⭐
  - P0/P1/P2/P3/P4 全部问题修复
  - 数据库 Schema 修复（V15）
  - 学生数据映射修复
  - 性能优化（50%+ 提升）
  - 运维功能增强

### 详细文档
- [Schema 修复详细文档](SCHEMA_FIX_2026-05-05.md) - V15 迁移脚本详解
- [性能优化详情](../PERFORMANCE_OPTIMIZATION.md) - 文章详情页 5 处优化

### 验证脚本
- [verify_v15.sql](verify_v15.sql) - 12 个自动化验证测试

---

## 🛠️ 运维功能

### 数据浏览
- [运维数据浏览使用指南](SCHOOL_DATA_BROWSER_GUIDE.md) - 筛选、导出功能使用说明
- [技术实现总结](SCHOOL_DATA_BROWSER_IMPLEMENTATION.md) - 前后端实现细节
- [测试文档](SCHOOL_DATA_BROWSER_TEST.md) - 功能测试清单

### Docker 镜像同步
- [Docker 镜像同步工具](DOCKER_IMAGE_SYNC.md) - 本地 registry 镜像同步、内网部署

---

## 📚 历史修复记录

### 学校数据集成
- [学校数据修复记录](SCHOOL_DATA_FIX.md) - 学校数据集成问题修复文档
- [学校数据 API 修复](SCHOOL_DATA_API_FIX.md) - API 端点配置修复
- [CDSP 字段映射修复](CDSP_FIELD_MAPPING_FIX_2026-04-27.md) - 字段映射问题修复
- [CDSP 分页修复](CDSP_PAGINATION_FIX_2026-04-27.md) - 分页参数修复
- [字段映射审计](FIELD_MAPPING_AUDIT_20260428.md) - 字段映射完整性审计
- [学校数据同步修复](SCHOOL_DATA_SYNC_FIX_20260428.md) - 同步逻辑修复
- [学校数据修复总结](SCHOOL_DATA_FIX_SUMMARY_20260428.md) - 2026-04-28 修复总结

### ETL 模式
- [ETL 模式完成总结](ETL_MODE_COMPLETION_SUMMARY.md) - ETL 模式实现总结
- [ETL 模式设置指南](ETL_MODE_SETUP.md) - 配置指南
- [ETL 视图创建脚本](etl/create_views.sql) - 数据库视图创建
- [ETL 字段映射指南](etl/field_mapping_guide.md) - 字段映射说明

### 其他修复
- [硬编码域名修复](HARDCODED_DOMAIN_FIX.md) - 域名配置化修复
- [SSO 删除用户修复](SSO_DELETED_USER_FIX.md) - SSO 用户删除问题修复

---

## 📊 审计报告

### 部署脚本
- [部署脚本审计报告](../scripts/DEPLOYMENT_SCRIPTS_AUDIT.md) - deploy.sh 和 auto-update.sh 审计

---

## 🔍 快速查找

### 按问题类型
- **数据库问题**: SCHEMA_FIX_2026-05-05.md, verify_v15.sql
- **性能问题**: ../PERFORMANCE_OPTIMIZATION.md
- **学校数据同步**: SCHOOL_DATA_FIX.md, SCHOOL_DATA_SYNC_FIX_20260428.md
- **SSO 问题**: sso/, SSO_DELETED_USER_FIX.md
- **运维功能**: SCHOOL_DATA_BROWSER_GUIDE.md

### 按日期
- **2026-05-05**: FIXES_2026-05-05_COMPLETE.md ⭐
- **2026-04-28**: SCHOOL_DATA_FIX_SUMMARY_20260428.md
- **2026-04-27**: CDSP_FIELD_MAPPING_FIX_2026-04-27.md, CDSP_PAGINATION_FIX_2026-04-27.md

---

## 📝 文档规范

### 命名规范
- 修复文档: `{TOPIC}_FIX_YYYY-MM-DD.md`
- 功能文档: `{FEATURE}_GUIDE.md`
- 实现文档: `{FEATURE}_IMPLEMENTATION.md`
- 测试文档: `{FEATURE}_TEST.md`

### 文档结构
1. 问题描述 / 功能概述
2. 根因分析 / 技术方案
3. 修复方案 / 实现细节
4. 验证方法 / 测试清单
5. 部署步骤 / 使用指南

---

**最后更新**: 2026-05-05
