package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.User;
import com.english.entity.UserFollow;
import com.english.exception.BusinessException;
import com.english.mapper.UserFollowMapper;
import com.english.mapper.UserMapper;
import com.english.service.FollowService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow> implements FollowService {

    @Autowired
    private UserFollowMapper userFollowMapper;

    @Autowired
    private UserMapper userMapper;

    @Override
    @Transactional
    public void follow(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) {
            throw new BusinessException("Cannot follow yourself");
        }

        User target = userMapper.selectById(followingId);
        if (target == null) {
            throw new BusinessException("Target user not found");
        }

        if (isFollowing(followerId, followingId)) {
            throw new BusinessException("Already following this user");
        }

        UserFollow follow = new UserFollow();
        follow.setFollowerId(followerId);
        follow.setFollowingId(followingId);
        userFollowMapper.insert(follow);

        // Update denormalized counts
        updateFollowCounts(followerId, followingId, 1);
    }

    @Override
    @Transactional
    public void unfollow(Long followerId, Long followingId) {
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
               .eq(UserFollow::getFollowingId, followingId);

        int deleted = userFollowMapper.delete(wrapper);
        if (deleted > 0) {
            updateFollowCounts(followerId, followingId, -1);
        }
    }

    @Override
    public boolean isFollowing(Long followerId, Long followingId) {
        return userFollowMapper.countByFollowerAndFollowing(followerId, followingId) > 0;
    }

    @Override
    public IPage<User> getFollowingList(Long userId, int page, int size) {
        Page<UserFollow> followPage = new Page<>(page, size);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, userId)
               .orderByDesc(UserFollow::getCreatedTime);

        IPage<UserFollow> follows = userFollowMapper.selectPage(followPage, wrapper);
        return convertToUserPage(follows, true);
    }

    @Override
    public IPage<User> getFollowersList(Long userId, int page, int size) {
        Page<UserFollow> followPage = new Page<>(page, size);
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowingId, userId)
               .orderByDesc(UserFollow::getCreatedTime);

        IPage<UserFollow> follows = userFollowMapper.selectPage(followPage, wrapper);
        return convertToUserPage(follows, false);
    }

    @Override
    public Map<String, Integer> getFollowCounts(Long userId) {
        User user = userMapper.selectById(userId);
        Map<String, Integer> counts = new HashMap<>();
        if (user != null) {
            Integer followingCount = user.getFollowingCount();
            Integer followersCount = user.getFollowersCount();
            counts.put("followingCount", followingCount != null ? followingCount : 0);
            counts.put("followersCount", followersCount != null ? followersCount : 0);
        } else {
            counts.put("followingCount", 0);
            counts.put("followersCount", 0);
        }
        return counts;
    }

    @Override
    public Map<Long, Boolean> batchCheckFollowing(Long followerId, List<Long> targetUserIds) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LambdaQueryWrapper<UserFollow> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserFollow::getFollowerId, followerId)
               .in(UserFollow::getFollowingId, targetUserIds);
        List<UserFollow> follows = userFollowMapper.selectList(wrapper);
        Set<Long> followingSet = follows.stream()
                .map(UserFollow::getFollowingId)
                .collect(Collectors.toSet());
        Map<Long, Boolean> result = new HashMap<>();
        for (Long targetId : targetUserIds) {
            result.put(targetId, followingSet.contains(targetId));
        }
        return result;
    }

    private void updateFollowCounts(Long followerId, Long followingId, int delta) {
        // Update following_count for follower
        User follower = userMapper.selectById(followerId);
        if (follower != null) {
            int newCount = Math.max(0, (follower.getFollowingCount() != null ? follower.getFollowingCount() : 0) + delta);
            follower.setFollowingCount(newCount);
            userMapper.updateById(follower);
        }

        // Update followers_count for following
        User following = userMapper.selectById(followingId);
        if (following != null) {
            int newCount = Math.max(0, (following.getFollowersCount() != null ? following.getFollowersCount() : 0) + delta);
            following.setFollowersCount(newCount);
            userMapper.updateById(following);
        }
    }

    private IPage<User> convertToUserPage(IPage<UserFollow> followPage, boolean useFollowingId) {
        List<Long> userIds = followPage.getRecords().stream()
                .map(f -> useFollowingId ? f.getFollowingId() : f.getFollowerId())
                .collect(Collectors.toList());

        List<User> users = userIds.isEmpty() ? Collections.emptyList() :
                userMapper.selectBatchIds(userIds);

        // Maintain order
        Map<Long, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        List<User> orderedUsers = userIds.stream()
                .map(userMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Page<User> resultPage = new Page<>(followPage.getCurrent(), followPage.getSize(), followPage.getTotal());
        resultPage.setRecords(orderedUsers);
        return resultPage;
    }
}
