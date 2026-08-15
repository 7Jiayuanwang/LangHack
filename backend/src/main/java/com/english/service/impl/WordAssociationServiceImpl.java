package com.english.service.impl;

import com.english.entity.ECDICTWord;
import com.english.entity.Word;
import com.english.entity.WordAssociation;
import com.english.mapper.ECDICTWordMapper;
import com.english.mapper.WordAssociationMapper;
import com.english.service.WordAssociationService;
import com.english.service.WordEtymologyService;
import com.english.service.WordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 单词联想服务实现
 * 数据来源优先级：
 * 1. word_associations 表（预置/缓存数据）
 * 2. ECDICT exchange 字段（衍生词 + 部分同义词）
 * 3. words 表 synonyms 字段（数据库纠错数据）
 */
@Service
public class WordAssociationServiceImpl implements WordAssociationService {

    private static final Logger logger = LoggerFactory.getLogger(WordAssociationServiceImpl.class);

    @Autowired
    private WordAssociationMapper wordAssociationMapper;

    @Autowired(required = false)
    private ECDICTWordMapper ecdictWordMapper;

    @Autowired
    private WordService wordService;

    @Autowired
    private WordEtymologyService wordEtymologyService;

    // 衍生词类型标签映射
    private static final Map<String, String> DERIVATIVE_LABELS;

    static {
        DERIVATIVE_LABELS = new LinkedHashMap<>();
        DERIVATIVE_LABELS.put("p", "过去式");
        DERIVATIVE_LABELS.put("d", "过去分词");
        DERIVATIVE_LABELS.put("i", "现在分词");
        DERIVATIVE_LABELS.put("s", "第三人称单数");
        DERIVATIVE_LABELS.put("3", "第三人称单数");
        DERIVATIVE_LABELS.put("r", "比较级");
        DERIVATIVE_LABELS.put("t", "最高级");
        DERIVATIVE_LABELS.put("1", "复数");
        DERIVATIVE_LABELS.put("0", "原形");
    }

    @PostConstruct
    public void init() {
        logger.info("单词联想服务初始化完成");
    }

    @Override
    public Map<String, Object> getAssociations(String word) {
        Map<String, Object> result = new HashMap<>();

        try {
            if (word == null || word.trim().isEmpty()) {
                result.put("found", false);
                result.put("error", "单词不能为空");
                return result;
            }

            word = word.trim().toLowerCase();

            // 从数据库查询本单词信息
            Word dbWord = wordService.getWordByWord(word);

            result.put("word", word);
            result.put("found", true);

            // 查询三种联想类型
            result.put("synonyms", getSynonyms(word));
            result.put("derivatives", getDerivatives(word));
            result.put("collocations", getCollocations(word));

            // 补充单词基本信息
            if (dbWord != null) {
                result.put("definition", dbWord.getDefinition());
                result.put("partOfSpeech", dbWord.getPartOfSpeech());
                result.put("pronunciation", dbWord.getPronunciation());
                // 从ECDICT补充翻译、音标、标签等
                ECDICTWord ecdict = findEcdictWord(word);
                if (ecdict != null) {
                    if (ecdict.getTranslation() != null) result.put("translation", ecdict.getTranslation());
                    if (ecdict.getPhonetic() != null) result.put("phonetic", ecdict.getPhonetic());
                    if (ecdict.getTag() != null) result.put("tag", ecdict.getTag());
                    if (ecdict.getCollins() != null) result.put("collins", ecdict.getCollins());
                    if (ecdict.getOxford() != null) result.put("oxford", ecdict.getOxford());
                    if (ecdict.getDefinition() != null && (dbWord.getDefinition() == null || dbWord.getDefinition().isEmpty())) {
                        result.put("definition", ecdict.getDefinition());
                    }
                    result.put("wordForms", parseWordForms(ecdict.getExchange()));
                } else {
                    result.put("wordForms", new HashMap<>());
                }
            } else {
                // 尝试从ECDICT获取
                ECDICTWord ecdict = findEcdictWord(word);
                if (ecdict != null) {
                    result.put("definition", ecdict.getDefinition());
                    result.put("translation", ecdict.getTranslation());
                    result.put("partOfSpeech", ecdict.getPos());
                    result.put("pronunciation", ecdict.getPhonetic());
                    result.put("phonetic", ecdict.getPhonetic());
                    result.put("tag", ecdict.getTag());
                    result.put("collins", ecdict.getCollins());
                    result.put("oxford", ecdict.getOxford());
                    // 解析词形变化
                    result.put("wordForms", parseWordForms(ecdict.getExchange()));
                } else {
                    result.put("definition", null);
                    result.put("partOfSpeech", null);
                    result.put("pronunciation", null);
                    result.put("wordForms", new HashMap<>());
                }
            }

            // 基于ECDICT大数据的深度联想
            if (ecdictWordMapper != null) {
                // 词源分析（前缀、后缀、词根）
                Map<String, Object> etymology = wordEtymologyService.analyze(word);
                result.put("etymology", etymology);

                // 同前缀词联想
                List<Map<String, String>> prefixWords = new ArrayList<>();
                List<Map<String, String>> prefixes = (List<Map<String, String>>) etymology.get("prefixes");
                if (prefixes != null && !prefixes.isEmpty()) {
                    String prefix = prefixes.get(0).get("prefix");
                    List<ECDICTWord> prefixResult = ecdictWordMapper.findByPrefix(prefix, 12);
                    for (ECDICTWord w : prefixResult) {
                        if (!w.getWord().equalsIgnoreCase(word)) {
                            Map<String, String> item = new LinkedHashMap<>();
                            item.put("word", w.getWord());
                            item.put("translation", w.getTranslation());
                            item.put("pos", w.getPos());
                            prefixWords.add(item);
                        }
                    }
                }
                result.put("prefixWords", prefixWords);

                // 同后缀词联想
                List<Map<String, String>> suffixWords = new ArrayList<>();
                List<Map<String, String>> suffixes = (List<Map<String, String>>) etymology.get("suffixes");
                if (suffixes != null && !suffixes.isEmpty()) {
                    String suffix = suffixes.get(0).get("suffix");
                    List<ECDICTWord> suffixResult = ecdictWordMapper.findBySuffix(suffix, 12);
                    for (ECDICTWord w : suffixResult) {
                        if (!w.getWord().equalsIgnoreCase(word)) {
                            Map<String, String> item = new LinkedHashMap<>();
                            item.put("word", w.getWord());
                            item.put("translation", w.getTranslation());
                            item.put("pos", w.getPos());
                            suffixWords.add(item);
                        }
                    }
                }
                result.put("suffixWords", suffixWords);

                // 同根词联想
                List<Map<String, String>> rootWords = new ArrayList<>();
                List<Map<String, String>> roots = (List<Map<String, String>>) etymology.get("roots");
                if (roots != null && !roots.isEmpty()) {
                    for (Map<String, String> rootMap : roots) {
                        String root = rootMap.get("root");
                        List<ECDICTWord> rootResult = ecdictWordMapper.findByRoot(root, word, 10);
                        for (ECDICTWord w : rootResult) {
                            boolean exists = rootWords.stream().anyMatch(item -> item.get("word").equalsIgnoreCase(w.getWord()));
                            if (!exists && rootWords.size() < 12) {
                                Map<String, String> item = new LinkedHashMap<>();
                                item.put("word", w.getWord());
                                item.put("translation", w.getTranslation());
                                item.put("pos", w.getPos());
                                item.put("root", root);
                                rootWords.add(item);
                            }
                        }
                    }
                }
                result.put("rootWords", rootWords);
            }

        } catch (Exception e) {
            logger.error("获取单词联想失败: word={}", word, e);
            result.put("found", false);
            result.put("error", "查询失败，请稍后重试");
        }

        return result;
    }

    @Override
    public List<Map<String, Object>> getSynonyms(String word) {
        List<Map<String, Object>> synonyms = new ArrayList<>();

        if (word == null || word.trim().isEmpty()) {
            return synonyms;
        }

        word = word.trim().toLowerCase();

        // 1. 先从 word_associations 表查询缓存的同义词
        List<WordAssociation> cachedAssociations = wordAssociationMapper.findByWordAndType(word, "synonym");
        Set<String> seen = new LinkedHashSet<>();

        for (WordAssociation wa : cachedAssociations) {
            String aw = wa.getAssociatedWord().trim().toLowerCase();
            if (!seen.contains(aw)) {
                seen.add(aw);
                Map<String, Object> item = new HashMap<>();
                item.put("word", wa.getAssociatedWord());
                item.put("definition", wa.getDefinition());
                item.put("partOfSpeech", wa.getPartOfSpeech());
                item.put("source", wa.getSource());
                synonyms.add(item);
            }
        }

        // 2. 从 ECDICT exchange 字段解析同义词
        ECDICTWord ecdict = findEcdictWord(word);
        if (ecdict != null) {
            List<String> ecdictSynonyms = parseSynonymsFromExchange(ecdict.getExchange());
            for (String s : ecdictSynonyms) {
                String sw = s.trim().toLowerCase();
                if (!seen.contains(sw) && !sw.equals(word) && sw.length() >= 2) {
                    seen.add(sw);
                    Map<String, Object> item = new HashMap<>();
                    item.put("word", s);
                    item.put("definition", lookupEcdictDefinition(s));
                    item.put("partOfSpeech", null);
                    item.put("source", "ecdict");
                    synonyms.add(item);
                }
            }
        }

        // 3. 从 words 表的 synonyms JSON 字段解析
        Word dbWord = wordService.getWordByWord(word);
        if (dbWord != null && dbWord.getSynonyms() != null && !dbWord.getSynonyms().isEmpty()) {
            try {
                List<String> dbSynonyms = parseJsonArray(dbWord.getSynonyms());
                for (String s : dbSynonyms) {
                    String sw = s.trim().toLowerCase();
                    if (!seen.contains(sw) && !sw.equals(word) && sw.length() >= 2) {
                        seen.add(sw);
                        Map<String, Object> item = new HashMap<>();
                        item.put("word", s);
                        item.put("definition", null);
                        item.put("partOfSpeech", null);
                        item.put("source", "database");
                        synonyms.add(item);
                    }
                }
            } catch (Exception e) {
                logger.warn("解析同义词JSON失败: word={}", word, e);
            }
        }

        return synonyms;
    }

    @Override
    public List<Map<String, Object>> getDerivatives(String word) {
        List<Map<String, Object>> derivatives = new ArrayList<>();

        if (word == null || word.trim().isEmpty()) {
            return derivatives;
        }

        word = word.trim().toLowerCase();

        // 1. 从 word_associations 表查询缓存的衍生词
        List<WordAssociation> cached = wordAssociationMapper.findByWordAndType(word, "derivative");
        Set<String> seen = new LinkedHashSet<>();

        for (WordAssociation wa : cached) {
            String aw = wa.getAssociatedWord().trim().toLowerCase();
            if (!seen.contains(aw)) {
                seen.add(aw);
                Map<String, Object> item = new HashMap<>();
                item.put("word", wa.getAssociatedWord());
                item.put("type", wa.getPartOfSpeech()); // 这里的 partOfSpeech 存储衍生类型标签
                item.put("definition", wa.getDefinition());
                item.put("source", wa.getSource());
                derivatives.add(item);
            }
        }

        // 2. 从 ECDICT exchange 字段解析衍生词
        ECDICTWord ecdict = findEcdictWord(word);
        if (ecdict != null && ecdict.getExchange() != null && !ecdict.getExchange().isEmpty()) {
            Map<String, List<String>> parsedExchange = parseExchange(ecdict.getExchange());

            for (Map.Entry<String, List<String>> entry : parsedExchange.entrySet()) {
                String typeKey = entry.getKey();
                String label = DERIVATIVE_LABELS.getOrDefault(typeKey, typeKey);

                for (String derivedWord : entry.getValue()) {
                    String dw = derivedWord.trim().toLowerCase();
                    if (!seen.contains(dw) && !dw.equals(word) && dw.length() >= 2) {
                        seen.add(dw);
                        Map<String, Object> item = new HashMap<>();
                        item.put("word", derivedWord);
                        item.put("type", label);
                        item.put("definition", lookupEcdictDefinition(derivedWord));
                        item.put("source", "ecdict");
                        derivatives.add(item);
                    }
                }
            }
        }

        return derivatives;
    }

    @Override
    public List<Map<String, Object>> getCollocations(String word) {
        List<Map<String, Object>> collocations = new ArrayList<>();

        if (word == null || word.trim().isEmpty()) {
            return collocations;
        }

        word = word.trim().toLowerCase();

        // 从 word_associations 表查询搭配
        List<WordAssociation> associations = wordAssociationMapper.findByWordAndType(word, "collocation");
        Set<String> seen = new LinkedHashSet<>();

        for (WordAssociation wa : associations) {
            String key = wa.getAssociatedWord().trim().toLowerCase();
            if (!seen.contains(key)) {
                seen.add(key);
                Map<String, Object> item = new HashMap<>();
                item.put("collocation", wa.getAssociatedWord());
                item.put("definition", wa.getDefinition());
                item.put("example", wa.getExampleSentence());
                item.put("source", wa.getSource());
                collocations.add(item);
            }
        }

        return collocations;
    }

    // ==================== 辅助方法 ====================

    /**
     * 从 ECDICT exchange 字段解析衍生词
     * 格式如: "p:went/d:gone/i:going/s:goes/3:goes"
     */
    private Map<String, List<String>> parseExchange(String exchange) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (exchange == null || exchange.trim().isEmpty()) {
            return result;
        }

        // exchange 格式: key:value/key:value...
        String[] parts = exchange.split("/");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            int colonIdx = part.indexOf(":");
            String key;
            String value;

            if (colonIdx > 0) {
                key = part.substring(0, colonIdx).trim();
                value = part.substring(colonIdx + 1).trim();
            } else {
                // 没有冒号的情况：直接是词形（视为同类型）
                key = "form";
                value = part.trim();
            }

            if (value.isEmpty()) continue;

            // 处理同一个key下有多个值（用逗号分隔）
            String[] values = value.split("[,，]");
            result.computeIfAbsent(key, k -> new ArrayList<>());

            for (String v : values) {
                v = v.trim();
                if (!v.isEmpty()) {
                    result.get(key).add(v);
                }
            }
        }

        return result;
    }

    /**
     * 从 exchange 字段解析同义词列表
     * 同义词解析逻辑：提取 exchange 中所有非结构化的单词片段
     */
    private List<String> parseSynonymsFromExchange(String exchange) {
        List<String> synonyms = new ArrayList<>();
        if (exchange == null || exchange.trim().isEmpty()) {
            return synonyms;
        }

        String[] parts = exchange.split("[/,\\s;；]+");
        Set<String> seen = new LinkedHashSet<>();

        for (String part : parts) {
            part = part.trim();
            // 跳过 exchange 结构化标签 (如 p:/d:/i: 等)
            if (part.matches("^[pdirst310]:$")) continue;
            // 跳过包含冒号的键值对（这些是衍生词，不是同义词）
            if (part.contains(":")) continue;
            // 过滤无效词
            if (part.length() < 2) continue;
            if (part.matches("\\d+")) continue;

            String[] invalidWords = {"the", "a", "an", "and", "or", "but", "of", "in", "on", "at", "to", "for", "with", "by"};
            boolean isValid = true;
            for (String iw : invalidWords) {
                if (part.equalsIgnoreCase(iw)) {
                    isValid = false;
                    break;
                }
            }
            if (!isValid) continue;

            if (!seen.contains(part.toLowerCase())) {
                seen.add(part.toLowerCase());
                synonyms.add(part);
            }
        }

        // 限制最多 20 个
        if (synonyms.size() > 20) {
            synonyms = synonyms.subList(0, 20);
        }

        return synonyms;
    }

    /**
     * 查询 ECDICT 词条
     */
    private ECDICTWord findEcdictWord(String word) {
        if (ecdictWordMapper == null) return null;
        try {
            return ecdictWordMapper.findByWord(word);
        } catch (Exception e) {
            logger.debug("ECDICT查询失败: word={}", word);
            return null;
        }
    }

    /**
     * 从 ECDICT 获取单词释义
     */
    private String lookupEcdictDefinition(String word) {
        ECDICTWord ecdict = findEcdictWord(word);
        if (ecdict != null && ecdict.getDefinition() != null) {
            return extractPrimaryDef(ecdict.getDefinition());
        }
        return null;
    }

    /**
     * 提取 ECDICT 定义的主释义（第一个分号前的内容）
     */
    private String extractPrimaryDef(String definition) {
        if (definition == null) return null;
        int idx = definition.indexOf(";");
        if (idx > 0) {
            return definition.substring(0, idx).trim();
        }
        // 也检查换行
        idx = definition.indexOf("\\n");
        if (idx > 0) {
            return definition.substring(0, idx).trim();
        }
        idx = definition.indexOf("\n");
        if (idx > 0) {
            return definition.substring(0, idx).trim();
        }
        return definition.trim();
    }

    /**
     * 将 JSON 数组字符串解析为 List
     */
    private List<String> parseJsonArray(String json) {
        List<String> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || "[]".equals(json.trim())) {
            return result;
        }

        try {
            // 简单去除 JSON 数组方括号和引号
            String content = json.trim();
            if (content.startsWith("[") && content.endsWith("]")) {
                content = content.substring(1, content.length() - 1);
            }
            String[] items = content.split(",");
            for (String item : items) {
                item = item.trim();
                // 去掉引号
                if (item.startsWith("\"") && item.endsWith("\"")) {
                    item = item.substring(1, item.length() - 1);
                }
                if (!item.isEmpty()) {
                    result.add(item);
                }
            }
        } catch (Exception e) {
            logger.warn("JSON解析失败: {}", json);
        }

        return result;
    }

    /**
     * 解析ECDICT exchange字段，获取词形变化表
     * 格式: p:went/d:gone/i:going/3:goes 等
     */
    private Map<String, String> parseWordForms(String exchange) {
        Map<String, String> forms = new LinkedHashMap<>();
        if (exchange == null || exchange.trim().isEmpty()) {
            return forms;
        }

        Map<String, String> typeLabels = new HashMap<>();
        typeLabels.put("p", "过去式");
        typeLabels.put("d", "过去分词");
        typeLabels.put("i", "现在分词");
        typeLabels.put("3", "第三人称单数");
        typeLabels.put("s", "复数");
        typeLabels.put("r", "比较级");
        typeLabels.put("t", "最高级");
        typeLabels.put("0", "原形");
        typeLabels.put("1", "复数");

        try {
            String[] parts = exchange.split("/");
            for (String part : parts) {
                if (part.contains(":")) {
                    String[] kv = part.split(":", 2);
                    if (kv.length == 2) {
                        String type = kv[0].trim();
                        String word = kv[1].trim();
                        String label = typeLabels.getOrDefault(type, type);
                        if (!word.isEmpty()) {
                            forms.put(label, word);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("解析词形变化失败: {}", exchange);
        }

        return forms;
    }
}
