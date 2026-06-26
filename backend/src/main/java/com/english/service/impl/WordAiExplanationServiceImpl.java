package com.english.service.impl;

import com.english.dto.WordExplanationDTO;
import com.english.entity.WordAiExplanation;
import com.english.mapper.WordAiExplanationMapper;
import com.english.service.WordAiExplanationService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 单词 AI 详细介绍服务实现
 */
@Slf4j
@Service
public class WordAiExplanationServiceImpl implements WordAiExplanationService {

    @Autowired
    private WordAiExplanationMapper wordAiExplanationMapper;

    @Autowired(required = false)
    private AiProviderRuntimeService aiProviderRuntimeService;

    @Autowired(required = false)
    private HiAgentServiceImpl hiAgentService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public WordExplanationDTO getWordExplanation(String word) throws Exception {
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("单词不能为空");
        }

        word = word.trim().toLowerCase();

        // 1. 先查询缓存
        WordAiExplanation cached = wordAiExplanationMapper.findByWord(word);
        if (cached != null) {
            log.info("[单词介绍] 命中缓存: word={}", word);
            return convertToDTO(cached, true);
        }

        // 2. 缓存未命中，调用 AI 生成
        log.info("[单词介绍] 缓存未命中，调用 AI 生成: word={}", word);
        return generateAndSaveExplanation(word);
    }

    @Override
    public WordExplanationDTO regenerateWordExplanation(String word) throws Exception {
        if (word == null || word.trim().isEmpty()) {
            throw new IllegalArgumentException("单词不能为空");
        }

        word = word.trim().toLowerCase();

        log.info("[单词介绍] 强制重新生成: word={}", word);

        // 删除旧缓存
        wordAiExplanationMapper.deleteByWord(word);

        // 生成新内容
        return generateAndSaveExplanation(word);
    }

    private WordExplanationDTO generateAndSaveExplanation(String word) throws Exception {
        // 构建 AI 提示词
        String prompt = buildPrompt(word);

        // 调用 AI 服务
        String aiResponse = callAiService(prompt);

        // 解析 AI 响应
        WordAiExplanation explanation = parseAiResponse(word, aiResponse);

        // 保存到数据库
        wordAiExplanationMapper.insert(explanation);

        log.info("[单词介绍] 生成并保存成功: word={}", word);

        return convertToDTO(explanation, false);
    }

    private String buildPrompt(String word) {
        return String.format(
            "请为英语单词 \"%s\" 生成详细的学习介绍，包括以下内容（以 JSON 格式返回）：\n" +
            "{\n" +
            "  \"phonetic\": \"音标（如 /wɜːrd/）\",\n" +
            "  \"wordClass\": \"词性（如 noun, verb, adjective 等）\",\n" +
            "  \"basicMeaning\": \"基本中文释义\",\n" +
            "  \"detailedExplanation\": \"详细解释（200字左右，包括用法、常见场景）\",\n" +
            "  \"etymology\": \"词源（如果有）\",\n" +
            "  \"usageExamples\": [\"例句1\", \"例句2\", \"例句3\"],\n" +
            "  \"synonyms\": [\"同义词1\", \"同义词2\"],\n" +
            "  \"antonyms\": [\"反义词1\", \"反义词2\"],\n" +
            "  \"collocations\": [\"常见搭配1\", \"常见搭配2\"]\n" +
            "}\n" +
            "注意：\n" +
            "1. 请确保返回纯 JSON 格式，不要包含额外的文字说明\n" +
            "2. 例句要地道实用，适合中国学生学习\n" +
            "3. 详细解释要通俗易懂，突出重点用法\n" +
            "4. 如果某些字段没有内容，可以留空字符串或空数组",
            word
        );
    }

    private String callAiService(String prompt) throws Exception {
        // 优先使用 HiAgent
        if (hiAgentService != null) {
            try {
                return hiAgentService.chat(prompt);
            } catch (Exception e) {
                log.warn("[单词介绍] HiAgent 调用失败，尝试 OpenRouter: {}", e.getMessage());
            }
        }

        // 备用 OpenRouter
        if (aiProviderRuntimeService != null) {
            return aiProviderRuntimeService.sendMessageToAI(prompt);
        }

        throw new RuntimeException("AI 服务未配置或不可用");
    }

    private WordAiExplanation parseAiResponse(String word, String aiResponse) throws Exception {
        // 提取 JSON（AI 可能会在前后加说明文字）
        String jsonStr = extractJson(aiResponse);

        // 解析 JSON
        Map<String, Object> responseMap = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});

        WordAiExplanation explanation = new WordAiExplanation();
        explanation.setWord(word);
        explanation.setPhonetic((String) responseMap.getOrDefault("phonetic", ""));
        explanation.setWordClass((String) responseMap.getOrDefault("wordClass", ""));
        explanation.setBasicMeaning((String) responseMap.getOrDefault("basicMeaning", ""));
        explanation.setDetailedExplanation((String) responseMap.getOrDefault("detailedExplanation", ""));
        explanation.setEtymology((String) responseMap.getOrDefault("etymology", ""));

        // 处理数组字段
        explanation.setUsageExamples(serializeList(responseMap.get("usageExamples")));
        explanation.setSynonyms(serializeList(responseMap.get("synonyms")));
        explanation.setAntonyms(serializeList(responseMap.get("antonyms")));
        explanation.setCollocations(serializeList(responseMap.get("collocations")));

        return explanation;
    }

    private String extractJson(String text) {
        // 尝试提取 JSON 部分
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String serializeList(Object obj) throws Exception {
        if (obj == null) {
            return "[]";
        }
        if (obj instanceof List) {
            return objectMapper.writeValueAsString(obj);
        }
        return "[]";
    }

    private WordExplanationDTO convertToDTO(WordAiExplanation entity, boolean fromCache) throws Exception {
        WordExplanationDTO dto = new WordExplanationDTO();
        dto.setWord(entity.getWord());
        dto.setPhonetic(entity.getPhonetic());
        dto.setWordClass(entity.getWordClass());
        dto.setBasicMeaning(entity.getBasicMeaning());
        dto.setDetailedExplanation(entity.getDetailedExplanation());
        dto.setEtymology(entity.getEtymology());
        dto.setFromCache(fromCache);

        // 反序列化数组字段
        dto.setUsageExamples(deserializeList(entity.getUsageExamples()));
        dto.setSynonyms(deserializeList(entity.getSynonyms()));
        dto.setAntonyms(deserializeList(entity.getAntonyms()));
        dto.setCollocations(deserializeList(entity.getCollocations()));

        return dto;
    }

    private List<String> deserializeList(String json) throws Exception {
        if (json == null || json.trim().isEmpty() || json.equals("[]")) {
            return new ArrayList<>();
        }
        return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    }
}
