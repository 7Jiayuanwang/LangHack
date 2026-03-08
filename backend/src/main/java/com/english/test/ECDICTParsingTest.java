package com.english.test;

import com.english.service.ECDICTService;
import com.english.service.ECDICTService.ECDICTWordDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * ECDICT解析优化测试类
 * 验证文本清理和格式化是否正常工作
 */
// @Component
public class ECDICTParsingTest implements CommandLineRunner {

    @Autowired
    private ECDICTService ecdictService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=== ECDICT解析优化测试开始 ===");

        // 测试1: 检查常见词汇的解析质量
        String[] testWords = { "hello", "world", "book", "good", "very" };

        for (String word : testWords) {
            System.out.println("\n--- 测试词汇: " + word + " ---");
            Map<String, Object> wordInfo = ecdictService.getWordInfo(word);

            if (Boolean.TRUE.equals(wordInfo.get("found"))) {
                System.out.println("✓ 找到词汇");

                // 检查音标
                String phonetic = (String) wordInfo.get("phonetic");
                if (phonetic != null && !phonetic.isEmpty()) {
                    System.out.println("  音标: " + phonetic);
                    // 检查是否有异常字符
                    if (containsAbnormalChars(phonetic)) {
                        System.out.println("  ⚠️ 音标包含异常字符");
                    } else {
                        System.out.println("  ✓ 音标格式正常");
                    }
                }

                // 检查翻译
                String translation = (String) wordInfo.get("translation");
                if (translation != null && !translation.isEmpty()) {
                    System.out.println("  翻译: " + translation);
                    // 检查是否有异常引号
                    if (containsAbnormalQuotes(translation)) {
                        System.out.println("  ⚠️ 翻译包含异常引号");
                    } else {
                        System.out.println("  ✓ 翻译格式正常");
                    }
                }

                // 检查定义
                String definition = (String) wordInfo.get("definition");
                if (definition != null && !definition.isEmpty()) {
                    System.out.println("  定义: " + definition.substring(0, Math.min(100, definition.length())) + "...");
                    // 检查是否有异常字符
                    if (containsAbnormalChars(definition)) {
                        System.out.println("  ⚠️ 定义包含异常字符");
                    } else {
                        System.out.println("  ✓ 定义格式正常");
                    }
                }

            } else {
                System.out.println("✗ 未找到词汇");
            }
        }

        // 测试2: 特殊字符处理测试
        System.out.println("\n--- 特殊字符处理测试 ---");
        String[] specialChars = {
                "\u201C", "\u201D", // 中文双引号
                "\u2018", "\u2019", // 中文单引号
                "[法]", "[计]",
                "   " // 多个空格
        };

        for (String test : specialChars) {
            System.out.println("测试字符: '" + test + "' -> 清理结果: '" + testAndCleanField(test) + "'");
        }

        System.out.println("\n=== ECDICT解析优化测试完成 ===");
    }

    /**
     * 检查是否包含异常字符
     */
    private boolean containsAbnormalChars(String text) {
        // 检查是否包含非标准ASCII字符和中文字符
        Pattern normalPattern = Pattern.compile("^[\\u4e00-\\u9fa5\\w\\s.,;:!?()\\[\\]{}'\\\"-]+$");
        return !normalPattern.matcher(text).matches();
    }

    /**
     * 检查是否包含异常引号
     */
    private boolean containsAbnormalQuotes(String text) {
        return text.contains("\u201C") || text.contains("\u201D") || // 中文引号 ""
                text.contains("\u2018") || text.contains("\u2019") || // 中文单引号 ''
                text.contains("\u00B4"); // 特殊撇号 ´
    }

    /**
     * 测试字段清理效果
     */
    private String testAndCleanField(String field) {
        if (field == null || field.isEmpty())
            return "";

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
        field = field.replaceAll("\\[a-zA-Z+\\]", "");

        // 清理多余的空白字符（包括制表符等）
        field = field.replaceAll("\\s+", " ").trim();

        // 清理其他特殊字符，保留中文、英文、数字、基本标点
        field = field.replaceAll("[^\\u4e00-\\u9fa5\\w\\s.,;:!?()\\[\\]{}'\"-]", "");

        return field;
    }
}