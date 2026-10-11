package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.CourseExemptionApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface CourseExemptionApplicationMapper extends BaseMapper<CourseExemptionApplication> {

    /** 教师端列表：附带申请人姓名、学号与所属班级，避免前端逐条再查。别名用驼峰，Map 结果不做下划线转换。 */
    @Select("<script>" +
            "SELECT a.id, a.student_id AS studentId, a.course_name AS courseName, a.apply_type AS applyType," +
            " a.exam_type AS examType, a.exam_score AS examScore, a.exam_date AS examDate," +
            " a.certificate_url AS certificateUrl, a.student_remark AS studentRemark," +
            " a.converted_score AS convertedScore, a.status, a.review_comment AS reviewComment," +
            " a.reviewed_time AS reviewedTime, a.created_time AS createdTime," +
            " u.real_name AS studentName, u.username AS studentNo," +
            " (SELECT GROUP_CONCAT(c.name SEPARATOR '、') FROM student_classes sc" +
            "   JOIN classes c ON c.id = sc.class_id" +
            "   WHERE sc.student_id = a.student_id AND sc.status = 'ACTIVE') AS classNames" +
            " FROM course_exemption_application a" +
            " JOIN users u ON u.id = a.student_id" +
            " <where>" +
            "  <if test='status != null and status != &quot;&quot;'> AND a.status = #{status} </if>" +
            "  AND (EXISTS (SELECT 1 FROM student_classes sc JOIN classes c ON c.id = sc.class_id" +
            "       WHERE sc.student_id = a.student_id AND sc.status = 'ACTIVE' AND c.teacher_id = #{teacherId})" +
            "    OR EXISTS (SELECT 1 FROM teacher_student_binding tsb" +
            "       WHERE tsb.student_id COLLATE utf8mb4_unicode_ci = u.username COLLATE utf8mb4_unicode_ci" +
            "         AND tsb.teacher_id COLLATE utf8mb4_unicode_ci = #{teacherUsername} COLLATE utf8mb4_unicode_ci))" +
            " </where>" +
            " ORDER BY a.created_time DESC, a.id DESC" +
            "</script>")
    IPage<Map<String, Object>> selectWithStudent(Page<Map<String, Object>> page,
                                                  @Param("status") String status,
                                                  @Param("teacherId") Long teacherId,
                                                  @Param("teacherUsername") String teacherUsername);

    @Select("SELECT COUNT(*) FROM course_exemption_application a JOIN users u ON u.id = a.student_id " +
            "WHERE (#{status} = '' OR a.status = #{status}) AND (" +
            "EXISTS (SELECT 1 FROM student_classes sc JOIN classes c ON c.id = sc.class_id " +
            "WHERE sc.student_id = a.student_id AND sc.status = 'ACTIVE' AND c.teacher_id = #{teacherId}) OR " +
            "EXISTS (SELECT 1 FROM teacher_student_binding tsb " +
            "WHERE tsb.student_id COLLATE utf8mb4_unicode_ci = u.username COLLATE utf8mb4_unicode_ci " +
            "AND tsb.teacher_id COLLATE utf8mb4_unicode_ci = #{teacherUsername} COLLATE utf8mb4_unicode_ci))")
    long countAccessible(@Param("status") String status,
                         @Param("teacherId") Long teacherId,
                         @Param("teacherUsername") String teacherUsername);

    @Select("SELECT a.* FROM course_exemption_application a JOIN users u ON u.id = a.student_id " +
            "WHERE a.id = #{id} AND (" +
            "EXISTS (SELECT 1 FROM student_classes sc JOIN classes c ON c.id = sc.class_id " +
            "WHERE sc.student_id = a.student_id AND sc.status = 'ACTIVE' AND c.teacher_id = #{teacherId}) OR " +
            "EXISTS (SELECT 1 FROM teacher_student_binding tsb " +
            "WHERE tsb.student_id COLLATE utf8mb4_unicode_ci = u.username COLLATE utf8mb4_unicode_ci " +
            "AND tsb.teacher_id COLLATE utf8mb4_unicode_ci = #{teacherUsername} COLLATE utf8mb4_unicode_ci))")
    CourseExemptionApplication selectAccessibleById(@Param("id") Long id,
                                                     @Param("teacherId") Long teacherId,
                                                     @Param("teacherUsername") String teacherUsername);
}
