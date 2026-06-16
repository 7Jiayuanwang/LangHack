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

    @Autowired(required = false)
    private TencentTranslateService translateService;

    private ObjectMapper objectMapper;

    // CSV 兜底词典缓存(首次调用时加载)
    private Map<String, String> csvFallbackDict = null;

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
                // 如果数据库中也没有，尝试 AI 翻译服务
                result.put("word", word);
                result.put("found", false);
                result.put("primarySource", "none");

                String translation = tryTranslate(word);
                if (translation != null && !translation.isEmpty()) {
                    result.put("definition", translation);
                    result.put("suggestion", "AI 翻译结果，仅供参考");
                } else {
                    result.put("definition", "该词汇暂无详细定义");
                    result.put("suggestion", "可以在词汇管理中添加该词汇的详细信息");
                }
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

            // 第三步：ECDICT 也没有,尝试 CSV 兜底 + AI 翻译兜底
            String fallbackDefinition = tryGetDefinitionFromFallback(word);

            if (fallbackDefinition != null && !fallbackDefinition.isEmpty()) {
                // 从兜底途径获取到真实释义,创建数据库记录
                Word newWord = new Word();
                newWord.setWord(word.toLowerCase());
                newWord.setPronunciation("");
                newWord.setDefinition(fallbackDefinition);
                newWord.setPartOfSpeech("unknown");
                newWord.setDifficulty("MEDIUM");
                newWord.setExampleSentence("");
                newWord.setAudioUrl("");
                newWord.setSynonyms("[]");
                newWord.setAntonyms("[]");
                newWord.setWordnetId("fallback_" + word.hashCode());
                newWord.setMultipleDefinitions("[]");
                newWord.setSource("fallback_auto");
                newWord.setDeleted(0);

                boolean success = wordService.createWord(newWord);
                if (success) {
                    logger.info("通过兜底途径创建词汇成功: {} -> {}", word, fallbackDefinition);
                    result = convertDatabaseWordToMap(newWord);
                    result.put("found", true);
                    result.put("primarySource", "fallback_created");
                    result.put("word", word);
                    result.put("wordId", newWord.getId());
                    result.put("created", true);
                    return result;
                } else {
                    logger.warn("兜底途径获取释义成功但写库失败: {}", word);
                }
            }

            // 所有途径都失败,返回 not found(不写占位记录)
            logger.warn("所有途径均无法获取释义: {}", word);
            result.put("word", word);
            result.put("found", false);
            result.put("error", "无法获取词汇释义");
            result.put("primarySource", "not_found");

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
     * 从 CSV 兜底词典或 AI 翻译获取释义
     * 返回 null 表示所有途径都失败
     */
    private String tryGetDefinitionFromFallback(String word) {
        // 1. 尝试从三本 CSV 获取(懒加载缓存)
        if (csvFallbackDict == null) {
            loadCsvFallbackDict();
        }

        String key = word.toLowerCase().trim();
        String csvDef = csvFallbackDict.get(key);
        if (csvDef != null && !csvDef.isEmpty()) {
            logger.info("从 CSV 兜底词典找到释义: {} -> {}", word, csvDef);
            return csvDef;
        }

        // 2. CSV 没有,尝试 AI 翻译
        if (translateService != null) {
            try {
                String translated = translateService.translateEnToCn(word);
                if (translated != null && !translated.trim().isEmpty()
                    && !translated.equals(word)) {
                    logger.info("从 AI 翻译获取释义: {} -> {}", word, translated);
                    return translated;
                }
            } catch (Exception e) {
                logger.warn("AI 翻译失败: {}", word, e);
            }
        }

        return null;
    }

    /**
     * 加载三本 CSV 作为兜底词典(六级、四级、高考)
     */
    private void loadCsvFallbackDict() {
        csvFallbackDict = new HashMap<>();
        String[] csvPaths = {
            "frontend/public/data/六级核心词汇.csv",
            "frontend/public/data/四级核心词汇.csv",
            "frontend/public/data/高考英语必背词汇.csv"
        };

        for (String relativePath : csvPaths) {
            try {
                // 从项目根目录读取(Spring Boot 启动目录)
                java.io.File file = new java.io.File(relativePath);
                if (!file.exists()) {
                    logger.warn("CSV 兜底词典不存在: {}", relativePath);
                    continue;
                }

                List<String> lines = java.nio.file.Files.readAllLines(file.toPath(),
                    java.nio.charset.StandardCharsets.UTF_8);

                // 跳过表头,解析 "word","meaning"
                for (int i = 1; i < lines.size(); i++) {
                    String line = lines.get(i).trim();
                    if (line.isEmpty()) continue;

                    // 简单 CSV 解析: "word","meaning"
                    java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"([^\"]+)\",\"([^\"]+)\"")
                        .matcher(line);
                    if (m.find()) {
                        String w = m.group(1).toLowerCase().trim();
                        String meaning = m.group(2).trim();
                        // 第一个出现的优先(六级 > 四级 > 高考)
                        if (!csvFallbackDict.containsKey(w)) {
                            csvFallbackDict.put(w, meaning);
                        }
                    }
                }
                logger.info("加载 CSV 兜底词典: {} ({} 词)", relativePath, lines.size() - 1);
            } catch (Exception e) {
                logger.error("加载 CSV 兜底词典失败: {}", relativePath, e);
            }
        }
        logger.info("CSV 兜底词典加载完成,共 {} 词", csvFallbackDict.size());
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

    /**
     * 尝试使用 AI 翻译服务获取词义
     * 仅在 ECDICT 和数据库都没有该词时调用
     */
    private String tryTranslate(String word) {
        if (translateService == null) {
            logger.debug("翻译服务不可用，跳过 AI 翻译: {}", word);
            return null;
        }
        try {
            String translation = translateService.translateEnToCn(word);
            if (translation != null && !translation.isEmpty()) {
                logger.info("AI 翻译成功: {} → {}", word, translation);
                return translation;
            }
        } catch (Exception e) {
            logger.warn("AI 翻译失败: {} - {}", word, e.getMessage());
        }
        return null;
    }
}