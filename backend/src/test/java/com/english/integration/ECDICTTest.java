package com.english.integration;

import com.english.service.ECDICTService;
import com.english.service.ECDICTService.ECDICTWordDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ECDICT服务测试类
 * 用于验证ECDICT集成是否正常工作
 * 仅在 dev 环境执行
 */
@SpringBootTest
@ActiveProfiles("dev")
public class ECDICTTest {

    @Autowired
    private ECDICTService ecdictService;

    @Test
    public void testECDICTServiceAvailability() throws Exception {
        System.out.println("=== ECDICT服务测试开始 ===");

        // 测试1: 检查服务是否可用
        boolean isAvailable = ecdictService.isECDICTAvailable();
        System.out.println("ECDICT服务可用性: " + (isAvailable ? "✓ 可用" : "✗ 不可用"));

        if (!isAvailable) {
            System.out.println("ECDICT服务不可用，测试终止");
            return;
        }

        // 测试2: 获取词典统计信息
        Map<String, Object> stats = ecdictService.getDictionaryStats();
        assertNotNull(stats, "词典统计信息不应为null");
        System.out.println("词典统计信息:");
        System.out.println("  - 总词汇数: " + stats.get("totalWords"));
        System.out.println("  - 最后更新: " + stats.get("lastUpdated"));

        // 测试3: 查询单个单词
        String[] testWords = { "hello", "world", "beautiful", "run", "book" };
        for (String word : testWords) {
            System.out.println("\n--- 测试单词: " + word + " ---");
            Map<String, Object> wordInfo = ecdictService.getWordInfo(word);
            assertNotNull(wordInfo, "单词信息不应为null");
            System.out.println("  查找结果: " + (Boolean.TRUE.equals(wordInfo.get("found")) ? "✓ 找到" : "✗ 未找到"));
            System.out.println("  单词: " + wordInfo.get("word"));
            System.out.println("  音标: " + wordInfo.get("phonetic"));
            System.out.println("  定义: " + wordInfo.get("definition"));
            System.out.println("  翻译: " + wordInfo.get("translation"));
            System.out.println("  词性: " + wordInfo.get("pos"));

            if (Boolean.TRUE.equals(wordInfo.get("fuzzy"))) {
                System.out.println("  模糊匹配: " + wordInfo.get("suggestion"));
            }
        }

        // 测试4: 搜索功能
        System.out.println("\n--- 测试搜索功能 ---");
        String searchKeyword = "run";
        List<Map<String, Object>> searchResults = ecdictService.searchWords(searchKeyword, 5);
        assertNotNull(searchResults, "搜索结果不应为null");
        System.out.println("搜索关键词: " + searchKeyword);
        System.out.println("搜索结果数量: " + searchResults.size());

        for (int i = 0; i < Math.min(3, searchResults.size()); i++) {
            Map<String, Object> result = searchResults.get(i);
            System.out.println("  结果" + (i + 1) + ": " + result.get("word") + " - " + result.get("translation"));
        }

        System.out.println("\n=== ECDICT服务测试完成 ===");
    }
}
