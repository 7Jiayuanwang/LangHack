package com.english.test;

import com.english.entity.Word;
import com.english.service.ECDICTService;
import com.english.service.WordNetService;
import com.english.utils.WordConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 词汇格式统一测试类
 * 验证ECDICT和数据库词汇数据格式的一致性
 */
// @Component
public class WordFormatTest implements CommandLineRunner {

    @Autowired
    private ECDICTService ecdictService;

    @Autowired
    private WordNetService wordNetService;

    @Autowired
    private WordConverter wordConverter;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== 词汇格式统一测试开始 ===");

        // 测试词汇
        String[] testWords = { "hello", "world", "very", "book" };

        for (String testWord : testWords) {
            System.out.println("\n--- 测试词汇: " + testWord + " ---");

            // 1. 测试WordNetService返回格式
            Map<String, Object> wordNetResult = wordNetService.getWordInfo(testWord);
            if (wordNetResult == null) {
                System.out.println("✗ 无法获取词汇数据: " + testWord);
                continue;
            }

            System.out.println("WordNetService数据源: " + wordNetResult.getOrDefault("primarySource", "未知"));

            // 2. 验证字段完整性
            if (validateWordFields(wordNetResult)) {
                System.out.println("✓ WordNetService返回格式正确");
            } else {
                System.out.println("✗ WordNetService返回格式不完整");
                printMissingFields(wordNetResult);
            }

            // 3. 测试转换为Word实体
            Word wordEntity = wordConverter.convertToWordEntity(wordNetResult);
            if (wordEntity != null) {
                System.out.println("✓ 转换为Word实体成功");
                System.out.println("  单词: " + wordEntity.getWord());
                System.out.println("  词性: " + wordEntity.getPartOfSpeech());
                System.out.println("  难度: " + wordEntity.getDifficulty());
                System.out.println("  数据源: " + wordEntity.getSource());
            } else {
                System.out.println("✗ 转换为Word实体失败");
            }

            // 4. 测试同义词提取
            List<String> synonyms = wordConverter.extractSynonyms(wordNetResult);
            if (synonyms != null) {
                System.out.println("同义词数量: " + synonyms.size());
                if (!synonyms.isEmpty()) {
                    System.out.println("同义词: " + synonyms.subList(0, Math.min(3, synonyms.size())));
                }
            }

            // 5. 测试多词义定义提取
            List<Map<String, Object>> definitions = wordConverter.extractDefinitions(wordNetResult);
            if (definitions != null) {
                System.out.println("多词义定义数量: " + definitions.size());
            }
        }

        // 测试字段格式一致性
        System.out.println("\n--- 字段格式一致性测试 ---");
        try {
            testFieldConsistency();
        } catch (Exception e) {
            System.err.println("✗ 字段格式一致性测试执行出错: " + e.getMessage());
            System.err.println("错误类型: " + e.getClass().getName());
        }

        System.out.println("\n=== 词汇格式统一测试完成 ===");
    }

    /**
     * 验证词汇字段完整性
     */
    private boolean validateWordFields(Map<String, Object> wordData) {
        String[] requiredFields = {
                "word", "pronunciation", "definition", "exampleSentence",
                "audioUrl", "partOfSpeech", "difficulty", "synonyms",
                "antonyms", "wordnetId", "multipleDefinitions", "source"
        };

        for (String field : requiredFields) {
            if (!wordData.containsKey(field)) {
                System.out.println("缺少字段: " + field);
                return false;
            }
        }

        // 验证兼容性字段
        String[] compatibilityFields = { "pos", "translation" };
        for (String field : compatibilityFields) {
            if (!wordData.containsKey(field)) {
                System.out.println("缺少兼容性字段: " + field);
                return false;
            }
        }

        return true;
    }

    /**
     * 打印缺失字段
     */
    private void printMissingFields(Map<String, Object> wordData) {
        String[] allFields = {
                "word", "pronunciation", "definition", "exampleSentence",
                "audioUrl", "partOfSpeech", "difficulty", "synonyms",
                "antonyms", "wordnetId", "multipleDefinitions", "source",
                "pos", "translation", "collins", "oxford", "tag", "bnc", "frq"
        };

        System.out.println("缺失字段详情:");
        for (String field : allFields) {
            if (!wordData.containsKey(field)) {
                System.out.println("  ✗ " + field);
            } else {
                System.out.println("  ✓ " + field);
            }
        }
    }

    /**
     * 测试字段格式一致性
     */
    private void testFieldConsistency() {
        // 测试ECDICT直接查询
        if (ecdictService.isECDICTAvailable()) {
            Map<String, Object> ecdictResult = ecdictService.getWordInfo("hello");
            if (ecdictResult != null) {
                System.out.println("ECDICT直接查询字段数量: " + ecdictResult.keySet().size());

                // 验证JSON字段格式 - 增加了防御性非空检查
                String synonyms = (String) ecdictResult.get("synonyms");
                String multipleDefinitions = (String) ecdictResult.get("multipleDefinitions");
                String antonyms = (String) ecdictResult.get("antonyms");

                // 检查同义词格式
                if (synonyms != null) {
                    boolean isValid = synonyms.startsWith("[") && synonyms.endsWith("]");
                    System.out.println("同义词JSON格式: " + (isValid ? "✓" : "✗ (内容不是有效的JSON数组)"));
                } else {
                    System.out.println("同义词JSON格式: - (字段为空)");
                }

                // 检查多词义格式 (之前报错的第150行)
                if (multipleDefinitions != null) {
                    boolean isValid = multipleDefinitions.startsWith("[") && multipleDefinitions.endsWith("]");
                    System.out.println("多词义JSON格式: " + (isValid ? "✓" : "✗ (内容不是有效的JSON数组)"));
                } else {
                    System.out.println("多词义JSON格式: - (字段为空)");
                }

                // 检查反义词格式
                if (antonyms != null) {
                    boolean isValid = antonyms.startsWith("[") && antonyms.endsWith("]");
                    System.out.println("反义词JSON格式: " + (isValid ? "✓" : "✗ (内容不是有效的JSON数组)"));
                }
            }
        }

        // 测试WordNetService整合
        Map<String, Object> wordNetResult = wordNetService.getWordInfo("hello");
        if (wordNetResult != null) {
            System.out.println("WordNetService整合字段数量: " + wordNetResult.keySet().size());

            // 验证解析后的对象字段
            Object synonymsList = wordNetResult.get("synonymsList");
            Object definitionsList = wordNetResult.get("definitionsList");

            System.out.println("同义词列表格式: " + (synonymsList instanceof List ? "✓" : "✗"));
            System.out.println("定义列表格式: " + (definitionsList instanceof List ? "✓" : "✗"));
        }
    }
}