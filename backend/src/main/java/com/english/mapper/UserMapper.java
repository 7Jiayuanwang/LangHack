package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    /**
     * 查找已软删除的用户（绕过 @TableLogic 过滤）
     */
    @Select("SELECT * FROM users WHERE username = #{username} AND deleted = 1 LIMIT 1")
    User findDeletedByUsername(String username);

    /**
     * 恢复软删除用户（设置 deleted=0）
     */
    @Update("UPDATE users SET deleted = 0, updated_time = NOW() WHERE id = #{id} AND deleted = 1")
    int undeleteById(Long id);
}
