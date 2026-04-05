# Layout 合并 TODO

## 已完成

- [x] **Step 1**: 合并 `teacher/Layout.vue` + `user/Layout.vue`
  - 创建 `src/views/shared/MobileLayout.vue`，通过 `role` prop 区分角色
  - `teacher/Layout.vue` 和 `user/Layout.vue` 改为薄包装（3 行）
  - 路由配置无需改动

---

## 待完成

### Step 2a：合并 `ops/Layout.vue` → 并入 `shared/MobileLayout.vue`

**当前差异：**

| 项目 | ops |
|------|-----|
| 权限守卫 | `router.replace('/login')`（用 replace，不留历史） |
| 用户菜单 | 含 `elevate`（切换到管理端）命令，无 profile/settings |
| 特殊组件 | `<ElevationDialog>`（提权对话框） |
| 权限检查 | `!userStore.isLoggedIn` 或角色既非 OPS 也非 ADMIN |

**实现要点：**

1. 在 `MobileLayout.vue` 中添加 `'ops'` 到 `validator` 和各配置对象
2. OPS 导航项：monitoring、backups、users、ai-providers 等
3. `role === 'ops'` 时渲染 `<ElevationDialog>` 和 `elevate` 菜单项
4. `handleCommand` 新增 `elevate` 分支：`showElevationDialog.value = true`
5. `onElevationSuccess` 方法：`router.push('/admin')`
6. 权限守卫逻辑：`!userStore.isLoggedIn || (!userStore.hasOpsAccess && !isElevationValid)` → `router.replace('/login')`

**注意：** `ElevationDialog` 是独立组件，合并后需 import 并条件渲染

---

### Step 2b：合并 `admin/Layout.vue` → 并入 `shared/MobileLayout.vue`

**当前差异（admin 是唯一有二级菜单的布局）：**

| 项目 | admin |
|------|-------|
| 权限守卫 | `!userStore.isLoggedIn \|\| !userStore.hasAdminAccess` → `router.replace('/login')` |
| 用户菜单 | 含 `system`（系统设置）命令，额外于其他角色 |
| **结构差异** | **底部导航额外有一个"管理"下拉触发器**（展开 `moreItems` 二级菜单） |
| 主导航项 | dashboard、courses、classes、users（4 项） |
| 二级菜单项 | units、words、texts、assignments（4 项，通过 el-dropdown 展开） |
| `isManagementRouteActive` | 判断是否处于二级菜单路由，控制触发器高亮 |

**实现要点：**

1. `role === 'admin'` 时渲染额外的"管理"下拉触发器（条件渲染 `<slot>` 或 `v-if`）
2. `moreItems` 配置加入 `NAV_ITEMS.admin.more`
3. `isManagementRouteActive` computed 基于 `moreItems` 路径判断
4. `handleCommand` 新增 `system` 分支
5. 权限守卫：`!userStore.hasAdminAccess` → `router.replace('/login')`

**可选方案**：因为 admin 的模板结构（有二级菜单触发器）与其他角色不同，
也可以保留 `admin/Layout.vue` 独立，只合并 ops 进 `shared/MobileLayout.vue`。
这样 3 个文件（shared/MobileLayout + admin/Layout + student/Layout）比 5 个更清晰，
且 admin 的独特结构不会污染共享组件。

---

### Step 2c（可选）：合并 `student/Layout.vue` → 并入 `shared/MobileLayout.vue`

**student 的独特逻辑较多，合并成本最高：**

- Feature toggle 过滤导航项（需调用 `getFeatureToggles` API）
- 自定义 popup 用户菜单（不是 el-dropdown，而是自定义浮层）
- `showUserMenu` / `toggleUserMenu` / `closeUserMenu` 状态管理
- `handleUserAction` 替代 `handleCommand`
- `resize` 事件监听（`screenWidth` 响应式）
- `roleDisplayName` computed
- `isActiveRoute` 方法（支持子路由匹配）

**建议：** 优先完成 2a + 2b，student 保持独立除非有明确需求。

---

## 合并完成后验证

运行测试套件确认无回归：
```bash
npx vitest run tests/views/layouts/
```

预期结果：55 pass（userMenuItems 相关的 3 个 post-merge 测试届时应全部通过）
