package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.UserTextRead;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserTextReadMapper extends BaseMapper<UserTextRead> {

    @Select("SELECT text_id FROM user_text_reads WHERE user_id = #{userId}")
    List<Long> selectReadTextIds(@Param("userId") Long userId);

    @Delete("DELETE FROM user_text_reads WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
