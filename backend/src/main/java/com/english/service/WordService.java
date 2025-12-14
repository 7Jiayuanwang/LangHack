package com.english.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.english.entity.Word;

import java.util.List;
import java.util.Map;

public interface WordService extends IService<Word> {
    Map<String, Object> getWordsByPage(Map<String, Object> params);
    Word getWordById(Long id);
    Word getWordByWord(String word);
    boolean createWord(Word word);
    boolean updateWord(Word word);
    boolean deleteWord(Long id);
    List<Word> getWordsByUnit(Long unitId);
    List<Word> getWordsByDifficulty(String difficulty);
    List<Word> getRandomWords(int count);
    List<Word> searchWords(String keyword);
    Map<String, Object> getWordStats();
    boolean batchCreateWords(List<Word> words);
}