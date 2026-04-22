package com.english.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * CDSP 学校数据 API 字段名对照表 — 中文参数名 → 英文别名（lowercase）。
 * <p>
 * 依据：cdsp.imau.edu.cn data-api/v2 九份官方 PDF 接口文档（2026-04-21 复核）。
 * PDF "参数名称"列是中文（如"学号"），"参数别名"列是英文（如 XH / xh）。
 * <p>
 * 实测 CDSP API 返回 JSON keys 默认是<b>中文参数名</b>，而 V12 {@code field_mappings.source_field}
 * 按 PDF 原文大小写存英文别名（3 个 UPPERCASE + 6 个 lowercase）。为避免英文别名 seed 与实际返回
 * 字段不匹配导致 100% 映射失败，此类提供中文 → 英文别名兜底查表，供
 * {@link FieldMappingServiceImpl#applyMapping} 双向查表使用。
 * <p>
 * 值统一为 lowercase，对齐 {@code FieldMappingServiceImpl} 的 case-insensitive lookup。
 */
final class SchoolDataFieldAlias {

    private SchoolDataFieldAlias() {
    }

    private static final Map<String, Map<String, String>> CHINESE_TO_ALIAS;

    static {
        Map<String, Map<String, String>> all = new HashMap<>();

        // 1. administrative_class — 行政班基本信息（PDF 列: UPPERCASE）
        Map<String, String> adminClass = new HashMap<>();
        adminClass.put("行政班号", "xzbh");
        adminClass.put("班级名称", "bjmc");
        adminClass.put("学生类别", "xslb");
        adminClass.put("所属学院编码", "ssxybm");
        adminClass.put("所属学院名称", "ssxymc");
        adminClass.put("所属年级", "ssnj");
        adminClass.put("班主任工号", "bzrgh");
        adminClass.put("班主任姓名", "bzrxm");
        adminClass.put("辅导员工号", "fdygh");
        adminClass.put("辅导员姓名", "fdyxm");
        adminClass.put("班长学号", "bcxh");
        adminClass.put("班长姓名", "bcxm");
        adminClass.put("建班年月", "jbny");
        adminClass.put("专业", "zy");
        adminClass.put("校区号", "xqh");
        all.put("administrative_class", Collections.unmodifiableMap(adminClass));

        // 2. school_course — 课程基本信息（PDF 列: lowercase）
        Map<String, String> schoolCourse = new HashMap<>();
        schoolCourse.put("课程学时", "kcxs");
        schoolCourse.put("课程代码", "kcdm");
        schoolCourse.put("课程学分", "kcxf");
        schoolCourse.put("课程英文名称", "kcywmc");
        schoolCourse.put("课程简介", "kcjj");
        schoolCourse.put("考试类型", "kslx");
        schoolCourse.put("开课院系", "kkyx");
        schoolCourse.put("课程名称", "kcmc");
        all.put("school_course", Collections.unmodifiableMap(schoolCourse));

        // 3. teaching_class — 本专科生课程信息（PDF 列: UPPERCASE，实际返回课程信息）
        Map<String, String> teachingClass = new HashMap<>();
        teachingClass.put("课程学时", "kcxs");
        teachingClass.put("课程学分", "kcxf");
        teachingClass.put("开课院系", "kkyx");
        teachingClass.put("课程简介", "kcjj");
        teachingClass.put("课程英文名称", "kcywmc");
        teachingClass.put("课程名称", "kcmc");
        teachingClass.put("课程号", "kch");
        teachingClass.put("考试类型", "kslx");
        all.put("teaching_class", Collections.unmodifiableMap(teachingClass));

        // 4. course_schedule — 课程安排信息（PDF 列: lowercase）
        Map<String, String> courseSchedule = new HashMap<>();
        courseSchedule.put("选课课程号", "xkkch");
        courseSchedule.put("教师工号", "jsgh");
        courseSchedule.put("学年", "xn");
        courseSchedule.put("学期", "xq");
        courseSchedule.put("课程代码", "kcdm");
        courseSchedule.put("课程名称", "kcmc");
        courseSchedule.put("课程类别", "kclb");
        courseSchedule.put("课程性质", "kcxz");
        courseSchedule.put("选课序号", "xkxh");
        courseSchedule.put("开课学期", "kkxq");
        courseSchedule.put("学分", "xf");
        courseSchedule.put("课时", "ks");
        courseSchedule.put("总课时", "zks");
        courseSchedule.put("课程周次", "kczc");
        courseSchedule.put("课程周次代码", "kczcdm");
        courseSchedule.put("上课地点", "skdd");
        courseSchedule.put("上课星期几", "skxqj");
        courseSchedule.put("上课节次", "skjc");
        courseSchedule.put("上课开始时间", "skkssj");
        courseSchedule.put("上课结束时间", "skjssj");
        courseSchedule.put("开课院系", "kkyx");
        courseSchedule.put("教师姓名", "jsxm");
        courseSchedule.put("课程班号", "kcbh");
        all.put("course_schedule", Collections.unmodifiableMap(courseSchedule));

        // 5. course_selection — 学生选课（PDF 列: UPPERCASE）
        Map<String, String> courseSelection = new HashMap<>();
        courseSelection.put("学号", "xh");
        courseSelection.put("考试性质代码", "ksxzdm");
        courseSelection.put("考试性质名称", "ksxzmc");
        courseSelection.put("课程班号", "kcbh");
        all.put("course_selection", Collections.unmodifiableMap(courseSelection));

        // 6. level_exam_scores — 本专科生等级考试成绩（PDF 列: lowercase）
        Map<String, String> levelExamScores = new HashMap<>();
        levelExamScores.put("学号", "xh");
        levelExamScores.put("姓名", "xm");
        levelExamScores.put("考试科目", "kskm");
        levelExamScores.put("考试日期", "ksrq");
        levelExamScores.put("分数考试成绩", "fskscj");
        all.put("level_exam_scores", Collections.unmodifiableMap(levelExamScores));

        // 7. teaching_plans — 教学计划信息（PDF 列: lowercase）
        Map<String, String> teachingPlans = new HashMap<>();
        teachingPlans.put("总学时", "zxs");
        teachingPlans.put("授课教师职工号", "skjszgh");
        teachingPlans.put("开课单位代码", "kkdwdm");
        teachingPlans.put("开课学期", "kkxq");
        teachingPlans.put("参考书目", "cksm");
        teachingPlans.put("考试方式代码", "ksfsdm");
        teachingPlans.put("开课计划id", "kkjhid");
        teachingPlans.put("开课学年", "kkxn");
        teachingPlans.put("课程代码", "kcdm");
        teachingPlans.put("开课学期_name", "kkxq_name");
        teachingPlans.put("开课单位代码_name", "kkdwdm_name");
        teachingPlans.put("考试方式名称", "ksfsmc");
        all.put("teaching_plans", Collections.unmodifiableMap(teachingPlans));

        // 8. teacher_courses — 教师授课（PDF 列: lowercase）
        Map<String, String> teacherCourses = new HashMap<>();
        teacherCourses.put("任课角色", "rkjs");
        teacherCourses.put("学年", "xn");
        teacherCourses.put("学期", "xq");
        teacherCourses.put("课程代码", "kcdm");
        teacherCourses.put("课序号", "kxh");
        teacherCourses.put("任课教师号", "rkjsh");
        teacherCourses.put("课程名称", "kcmc");
        teacherCourses.put("课程班号", "kcbh");
        teacherCourses.put("是否主讲", "sfzj");
        teacherCourses.put("课程班别名", "kcbbm");
        teacherCourses.put("任课教师姓名", "rkjsxm");
        all.put("teacher_courses", Collections.unmodifiableMap(teacherCourses));

        // 9. student_schedule — 学生课表（PDF 列: lowercase）
        Map<String, String> studentSchedule = new HashMap<>();
        studentSchedule.put("周次代码", "zcdm");
        studentSchedule.put("上课教师职工号", "skjszgh");
        studentSchedule.put("教师名称", "jsmc");
        studentSchedule.put("老师校园卡号", "lsxykh");
        studentSchedule.put("课程名称", "kcmc");
        studentSchedule.put("教室", "js");
        studentSchedule.put("上课长度", "skcd");
        studentSchedule.put("第几节", "djj");
        studentSchedule.put("星期几", "xqj");
        studentSchedule.put("选课课号", "xkkh");
        studentSchedule.put("学号", "xh");
        studentSchedule.put("学期", "xq");
        studentSchedule.put("学年", "xn");
        studentSchedule.put("周次安排", "zcap");
        studentSchedule.put("课程班号", "kcbh");
        all.put("student_schedule", Collections.unmodifiableMap(studentSchedule));

        CHINESE_TO_ALIAS = Collections.unmodifiableMap(all);
    }

    /**
     * 将中文参数名转换为英文别名（lowercase）。
     *
     * @param dataSource  数据源名称（如 administrative_class）
     * @param sourceField API 返回的字段名（通常是中文，也允许英文）
     * @return 对应英文别名的 lowercase 形式；未配置或未命中返回 {@code null}
     */
    static String resolveAlias(String dataSource, String sourceField) {
        if (dataSource == null || sourceField == null) {
            return null;
        }
        Map<String, String> dict = CHINESE_TO_ALIAS.get(dataSource);
        if (dict == null) {
            return null;
        }
        return dict.get(sourceField);
    }
}
