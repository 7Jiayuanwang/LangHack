package com.english.service;

import com.english.entity.ECDICTWord;
import com.english.mapper.ECDICTWordMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ECDICT服务类
 * 提供ECDICT词典数据的解析和查询功能
 * 现在从数据库加载数据，避免每次启动都解析CSV文件
 */
@Service
public class ECDICTService {

    private static final Logger logger = LoggerFactory.getLogger(ECDICTService.class);

    @Autowired(required = false)
    private ECDICTWordMapper ecdictWordMapper;

    private Map<String, ECDICTWordDTO> ecdictDictionary;
    private ObjectMapper objectMapper;
    private boolean ecdictAvailable = false;
    private boolean useDatabase = true; // 是否使用数据库模式

    @PostConstruct
    public void init() {
        try {
            objectMapper = new ObjectMapper();
            ecdictDictionary = new ConcurrentHashMap<>();

            // 优先尝试从数据库加载
            if (ecdictWordMapper != null) {
                try {
                    loadFromDatabase();
                    ecdictAvailable = true;
                    useDatabase = true;
                    logger.info("ECDICT词典服务初始化成功（数据库模式），词汇总数: {}", ecdictWordMapper.getTotalCount());
                    return;
                } catch (Exception e) {
                    logger.warn("从数据库加载ECDICT失败，尝试从CSV文件加载: {}", e.getMessage());
                    useDatabase = false;
                }
            }

            // 降级到CSV文件模式
            loadECDICTData();
            ecdictAvailable = true;
            useDatabase = false;
            logger.info("ECDICT词典服务初始化成功（CSV模式），已加载 {} 个词汇", ecdictDictionary.size());
        } catch (Exception e) {
            logger.error("ECDICT词典初始化失败", e);
            ecdictAvailable = false;
        }
    }

    /**
     * 从数据库加载ECDICT数据（新方法，快速启动）
     */
    private void loadFromDatabase() {
        logger.info("开始从数据库加载ECDICT数据...");
        // 数据库模式下，我们不需要预加载所有数据到内存
        // 只在需要时通过SQL查询
        long count = ecdictWordMapper.getTotalCount();
        logger.info("数据库中共有 {} 条ECDICT词汇", count);
    }

    /**
     * 加载ECDICT数据（CSV模式，降级方案）
     */
    private void loadECDICTData() throws IOException {
        // 尝试多个可能的路径
        String[] possiblePaths = {
                "ECDICT-master/ecdict.csv", // 项目根目录
                "../ECDICT-master/ecdict.csv", // 上级目录
                "/usr/local/ecdict/ecdict.csv" // 系统路径
        };

        File ecdictFile = null;
        for (String path : possiblePaths) {
            File testFile = new File(path);
            if (testFile.exists() && testFile.isFile()) {
                ecdictFile = testFile;
                break;
            }
        }

        if (ecdictFile == null) {
            throw new IOException("找不到ECDICT数据文件，请确保ECDICT-master目录在项目根目录或已导入数据库");
        }

        logger.info("开始加载ECDICT数据文件: {}", ecdictFile.getAbsolutePath());
        parseECDICTFile(ecdictFile);
    }

    /**
     * 解析ECDICT CSV文件
     */
    private void parseECDICTFile(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            boolean isFirstLine = true;
            int count = 0;

            while ((line = reader.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // 跳过标题行
                }

                ECDICTWordDTO word = parseECDICTLine(line);
                if (word != null && word.getWord() != null) {
                    ecdictDictionary.put(word.getWord().toLowerCase(), word);
                    count++;
                }

                // 每10000个词输出一次进度
                if (count % 10000 == 0) {
                    logger.info("已加载 {} 个词汇...", count);
                }
            }

            logger.info("ECDICT数据加载完成，共加载 {} 个词汇", count);
        }
    }

    /**
     * 稳健的ECDICT数据行解析方法
     */
    private ECDICTWordDTO parseECDICTLine(String line) {
        if (line == null || line.trim().isEmpty()) {
            return null;
        }

        try {
            // 使用CSV格式解析，处理带引号的字段
            List<String> fields = parseCSVLine(line);
            if (fields.size() < 4) {
                return null;
            }

            ECDICTWordDTO word = new ECDICTWordDTO();
            word.setWord(cleanField(fields.get(0)));
            word.setPhonetic(cleanField(fields.get(1)));
            word.setDefinition(cleanField(fields.get(2)));

            // 修复翻译字段提取逻辑
            String translation = cleanField(fields.get(3));
            word.setTranslation(translation);

            // 安全地设置可选字段
            if (fields.size() > 4)
                word.setPos(cleanField(fields.get(4)));
            if (fields.size() > 5)
                word.setCollins(cleanField(fields.get(5)));
            if (fields.size() > 6)
                word.setOxford(cleanField(fields.get(6)));
            if (fields.size() > 7)
                word.setTag(cleanField(fields.get(7)));
            if (fields.size() > 8)
                word.setBnc(cleanField(fields.get(8)));
            if (fields.size() > 9)
                word.setFrq(cleanField(fields.get(9)));
            if (fields.size() > 10)
                word.setExchange(cleanField(fields.get(10)));

            return word;

        } catch (Exception e) {
            // 记录错误但继续处理
            logger.warn("解析行失败: {}...", line.substring(0, Math.min(50, line.length())));
            return null;
        }
    }

    /**
     * 稳健的CSV行解析方法
     */
    private List<String> parseCSVLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);

            if (c == '"') {
                // 处理引号
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 转义的双引号 ""
                    currentField.append('"');
                    i++; // 跳过下一个字符
                } else {
                    // 开始或结束引号
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // 字段分隔符
                fields.add(currentField.toString());
                currentField.setLength(0); // 清空builder
            } else {
                // 普通字符
                currentField.append(c);
            }
        }

        // 添加最后一个字段
        fields.add(currentField.toString());

        return fields;
    }

    // /**
    // * 解析单行ECDICT数据
    // */
    // private ECDICTWord parseECDICTLine(String line) {
    // try {
    // String[] fields = line.split(",", -1); // -1表示保留所有空字段
    // if (fields.length < 4) return null;
    //
    // ECDICTWord word = new ECDICTWord();
    // word.setWord(cleanField(fields[0]));
    // word.setPhonetic(cleanField(fields[1]));
    // word.setDefinition(cleanField(fields[2]));
    // word.setTranslation(cleanField(fields[3]));
    //
    // if (fields.length > 4) {
    // word.setPos(cleanField(fields[4]));
    // }
    // if (fields.length > 5) {
    // word.setCollins(cleanField(fields[5]));
    // }
    // if (fields.length > 6) {
    // word.setOxford(cleanField(fields[6]));
    // }
    // if (fields.length > 7) {
    // word.setTag(cleanField(fields[7]));
    // }
    // if (fields.length > 8) {
    // word.setBnc(cleanField(fields[8]));
    // }
    // if (fields.length > 9) {
    // word.setFrq(cleanField(fields[9]));
    // }
    // if (fields.length > 10) {
    // word.setExchange(cleanField(fields[10]));
    // }
    //
    // return word;
    // } catch (Exception e) {
    // // 忽略解析错误，继续处理下一行
    // // System.err.println("解析行失败: " + line + ", 错误: " + e.getMessage());
    // return null;
    // }
    // }

    /**
     * 清理字段值（去除引号和特殊字符）
     */
    private String cleanField(String field) {
        if (field == null || field.isEmpty() || field.equals("NULL")) {
            return "";
        }

        // 去除首尾的引号和空格
        field = field.trim();

        // 处理不同类型的引号包围
        if ((field.startsWith("'") && field.endsWith("'")) ||
                (field.startsWith("\"") && field.endsWith("\""))) {
            field = field.substring(1, field.length() - 1);
        }

        // 替换各种类型的引号为标准引号
        field = field.replace("\u201C", "\"") // 中文左双引号
                .replace("\u201D", "\"") // 中文右双引号
                .replace("\u2018", "'") // 中文左单引号
                .replace("\u2019", "'") // 中文右单引号
                .replace("\u00B4", "'"); // 特殊撇号

        // 清理特殊标记（如[法]、[计]等）
        field = field.replaceAll("\\[[a-zA-Z\\+]+\\]", "");

        // 清理多余的空白字符（包括制表符等）
        field = field.replaceAll("\\s+", " ").trim();

        // 清理其他特殊字符，保留中文、英文、数字、基本标点
        field = field.replaceAll("[^\\u4e00-\\u9fa5\\w\\s.,;:!?()\\[\\]{}'\"-]", "");

        return field;
    }

    /**
     * 为MCQ测验生成干扰项定义
     * 策略：优先从相同词性中抽取，不足时从全库补充
     *
     * @param word  目标单词
     * @param count 需要的干扰项数量（通常3）
     * @return 干扰项定义列表（中文翻译）
     */
    public List<String> generateQuizDistractors(String word, int count) {
        List<String> distractors = new ArrayList<>();

        if (word == null || word.trim().isEmpty() || count <= 0) {
            return distractors;
        }

        // 限制最大数量，防滥用
        count = Math.min(count, 10);

        try {
            // 1. 查询目标单词的ECDICT记录，获取id和pos
            Long excludeId = 0L;
            String pos = null;
            String correctTranslation = null;

            if (ecdictWordMapper != null) {
                ECDICTWord targetWord = ecdictWordMapper.findByWord(word.trim());
                if (targetWord != null) {
                    excludeId = targetWord.getId() != null ? targetWord.getId() : 0L;
                    pos = targetWord.getPos();
                    correctTranslation = targetWord.getTranslation();
                }

                // 2. 优先从相同词性中随机抽取
                if (pos != null && !pos.isEmpty()) {
                    List<ECDICTWord> posBased = ecdictWordMapper.findRandomDistractorsByPos(pos, excludeId, count);
                    if (posBased != null) {
                        for (ECDICTWord w : posBased) {
                            String translation = extractShortTranslation(w.getTranslation());
                            if (translation != null && !translation.isEmpty()
                                    && !translation.equals(correctTranslation)
                                    && !distractors.contains(translation)) {
                                distractors.add(translation);
                            }
                        }
                    }
                }

                // 3. 不够时从全库补充
                if (distractors.size() < count) {
                    int remaining = count - distractors.size();
                    // 多取一些以防去重后不够
                    List<ECDICTWord> general = ecdictWordMapper.findRandomDistractors(excludeId, remaining + 5);
                    if (general != null) {
                        for (ECDICTWord w : general) {
                            if (distractors.size() >= count) break;
                            String translation = extractShortTranslation(w.getTranslation());
                            if (translation != null && !translation.isEmpty()
                                    && !translation.equals(correctTranslation)
                                    && !distractors.contains(translation)) {
                                distractors.add(translation);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("生成MCQ干扰项失败: word={}", word, e);
        }

        return distractors;
    }

    /**
     * 为MCQ测验生成完整的选项数据：包含正确答案（来自ECDICT）和干扰项
     * 确保正确答案和干扰项风格一致（都来自同一词库）
     *
     * @param word  目标单词
     * @param count 需要的干扰项数量（通常3）
     * @return Map包含 correctTranslation(String) 和 distractors(List<String>)
     */
    public Map<String, Object> generateQuizData(String word, int count) {
        Map<String, Object> result = new HashMap<>();
        result.put("correctTranslation", null);
        result.put("distractors", new ArrayList<>());

        if (word == null || word.trim().isEmpty() || count <= 0) {
            return result;
        }

        count = Math.min(count, 10);

        try {
            if (ecdictWordMapper != null) {
                ECDICTWord targetWord = ecdictWordMapper.findByWord(word.trim());
                if (targetWord != null && targetWord.getTranslation() != null) {
                    String correctTranslation = extractShortTranslation(targetWord.getTranslation());
                    result.put("correctTranslation", correctTranslation);
                }
            }
            // 复用已有的干扰项生成逻辑
            List<String> distractors = generateQuizDistractors(word, count);
            // 确保干扰项不包含正确答案
            String correct = (String) result.get("correctTranslation");
            if (correct != null) {
                distractors.removeIf(d -> d.equals(correct));
                // 如果去重后不够，再补充
                if (distractors.size() < count) {
                    List<String> extra = generateQuizDistractors(word, count + 3);
                    for (String e : extra) {
                        if (distractors.size() >= count) break;
                        if (!e.equals(correct) && !distractors.contains(e)) {
                            distractors.add(e);
                        }
                    }
                }
            }
            result.put("distractors", distractors);
        } catch (Exception e) {
            logger.error("生成MCQ完整选项数据失败: word={}", word, e);
        }

        return result;
    }

    /**
     * 从翻译字段提取简短的中文翻译，用于MCQ选项
     * 只取第一个分隔符前的内容，确保选项简洁
     */
    private String extractShortTranslation(String translation) {
        if (translation == null || translation.isEmpty()) {
            return null;
        }
        // 按常见分隔符切分，取第一段
        String[] parts = translation.split("[,;；\\n]");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty() && trimmed.length() >= 2) {
                // 清理标记如[法]、[计]等
                trimmed = trimmed.replaceAll("\\[.*?\\]", "").trim();
                if (!trimmed.isEmpty()) {
                    return trimmed;
                }
            }
        }
        return translation.trim();
    }

    /**
     * 查询单词信息
     */
    public Map<String, Object> getWordInfo(String word) {
        Map<String, Object> result = new HashMap<>();

        try {
            ECDICTWordDTO ecdictWord = null;

            // 数据库模式：从数据库查询
            if (ecdictWordMapper != null) {
                try {
                    ECDICTWord entity = ecdictWordMapper.findByWord(word);
                    if (entity != null) {
                        ecdictWord = convertEntityToDTO(entity);
                    }
                } catch (Exception e) {
            logger.warn("数据库查询失败，降级到内存查询: {}", e.getMessage());
                }
            }

            // CSV模式：从内存查询
            if (ecdictWord == null) {
                ecdictWord = ecdictDictionary.get(word.toLowerCase());
            }

            if (ecdictWord != null) {
                result = convertECDICTToMap(ecdictWord);
                result.put("found", true);
                result.put("source", "ecdict");
            } else {
                // 如果完全匹配失败，尝试模糊匹配
                ECDICTWordDTO fuzzyWord = findFuzzyMatch(word.toLowerCase());
                if (fuzzyWord != null) {
                    result = convertECDICTToMap(fuzzyWord);
                    result.put("found", true);
                    result.put("fuzzy", true);
                    result.put("source", "ecdict");
                    result.put("suggestion", "您可能要查找: " + fuzzyWord.getWord());
                } else {
                    result.put("word", word.toLowerCase());
                    result.put("found", false);
                    result.put("source", "none");
                }
            }

            result.put("word", word);

        } catch (Exception e) {
            logger.error("ECDICT查询单词失败: {}", word, e);
            result.put("word", word);
            result.put("found", false);
            result.put("error", "词典查询失败");
        }

        return result;
    }

    /**
     * 将实体类转换为DTO
     */
    private ECDICTWordDTO convertEntityToDTO(ECDICTWord entity) {
        ECDICTWordDTO dto = new ECDICTWordDTO();
        dto.setWord(entity.getWord());
        dto.setPhonetic(entity.getPhonetic());
        dto.setDefinition(entity.getDefinition());
        dto.setTranslation(entity.getTranslation());
        dto.setPos(entity.getPos());
        dto.setCollins(entity.getCollins());
        dto.setOxford(entity.getOxford());
        dto.setTag(entity.getTag());
        dto.setBnc(entity.getBnc());
        dto.setFrq(entity.getFrq());
        dto.setExchange(entity.getExchange());
        return dto;
    }

    /**
     * 模糊匹配查找相似单词
     */
    private ECDICTWordDTO findFuzzyMatch(String word) {
        // 简单的模糊匹配：移除常见的前后缀
        String[] prefixes = { "", "'", "un-", "re-", "in-", "dis-", "mis-", "over-", "under-", "out-" };
        String[] suffixes = { "", "'", "-s", "-ed", "-ing", "-er", "-est", "-ly", "-tion", "-ment", "-ness", "-able",
                "-ible" };

        for (String prefix : prefixes) {
            if (word.startsWith(prefix)) {
                String modifiedWord = word.substring(prefix.length());
                ECDICTWordDTO found = ecdictDictionary.get(modifiedWord);
                if (found != null)
                    return found;
            }
        }

        for (String suffix : suffixes) {
            if (word.endsWith(suffix) && word.length() > suffix.length() + 2) {
                String modifiedWord = word.substring(0, word.length() - suffix.length());
                ECDICTWordDTO found = ecdictDictionary.get(modifiedWord);
                if (found != null)
                    return found;
            }
        }

        return null;
    }

    /**
     * 将ECDICT单词对象转换为Map（匹配Word实体类格式）
     */
    private Map<String, Object> convertECDICTToMap(ECDICTWordDTO word) {
        Map<String, Object> result = new HashMap<>();

        // 基本字段，与Word实体类对应
        result.put("word", word.getWord());
        result.put("pronunciation", word.getPhonetic());
        result.put("definition", extractPrimaryDefinition(word));
        result.put("exampleSentence", extractExampleSentence(word));
        result.put("audioUrl", ""); // ECDICT不包含音频URL，留空
        result.put("partOfSpeech", convertPosFormat(word.getPos()));
        result.put("difficulty", inferDifficulty(word));

        // JSON格式字段，与数据库字段格式匹配
        result.put("synonyms", formatSynonymsAsJson(parseSynonyms(word.getExchange())));
        result.put("antonyms", "[]"); // ECDICT通常不包含反义词
        result.put("wordnetId", "ecdict_" + word.getWord().hashCode());
        result.put("multipleDefinitions", formatMultipleDefinitions(word));
        result.put("source", "ecdict");

        // ECDICT特有字段，额外提供
        result.put("translation", word.getTranslation());
        result.put("collins", word.getCollins());
        result.put("oxford", word.getOxford());
        result.put("tag", word.getTag());
        result.put("bnc", word.getBnc());
        result.put("frq", word.getFrq());
        result.put("exchange", word.getExchange());

        // 兼容性字段 - 为前端提供便利
        result.put("pos", result.get("partOfSpeech"));

        // 解析JSON字段为对象，便于前端使用
        result.put("synonymsList", parseSynonyms(word.getExchange()));
        result.put("antonymsList", new ArrayList<>());
        result.put("definitionsList", createDefinitionList(word));

        return result;
    }

    /**
     * 从exchange字段解析同义词（标准化格式）
     */
    private List<String> parseSynonyms(String exchange) {
        List<String> synonyms = new ArrayList<>();
        if (exchange == null || exchange.isEmpty()) {
            return synonyms;
        }

        // 按多种分隔符分割
        String[] parts = exchange.split("[/\\s,;；]");
        Set<String> uniqueSynonyms = new LinkedHashSet<>(); // 使用LinkedHashSet保持顺序并去重

        for (String part : parts) {
            part = part.trim();

            // 过滤无效同义词
            if (isValidSynonym(part)) {
                uniqueSynonyms.add(part);
            }
        }

        // 转换为List并限制数量（最多10个）
        synonyms.addAll(uniqueSynonyms);
        if (synonyms.size() > 10) {
            synonyms = synonyms.subList(0, 10);
        }

        return synonyms;
    }

    /**
     * 验证同义词的有效性
     */
    private boolean isValidSynonym(String synonym) {
        if (synonym == null || synonym.isEmpty()) {
            return false;
        }

        // 过滤掉不符合条件的词汇
        String lower = synonym.toLowerCase();

        // 跳过单个字符
        if (synonym.length() < 2) {
            return false;
        }

        // 跳过纯数字
        if (synonym.matches("\\d+")) {
            return false;
        }

        // 跳过常见无效词汇
        String[] invalidWords = { "the", "a", "an", "and", "or", "but", "of", "in", "on", "at", "to", "for", "of",
                "with", "by" };
        for (String invalid : invalidWords) {
            if (lower.equals(invalid)) {
                return false;
            }
        }

        return true;
    }

    /**
     * 创建定义列表（简化版本，避免重复逻辑）
     */
    private List<Map<String, Object>> createDefinitionList(ECDICTWordDTO word) {
        try {
            String multipleDefsJson = formatMultipleDefinitions(word);
            return parseMultipleDefinitions(multipleDefsJson);
        } catch (Exception e) {
            logger.error("创建定义列表失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 搜索词汇（支持模糊匹配）
     */
    public List<Map<String, Object>> searchWords(String keyword, int limit) {
        List<Map<String, Object>> results = new ArrayList<>();

        if (keyword == null || keyword.trim().isEmpty()) {
            return results;
        }

        String searchKey = keyword.toLowerCase().trim();

        // 精确匹配
        ECDICTWordDTO exactMatch = ecdictDictionary.get(searchKey);
        if (exactMatch != null) {
            results.add(convertECDICTToMap(exactMatch));
        }

        // 模糊匹配
        for (Map.Entry<String, ECDICTWordDTO> entry : ecdictDictionary.entrySet()) {
            if (results.size() >= limit)
                break;

            String word = entry.getKey();
            ECDICTWordDTO wordObj = entry.getValue();

            // 检查是否包含关键词
            if (word.contains(searchKey) ||
                    (wordObj.getTranslation() != null && wordObj.getTranslation().contains(keyword)) ||
                    (wordObj.getDefinition() != null && wordObj.getDefinition().contains(keyword))) {

                // 避免重复
                boolean exists = results.stream().anyMatch(r -> wordObj.getWord() != null && wordObj.getWord().equals(r.get("word")));
                if (!exists) {
                    Map<String, Object> result = convertECDICTToMap(wordObj);
                    result.put("matchType", "partial");
                    results.add(result);
                }
            }
        }

        return results;
    }

    /**
     * 清理定义文本（去除特殊格式）
     */
    private String cleanDefinitionText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 替换各种引号和特殊字符
        text = text.replace("\u201C", "\"") // 中文左双引号
                .replace("\u201D", "\"") // 中文右双引号
                .replace("\u2018", "'") // 中文左单引号
                .replace("\u2019", "'") // 中文右单引号
                .replace("\u00B4", "'"); // 特殊撇号

        // 清理特殊标记和多余空格
        text = text.replaceAll("\\[.*?\\]", ""); // 清理[法]、[计]等标记
        text = text.replaceAll("\\s+", " "); // 合并多个空格
        text = text.replaceAll("\\n+", "; "); // 换行符转为分号分隔

        return text.trim();
    }

    /**
     * 按词性分组定义
     */
    private Map<String, List<String>> groupDefinitionsByPos(String definition, String translation, String primaryPos) {
        Map<String, List<String>> result = new HashMap<>();

        // 处理包含词性前缀的定义（如 "n. xxx v. yyy"）
        if (definition != null && definition.matches(".*[nvar]\\..*")) {
            String[] parts = definition.split("(?=[nvar]\\.\\s)");
            for (String part : parts) {
                part = part.trim();
                if (part.matches("[nvar]\\.\\s.*")) {
                    String pos = part.substring(0, part.indexOf('.'));
                    String def = part.substring(part.indexOf('.') + 1).trim();

                    result.computeIfAbsent(pos, k -> new ArrayList<>()).add(def);
                } else if (!part.isEmpty()) {
                    result.computeIfAbsent(primaryPos != null ? primaryPos : "unknown",
                            k -> new ArrayList<>()).add(part);
                }
            }
        } else if (definition != null) {
            // 单一定义
            result.computeIfAbsent(primaryPos != null ? primaryPos : "unknown",
                    k -> new ArrayList<>()).add(definition);
        }

        return result;
    }

    /**
     * 提取相关的翻译
     */
    private String extractRelevantTranslation(String translation, String pos) {
        if (translation == null || translation.isEmpty()) {
            return "";
        }

        // 简单的翻译提取逻辑，优先返回前面的翻译
        String[] parts = translation.split("[,;；]");
        if (parts.length > 0) {
            return parts[0].trim();
        }

        return translation;
    }

    /**
     * 提取主要定义（标准化格式：简洁中文定义）
     */
    private String extractPrimaryDefinition(ECDICTWordDTO word) {
        String translation = word.getTranslation();
        String definition = word.getDefinition();
        String primaryDef = "";

        // 第一步：从翻译字段提取中文定义
        if (translation != null && !translation.isEmpty()) {
            String cleanTranslation = cleanDefinitionText(translation);

            if (isMainlyChinese(cleanTranslation)) {
                // 纯中文翻译：提取最简洁的定义
                primaryDef = extractConciseDefinition(cleanTranslation);
            } else {
                // 英文翻译：尝试提取中文部分
                primaryDef = extractChineseFromMixedText(cleanTranslation, definition);
            }
        }

        // 第二步：如果翻译字段没有有效中文，从定义字段提取
        if (primaryDef.isEmpty() && definition != null && !definition.isEmpty()) {
            String cleanDefinition = cleanDefinitionText(definition);
            primaryDef = extractChineseFromMixedText("", cleanDefinition);
        }

        // 第三步：格式标准化处理
        primaryDef = normalizeDefinitionFormat(primaryDef);

        // 第四步：如果仍然为空，提供默认值
        return primaryDef.isEmpty() ? "暂无中文定义" : primaryDef;
    }

    /**
     * 提取简洁定义（从中文翻译中提取最核心的定义）
     */
    private String extractConciseDefinition(String chineseText) {
        if (chineseText == null || chineseText.isEmpty()) {
            return "";
        }

        // 按分隔符分割
        String[] parts = chineseText.split("[,;；\n]");
        StringBuilder conciseDef = new StringBuilder();

        // 优先选择第一个最简洁的定义
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                // 跳过过于简单的标记
                if (part.length() > 1 && !part.matches("[a-zA-Z]+")) {
                    if (conciseDef.length() > 0) {
                        conciseDef.append("；");
                    }
                    conciseDef.append(part);

                    // 只取前1-2个最相关的定义
                    if (conciseDef.length() > 50) {
                        break;
                    }
                }
            }
        }

        return conciseDef.toString();
    }

    /**
     * 标准化定义格式
     */
    private String normalizeDefinitionFormat(String definition) {
        if (definition == null || definition.isEmpty()) {
            return "";
        }

        // 移除多余的分隔符
        definition = definition.replaceAll("；+", "；");
        definition = definition.replaceAll("^；+", "");
        definition = definition.replaceAll("；+$", "");

        // 移除英文标记
        definition = definition.replaceAll("\\b(\\.\\.\\.|\\. |\\. )", "；");

        // 确保中文标点统一
        definition = definition.replace(",", "；")
                .replace(";", "；")
                .replace(",,", "；")
                .trim();

        return definition;
    }

    /**
     * 检查文本是否主要为中文内容
     */
    private boolean isMainlyChinese(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        int chineseCount = 0;
        int englishCount = 0;

        for (char c : text.toCharArray()) {
            if (Character.toString(c).matches("[\\u4e00-\\u9fa5]")) {
                chineseCount++;
            } else if (Character.toString(c).matches("[a-zA-Z]")) {
                englishCount++;
            }
        }

        return chineseCount > englishCount;
    }

    /**
     * 从混合文本中提取中文内容
     */
    private String extractChineseFromMixedText(String translation, String definition) {
        // 先尝试从翻译中提取中文
        if (translation != null && !translation.isEmpty()) {
            String chineseText = extractChineseContent(translation);
            if (!chineseText.isEmpty()) {
                return chineseText;
            }
        }

        // 从英文定义中提取中文部分
        if (definition != null && !definition.isEmpty()) {
            String chineseText = extractChineseContent(definition);
            if (!chineseText.isEmpty()) {
                return chineseText;
            }
        }

        // 如果都没有中文，返回清理后的英文定义
        return (definition != null && !definition.isEmpty()) ? cleanDefinitionText(definition).split("\\n")[0] : "暂无定义";
    }

    /**
     * 提取文本中的中文内容
     */
    private String extractChineseContent(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 按分隔符分割
        String[] parts = text.split("[,;；\\n]");
        StringBuilder chineseContent = new StringBuilder();

        for (String part : parts) {
            String cleanPart = part.trim();
            if (isMainlyChinese(cleanPart) || containsChinese(cleanPart)) {
                if (chineseContent.length() > 0) {
                    chineseContent.append("；");
                }
                chineseContent.append(cleanPart);
            }
        }

        return chineseContent.toString().replaceAll("^；+", "");
    }

    /**
     * 检查文本是否包含中文字符
     */
    private boolean containsChinese(String text) {
        return text != null && text.matches(".*[\\u4e00-\\u9fa5].*");
    }

    /**
     * 提取例句
     */
    private String extractExampleSentence(ECDICTWordDTO word) {
        String definition = word.getDefinition();
        String translation = word.getTranslation();

        if (definition != null) {
            // 从英文定义中寻找例句
            String[] lines = definition.split("\\n");
            for (String line : lines) {
                if (line.toLowerCase().contains("example") ||
                        line.toLowerCase().contains("e.g.") ||
                        line.contains("例句")) {
                    return cleanDefinitionText(line);
                }
            }
        }

        // 从中文翻译中寻找例句
        if (translation != null) {
            String[] lines = translation.split("[;；\\n]");
            for (String line : lines) {
                if (line.contains("例如") || line.contains("比如") || line.contains("例")) {
                    return cleanDefinitionText(line);
                }
            }
        }

        return "";
    }

    /**
     * 转换词性格式（ECDICT格式 -> 标准格式）
     */
    private String convertPosFormat(String pos) {
        if (pos == null || pos.isEmpty()) {
            return "unknown";
        }

        // ECDICT可能包含多个词性，取第一个
        String[] posParts = pos.split("[,;\\s]");
        String primaryPos = posParts[0].toLowerCase();

        // 完整的词性映射表
        switch (primaryPos) {
            case "n":
                return "noun";
            case "v":
                return "verb";
            case "adj":
                return "adjective";
            case "adv":
                return "adverb";
            case "prep":
                return "preposition";
            case "conj":
                return "conjunction";
            case "pron":
                return "pronoun";
            case "int":
                return "interjection";
            case "art":
                return "article";
            case "num":
                return "numeral";
            case "aux":
                return "auxiliary_verb";
            case "vi":
                return "verb"; // 不及物动词归类为动词
            case "vt":
                return "verb"; // 及物动词归类为动词
            case "n.pl":
                return "noun"; // 名词复数归类为名词
            case "inf":
                return "unknown"; // 不定式归类为未知
            default:
                // 处理更复杂的词性标识
                if (primaryPos.contains("n")) {
                    return "noun";
                } else if (primaryPos.contains("v")) {
                    return "verb";
                } else if (primaryPos.contains("adj")) {
                    return "adjective";
                } else if (primaryPos.contains("adv")) {
                    return "adverb";
                }
                return primaryPos.length() > 2 ? primaryPos : "unknown";
        }
    }

    /**
     * 安全解析整数字符串
     */
    private int safeParseInt(String str, int defaultValue) {
        if (str == null || str.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 推断难度等级
     */
    private String inferDifficulty(ECDICTWordDTO word) {
        String collins = word.getCollins();
        String oxford = word.getOxford();
        String tag = word.getTag();

        // 根据柯林斯和牛津等级推断难度（安全解析）
        if (collins != null && !collins.isEmpty() && !collins.equals("0")) {
            int level = safeParseInt(collins, -1);
            if (level > 0) {
                if (level <= 2)
                    return "EASY";
                if (level <= 4)
                    return "MEDIUM";
                return "HARD";
            }
        }

        if (oxford != null && !oxford.isEmpty() && !oxford.equals("0")) {
            int level = safeParseInt(oxford, -1);
            if (level > 0) {
                if (level <= 3000)
                    return "EASY";
                if (level <= 5000)
                    return "MEDIUM";
                return "HARD";
            }
        }

        // 根据标签推断
        if (tag != null) {
            if (tag.contains("cet4"))
                return "EASY";
            if (tag.contains("cet6") || tag.contains("ielts"))
                return "MEDIUM";
            if (tag.contains("toefl") || tag.contains("gre"))
                return "HARD";
        }

        return "MEDIUM"; // 默认中等难度
    }

    /**
     * 将同义词列表格式化为JSON字符串
     */
    private String formatSynonymsAsJson(List<String> synonyms) {
        if (synonyms == null || synonyms.isEmpty()) {
            return "[]";
        }

        try {
            return objectMapper.writeValueAsString(synonyms);
        } catch (Exception e) {
            logger.error("序列化同义词失败", e);
            return "[]";
        }
    }

    /**
     * 格式化多词义定义为JSON
     */
    private String formatMultipleDefinitions(ECDICTWordDTO word) {
        List<Map<String, Object>> definitions = new ArrayList<>();

        String definition = word.getDefinition();
        String translation = word.getTranslation();
        String pos = word.getPos();

        if (definition != null && !definition.isEmpty()) {
            String cleanDefinition = cleanDefinitionText(definition);
            String cleanTranslation = cleanDefinitionText(translation != null ? translation : "");

            // 按词性分组
            Map<String, List<String>> definitionsByPos = groupDefinitionsByPos(cleanDefinition, cleanTranslation, pos);

            for (Map.Entry<String, List<String>> entry : definitionsByPos.entrySet()) {
                String currentPos = entry.getKey();
                List<String> defs = entry.getValue();

                for (String def : defs) {
                    Map<String, Object> defMap = new HashMap<>();
                    defMap.put("pos", currentPos);
                    defMap.put("definition", def);
                    defMap.put("translation", extractRelevantTranslation(cleanTranslation, currentPos));
                    defMap.put("examples", new ArrayList<>()); // ECDICT通常不包含例句

                    definitions.add(defMap);
                }
            }
        }

        try {
            return objectMapper.writeValueAsString(definitions);
        } catch (Exception e) {
            logger.error("序列化多词义定义失败", e);
            return "[]";
        }
    }

    /**
     * 解析多词义定义JSON为对象列表
     */
    private List<Map<String, Object>> parseMultipleDefinitions(String multipleDefsJson) {
        try {
            if (multipleDefsJson != null && !multipleDefsJson.isEmpty() && !multipleDefsJson.equals("[]")) {
                return objectMapper.readValue(
                        multipleDefsJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            }
        } catch (Exception e) {
            // 忽略解析错误，返回空列表
        }
        return new ArrayList<>();
    }

    /**
     * 检查ECDICT是否可用
     */
    public boolean isECDICTAvailable() {
        return ecdictAvailable;
    }

    /**
     * 获取词典统计信息
     */
    public Map<String, Object> getDictionaryStats() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalWords", ecdictDictionary.size());
        stats.put("available", ecdictAvailable);
        stats.put("lastUpdated", new Date());

        // 统计不同级别的词汇数量
        Map<String, Integer> levelStats = new HashMap<>();
        levelStats.put("cet4", 0);
        levelStats.put("cet6", 0);
        levelStats.put("ielts", 0);
        levelStats.put("toefl", 0);
        levelStats.put("gre", 0);

        for (ECDICTWordDTO word : ecdictDictionary.values()) {
            String tag = word.getTag();
            if (tag != null) {
                if (tag.contains("cet4"))
                    levelStats.put("cet4", levelStats.get("cet4") + 1);
                if (tag.contains("cet6"))
                    levelStats.put("cet6", levelStats.get("cet6") + 1);
                if (tag.contains("ielts"))
                    levelStats.put("ielts", levelStats.get("ielts") + 1);
                if (tag.contains("toefl"))
                    levelStats.put("toefl", levelStats.get("toefl") + 1);
                if (tag.contains("gre"))
                    levelStats.put("gre", levelStats.get("gre") + 1);
            }
        }

        stats.put("levelStats", levelStats);
        return stats;
    }

    /**
     * ECDICT单词数据传输对象（DTO）
     * 用于CSV模式下的内存存储
     */
    public static class ECDICTWordDTO {
        private String word;
        private String phonetic;
        private String definition;
        private String translation;
        private String pos;
        private String collins;
        private String oxford;
        private String tag;
        private String bnc;
        private String frq;
        private String exchange;

        // Getters and Setters
        public String getWord() {
            return word;
        }

        public void setWord(String word) {
            this.word = word;
        }

        public String getPhonetic() {
            return phonetic;
        }

        public void setPhonetic(String phonetic) {
            this.phonetic = phonetic;
        }

        public String getDefinition() {
            return definition;
        }

        public void setDefinition(String definition) {
            this.definition = definition;
        }

        public String getTranslation() {
            return translation;
        }

        public void setTranslation(String translation) {
            this.translation = translation;
        }

        public String getPos() {
            return pos;
        }

        public void setPos(String pos) {
            this.pos = pos;
        }

        public String getCollins() {
            return collins;
        }

        public void setCollins(String collins) {
            this.collins = collins;
        }

        public String getOxford() {
            return oxford;
        }

        public void setOxford(String oxford) {
            this.oxford = oxford;
        }

        public String getTag() {
            return tag;
        }

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getBnc() {
            return bnc;
        }

        public void setBnc(String bnc) {
            this.bnc = bnc;
        }

        public String getFrq() {
            return frq;
        }

        public void setFrq(String frq) {
            this.frq = frq;
        }

        public String getExchange() {
            return exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }
    }
}
