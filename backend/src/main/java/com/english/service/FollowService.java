package com.english.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.english.entity.User;

import java.util.List;
import java.util.Map;

public interface FollowService {

    void follow(Long followerId, Long followingId);

    void unfollow(Long followerId, Long followingId);

    boolean isFollowing(Long followerId, Long followingId);

    IPage<User> getFollowingList(Long userId, int page, int size);

    IPage<User> getFollowersList(Long userId, int page, int size);

    Map<String, Integer> getFollowCounts(Long userId);

    Map<Long, Boolean> batchCheckFollowing(Long followerId, List<Long> targetUserIds);
}