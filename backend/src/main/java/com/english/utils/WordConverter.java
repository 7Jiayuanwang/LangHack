package com.english.utils;

import com.english.entity.Word;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 词汇数据转换工具类
 * 用于在Map格式和Word实体对象之间转换
 */
@Component
public class WordConverter {

    private final ObjectMapper objectMapper;

    public WordConverter() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 将Map格式的词汇数据转换为Word实体对象
     * @param wordData Map格式的词汇数据
     * @return Word实体对象
     */
    public Word convertToWordEntity(Map<String, Object> wordData) {
        if (wordData == null) {
            return null;
        }

        Word word = new Word();

        // 基本字段映射
        word.setWord(getStringValue(wordData, "word"));
        word.setPronunciation(getStringValue(wordData, "pronunciation"));
        word.setDefinition(getStringValue(wordData, "definition"));
        word.setExampleSentence(getStringValue(wordData, "exampleSentence"));
        word.setAudioUrl(getStringValue(wordData, "audioUrl"));
        word.setPartOfSpeech(getStringValue(wordData, "partOfSpeech"));
        word.setDifficulty(getStringValue(wordData, "difficulty"));

        // JSON字段映射
        word.setSynonyms(getStringValue(wordData, "synonyms"));
        word.setAntonyms(getStringValue(wordData, "antonyms"));
        word.setWordnetId(getStringValue(wordData, "wordnetId"));
        word.setMultipleDefinitions(getStringValue(wordData, "multipleDefinitions"));
        word.setSource(getStringValue(wordData, "source"));

        // 设置默认值
        if (word.getDifficulty() == null || word.getDifficulty().isEmpty()) {
            word.setDifficulty("MEDIUM");
        }

        if (word.getDeleted() == null) {
            word.setDeleted(0);
        }

        // 设置时间戳
        LocalDateTime now = LocalDateTime.now();
        word.setCreatedTime(now);
        word.setUpdatedTime(now);

        return word;
    }

    /**
     * 将Word实体对象转换为Map格式 (仅返回前端需要的字段，隐藏敏感内部字段)
     * 隐藏字段: deleted (逻辑删除标记), createdAt, updatedAt (时间戳)
     * @param word Word实体对象
     * @return Map格式的词汇数据
     */
    public Map<String, Object> convertToMap(Word word) {
        if (word == null) {
            return null;
        }

        Map<String, Object> result = new HashMap<>();

        // 仅返回前端需要的字段，不暴露内部实现细节
        result.put("id", word.getId());
        result.put("word", word.getWord() != null ? word.getWord() : "");
        result.put("pronunciation", word.getPronunciation() != null ? word.getPronunciation() : "");
        result.put("definition", word.getDefinition() != null ? word.getDefinition() : "");
        result.put("exampleSentence", word.getExampleSentence() != null ? word.getExampleSentence() : "");
        result.put("audioUrl", word.getAudioUrl() != null ? word.getAudioUrl() : "");
        result.put("partOfSpeech", word.getPartOfSpeech() != null ? word.getPartOfSpeech() : "");
        result.put("difficulty", word.getDifficulty() != null ? word.getDifficulty() : "MEDIUM");
        result.put("synonyms", word.getSynonyms() != null ? word.getSynonyms() : "[]");
        result.put("antonyms", word.getAntonyms() != null ? word.getAntonyms() : "[]");
        result.put("wordnetId", word.getWordnetId() != null ? word.getWordnetId() : "");
        result.put("multipleDefinitions", word.getMultipleDefinitions() != null ? word.getMultipleDefinitions() : "[]");
        result.put("source", word.getSource() != null ? word.getSource() : "manual");

        // 隐藏敏感字段：不返回 deleted (允许前端绕过逻辑删除), createdAt, updatedAt (时间戳)

        return result;
    }

    /**
     * 批量转换Map列表为Word实体列表
     * @param wordDataList Map格式的词汇数据列表
     * @return Word实体对象列表
     */
    public List<Word> batchConvertToWordEntities(List<Map<String, Object>> wordDataList) {
        List<Word> words = new ArrayList<>();
        if (wordDataList != null) {
            for (Map<String, Object> wordData : wordDataList) {
                Word word = convertToWordEntity(wordData);
                if (word != null) {
                    words.add(word);
                }
            }
        }
        return words;
    }

    /**
     * 从Map中安全获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    /**
     * 验证词汇数据完整性
     * @param wordData 词汇数据
     * @return 验证结果
     */
    public boolean validateWordData(Map<String, Object> wordData) {
        if (wordData == null) {
            return false;
        }

        // 必填字段检查
        String word = getStringValue(wordData, "word");
        String definition = getStringValue(wordData, "definition");

        if (word.isEmpty() || definition.isEmpty()) {
            return false;
        }

        // 难度等级验证
        String difficulty = getStringValue(wordData, "difficulty");
        if (!difficulty.isEmpty() && !isValidDifficulty(difficulty)) {
            return false;
        }

        // 词性验证
        String partOfSpeech = getStringValue(wordData, "partOfSpeech");
        if (!partOfSpeech.isEmpty() && !isValidPartOfSpeech(partOfSpeech)) {
            return false;
        }

        return true;
    }

    /**
     * 验证难度等级
     */
    private boolean isValidDifficulty(String difficulty) {
        return "EASY".equals(difficulty) || "MEDIUM".equals(difficulty) || "HARD".equals(difficulty);
    }

    /**
     * 验证词性
     */
    private boolean isValidPartOfSpeech(String partOfSpeech) {
        String[] validPos = {
            "noun", "verb", "adjective", "adverb", "preposition",
            "conjunction", "pronoun", "interjection", "article", "unknown"
        };

        for (String pos : validPos) {
            if (pos.equals(partOfSpeech)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从Map中提取同义词列表
     */
    public List<String> extractSynonyms(Map<String, Object> wordData) {
        try {
            // 尝试从synonymsList字段获取
            Object synonymsList = wordData.get("synonymsList");
            if (synonymsList instanceof List) {
                return (List<String>) synonymsList;
            }

            // 尝试从synonyms字段解析JSON
            String synonymsJson = getStringValue(wordData, "synonyms");
            if (!synonymsJson.isEmpty() && !synonymsJson.equals("[]")) {
                return objectMapper.readValue(synonymsJson, List.class);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return new ArrayList<>();
    }

    /**
     * 从Map中提取多词义定义列表
     */
    public List<Map<String, Object>> extractDefinitions(Map<String, Object> wordData) {
        try {
            // 尝试从definitionsList字段获取
            Object definitionsList = wordData.get("definitionsList");
            if (definitionsList instanceof List) {
                return (List<Map<String, Object>>) definitionsList;
            }

            // 尝试从multipleDefinitions字段解析JSON
            String definitionsJson = getStringValue(wordData, "multipleDefinitions");
            if (!definitionsJson.isEmpty() && !definitionsJson.equals("[]")) {
                return objectMapper.readValue(
                    definitionsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return new ArrayList<>();
    }
}
