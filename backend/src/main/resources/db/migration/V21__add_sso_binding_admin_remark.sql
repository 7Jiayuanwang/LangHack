-- V21: 为 SSO 绑定表添加管理员备注字段
-- 用于记录用户被删除后重新登录等需要管理员审核的情况

ALTER TABLE sso_user_bindings
ADD COLUMN admin_remark TEXT COMMENT '管理员备注：记录特殊情况（如用户被删除后重新登录）' AFTER sso_member_of;
