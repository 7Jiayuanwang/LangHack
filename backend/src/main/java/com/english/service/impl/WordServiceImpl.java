package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Word;
import com.english.mapper.WordMapper;
import com.english.service.WordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WordServiceImpl extends ServiceImpl<WordMapper, Word> implements WordService {

    @Override
    public Map<String, Object> getWordsByPage(Map<String, Object> params) {
        int page = params.get("page") != null ? ((Number) params.get("page")).intValue() : 1;
        int size = params.get("size") != null ? ((Number) params.get("size")).intValue() : 10;
        // Boundary validation to prevent negative offset/limit
        page = Math.max(1, page);
        size = Math.max(1, Math.min(size, 100));
        String keyword = (String) params.get("keyword");
        String difficulty = (String) params.get("difficulty");
        Long unitId = params.get("unitId") != null ? Long.valueOf(params.get("unitId").toString()) : null;

        Page<Word> pageParam = new Page<>(page, size);
        QueryWrapper<Word> queryWrapper = new QueryWrapper<>();

        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("word", keyword)
                .or()
                .like("definition", keyword)
                .or()
                .like("example", keyword)
            );
        }

        if (difficulty != null && !difficulty.trim().isEmpty()) {
            queryWrapper.eq("difficulty", difficulty);
        }

        if (unitId != null) {
            queryWrapper.eq("unit_id", unitId);
        }

        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");

        IPage<Word> pageResult = page(pageParam, queryWrapper);

        Map<String, Object> result = new HashMap<>();
        result.put("records", pageResult.getRecords());
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return result;
    }

    @Override
    public Word getWordById(Long id) {
        QueryWrapper<Word> wrapper = new QueryWrapper<>();
        wrapper.eq("id", id).eq("deleted", 0);
        return getOne(wrapper);
    }

    @Override
    public Word getWordByWord(String word) {
        if (word == null) return null;
        QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("word", word.toLowerCase());
        queryWrapper.eq("deleted", 0);
        return getOne(queryWrapper);
    }

    @Override
    @Transactional
    public boolean createWord(Word word) {
        if (word.getDifficulty() == null) {
            word.setDifficulty("MEDIUM");
        }
        // 统一转换为小写存储
        if (word.getWord() != null) {
            word.setWord(word.getWord().toLowerCase().trim());
        }
        word.setCreatedAt(LocalDateTime.now());
        word.setUpdatedAt(LocalDateTime.now());
        word.setDeleted(0);
        return save(word);
    }

    @Override
    @Transactional
    public boolean updateWord(Word word) {
        word.setUpdatedAt(LocalDateTime.now());
        return updateById(word);
    }

    @Override
    @Transactional
    public boolean deleteWord(Long id) {
        Word word = new Word();
        word.setId(id);
        word.setDeleted(1);
        word.setUpdatedAt(LocalDateTime.now());
        return updateById(word);
    }

    @Override
    public List<Word> getWordsByUnit(Long unitId) {
        QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("unit_id", unitId);
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByAsc("word");
        return list(queryWrapper);
    }

    @Override
    public List<Word> getWordsByDifficulty(String difficulty) {
        QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("difficulty", difficulty);
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public List<Word> getRandomWords(int count) {
        // 参数边界校验
        int safeCount = Math.max(1, Math.min(count, 100));

        QueryWrapper<Word> countWrapper = new QueryWrapper<>();
        countWrapper.eq("deleted", 0);
        Long total = count(countWrapper);

        if (total == null || total == 0) {
            return new java.util.ArrayList<>();
        }

        // 如果可用单词数少于请求数，直接返回所有单词
        if (total <= safeCount) {
            QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("deleted", 0);
            return list(queryWrapper);
        }

        // 随机选择 offset (避免 ORDER BY RAND() 的全表扫描性能问题)
        int offset = (int) (Math.random() * (total - safeCount));

        QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("deleted", 0);
        queryWrapper.last("LIMIT " + offset + ", " + safeCount);

        return list(queryWrapper);
    }

    @Override
    public List<Word> searchWords(String keyword) {
        QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.trim().isEmpty()) {
            queryWrapper.and(wrapper -> wrapper
                .like("word", keyword)
                .or()
                .like("definition", keyword)
                .or()
                .like("example", keyword)
            );
        }
        queryWrapper.eq("deleted", 0);
        queryWrapper.orderByDesc("created_time");
        return list(queryWrapper);
    }

    @Override
    public Map<String, Object> getWordStats() {
        Map<String, Object> stats = new HashMap<>();

        QueryWrapper<Word> easyWrapper = new QueryWrapper<>();
        easyWrapper.eq("difficulty", "EASY");
        easyWrapper.eq("deleted", 0);
        Long easyCount = count(easyWrapper);

        QueryWrapper<Word> mediumWrapper = new QueryWrapper<>();
        mediumWrapper.eq("difficulty", "MEDIUM");
        mediumWrapper.eq("deleted", 0);
        Long mediumCount = count(mediumWrapper);

        QueryWrapper<Word> hardWrapper = new QueryWrapper<>();
        hardWrapper.eq("difficulty", "HARD");
        hardWrapper.eq("deleted", 0);
        Long hardCount = count(hardWrapper);

        stats.put("totalEasy", easyCount);
        stats.put("totalMedium", mediumCount);
        stats.put("totalHard", hardCount);
        stats.put("totalWords", easyCount + mediumCount + hardCount);

        return stats;
    }
}