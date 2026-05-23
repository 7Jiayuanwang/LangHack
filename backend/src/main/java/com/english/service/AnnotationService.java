package com.english.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.entity.AnnotationUpvote;
import com.english.entity.TextAnnotation;
import com.english.entity.User;
import com.english.mapper.AnnotationUpvoteMapper;
import com.english.mapper.TextAnnotationMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AnnotationService {

    @Autowired
    private TextAnnotationMapper annotationMapper;

    @Autowired
    private AnnotationUpvoteMapper upvoteMapper;

    @Autowired
    private UserService userService;

    public List<TextAnnotation> listByText(Long textId, Long currentUserId) {
        List<TextAnnotation> list = annotationMapper.selectList(
            new LambdaQueryWrapper<TextAnnotation>()
                .eq(TextAnnotation::getTextId, textId)
                .orderByDesc(TextAnnotation::getUpvoteCount)
                .orderByDesc(TextAnnotation::getCreatedTime)
        );

        // collect upvoted annotation IDs for current user
        Set<Long> upvotedIds = Set.of();
        if (currentUserId != null && !list.isEmpty()) {
            List<Long> annIds = list.stream().map(TextAnnotation::getId).collect(Collectors.toList());
            upvotedIds = upvoteMapper.selectList(
                new LambdaQueryWrapper<AnnotationUpvote>()
                    .eq(AnnotationUpvote::getUserId, currentUserId)
                    .in(AnnotationUpvote::getAnnotationId, annIds)
            ).stream().map(AnnotationUpvote::getAnnotationId).collect(Collectors.toSet());
        }

        final Set<Long> finalUpvotedIds = upvotedIds;
        for (TextAnnotation ann : list) {
            User u = userService.getUserById(ann.getUserId());
            ann.setUsername(u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "用户");
            ann.setUpvotedByMe(finalUpvotedIds.contains(ann.getId()));
        }
        return list;
    }

    public TextAnnotation create(Long textId, Long userId, String selectedText, String note) {
        TextAnnotation ann = new TextAnnotation();
        ann.setTextId(textId);
        ann.setUserId(userId);
        ann.setSelectedText(selectedText);
        ann.setNote(note);
        ann.setUpvoteCount(0);
        annotationMapper.insert(ann);
        User u = userService.getUserById(userId);
        ann.setUsername(u != null ? (u.getNickname() != null ? u.getNickname() : u.getUsername()) : "用户");
        ann.setUpvotedByMe(false);
        return ann;
    }

    public void delete(Long id, Long userId) {
        TextAnnotation ann = annotationMapper.selectById(id);
        if (ann == null || !ann.getUserId().equals(userId)) {
            throw new RuntimeException("无权删除此批注");
        }
        annotationMapper.deleteById(id);
    }

    @Transactional
    public int toggleUpvote(Long annotationId, Long userId) {
        TextAnnotation ann = annotationMapper.selectById(annotationId);
        if (ann == null) throw new RuntimeException("批注不存在");

        AnnotationUpvote existing = upvoteMapper.selectOne(
            new LambdaQueryWrapper<AnnotationUpvote>()
                .eq(AnnotationUpvote::getAnnotationId, annotationId)
                .eq(AnnotationUpvote::getUserId, userId)
        );

        if (existing != null) {
            upvoteMapper.deleteById(existing.getId());
            ann.setUpvoteCount(Math.max(0, ann.getUpvoteCount() - 1));
        } else {
            AnnotationUpvote upvote = new AnnotationUpvote();
            upvote.setAnnotationId(annotationId);
            upvote.setUserId(userId);
            upvoteMapper.insert(upvote);
            ann.setUpvoteCount(ann.getUpvoteCount() + 1);
        }
        annotationMapper.updateById(ann);
        return ann.getUpvoteCount();
    }
}
