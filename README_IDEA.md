# IntelliJ IDEA 导入指南

## 🎯 推荐方式：直接打开项目

### 步骤 1：打开项目

1. 启动 IntelliJ IDEA
2. 选择 `File` → `Open` 或点击欢迎页的 "Open"
3. 浏览到：`C:\Users\COLORFUL\Desktop\english-learning-full\backend`
4. 选择 **backend** 文件夹，点击 "OK"

### 步骤 2：等待 Maven 导入

- IDEA 会自动识别 Maven 项目
- 右下角提示："Maven projects need to be imported"
- 点击 **"Import Changes"** 或 **"Enable Auto-Import"**
- 等待依赖下载完成（可能需要几分钟）

### 步骤 3：配置 JDK

1. `File` → `Project Structure` → `Project`
2. SDK：选择或添加 **JDK 17**
3. Language Level：选择 **17**
4. 点击 "OK"

### 步骤 4：配置数据库

1. 打开文件：`src/main/resources/application.yml`
2. 修改数据库密码（如果需要）：

```yaml
spring:
  datasource:
    username: root
    password: 你的MySQL密码  # 修改这里
```

### 步骤 5：创建并导入数据库

**方式 A：使用 IDEA Database 工具**

1. 打开 Database 工具窗口：`View` → `Tool Windows` → `Database`
2. 点击 "+" → "Data Source" → "MySQL"
3. 配置连接：
   - Host: localhost
   - Port: 3306
   - User: root
   - Password: 你的密码
4. 点击 "Test Connection" 测试
5. 在 SQL Console 中执行：

```sql
CREATE DATABASE english_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

6. 导入数据：
   - 右键数据库 → "Run SQL Script"
   - 选择：`C:\Users\COLORFUL\Desktop\english_learning.sql`

**方式 B：使用命令行**

在 IDEA 的 Terminal 中执行：

```bash
mysql -u root -p
# 输入密码后执行：
CREATE DATABASE english_learning CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
exit

# 导入数据
mysql -u root -p english_learning < C:\Users\COLORFUL\Desktop\english_learning.sql
```

### 步骤 6：运行项目

1. 找到主类：`EnglishLearningPlatformApplication.java`
   - 位置：`src/main/java/com/english/EnglishLearningPlatformApplication.java`

2. 运行方式：
   - **方式 1**：右键文件 → `Run 'EnglishLearningPlatformApplication'`
   - **方式 2**：点击类名旁边的绿色运行按钮 ▶
   - **方式 3**：使用快捷键 `Shift + F10`

3. 查看控制台输出：
   - 看到 "Started EnglishLearningPlatformApplication in X seconds"
   - 后端启动成功！访问：http://localhost:8080

---

## 🔄 作为模块添加到现有项目

如果您想将此项目作为模块添加到现有的 IDEA 项目：

### 步骤：

1. 打开现有的 IDEA 项目

2. `File` → `New` → `Module from Existing Sources...`

3. 浏览并选择：
   - `C:\Users\COLORFUL\Desktop\english-learning-full\backend\pom.xml`

4. 选择导入方式：
   - 选择 "Import module from external model"
   - 选择 "Maven"
   - 点击 "Create"

5. 完成导入并等待依赖下载

---

## 🎨 前端开发

### 在 IDEA 中打开前端项目

IDEA 也支持前端开发（需要安装插件）：

1. **安装 Vue.js 插件**
   - `File` → `Settings` → `Plugins`
   - 搜索 "Vue.js"
   - 安装并重启 IDEA

2. **打开前端目录**
   - 在 IDEA 的 Project 面板中
   - 右键 `frontend` 文件夹
   - 选择 "Open in" → "Terminal"

3. **安装依赖**
   ```bash
   npm install
   ```

4. **运行前端**
   ```bash
   npm run dev
   ```

5. **创建运行配置**（可选）
   - `Run` → `Edit Configurations...`
   - 点击 "+" → "npm"
   - Name: "Frontend Dev Server"
   - Package.json: 选择 frontend/package.json
   - Command: run
   - Scripts: dev
   - 点击 "OK"

### 前端访问地址

启动后访问：http://localhost:3000

---

## ⚙️ IDEA 推荐设置

### 1. 启用 Auto-Import

`File` → `Settings` → `Editor` → `General` → `Auto Import`
- ✅ Add unambiguous imports on the fly
- ✅ Optimize imports on the fly

### 2. 配置 Maven

`File` → `Settings` → `Build, Execution, Deployment` → `Build Tools` → `Maven`
- Maven home path: 使用 bundled Maven 或指定本地 Maven
- User settings file: 如需要可指定自定义 settings.xml
- ✅ Always update snapshots
- ✅ Download sources
- ✅ Download documentation

### 3. 配置代码格式化

`File` → `Settings` → `Editor` → `Code Style` → `Java`
- 可以导入项目的代码风格配置（如果有）

### 4. 启用 Lombok 支持（如果项目使用）

`File` → `Settings` → `Plugins`
- 搜索并安装 "Lombok"
- `File` → `Settings` → `Build, Execution, Deployment` → `Compiler` → `Annotation Processors`
- ✅ Enable annotation processing

---

## 🐛 常见问题

### 问题 1：Maven 依赖下载失败

**解决方案**：
1. 检查网络连接
2. 配置 Maven 镜像（阿里云）：
   - 编辑 Maven settings.xml
   - 添加阿里云镜像配置
3. `右键 pom.xml` → `Maven` → `Reload Project`

### 问题 2：找不到 JDK 17

**解决方案**：
1. 下载并安装 JDK 17
2. 在 IDEA 中添加：
   - `File` → `Project Structure` → `SDKs`
   - 点击 "+" → "Add JDK..."
   - 选择 JDK 17 安装路径

### 问题 3：数据库连接失败

**解决方案**：
1. 确认 MySQL 服务正在运行
2. 检查 application.yml 中的数据库配置
3. 确认数据库已创建
4. 检查用户名和密码是否正确

### 问题 4：端口 8080 已被占用

**解决方案**：
1. 修改 `application.yml` 中的端口：
   ```yaml
   server:
     port: 8081  # 改为其他端口
   ```
2. 或关闭占用 8080 端口的程序

---

## 📚 项目结构

```
backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/english/
│   │   │       ├── config/              # 配置类
│   │   │       ├── controller/          # 控制器
│   │   │       ├── service/             # 服务层
│   │   │       ├── mapper/              # 数据访问层
│   │   │       ├── entity/              # 实体类
│   │   │       ├── dto/                 # 数据传输对象
│   │   │       └── EnglishLearningPlatformApplication.java  # 主类
│   │   └── resources/
│   │       ├── mapper/                  # MyBatis XML
│   │       └── application.yml          # 配置文件
│   └── test/                            # 测试代码
└── pom.xml                              # Maven 配置

frontend/
├── src/
│   ├── api/                             # API 接口
│   ├── components/                      # 组件
│   ├── views/                           # 页面
│   ├── router/                          # 路由
│   ├── store/                           # 状态管理
│   └── utils/                           # 工具类
├── package.json                         # npm 配置
└── vite.config.js                       # Vite 配置
```

---

## 🚀 开发工作流

### 1. 每日开发流程

1. 打开 IDEA
2. 点击运行按钮启动后端
3. 在 Terminal 中启动前端：`npm run dev`
4. 开始开发！

### 2. 调试技巧

**后端调试**：
- 在代码行号旁点击设置断点（红点）
- 使用 Debug 模式运行（绿色虫子图标）

**前端调试**：
- 使用浏览器开发者工具（F12）
- 查看 Console 和 Network 选项卡

### 3. 快捷键

- `Ctrl + N` - 查找类
- `Ctrl + Shift + N` - 查找文件
- `Ctrl + Alt + L` - 格式化代码
- `Shift + F10` - 运行
- `Shift + F9` - 调试
- `Alt + Enter` - 快速修复

---

## 📖 更多资源

- [Spring Boot 官方文档](https://spring.io/projects/spring-boot)
- [MyBatis Plus 官方文档](https://baomidou.com/)
- [Vue 3 官方文档](https://vuejs.org/)
- [Element Plus 组件库](https://element-plus.org/)

---

## ✅ 检查清单

导入项目后，确认以下内容：

- [ ] Maven 依赖全部下载完成
- [ ] JDK 17 配置正确
- [ ] application.yml 数据库配置正确
- [ ] 数据库已创建并导入数据
- [ ] 后端启动成功（控制台无错误）
- [ ] 前端依赖安装完成（npm install）
- [ ] 前端启动成功（http://localhost:3000 可访问）
- [ ] 可以正常登录（admin/admin123）

全部完成？恭喜！您已成功在 IDEA 中部署项目！🎉
