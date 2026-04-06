package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.UserTag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTagMapper extends BaseMapper<UserTag> {

    @Select("SELECT tag_name FROM user_tags WHERE user_id = #{userId}")
    List<String> selectTagsByUserId(Long userId);

    @Select("SELECT DISTINCT user_id FROM user_tags WHERE tag_name = #{tagName}")
    List<Long> selectUserIdsByTag(String tagName);

    @Select("SELECT DISTINCT tag_name FROM user_tags ORDER BY tag_name")
    List<String> selectAllTagNames();
}
