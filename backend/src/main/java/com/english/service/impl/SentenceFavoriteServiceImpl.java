package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.english.entity.SentenceFavorite;
import com.english.entity.Text;
import com.english.entity.Unit;
import com.english.exception.BusinessException;
import com.english.mapper.SentenceFavoriteMapper;
import com.english.mapper.TextMapper;
import com.english.mapper.TextUnitMapper;
import com.english.mapper.UnitMapper;
import com.english.service.ClassService;
import com.english.service.SentenceFavoriteService;
import com.english.service.StudentClassesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class SentenceFavoriteServiceImpl implements SentenceFavoriteService {

    @Autowired
    private SentenceFavoriteMapper sentenceFavoriteMapper;

    @Autowired
    private TextMapper textMapper;

    @Autowired
    private TextUnitMapper textUnitMapper;

    @Autowired
    private UnitMapper unitMapper;

    @Autowired
    private StudentClassesService studentClassesService;

    @Autowired
    private ClassService classService;

    @Override
    @Transactional
    public Map<String, Object> toggle(Long userId, Long textId, String sentence, boolean staffAccess) {
        String normalizedSentence = validate(userId, textId, sentence);
        assertCanAccessText(userId, textId, staffAccess);

        LambdaQueryWrapper<SentenceFavorite> query = new LambdaQueryWrapper<SentenceFavorite>()
                .eq(SentenceFavorite::getUserId, userId)
                .eq(SentenceFavorite::getTextId, textId)
                .eq(SentenceFavorite::getSentence, normalizedSentence);
        SentenceFavorite existing = sentenceFavoriteMapper.selectOne(query);
        if (existing != null) {
            sentenceFavoriteMapper.deleteById(existing.getId());
            return Map.of("favorited", false);
        }

        SentenceFavorite favorite = new SentenceFavorite();
        favorite.setUserId(userId);
        favorite.setTextId(textId);
        favorite.setSentence(normalizedSentence);
        try {
            sentenceFavoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ignored) {
            SentenceFavorite concurrent = sentenceFavoriteMapper.selectOne(query);
            if (concurrent == null) throw ignored;
            favorite = concurrent;
        }
        return Map.of("favorited", true, "id", favorite.getId());
    }

    @Override
    public IPage<SentenceFavorite> list(Long userId, int page, int size) {
        if (userId == null) throw new BusinessException("用户不存在");
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, 50));
        return sentenceFavoriteMapper.selectPageWithSource(new Page<>(safePage, safeSize), userId);
    }

    @Override
    @Transactional
    public void delete(Long id, Long userId) {
        if (id == null || id <= 0 || userId == null) throw new BusinessException("收藏记录无效");
        int deleted = sentenceFavoriteMapper.delete(new LambdaQueryWrapper<SentenceFavorite>()
                .eq(SentenceFavorite::getId, id)
                .eq(SentenceFavorite::getUserId, userId));
        if (deleted == 0) throw new BusinessException("收藏记录不存在或无权删除");
    }

    private String validate(Long userId, Long textId, String sentence) {
        if (userId == null) throw new BusinessException("用户不存在");
        if (textId == null || textId <= 0) throw new BusinessException("文章ID无效");
        String normalized = sentence == null ? "" : sentence.trim();
        if (normalized.isEmpty()) throw new BusinessException("例句不能为空");
        if (normalized.length() > 2000) throw new BusinessException("例句不能超过2000字");
        return normalized;
    }

    private void assertCanAccessText(Long userId, Long textId, boolean staffAccess) {
        Text text = textMapper.selectById(textId);
        if (text == null) throw new BusinessException("文章不存在或已删除");
        if (staffAccess || userId.equals(text.getUploadedBy()) || Integer.valueOf(1).equals(text.getIsPublic())
                || Integer.valueOf(1).equals(text.getIsFeatured())) return;

        List<Long> linkedUnitIds = textUnitMapper.selectUnitIdsByTextId(textId);
        List<Long> unitIds = new ArrayList<>(linkedUnitIds == null ? List.of() : linkedUnitIds);
        if (text.getUnitId() != null && !unitIds.contains(text.getUnitId())) unitIds.add(text.getUnitId());
        unitIds.removeIf(id -> id == null || id.equals(999L));
        if (unitIds.isEmpty()) throw new BusinessException("无权访问该文章");

        Set<Long> accessibleCourseIds = new HashSet<>();
        List<Long> classIds = studentClassesService.getClassIdsByStudentId(userId);
        for (Long classId : classIds == null ? List.<Long>of() : classIds) {
            com.english.entity.Class studentClass = classService.getClassById(classId);
            if (studentClass != null && studentClass.getCourseId() != null) {
                accessibleCourseIds.add(studentClass.getCourseId());
            }
        }
        for (Unit unit : unitMapper.selectBatchIds(unitIds)) {
            if (unit != null && accessibleCourseIds.contains(unit.getCourseId())) return;
        }
        throw new BusinessException("无权访问该文章");
    }
}
