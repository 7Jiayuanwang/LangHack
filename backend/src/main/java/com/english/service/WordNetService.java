package com.english.service;

import com.english.entity.Word;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 词汇查询服务
 * 提供词汇查询、词义获取、同义词反义词等功能
 * 集成ECDICT、WordNet和数据库词汇数据
 */
@Service
public class WordNetService {

    private static final Logger logger = LoggerFactory.getLogger(WordNetService.class);

    @Autowired
    private WordService wordService;

    @Autowired
    private ECDICTService ecdictService;

    private ObjectMapper objectMapper;

    @PostConstruct
    public void init() {
        try {
            // 初始化Jackson ObjectMapper
            objectMapper = new ObjectMapper();
            logger.info("词汇服务初始化完成，使用数据库词汇数据");
        } catch (Exception e) {
            logger.error("词汇服务初始化失败", e);
        }
    }

    /**
     * 获取单词的完整信息（集成ECDICT优先，数据库质量覆盖）
     */
    public Map<String, Object> getWordInfo(String word) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 输入验证 — 防止无效输入
            if (word == null || word.trim().isEmpty()) {
                result.put("found", false);
                result.put("error", "单词不能为空");
                result.put("primarySource", "error");
                return result;
            }

            // 单词长度限制
            if (word.length() > 100) {
                logger.warn("单词过长: {} 字符，已截断为 100", word.length());
                word = word.substring(0, 100);
            }

            word = word.trim().toLowerCase();

            // 检查是否为需要数据库覆盖的常见词汇（质量修正）
            if (needsDatabaseOverride(word)) {
                Word dbWord = wordService.getWordByWord(word);
                if (dbWord != null) {
                    result = convertDatabaseWordToMap(dbWord);
                    result.put("found", true);
                    result.put("primarySource", "database_override");
                    result.put("word", word);
                    return result;
                }
            }

            // 优先查询ECDICT
            if (ecdictService.isECDICTAvailable()) {
                Map<String, Object> ecdictResult = ecdictService.getWordInfo(word);
                if (Boolean.TRUE.equals(ecdictResult.get("found"))) {
                    result = ecdictResult;
                    result.put("primarySource", "ecdict");
                    return result;
                } else if (Boolean.TRUE.equals(ecdictResult.get("fuzzy"))) {
                    result = ecdictResult;
                    result.put("primarySource", "ecdict_fuzzy");
                    return result;
                }
            }

            // ECDICT中没有，查询数据库
            Word dbWord = wordService.getWordByWord(word);

            if (dbWord != null) {
                result = convertDatabaseWordToMap(dbWord);
                result.put("found", true);
                result.put("primarySource", "database");
            } else {
                // 如果数据库中也没有，创建一个基本的词汇信息
                result.put("word", word);
                result.put("definition", "该词汇暂无详细定义");
                result.put("found", false);
                result.put("suggestion", "可以在词汇管理中添加该词汇的详细信息");
                result.put("primarySource", "none");
            }

            result.put("word", word);

        } catch (Exception e) {
            logger.error("查询单词信息失败: {}", word, e);
            result.put("word", word);
            result.put("found", false);
            result.put("error", "词汇查询失败");
            result.put("primarySource", "error");
        }

        return result;
    }

    /**
     * 获取词典统计信息
     */
    public Map<String, Object> getDictionaryStats() {
        Map<String, Object> stats = new HashMap<>();

        if (ecdictService.isECDICTAvailable()) {
            Map<String, Object> ecdictStats = ecdictService.getDictionaryStats();
            stats.putAll(ecdictStats);
            stats.put("primaryDictionary", "ECDICT");
        } else {
            stats.put("primaryDictionary", "Database");
            stats.put("totalWords", 0);
        }

        return stats;
    }

    /**
     * 搜索词汇
     */
    public List<Map<String, Object>> searchWords(String keyword, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            // 输入验证 — 防止无效输入
            if (keyword == null || keyword.trim().isEmpty()) {
                return results;
            }
            if (keyword.length() > 100) {
                logger.warn("搜索关键词过长: {} 字符，已截断为 100", keyword.length());
                keyword = keyword.substring(0, 100);
            }

            // 限制返回结果数量
            int safeLim = Math.max(1, Math.min(limit, 100));

            // 优先使用ECDICT搜索
            if (ecdictService.isECDICTAvailable()) {
                results = ecdictService.searchWords(keyword, safeLim);
            }

            // 如果ECDICT结果不足，补充数据库搜索
            if (results.size() < safeLim) {
                // 这里可以实现数据库的模糊搜索逻辑
                // 简单起见，先返回ECDICT的结果
            }

        } catch (Exception e) {
            logger.error("搜索词汇失败: {}", keyword, e);
        }

        return results;
    }

    /**
     * 查询或创建单词信息（用于生词本自动添加功能）
     * 如果数据库中不存在该词，则从ECDICT获取信息并创建
     */
    public Map<String, Object> getOrCreateWord(String word) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 输入验证 — 防止无效输入
            if (word == null || word.trim().isEmpty()) {
                result.put("found", false);
                result.put("error", "单词不能为空");
                result.put("primarySource", "error");
                return result;
            }

            // 单词长度限制
            if (word.length() > 100) {
                logger.warn("单词过长: {} 字符，已截断为 100", word.length());
                word = word.substring(0, 100);
            }

            word = word.trim().toLowerCase();

            // 第一步：检查数据库中是否已存在该词
            Word dbWord = wordService.getWordByWord(word);

            if (dbWord != null) {
                // 数据库中已存在，直接返回
                result = convertDatabaseWordToMap(dbWord);
                result.put("found", true);
                result.put("primarySource", "database");
                result.put("word", word);
                result.put("wordId", dbWord.getId());
                return result;
            }

            // 第二步：数据库中不存在，尝试从ECDICT获取信息并创建
            if (ecdictService.isECDICTAvailable()) {
                Map<String, Object> ecdictResult = ecdictService.getWordInfo(word);

                if (Boolean.TRUE.equals(ecdictResult.get("found"))) {
                    // 从ECDICT获取到信息，创建数据库记录
                    Word newWord = createWordFromECDICT(word, ecdictResult);

                    if (newWord != null) {
                        // 创建成功，返回新创建的词汇信息
                        result = convertDatabaseWordToMap(newWord);
                        result.put("found", true);
                        result.put("primarySource", "ecdict_created");
                        result.put("word", word);
                        result.put("wordId", newWord.getId());
                        result.put("created", true);
                        return result;
                    }
                }
            }

            // 第三步：ECDICT也没有，创建一个基本的词汇记录
            Word basicWord = createBasicWord(word);
            if (basicWord != null) {
                result = convertDatabaseWordToMap(basicWord);
                result.put("found", true);
                result.put("primarySource", "basic_created");
                result.put("word", word);
                result.put("wordId", basicWord.getId());
                result.put("created", true);
                result.put("definition", "基础词汇，待完善定义");
            } else {
                // 创建失败
                result.put("word", word);
                result.put("found", false);
                result.put("error", "无法创建词汇记录");
                result.put("primarySource", "error");
            }

        } catch (Exception e) {
            logger.error("查询或创建词汇失败: {}", word, e);
            result.put("word", word);
            result.put("found", false);
            result.put("error", "词汇查询失败");
            result.put("primarySource", "error");
        }

        return result;
    }

    /**
     * 从ECDICT信息创建Word实体
     * FIX: 检查 createWord 返回值，创建失败时返回 null
     */
    private Word createWordFromECDICT(String word, Map<String, Object> ecdictResult) {
        try {
            Word newWord = new Word();

            // 设置基本词汇信息
            newWord.setWord(word.toLowerCase());
            newWord.setPronunciation(getStringValue(ecdictResult, "pronunciation"));
            newWord.setDefinition(getStringValue(ecdictResult, "definition"));
            newWord.setPartOfSpeech(getStringValue(ecdictResult, "partOfSpeech"));
            newWord.setDifficulty(getStringValue(ecdictResult, "difficulty"));
            newWord.setExampleSentence(getStringValue(ecdictResult, "exampleSentence"));
            newWord.setAudioUrl(getStringValue(ecdictResult, "audioUrl"));

            // 设置JSON字段
            newWord.setSynonyms(getStringValue(ecdictResult, "synonyms"));
            newWord.setAntonyms(getStringValue(ecdictResult, "antonyms"));
            newWord.setWordnetId(getStringValue(ecdictResult, "wordnetId"));
            newWord.setMultipleDefinitions(getStringValue(ecdictResult, "multipleDefinitions"));
            newWord.setSource("ecdict_auto");
            newWord.setDeleted(0);

            // 创建数据库记录
            boolean success = wordService.createWord(newWord);

            if (success) {
                logger.info("自动创建词汇成功: {} (来源: ECDICT)", word);
                return newWord;
            } else {
                logger.warn("自动创建词汇失败: {} (来源: ECDICT)", word);
                return null;
            }

        } catch (Exception e) {
            logger.error("从ECDICT创建词汇失败: {}", word, e);
            return null;
        }
    }

    /**
     * 创建基础词汇记录
     */
    private Word createBasicWord(String word) {
        try {
            Word newWord = new Word();

            // 设置基本词汇信息
            newWord.setWord(word.toLowerCase());
            newWord.setPronunciation("");
            newWord.setDefinition("基础词汇，待完善定义");
            newWord.setPartOfSpeech("unknown");
            newWord.setDifficulty("MEDIUM");
            newWord.setExampleSentence("");
            newWord.setAudioUrl("");

            // 设置JSON字段为空
            newWord.setSynonyms("[]");
            newWord.setAntonyms("[]");
            newWord.setWordnetId("basic_" + word.hashCode());
            newWord.setMultipleDefinitions("[]");
            newWord.setSource("basic_auto");
            newWord.setDeleted(0);

            // 创建数据库记录
            boolean success = wordService.createWord(newWord);

            if (success) {
                logger.info("自动创建基础词汇成功: {}", word);
                return newWord;
            } else {
                logger.warn("自动创建基础词汇失败: {}", word);
                return null;
            }

        } catch (Exception e) {
            logger.error("创建基础词汇失败: {}", word, e);
            return null;
        }
    }

    /**
     * 安全获取字符串值
     */
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value == null) {
            return "";
        }
        String result = value.toString();
        return result.isEmpty() ? "" : result;
    }

    /**
     * 将数据库单词对象转换为Map（与ECDICT格式统一）
     */
    private Map<String, Object> convertDatabaseWordToMap(Word word) {
        Map<String, Object> result = new HashMap<>();

        // 基本字段，与Word实体类对应
        result.put("word", word.getWord());
        result.put("pronunciation", word.getPronunciation() != null ? word.getPronunciation() : "");
        result.put("definition", word.getDefinition() != null ? word.getDefinition() : "");
        result.put("exampleSentence", word.getExampleSentence() != null ? word.getExampleSentence() : "");
        result.put("audioUrl", word.getAudioUrl() != null ? word.getAudioUrl() : "");
        result.put("partOfSpeech", word.getPartOfSpeech() != null ? word.getPartOfSpeech() : "unknown");
        result.put("difficulty", word.getDifficulty() != null ? word.getDifficulty() : "MEDIUM");

        // JSON格式字段，保持数据库原始格式
        result.put("synonyms", word.getSynonyms() != null ? word.getSynonyms() : "[]");
        result.put("antonyms", word.getAntonyms() != null ? word.getAntonyms() : "[]");
        result.put("wordnetId", word.getWordnetId() != null ? word.getWordnetId() : "");
        result.put("multipleDefinitions", word.getMultipleDefinitions() != null ? word.getMultipleDefinitions() : "[]");
        result.put("source", word.getSource() != null ? word.getSource() : "manual");

        // 兼容性字段 - 为前端提供便利
        result.put("pos", result.get("partOfSpeech"));
        result.put("translation", ""); // 数据库通常不包含翻译字段
        result.put("collins", "");    // 数据库通常不包含柯林斯等级
        result.put("oxford", "");    // 数据库通常不包含牛津等级
        result.put("tag", "");       // 数据库通常不包含标签
        result.put("bnc", "");       // 数据库通常不包含BNC词频
        result.put("frq", "");       // 数据库通常不包含词频
        result.put("exchange", "");  // 数据库通常不包含词形变化

        // 解析JSON字段为对象，便于前端使用
        result.put("synonymsList", parseJsonArray(word.getSynonyms()));
        result.put("antonymsList", parseJsonArray(word.getAntonyms()));
        result.put("definitionsList", getDefinitionsField(word));

        return result;
    }

    /**
     * 安全获取字符串字段
     */
    private Object getStringField(Word word, String fieldName) {
        try {
            switch (fieldName) {
                case "synonyms":
                    return parseJsonArray(word.getSynonyms());
                case "antonyms":
                    return parseJsonArray(word.getAntonyms());
                case "source":
                    return word.getSource() != null ? word.getSource() : "manual";
                default:
                    return "";
            }
        } catch (Exception e) {
            // 返回默认值
            if ("synonyms".equals(fieldName) || "antonyms".equals(fieldName)) {
                return new ArrayList<>();
            }
            return "manual";
        }
    }

    /**
     * 解析JSON数组
     */
    private List<String> parseJsonArray(String jsonString) {
        try {
            if (jsonString != null && !jsonString.isEmpty()) {
                return objectMapper.readValue(jsonString, List.class);
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return new ArrayList<>();
    }

    /**
     * 安全获取多词义字段
     */
    private List<Map<String, Object>> getDefinitionsField(Word word) {
        try {
            String multipleDefs = word.getMultipleDefinitions();
            if (multipleDefs != null && !multipleDefs.isEmpty()) {
                return objectMapper.readValue(
                    multipleDefs,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
                );
            }
        } catch (Exception e) {
            // 忽略解析错误，返回空列表
        }
        return new ArrayList<>();
    }

    /**
     * 获取文本中可点击的单词列表
     */
    public List<Map<String, Object>> getClickableWordsFromText(String text) {
        List<Map<String, Object>> clickableWords = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            return clickableWords;
        }

        // 文本长度防御 — 防止处理过大的文本
        if (text.length() > 50000) {
            logger.warn("文本过长: {} 字符，已截断为 50000", text.length());
            text = text.substring(0, 50000);
        }

        // 简单的单词分割逻辑
        String[] words = text.split("\\s+");
        int position = 0;

        for (String word : words) {
            // 清理标点符号
            String cleanWord = word.replaceAll("[^a-zA-Z']", "").toLowerCase();

            if (cleanWord.length() > 1) { // 只处理长度大于1的单词
                Map<String, Object> wordInfo = new HashMap<>();
                wordInfo.put("text", word);
                wordInfo.put("cleanWord", cleanWord);
                wordInfo.put("position", position);
                wordInfo.put("clickable", true);

                clickableWords.add(wordInfo);
            }

            position += word.length() + 1; // +1 for space
        }

        return clickableWords;
    }

    /**
     * 检查是否需要数据库覆盖（ECDICT数据质量修正）
     * 对于常见的翻译错误，优先使用数据库中的正确翻译
     */
    private boolean needsDatabaseOverride(String word) {
        if (word == null || word.isEmpty()) {
            return false;
        }

        String lowerWord = word.toLowerCase().trim();

        // 长度检查防御
        if (lowerWord.length() > 100) {
            lowerWord = lowerWord.substring(0, 100);
        }

        // ECDICT中存在翻译问题的常见词汇列表
        // 这些词汇在ECDICT中翻译不准确，需要用数据库覆盖
        String[] problematicWords = {
            "very",     // ECDICT翻译为"真正的"，应为"非常；很"
            "really",   // 可能存在类似问题
            "quite",    // 可能存在类似问题
            // 可以根据需要添加更多词汇
        };

        for (String problematicWord : problematicWords) {
            if (lowerWord.equals(problematicWord)) {
                logger.debug("检测到ECDICT翻译问题词汇: {}，使用数据库覆盖", word);
                return true;
            }
        }

        return false;
    }

    /**
     * 检查服务是否可用
     */
    public boolean isWordNetAvailable() {
        // 当前版本总是返回true，因为使用数据库词汇
        return true;
    }
}