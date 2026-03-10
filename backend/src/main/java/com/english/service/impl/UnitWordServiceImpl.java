package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.english.entity.UnitWord;
import com.english.entity.Word;
import com.english.mapper.UnitWordMapper;
import com.english.mapper.WordMapper;
import com.english.service.UnitWordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Collections;

@Service
public class UnitWordServiceImpl extends ServiceImpl<UnitWordMapper, UnitWord> implements UnitWordService {

    @Autowired
    private WordMapper wordMapper;

    /**
     * 私有方法：过滤已删除的单词
     */
    private List<Word> filterNotDeleted(List<Word> words) {
        if (words == null) {
            return Collections.emptyList();
        }
        return words.stream()
                .filter(w -> w.getDeleted() == null || w.getDeleted() == 0)
                .collect(Collectors.toList());
    }

    @Override
    public boolean exists(Long unitId, Long wordId) {
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId)
               .eq("word_id", wordId);
        return count(wrapper) > 0;
    }

    @Override
    @Transactional
    public void addToUnit(Long unitId, Long wordId) {
        if (!exists(unitId, wordId)) {
            UnitWord unitWord = new UnitWord();
            unitWord.setUnitId(unitId);
            unitWord.setWordId(wordId);
            save(unitWord);
        }
    }

    @Override
    @Transactional
    public void removeFromUnit(Long unitId, Long wordId) {
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId)
               .eq("word_id", wordId);
        remove(wrapper);
    }

    @Override
    public List<Map<String, Object>> getWordsWithDetailsByUnitId(Long unitId) {
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId)
               .orderByAsc("id");

        List<UnitWord> unitWords = list(wrapper);
        if (unitWords == null || unitWords.isEmpty()) {
            return new ArrayList<>();
        }

        // 批量获取所有 Word（避免 N+1 查询）
        List<Long> wordIds = unitWords.stream()
                .map(UnitWord::getWordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Word> selectedWords = wordMapper.selectBatchIds(wordIds);
        Map<Long, Word> wordMapData = filterNotDeleted(selectedWords).stream()
                .collect(Collectors.toMap(Word::getId, w -> w, (a, b) -> a));

        List<Map<String, Object>> result = new ArrayList<>();
        for (UnitWord unitWord : unitWords) {
            Word word = wordMapData.get(unitWord.getWordId());
            // 跳过已删除的单词
            if (word == null) {
                continue;
            }

            Map<String, Object> wordMap_obj = new HashMap<>();
            wordMap_obj.put("id", unitWord.getId());
            wordMap_obj.put("unitId", unitWord.getUnitId());
            wordMap_obj.put("wordId", unitWord.getWordId());
            wordMap_obj.put("addedTime", unitWord.getCreatedTime());
            wordMap_obj.put("word", word.getWord());
            wordMap_obj.put("pronunciation", word.getPronunciation());
            wordMap_obj.put("definition", word.getDefinition());
            wordMap_obj.put("exampleSentence", word.getExampleSentence());
            wordMap_obj.put("partOfSpeech", word.getPartOfSpeech());
            wordMap_obj.put("difficulty", word.getDifficulty());
            wordMap_obj.put("audioUrl", word.getAudioUrl());

            result.add(wordMap_obj);
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getUnitsWithDetailsByWordId(Long wordId) {
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("word_id", wordId)
               .orderByAsc("id");

        List<UnitWord> unitWords = list(wrapper);
        if (unitWords == null) {
            unitWords = new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();

        for (UnitWord unitWord : unitWords) {
            Map<String, Object> unitMap = new HashMap<>();
            unitMap.put("id", unitWord.getId());
            unitMap.put("unitId", unitWord.getUnitId());
            unitMap.put("wordId", unitWord.getWordId());
            unitMap.put("addedTime", unitWord.getCreatedTime());
            result.add(unitMap);
        }

        return result;
    }

    @Override
    @Transactional
    public void batchAddToUnit(Long unitId, List<Long> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return;
        }

        // 一次查询获取所有已存在的记录（避免 N+1 查询）
        QueryWrapper<UnitWord> existWrapper = new QueryWrapper<>();
        existWrapper.eq("unit_id", unitId).in("word_id", wordIds);
        List<UnitWord> existing = list(existWrapper);
        Set<Long> existingWordIds = (existing != null ? existing : Collections.<UnitWord>emptyList()).stream()
                .map(UnitWord::getWordId)
                .collect(Collectors.toSet());

        // 只插入新的记录
        List<UnitWord> newRecords = wordIds.stream()
                .filter(wid -> !existingWordIds.contains(wid))
                .map(wid -> {
                    UnitWord uw = new UnitWord();
                    uw.setUnitId(unitId);
                    uw.setWordId(wid);
                    return uw;
                })
                .collect(Collectors.toList());

        if (!newRecords.isEmpty()) {
            saveBatch(newRecords);
        }
    }

    @Override
    @Transactional
    public void batchRemoveFromUnit(Long unitId, List<Long> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return;
        }
        QueryWrapper<UnitWord> wrapper = new QueryWrapper<>();
        wrapper.eq("unit_id", unitId)
               .in("word_id", wordIds);
        remove(wrapper);
    }
}