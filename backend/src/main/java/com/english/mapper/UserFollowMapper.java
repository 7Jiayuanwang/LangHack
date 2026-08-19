package com.english.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.english.entity.UserFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {

    @Select("SELECT COUNT(*) FROM user_follows WHERE follower_id = #{followerId} AND following_id = #{followingId}")
    int countByFollowerAndFollowing(@Param("followerId") Long followerId, @Param("followingId") Long followingId);
}