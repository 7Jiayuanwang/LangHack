# SSO 删除用户处理逻辑改进

## 问题描述

**日志错误**：
```
com.english.sso.exception.SsoBusinessException: SSO 绑定已存在但对应用户丢失，请联系管理员
```

**根因**：
- `sso_user_bindings` 表中的绑定记录（bindingId=1, userId=5）仍然存在
- 但 `users` 表中对应的用户（userId=5）已被软删除（deleted=1）
- MyBatis Plus 的 `@TableLogic` 自动过滤软删除记录，导致 `getUserById` 返回 null
- 原代码直接抛出异常，用户无法登录

## 解决方案

**策略**：再次提交管理员审核，并向管理员备注说明

### 1. 数据库变更

**新增迁移脚本**：`V21__add_sso_binding_admin_remark.sql`
```sql
ALTER TABLE sso_user_bindings
ADD COLUMN admin_remark TEXT COMMENT '管理员备注：记录特殊情况（如用户被删除后重新登录）' AFTER sso_member_of;
```

**同步更新**：`docker/mysql/init/04-apply-all-migrations.sql`（幂等脚本）

### 2. 实体类更新

**SsoUserBinding.java**：
```java
/**
 * 管理员备注：记录特殊情况（如用户被删除后重新登录）
 */
private String adminRemark;
```

### 3. Mapper 增强

**UserMapper.java** 新增方法：
```java
/**
 * 通过 ID 查找已软删除的用户（绕过 @TableLogic 过滤）
 */
@Select("SELECT * FROM users WHERE id = #{id} AND deleted = 1 LIMIT 1")
User findDeletedById(Long id);
```

### 4. 业务逻辑改进

**SsoUserMappingService.java** 核心逻辑：

```java
// 获取用户信息
User user = userService.getUserById(binding.getUserId());
if (user == null) {
    // 检查用户是否被软删除
    User deletedUser = userMapper.findDeletedById(binding.getUserId());
    if (deletedUser != null) {
        log.warn("SSO 用户对应的本地账号已被删除，提交管理员审核: ...");

        // 构建管理员备注
        String remark = String.format(
                "用户 %s (ID: %d) 曾于 %s 被删除，于 %s 通过 SSO 重新登录。" +
                "删除前角色: %s，状态: %s。请管理员审核是否允许恢复访问。",
                deletedUser.getUsername(),
                deletedUser.getId(),
                deletedUser.getUpdatedTime(),
                LocalDateTime.now(),
                deletedUser.getRole(),
                deletedUser.getStatus()
        );

        // 更新绑定为待审批状态，并添加备注
        binding.setAutoApproved(0);
        binding.setAdminRemark(remark);
        bindingMapper.updateById(binding);

        return SsoLoginResult.pendingApproval(
                binding.getId(),
                "您的账号曾被删除，需要管理员重新审核。\n如有疑问请致电：计算机与信息工程学院43019235"
        );
    }

    // 用户彻底不存在（硬删除或数据异常）
    throw new SsoBusinessException(SsoErrorCode.USER_NOT_FOUND,
            "SSO 绑定已存在但对应用户丢失，请联系管理员");
}
```

## 处理流程

```
SSO 用户登录
    ↓
找到已有绑定（bindingId=1, userId=5）
    ↓
查询用户（getUserById）→ null
    ↓
检查是否软删除（findDeletedById）
    ↓
    ├─ 找到删除用户 → 构建备注 → 设为待审批 → 返回待审批状态
    │                   ↓
    │              管理员在后台看到备注：
    │              "用户 2023112101479 (ID: 5) 曾于 2026-04-15T10:30 被删除，
    │               于 2026-04-21T18:31 通过 SSO 重新登录。
    │               删除前角色: STUDENT，状态: ACTIVE。
    │               请管理员审核是否允许恢复访问。"
    │                   ↓
    │              管理员决策：
    │              - 批准 → 用户恢复访问
    │              - 拒绝 → 用户继续无法登录
    │
    └─ 未找到（硬删除）→ 抛出异常
```

## 管理员审核界面

管理员在 SSO 绑定审核页面会看到：

| 字段 | 值 |
|------|-----|
| 绑定 ID | 1 |
| SSO 用户 | 2023112101479 (白伟杰) |
| 本地用户 ID | 5 |
| 审批状态 | 待审批 |
| **管理员备注** | **用户 2023112101479 (ID: 5) 曾于 2026-04-15T10:30 被删除，于 2026-04-21T18:31 通过 SSO 重新登录。删除前角色: STUDENT，状态: ACTIVE。请管理员审核是否允许恢复访问。** |

## 测试覆盖

**测试文件**：`SsoUserMappingServiceDeletedUserTest.java`

测试场景：
1. ✅ 软删除用户重新登录 → 返回待审批状态
2. ✅ 备注包含完整信息（用户名、ID、删除时间、角色、状态）
3. ✅ 硬删除用户 → 抛出异常

## 部署注意事项

1. **Flyway 迁移**：V21 会自动执行，添加 `admin_remark` 字段
2. **04 脚本同步**：已更新，支持幂等执行
3. **向后兼容**：旧绑定记录的 `admin_remark` 为 NULL，不影响现有逻辑
4. **日志监控**：关键日志 `"SSO 用户对应的本地账号已被删除，提交管理员审核"`

## 相关文件

| 文件 | 变更类型 |
|------|---------|
| `V21__add_sso_binding_admin_remark.sql` | 新增 |
| `docker/mysql/init/04-apply-all-migrations.sql` | 更新（追加 V21） |
| `SsoUserBinding.java` | 更新（新增字段） |
| `UserMapper.java` | 更新（新增方法） |
| `SsoUserMappingService.java` | 更新（核心逻辑） |
| `SsoUserMappingServiceDeletedUserTest.java` | 新增 |

## 后续优化建议

1. **前端展示**：在管理员审核页面高亮显示 `admin_remark` 字段
2. **自动化策略**：可配置"删除超过 N 天的用户自动拒绝"
3. **审计日志**：记录管理员的审批决策（批准/拒绝原因）
4. **通知机制**：用户被删除后重新登录时，自动通知管理员

---

**修复日期**：2026-04-21  
**修复人员**：Claude (Kiro AI Assistant)  
**问题来源**：生产日志分析
