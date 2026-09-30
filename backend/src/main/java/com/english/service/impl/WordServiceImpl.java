package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.Word;
import com.english.exception.BusinessException;
import com.english.mapper.WordMapper;
import com.english.service.WordService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
                .like("example_sentence", keyword)
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
        queryWrapper.last("LIMIT 1");
        return getOne(queryWrapper);
    }

    @Override
    @Transactional
    public boolean createWord(Word word) {
        if (word == null || word.getWord() == null || word.getWord().trim().isEmpty()) {
            throw new BusinessException("单词不能为空");
        }
        if (word.getDifficulty() == null) {
            word.setDifficulty("MEDIUM");
        }
        // 统一转换为小写存储
        word.setWord(word.getWord().toLowerCase().trim());
        word.setCreatedTime(LocalDateTime.now());
        word.setUpdatedTime(LocalDateTime.now());
        word.setDeleted(0);
        return save(word);
    }

    @Override
    @Transactional
    public boolean updateWord(Word word) {
        if (word == null || word.getId() == null) {
            throw new BusinessException("单词ID不能为空");
        }
        word.setUpdatedTime(LocalDateTime.now());
        return updateById(word);
    }

    @Override
    @Transactional
    public boolean deleteWord(Long id) {
        // @TableLogic 会将 removeById 转换为 UPDATE SET deleted=1
        // 注意：不能用 updateById + setDeleted(1)，因为 @TableLogic 会从 SET 子句中排除 deleted 字段
        return removeById(id);
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
            return new ArrayList<>();
        }

        // 如果可用单词数少于请求数，直接返回所有单词
        if (total <= safeCount) {
            QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("deleted", 0);
            return list(queryWrapper);
        }

        // SQL 注入防护修复：使用 MyBatis Plus Page 对象实现分页，避免字符串拼接
        // 计算随机页码（页码从 1 开始）
        long maxPage = (total - 1) / safeCount + 1;
        int randomPage = (int) (Math.random() * maxPage) + 1;
        
        Page<Word> pageParam = new Page<>(randomPage, safeCount);
        QueryWrapper<Word> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("deleted", 0);
        
        IPage<Word> pageResult = page(pageParam, queryWrapper);
        return pageResult.getRecords();
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
                .like("example_sentence", keyword)
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
