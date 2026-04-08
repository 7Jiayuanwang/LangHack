# 开发指南

本文档提供 LangHack 项目的详细开发指南，包括分支管理、工作流程、代码规范和常见问题。

---

## 目录

- [分支管理策略](#分支管理策略)
- [开发工作流](#开发工作流)
- [代码规范](#代码规范)
- [测试规范](#测试规范)
- [常见问题](#常见问题)

---

## 分支管理策略

### 分支结构

```
master (生产分支)
  ↑
  └─ develop (开发分支)
       ↑
       ├─ feature/xxx (功能分支)
       ├─ fix/xxx (修复分支)
       └─ refactor/xxx (重构分支)
```

### 分支说明

| 分支 | 用途 | 保护规则 | 部署环境 |
|------|------|---------|---------|
| `master` | 生产代码，随时可发布 | 禁止直接推送，仅接受来自 develop 的合并 | 生产环境 |
| `develop` | 开发主分支，集成所有功能 | 禁止直接推送，仅接受来自功能分支的 PR | 测试环境 |
| `feature/*` | 新功能开发 | 开发完成后合并到 develop | 本地 |
| `fix/*` | Bug 修复 | 修复完成后合并到 develop | 本地 |
| `hotfix/*` | 紧急修复 | 从 master 创建，修复后合并回 master 和 develop | 本地 |

---

## 开发工作流

### 1. 新功能开发

```bash
# 步骤 1: 更新 develop 分支
git checkout develop
git pull origin develop

# 步骤 2: 创建功能分支（命名规范：feature/功能描述）
git checkout -b feature/add-user-profile

# 步骤 3: 开发并提交
git add .
git commit -m "feat: 添加用户个人资料页面"

# 步骤 4: 推送到远程
git push origin feature/add-user-profile

# 步骤 5: 在 GitHub 创建 Pull Request
# - Base: develop
# - Compare: feature/add-user-profile
# - 填写 PR 描述，关联相关 Issue

# 步骤 6: 代码审查通过后，使用 "Merge" 按钮合并
# - 选择 "Create a merge commit" 保留完整历史
# - 删除远程功能分支

# 步骤 7: 本地清理
git checkout develop
git pull origin develop
git branch -d feature/add-user-profile
```

### 2. Bug 修复

```bash
# 与新功能开发流程相同，但分支命名为 fix/bug-description
git checkout develop
git checkout -b fix/login-error
# ... 开发、提交、推送、PR
```

### 3. 紧急修复（Hotfix）

用于修复生产环境的紧急问题，需要同时更新 master 和 develop。

```bash
# 步骤 1: 从 master 创建 hotfix 分支
git checkout master
git pull origin master
git checkout -b hotfix/critical-security-fix

# 步骤 2: 修复并提交
git add .
git commit -m "fix(security): 修复 SQL 注入漏洞"

# 步骤 3: 合并到 master
git checkout master
git merge --no-ff hotfix/critical-security-fix -m "Merge hotfix: 修复 SQL 注入漏洞"
git push origin master

# 步骤 4: 同步到 develop（重要！）
git checkout develop
git pull origin develop
git merge --no-ff master -m "Merge master hotfix into develop"
git push origin develop

# 步骤 5: 清理 hotfix 分支
git branch -d hotfix/critical-security-fix
```

### 4. 发布流程

定期将 develop 合并到 master 进行发布。

```bash
# 步骤 1: 确保 develop 分支稳定
cd frontend && npx vitest run  # 前端测试
cd ../backend && mvn test      # 后端测试

# 步骤 2: 合并到 master
git checkout master
git pull origin master
git merge --no-ff develop -m "Release: v1.2.0 - 新增用户个人资料功能"

# 步骤 3: 打标签
git tag -a v1.2.0 -m "Release v1.2.0"
git push origin master --tags

# 步骤 4: 部署到生产环境
# 在服务器上执行：
# cd /path/to/LangHack
# git pull origin master
# ./scripts/deploy.sh
```

---

## 代码规范

### 后端（Java + Spring Boot）

**文件组织**：
```
backend/src/main/java/com/english/
├── controller/     # REST API 控制器
├── service/        # 业务逻辑层
├── mapper/         # MyBatis 数据访问层
├── entity/         # 数据库实体类
├── dto/            # 数据传输对象
├── config/         # 配置类
└── util/           # 工具类
```

**命名规范**：
- 类名：PascalCase（如 `UserController`）
- 方法名：camelCase（如 `getUserById`）
- 常量：UPPER_SNAKE_CASE（如 `MAX_RETRY_COUNT`）

**数据库变更**：
```bash
# 所有 schema 变更必须创建 Flyway 迁移脚本
backend/src/main/resources/db/migration/V{版本号}__{描述}.sql

# 示例：
V20260408001__add_user_profile_table.sql
```

**API 响应格式**：
```java
// 统一使用 Result<T> 包装
return Result.success(data);           // 成功
return Result.error("错误信息");        // 失败
```

### 前端（Vue 3 + Vite）

**文件组织**：
```
frontend/src/
├── views/          # 页面组件（按角色分目录）
│   ├── admin/
│   ├── teacher/
│   ├── student/
│   ├── user/
│   └── shared/     # 共享布局和组件
├── components/     # 可复用组件
├── api/            # API 封装（按模块分文件）
├── store/          # Pinia 状态管理
├── router/         # 路由配置
└── assets/         # 静态资源
```

**命名规范**：
- 组件文件：PascalCase（如 `UserProfile.vue`）
- 组件名：PascalCase（如 `<UserProfile />`）
- 变量/函数：camelCase（如 `const userName = ref('')`）

**组件规范**：
```vue
<script setup>
// 1. 导入
import { ref, computed, onMounted } from 'vue'
import { useUserStore } from '@/store/user'

// 2. Props 和 Emits
const props = defineProps({
  userId: { type: Number, required: true }
})
const emit = defineEmits(['update', 'delete'])

// 3. 响应式数据
const userInfo = ref(null)
const store = useUserStore()

// 4. 计算属性
const displayName = computed(() => userInfo.value?.name || '未知用户')

// 5. 方法
const fetchUser = async () => {
  // ...
}

// 6. 生命周期
onMounted(() => {
  fetchUser()
})
</script>

<template>
  <!-- 模板 -->
</template>

<style scoped lang="scss">
/* 样式 */
</style>
```

**API 调用规范**：
```javascript
// 封装在 src/api/*.js
import request from '@/utils/request'

export function getUserProfile(userId) {
  return request({
    url: `/api/user/${userId}/profile`,
    method: 'get'
  })
}

// 组件中使用
import { getUserProfile } from '@/api/user'

const loadProfile = async () => {
  try {
    const response = await getUserProfile(props.userId)
    if (response.data.code === 200) {
      userInfo.value = response.data.data
    }
  } catch (error) {
    console.error('加载失败:', error)
  }
}
```

### Git 提交信息规范

遵循 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type 类型**：
- `feat`: 新功能
- `fix`: Bug 修复
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `refactor`: 重构（既不是新功能也不是修复）
- `test`: 测试相关
- `chore`: 构建/工具链更新
- `perf`: 性能优化

**示例**：
```bash
feat(user): 添加用户个人资料页面

- 新增 UserProfile.vue 组件
- 添加 /api/user/profile 接口
- 更新路由配置

Closes #123
```

---

## 测试规范

### 前端测试

使用 Vitest + @vue/test-utils：

```bash
# 运行所有测试
cd frontend && npx vitest run

# 监听模式（开发时使用）
cd frontend && npx vitest

# 运行特定测试文件
cd frontend && npx vitest run tests/views/user/Profile.test.js

# 生成覆盖率报告
cd frontend && npx vitest run --coverage
```

**测试文件组织**：
```
frontend/tests/
├── views/          # 页面组件测试
├── components/     # 可复用组件测试
└── utils/          # 工具函数测试
```

**测试示例**：
```javascript
import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import UserProfile from '@/views/user/Profile.vue'

describe('UserProfile.vue', () => {
  let wrapper

  beforeEach(() => {
    wrapper = mount(UserProfile, {
      props: { userId: 1 }
    })
  })

  it('renders user name', () => {
    expect(wrapper.find('.user-name').text()).toBe('测试用户')
  })

  it('emits update event on save', async () => {
    await wrapper.find('.save-btn').trigger('click')
    expect(wrapper.emitted('update')).toBeTruthy()
  })
})
```

### 后端测试

使用 JUnit 5 + Spring Boot Test：

```bash
# 运行所有测试
cd backend && mvn test

# 运行特定测试类
cd backend && mvn test -Dtest=UserServiceTest

# 跳过测试（不推荐）
cd backend && mvn package -DskipTests
```

**测试示例**：
```java
@SpringBootTest
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    void testGetUserById() {
        User user = userService.getUserById(1L);
        assertNotNull(user);
        assertEquals("admin", user.getUsername());
    }

    @Test
    void testCreateUser() {
        User newUser = new User();
        newUser.setUsername("testuser");
        newUser.setPassword("password123");

        User created = userService.createUser(newUser);
        assertNotNull(created.getId());
    }
}
```

---

## 常见问题

### Q1: 为什么不能直接在 master/develop 上开发？

**原因**：
- 保持主分支稳定，避免未完成的功能影响其他开发者
- 便于代码审查和回滚
- 清晰的提交历史

### Q2: cherry-pick 和 merge 有什么区别？

| 操作 | 效果 | 适用场景 |
|------|------|---------|
| `git merge` | 创建合并提交，保留完整历史 | 分支合并（推荐） |
| `git cherry-pick` | 复制提交，创建新的 commit hash | 选择性合并单个提交 |

**问题案例**：
```bash
# ❌ 错误做法：cherry-pick 导致分支分叉
git checkout develop
git cherry-pick abc123  # 从 master 复制提交

# 结果：develop 和 master 有相同内容但不同 hash 的提交
# 未来合并时可能产生冲突

# ✅ 正确做法：使用 merge
git checkout develop
git merge master  # 保留完整历史
```

### Q3: 如何解决合并冲突？

```bash
# 1. 尝试合并
git merge feature/xxx

# 2. 如果有冲突，Git 会提示冲突文件
# 打开冲突文件，手动解决冲突标记：
# <<<<<<< HEAD
# 当前分支的内容
# =======
# 要合并的分支的内容
# >>>>>>> feature/xxx

# 3. 解决后标记为已解决
git add <冲突文件>

# 4. 完成合并
git commit -m "Merge feature/xxx: 解决冲突"
```

### Q4: 如何撤销错误的提交？

```bash
# 场景 1: 提交还没推送到远程
git reset --soft HEAD~1  # 撤销提交，保留更改
git reset --hard HEAD~1  # 撤销提交，丢弃更改

# 场景 2: 提交已推送到远程
git revert <commit-hash>  # 创建一个新提交来撤销更改
git push origin <branch>
```

### Q5: deploy.sh 报错 "无效的范围结束符"

**问题**：sed 命令在某些环境下不支持 `\x` 十六进制转义。

**解决**：已在 commit `41be065` 修复，使用 POSIX 字符类 `[[:cntrl:]]` 替代。

**验证**：
```bash
git pull origin master  # 或 develop
./scripts/deploy.sh     # 应该正常运行
```

### Q6: 前端测试失败：Pinia getter 无法 mock

**问题**：Pinia 的 getter 是只读计算属性，不能直接赋值。

**解决**：
```javascript
// ❌ 错误
store.isLoggedIn = true  // isLoggedIn 是 getter

// ✅ 正确
store.token = 'fake-token'  // 修改 state，getter 自动更新
```

### Q7: Docker 部署后前端修改不生效

**问题**：前端代码打包进 Docker 镜像，不是 volume 挂载。

**解决**：
```bash
# 方法 1: 重新构建镜像（推荐）
docker-compose down
docker-compose up -d --build

# 方法 2: 手动替换静态文件（快速测试）
cd frontend && npm run build
docker cp dist/. langhack-frontend:/usr/share/nginx/html/
docker exec langhack-frontend nginx -s reload
```

---

## 相关文档

- [部署指南](DEPLOYMENT.md)
- [故障排除](TROUBLESHOOTING.md)
- [部署问题案例库](DEPLOYMENT-LESSONS-LEARNED.md)
- [API 文档](API接口文档.md)

---

**最后更新**: 2026-04-08
