package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Video;
import com.english.exception.BusinessException;
import com.english.mapper.VideoMapper;
import com.english.service.VideoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class VideoServiceImpl extends ServiceImpl<VideoMapper, Video> implements VideoService {

    @Override
    public Map<String, Object> getVideosByPage(Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        Object keywordObj = params.get("keyword");
        String keyword = keywordObj != null ? keywordObj.toString() : null;

        Page<Video> pageParam = new Page<>(page, size);
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("title", keyword)
                .or()
                .like("description", keyword)
                .or()
                .like("tags", keyword)
            );
        }

        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");

        IPage<Video> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public Video getVideoById(Long id) {
        QueryWrapper<Video> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("deleted", 0);
        return getOne(wrapper);
    }

    @Override
    @Transactional
    public void createVideo(Video video) {
        video.setCreatedTime(LocalDateTime.now());
        video.setUpdatedTime(LocalDateTime.now());
        video.setDeleted(0);
        if (video.getViewCount() == null) video.setViewCount(0);
        if (video.getLikeCount() == null) video.setLikeCount(0);
        if (video.getIsPublic() == null) video.setIsPublic(1);
        if (video.getIsFeatured() == null) video.setIsFeatured(0);
        if (video.getFeaturedPriority() == null) video.setFeaturedPriority(0);
        if (video.getStatus() == null) video.setStatus("APPROVED");
        if (video.getTranscriptionStatus() == null) video.setTranscriptionStatus("NONE");
        save(video);
    }

    @Override
    @Transactional
    public void updateVideo(Video video) {
        if (video == null || video.getId() == null) {
            throw new BusinessException("视频对象及ID不能为空");
        }
        Video existing = getVideoById(video.getId());
        if (existing == null) {
            throw new BusinessException("视频不存在或已被删除");
        }
        video.setUpdatedTime(LocalDateTime.now());
        updateById(video);
    }

    @Override
    @Transactional
    public void deleteVideo(Long id) {
        Video video = getVideoById(id);
        if (video == null) {
            throw new BusinessException("视频不存在或已被删除");
        }
        // 逻辑删除：@TableLogic 会将 removeById 转换为 UPDATE SET deleted=1
        removeById(id);
    }

    @Override
    public List<Video> getVideosByUploader(Long userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public List<Video> getFeaturedVideos(int count) {
        count = Math.min(Math.max(count, 1), 50);

        List<Video> featuredVideos = lambdaQuery()
                .eq(Video::getDeleted, 0)
                .eq(Video::getIsFeatured, 1)
                .eq(Video::getIsPublic, 1)
                .orderByDesc(Video::getFeaturedPriority)
                .orderByDesc(Video::getCreatedTime)
                .list();

        // 有优先级(>0)的视频按优先级降序排在前面，priority=0 的视频随机填充剩余位置
        List<Video> prioritized = featuredVideos.stream()
                .filter(v -> v.getFeaturedPriority() != null && v.getFeaturedPriority() > 0)
                .sorted((a, b) -> Integer.compare(
                        b.getFeaturedPriority() != null ? b.getFeaturedPriority() : 0,
                        a.getFeaturedPriority() != null ? a.getFeaturedPriority() : 0))
                .collect(Collectors.toList());
        List<Video> randomPool = new ArrayList<>(featuredVideos.stream()
                .filter(v -> v.getFeaturedPriority() == null || v.getFeaturedPriority() == 0)
                .collect(Collectors.toList()));
        Collections.shuffle(randomPool);

        List<Video> result = new ArrayList<>();
        if (prioritized.size() >= count) {
            result = prioritized.subList(0, count);
        } else {
            result.addAll(prioritized);
            int remaining = count - prioritized.size();
            result.addAll(randomPool.subList(0, Math.min(remaining, randomPool.size())));
        }

        return result;
    }

    @Override
    public List<Video> getPublicVideos(int page, int size, String keyword) {
        if (page < 1) page = 1;
        if (size < 1) size = 10;
        if (size > 100) size = 100;

        QueryWrapper<Video> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("is_public", 1);
        queryWrapper.eq("deleted", 0);

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                    .like("title", keyword)
                    .or()
                    .like("description", keyword)
                    .or()
                    .like("tags", keyword)
            );
        }

        queryWrapper.orderByDesc("created_time");

        Page<Video> pageParam = new Page<>(page, size);
        IPage<Video> pageResult = page(pageParam, queryWrapper);

        return pageResult.getRecords();
    }

    @Override
    public Map<String, Object> getVideoStats() {
        Map<String, Object> stats = new HashMap<>();

        long totalVideos = lambdaQuery().eq(Video::getDeleted, 0).count();
        long totalFeatured = lambdaQuery().eq(Video::getDeleted, 0).eq(Video::getIsFeatured, 1).count();
        long totalPublic = lambdaQuery().eq(Video::getDeleted, 0).eq(Video::getIsPublic, 1).count();
        long totalPending = lambdaQuery().eq(Video::getDeleted, 0).eq(Video::getStatus, "PENDING").count();

        stats.put("totalVideos", totalVideos);
        stats.put("totalFeatured", totalFeatured);
        stats.put("totalPublic", totalPublic);
        stats.put("totalPending", totalPending);

        return stats;
    }

    @Override
    public void incrementViewCount(Long videoId) {
        baseMapper.incrementViewCount(videoId);
    }

    @Override
    public void incrementLikeCount(Long videoId) {
        baseMapper.incrementLikeCount(videoId);
    }
}
