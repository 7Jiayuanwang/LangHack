package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 一次查询获取各角色计数和活跃用户数，避免多次 COUNT 往返
     */
    @Select("SELECT " +
            "SUM(CASE WHEN role='ADMIN'   THEN 1 ELSE 0 END) AS adminCount, " +
            "SUM(CASE WHEN role='TEACHER' THEN 1 ELSE 0 END) AS teacherCount, " +
            "SUM(CASE WHEN role='STUDENT' THEN 1 ELSE 0 END) AS studentCount, " +
            "SUM(CASE WHEN status='ACTIVE' THEN 1 ELSE 0 END) AS activeCount " +
            "FROM users WHERE deleted=0")
    Map<String, Object> selectUserStats();
}
