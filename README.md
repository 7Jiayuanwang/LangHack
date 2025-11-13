# English Learning Platform
一个基于 Vue3 + Spring Boot + MyBatis 的英语学习平台，提供学生、教师和管理员三端功能。

## 项目简介
本平台是一个全面的英语学习解决方案，支持课程管理、学习任务分配、单词学习和生词管理等功能。

## 功能模块

### 管理员端
- **课程管理**: 创建和管理课程、单元
- **内容管理**: 添加课文和单词到相应单元
- **班级管理**: 创建班级并绑定教师和学生
- **权限管理**: 管理用户角色和权限

### 教师端
- **任务布置**: 选择单元内容为班级布置学习任务
- **进度跟踪**: 查看学生学习进度
- **内容选择**: 从已有课文和单词库中选择教学内容

### 学生端
- **内容学习**: 浏览课文和单词
- **单词查询**: 点击单词查看详细讲解
- **生词本**: 收藏和管理生词
- **单词记忆**: 支持单元学习和生词本学习两种模式

## 技术栈

### 前端
- **Vue 3** - 前端框架
- **Vue Router** - 路由管理
- **Pinia** - 状态管理
- **Element Plus** - UI组件库
- **Axios** - HTTP请求
- **Vite** - 构建工具

### 后端
- **Spring Boot 2.7+** - 后端框架
- **Spring Security** - 安全认证
- **MyBatis Plus** - ORM框架
- **MySQL 8.0** - 数据库
- **Maven** - 依赖管理
- **JWT** - Token认证

### 数据库
- **MySQL 8.0**

## 🚀 快速启动

### 第一步：环境检查
```bash
# 运行环境检查脚本
./check-env.sh
```

**必需环境：**
- JDK 11+
- Maven 3.6+
- Node.js 16+
- MySQL 8.0+

### 第二步：一键启动 (推荐)
```bash
# 使用快速启动脚本
./quick-start.sh
```

### 第三步：访问应用
- **前端地址**: http://localhost:3000
- **后端地址**: http://localhost:8080

### 默认账户
| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | admin123 | 管理员 |

## 📋 其他启动方式

### Windows用户
```cmd
# 双击运行或在命令行执行
start.bat
```

### 手动启动
1. **数据库配置**：
   ```bash
   # 创建数据库
   mysql -u root -p -e "CREATE DATABASE english_learning;"

   # 初始化数据
   mysql -u root -p english_learning < docs/database/init.sql
   ```

2. **修改数据库连接**：
   编辑 `backend/src/main/resources/application.yml` 中的数据库密码

3. **启动后端**：
   ```bash
   cd backend
   mvn spring-boot:run
   ```

4. **启动前端**：
   ```bash
   cd frontend
   npm install
   npm run dev
   ```

## 🛠️ 环境安装
如果环境检查失败，请参考：
- Ubuntu/Debian: `./install-env-ubuntu.sh`
- 详细指南: [DETAILED_RUN_GUIDE.md](DETAILED_RUN_GUIDE.md)

## 🧪 测试接口
```bash
# 测试后端接口
./test-backend.sh
```

## 项目结构

```
english-learning-platform/
├── backend/                    # Spring Boot 后端
│   ├── src/
│   │   └── main/
│   │       ├── java/
│   │       │   └── com/english/
│   │       │       ├── controller/     # 控制器层
│   │       │       ├── service/        # 服务层
│   │       │       ├── mapper/         # MyBatis Mapper
│   │       │       ├── entity/         # 实体类
│   │       │       ├── dto/            # 数据传输对象
│   │       │       └── config/         # 配置类
│   │       └── resources/
│   │           ├── mapper/             # MyBatis XML
│   │           └── application.yml
│   └── pom.xml
├── frontend/                   # Vue3 前端
│   ├── public/
│   ├── src/
│   │   ├── components/         # 公共组件
│   │   ├── views/              # 页面组件
│   │   │   ├── admin/          # 管理员页面
│   │   │   ├── teacher/        # 教师页面
│   │   │   └── student/        # 学生页面
│   │   ├── router/             # 路由配置
│   │   ├── store/              # 状态管理
│   │   ├── api/                # API接口
│   │   └── utils/              # 工具函数
│   ├── package.json
│   └── vite.config.js
├── docs/                       # 项目文档
│   └── database/
│       └── init.sql           # 数据库初始化脚本
└── README.md
```

## API接口设计

### 认证相关
- `POST /api/auth/login` - 用户登录
- `POST /api/auth/register` - 用户注册
- `POST /api/auth/logout` - 用户登出

### 课程管理
- `GET /api/courses` - 获取课程列表
- `POST /api/courses` - 创建课程
- `PUT /api/courses/{id}` - 更新课程
- `DELETE /api/courses/{id}` - 删除课程

### 单元管理
- `GET /api/units/course/{courseId}` - 获取课程单元
- `POST /api/units` - 创建单元
- `PUT /api/units/{id}` - 更新单元

### 单词管理
- `GET /api/words` - 获取单词列表
- `POST /api/words` - 添加单词
- `GET /api/words/unit/{unitId}` - 获取单元单词

### 学习功能
- `GET /api/learning/texts/{textId}` - 获取课文内容
- `POST /api/learning/vocabulary` - 添加生词
- `GET /api/learning/vocabulary` - 获取生词本
- `POST /api/learning/review` - 记录学习进度

## 开发说明

### 前端开发
1. 所有API请求通过 `src/utils/request.js` 统一处理
2. 用户状态管理使用 Pinia，配置在 `src/store/user.js`
3. 路由守卫已配置，根据用户角色自动跳转
4. UI组件基于 Element Plus，确保组件使用的一致性

### 后端开发
1. 使用 MyBatis Plus 简化数据库操作
2. 统一返回格式通过 `Result` 类处理
3. JWT Token 认证，有效期24小时
4. 所有接口需要进行权限验证

### 数据库设计
核心表结构包括：
- users - 用户表
- courses - 课程表
- units - 单元表
- texts - 课文表
- words - 单词表
- classes - 班级表
- assignments - 学习任务表
- vocabulary_books - 生词本表
- learning_records - 学习记录表

## 部署说明

### 后端部署
1. 打包应用：
```bash
cd backend
mvn clean package -DskipTests
```

2. 运行JAR文件：
```bash
java -jar target/english-learning-platform-1.0.0.jar
```

### 前端部署
1. 构建生产版本：
```bash
cd frontend
npm run build
```

2. 部署dist目录到Web服务器

## 功能特性

### 单词学习
- 点击课文中的单词即时显示解释
- 例句展示
- 词性标注

### 生词管理
- 一键添加到生词本
- 按掌握程度分类
- 智能复习提醒

### 学习进度跟踪
- 实时记录学习行为
- 统计学习时长和效果

### 任务系统
- 教师可布置单元学习任务
- 设置截止时间
- 学习进度统计
- 任务完成情况反馈

## 贡献指南
1. Fork 本仓库
2. 创建特性分支
3. 提交变更
4. 推送到分支
5. 创建 Pull Request

## 许可证
本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情