package com.english.service;

import java.util.Map;

/**
 * 教师账号批量开通服务。
 * <p>
 * 依据 {@code school_teacher} 目录（由链式同步落库）批量创建本地 TEACHER 账号，
 * 供统一身份认证（SSO）登录时直接命中。账号为 SSO-only：无可用本地密码，只能通过 SSO 登录。
 */
public interface TeacherAccountProvisionService {

    /**
     * 按学院批量开通教师账号。
     *
     * @param collegeName 学院名称，如“外国语学院”
     * @param matchMode   eq=精确匹配 / contains=模糊匹配
     * @return 结果摘要：目录命中数、新建数、跳过数（已存在）、失败数、明细
     */
    Map<String, Object> provisionTeacherAccountsByCollege(String collegeName, String matchMode);
}
