package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.SchoolTeacher;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 教师主数据 Mapper。
 */
@Mapper
public interface SchoolTeacherMapper extends BaseMapper<SchoolTeacher> {

    /**
     * 按 teacher_id 唯一键 UPSERT。
     */
    void upsertByTeacherId(SchoolTeacher entity);

    /**
     * 按工号查询（SSO 匹配）。
     */
    SchoolTeacher selectByTeacherId(@Param("teacherId") String teacherId);
}
