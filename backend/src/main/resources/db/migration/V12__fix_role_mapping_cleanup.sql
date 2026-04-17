-- V12: 清理 sso_config 表中无效的角色映射规则，修复被误映射的用户角色
--
-- 问题：sso_config 表中可能存在 config_key='sso.role_mapping.ou' 的脏规则，
-- strip 前缀后 key='ou'，由于旧版 RoleMappingService 使用 contains() 匹配，
-- 'ou' 匹配所有 LDAP DN（都包含 'ou'），导致所有用户被映射为同一角色。
-- 新版改用 DN 组件精确匹配，同时此迁移清理数据库中的脏规则。

-- 1. 删除无效的短 key 规则（key strip 后不含 '='，如 'ou'、'dc' 等）
DELETE FROM sso_config
WHERE config_type = 'ROLE_MAPPING'
  AND REPLACE(config_key, 'sso.role_mapping.', '') NOT LIKE '%=%';

-- 2. 修复被误映射的用户角色
--    旧代码因 'ou' 规则把所有 CAS 用户映射为 TEACHER（TEACHER 是优先级高于 STUDENT 的第一个命中角色）
--    修复策略：将被 SSO 错误映射为 TEACHER 但实际是学生的用户恢复为 STUDENT
--    判断依据：用户有 sso_user_binding 且 auto_approved=1，且当前角色为 TEACHER，
--    但其 sso_member_of 中不含 'ou=教师' 或 'ou=teacher'（即真正的教师标识）

UPDATE users u
INNER JOIN sso_user_bindings b ON u.id = b.user_id AND b.deleted = 0
SET u.role = 'STUDENT', u.updated_time = NOW()
WHERE u.role = 'TEACHER'
  AND u.sso_bound = 1
  AND u.deleted = 0
  AND b.auto_approved = 1
  AND b.sso_member_of IS NOT NULL
  AND b.sso_member_of NOT LIKE '%ou=教师%'
  AND b.sso_member_of NOT LIKE '%ou=teacher%';
