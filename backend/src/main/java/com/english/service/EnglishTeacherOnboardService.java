package com.english.service;

import java.util.Map;

/**
 * 英语教师一键接入服务。
 * <p>
 * 数据源为 CDSP「教师授课」(teacher_courses) 接口。一键完成：
 * <ol>
 *   <li>按学年 OData $filter 拉取 teacher_courses，客户端筛选「指定学期 + 课程名称含关键词（默认英语）」；</li>
 *   <li>把命中的教学班写入 teaching_class；</li>
 *   <li>从命中记录去重得到任课教师，批量创建 TEACHER 账号（SSO 登录，已存在则跳过）；</li>
 *   <li>逐个教师触发 {@link TeacherClassSyncService#syncClassesForTeacher(Long, String, String)}，
 *       实时拉取选课学生并写入 student_class 绑定。</li>
 * </ol>
 */
public interface EnglishTeacherOnboardService {

    /**
     * 按课程名称关键词 + 学年学期，一键接入英语教师并绑定其班级学生。
     *
     * @param academicYear   学年（单起始年份，如 "2026"，必填）
     * @param semester       学期（"秋"=上学期 / "春"=下学期，必填）
     * @param courseKeyword  课程名称关键词，命中即纳入（默认 "英语"）
     * @return 结果摘要：命中教学班数、教师数、新建账号数、班级/学生绑定统计、明细
     */
    Map<String, Object> onboardEnglishTeachers(String academicYear, String semester, String courseKeyword);
}
