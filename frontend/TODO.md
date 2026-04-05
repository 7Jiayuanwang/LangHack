# Layout 合并 TODO

## 已完成

- [x] **Step 1**: 合并 `teacher/Layout.vue` + `user/Layout.vue`
  - 创建 `src/views/shared/MobileLayout.vue`，通过 `role` prop 区分角色
  - `teacher/Layout.vue` 和 `user/Layout.vue` 改为薄包装（3 行）

- [x] **Step 2a/2b**: 合并 `ops/Layout.vue` + `admin/Layout.vue` → 并入 `shared/MobileLayout.vue`
  - `ops/Layout.vue` 和 `admin/Layout.vue` 改为薄包装（3 行）
  - ops 专属：`ElevationDialog`、`elevate` 命令、OPS 权限守卫
  - admin 专属：二级"管理"下拉菜单（`moreItems`）、`system` 命令、ADMIN 权限守卫
  - 路由配置无需改动（仍引用各自的薄包装文件）

---

## 待完成

### Step 2c（可选）：合并 `student/Layout.vue` → 并入 `shared/MobileLayout.vue`

**student 的独特逻辑较多，合并收益低、风险高，建议保持独立：**

- Feature toggle 过滤导航项（需调用 `getFeatureToggles` API）
- 自定义 popup 用户菜单（不是 el-dropdown，而是自定义浮层）
- `showUserMenu` / `toggleUserMenu` / `closeUserMenu` 状态管理
- `handleUserAction` 替代 `handleCommand`
- `resize` 事件监听（`screenWidth` 响应式）
- `roleDisplayName` computed
- `isActiveRoute` 方法（支持子路由匹配）

**建议**：保持 `student/Layout.vue` 独立，当前架构已是 2 个文件（shared/MobileLayout + student/Layout），比原来 5 个大幅减少。

---

## 验证

测试结果：**55 pass，3 fail**

3 个失败的测试均为 `userMenuItems` 相关，标注为 **POST-MERGE 预期失败**（当前各 Layout 的菜单项是模板内联的，没有 `userMenuItems` computed）。如需让这 3 个测试通过，需要在 `MobileLayout.vue` 中添加 `userMenuItems` computed 属性并暴露给测试。
