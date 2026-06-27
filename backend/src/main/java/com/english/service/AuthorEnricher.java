package com.english.service;

import com.english.dto.AuthorDTO;
import com.english.entity.Text;
import com.english.entity.User;
import com.english.entity.Video;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 作者信息填充器
 *
 * 统一为文章/视频（单个或列表）挂载上传者（作者）信息。
 * 列表场景采用一次批量查询，避免逐条查询造成的 N+1 问题。
 * 无上传者的系统内容标记为官方内容。
 */
@Component
public class AuthorEnricher {

    @Autowired
    private UserService userService;

    /** 为单篇文章填充作者 */
    public void enrichText(Text text) {
        if (text == null) {
            return;
        }
        text.setAuthor(resolveOne(text.getUploadedBy()));
    }

    /** 为单个视频填充作者 */
    public void enrichVideo(Video video) {
        if (video == null) {
            return;
        }
        video.setAuthor(resolveOne(video.getUserId()));
    }

    /** 批量为文章列表填充作者（一次查询） */
    public void enrichTexts(List<Text> texts) {
        if (texts == null || texts.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = loadUsers(texts.stream().map(Text::getUploadedBy));
        for (Text t : texts) {
            t.setAuthor(toAuthor(t.getUploadedBy(), userMap));
        }
    }

    /** 批量为视频列表填充作者（一次查询） */
    public void enrichVideos(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }
        Map<Long, User> userMap = loadUsers(videos.stream().map(Video::getUserId));
        for (Video v : videos) {
            v.setAuthor(toAuthor(v.getUserId(), userMap));
        }
    }

    // ===== 内部辅助 =====

    private AuthorDTO resolveOne(Long uploaderId) {
        if (uploaderId == null) {
            return AuthorDTO.official();
        }
        return AuthorDTO.from(userService.getUserById(uploaderId));
    }

    private Map<Long, User> loadUsers(java.util.stream.Stream<Long> idStream) {
        Set<Long> ids = idStream.filter(java.util.Objects::nonNull).collect(Collectors.toCollection(HashSet::new));
        if (ids.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        List<User> users = userService.getUsersByIds(ids);
        return users.stream().collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    private AuthorDTO toAuthor(Long uploaderId, Map<Long, User> userMap) {
        if (uploaderId == null) {
            return AuthorDTO.official();
        }
        // 上传者可能已被删除（查不到），AuthorDTO.from(null) 会返回官方标识作为兜底
        return AuthorDTO.from(userMap.get(uploaderId));
    }
}
