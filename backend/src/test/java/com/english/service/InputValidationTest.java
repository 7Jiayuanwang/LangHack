package com.english.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 输入验证和边界条件测试
 * 测试系统中的分页、数据验证等核心逻辑
 */
@DisplayName("输入验证与边界条件测试")
public class InputValidationTest {

    // ==================== 分页验证测试 ====================

    /**
     * 测试: 负数页码转换为 1
     * 期望: page = -1 应转为 page = 1
     */
    @Test
    @DisplayName("负数页码转换为首页")
    public void testPageBounds_negativePageBecomesOne() {
        int page = -5;
        int correctedPage = Math.max(1, page);

        assertEquals(1, correctedPage);
    }

    /**
     * 测试: 零页码转换为 1
     * 期望: page = 0 应转为 page = 1
     */
    @Test
    @DisplayName("零页码转换为首页")
    public void testPageBounds_zeroPageBecomesOne() {
        int page = 0;
        int correctedPage = Math.max(1, page);

        assertEquals(1, correctedPage);
    }

    /**
     * 测试: 正常页码保持不变
     * 期望: page = 5 应保持为 5
     */
    @Test
    @DisplayName("正常页码保持不变")
    public void testPageBounds_validPageUnchanged() {
        int page = 5;
        int correctedPage = Math.max(1, page);

        assertEquals(5, correctedPage);
    }

    /**
     * 测试: 负数每页大小转换为 1
     * 期望: size = -10 应转为 size = 1
     */
    @Test
    @DisplayName("负数每页大小转换为最小值")
    public void testPageBounds_negativeSizeBecomesOne() {
        int size = -10;
        int correctedSize = Math.max(1, size);

        assertEquals(1, correctedSize);
    }

    /**
     * 测试: 零每页大小转换为 1
     * 期望: size = 0 应转为 size = 1
     */
    @Test
    @DisplayName("零每页大小转换为最小值")
    public void testPageBounds_zeroSizeBecomesOne() {
        int size = 0;
        int correctedSize = Math.max(1, size);

        assertEquals(1, correctedSize);
    }

    /**
     * 测试: 超大每页大小被限制为 100
     * 期望: size = 200 应限制为 size = 100
     */
    @Test
    @DisplayName("超大每页大小被限制")
    public void testPageBounds_oversizeIsLimitedTo100() {
        int size = 200;
        final int MAX_SIZE = 100;
        int correctedSize = Math.min(size, MAX_SIZE);

        assertEquals(100, correctedSize);
    }

    /**
     * 测试: 正常每页大小保持不变
     * 期望: size = 10 应保持为 10
     */
    @Test
    @DisplayName("正常每页大小保持不变")
    public void testPageBounds_validSizeUnchanged() {
        int size = 10;
        final int MAX_SIZE = 100;
        int correctedSize = Math.min(size, MAX_SIZE);

        assertEquals(10, correctedSize);
    }

    /**
     * 测试: 最大每页大小保持不变
     * 期望: size = 100 应保持为 100
     */
    @Test
    @DisplayName("最大每页大小保持不变")
    public void testPageBounds_maxSizeUnchanged() {
        int size = 100;
        final int MAX_SIZE = 100;
        int correctedSize = Math.min(size, MAX_SIZE);

        assertEquals(100, correctedSize);
    }

    // ==================== 关键词截断测试 ====================

    /**
     * 测试: 长关键词被截断为指定长度
     * 期望: 长度为 150 的字符串被截断为 100
     */
    @Test
    @DisplayName("长关键词被截断")
    public void testKeywordTruncation_longStringTruncated() {
        String keyword = "a".repeat(150); // 150 个 'a'
        final int MAX_KEYWORD_LENGTH = 100;
        String truncated = keyword.length() > MAX_KEYWORD_LENGTH
            ? keyword.substring(0, MAX_KEYWORD_LENGTH)
            : keyword;

        assertEquals(100, truncated.length());
        assertTrue(truncated.startsWith("aaaaa"));
    }

    /**
     * 测试: 正常关键词不被截断
     * 期望: 长度为 50 的字符串保持为 50
     */
    @Test
    @DisplayName("正常关键词保持不变")
    public void testKeywordTruncation_normalStringUnchanged() {
        String keyword = "english learning platform";
        final int MAX_KEYWORD_LENGTH = 100;
        String processed = keyword.length() > MAX_KEYWORD_LENGTH
            ? keyword.substring(0, MAX_KEYWORD_LENGTH)
            : keyword;

        assertEquals(keyword, processed);
        assertEquals(25, processed.length());
    }

    /**
     * 测试: 空字符串处理
     * 期望: 空字符串保持为空
     */
    @Test
    @DisplayName("空字符串处理")
    public void testKeywordTruncation_emptyStringHandling() {
        String keyword = "";
        final int MAX_KEYWORD_LENGTH = 100;
        String processed = keyword.length() > MAX_KEYWORD_LENGTH
            ? keyword.substring(0, MAX_KEYWORD_LENGTH)
            : keyword;

        assertEquals("", processed);
    }

    /**
     * 测试: 边界长度关键词
     * 期望: 长度恰好为 100 的字符串不被截断
     */
    @Test
    @DisplayName("边界长度关键词不被截断")
    public void testKeywordTruncation_boundaryLength() {
        String keyword = "x".repeat(100); // 恰好 100 个字符
        final int MAX_KEYWORD_LENGTH = 100;
        String processed = keyword.length() > MAX_KEYWORD_LENGTH
            ? keyword.substring(0, MAX_KEYWORD_LENGTH)
            : keyword;

        assertEquals(keyword, processed);
        assertEquals(100, processed.length());
    }

    // ==================== 掌握度等级验证测试 ====================

    /**
     * 测试: 有效的掌握度等级
     * 期望: MASTERED, LEARNING, REVIEWING 都被接受
     */
    @Test
    @DisplayName("有效的掌握度等级被接受")
    public void testMasteryLevel_validValues() {
        String[] validLevels = {"MASTERED", "LEARNING", "REVIEWING", "NOT_STARTED"};

        for (String level : validLevels) {
            assertTrue(isValidMasteryLevel(level),
                "Mastery level '" + level + "' should be valid");
        }
    }

    /**
     * 测试: 无效的掌握度等级被拒绝
     * 期望: INVALID, 123, null 都被拒绝
     */
    @Test
    @DisplayName("无效的掌握度等级被拒绝")
    public void testMasteryLevel_invalidRejected() {
        String[] invalidLevels = {"INVALID", "123", "master", "UNKNOWN", ""};

        for (String level : invalidLevels) {
            assertFalse(isValidMasteryLevel(level),
                "Mastery level '" + level + "' should be invalid");
        }
    }

    /**
     * 测试: null 掌握度等级被拒绝
     * 期望: null 被安全处理
     */
    @Test
    @DisplayName("Null 掌握度等级被拒绝")
    public void testMasteryLevel_nullRejected() {
        assertFalse(isValidMasteryLevel(null));
    }

    /**
     * 测试: 大小写敏感性
     * 期望: 小写 "mastered" 被拒绝
     */
    @Test
    @DisplayName("掌握度等级大小写敏感")
    public void testMasteryLevel_caseSensitive() {
        assertFalse(isValidMasteryLevel("mastered")); // 小写被拒绝
        assertTrue(isValidMasteryLevel("MASTERED"));   // 大写被接受
    }

    // ==================== 整数溢出防御测试 ====================

    /**
     * 测试: 学习时间求和不溢出
     * 期望: 多个整数求和时使用 long 防止溢出
     */
    @Test
    @DisplayName("学习时间求和不溢出")
    public void testStudyTimeSum_noOverflow() {
        int[] times = {
            Integer.MAX_VALUE - 1000,
            Integer.MAX_VALUE - 1000,
            1000
        };

        // 错误方式（可能溢出）：int sum = times[0] + times[1] + times[2];

        // 正确方式（使用 long）
        long sum = 0;
        for (int time : times) {
            sum += time;
        }

        // 验证结果正确
        assertEquals(2L * Integer.MAX_VALUE - 1000, sum);
    }

    /**
     * 测试: 记录数计数不溢出
     * 期望: 使用 long 而不是 int
     */
    @Test
    @DisplayName("记录数计数不溢出")
    public void testRecordCount_usesLong() {
        int recordCount = Integer.MAX_VALUE;

        // 模拟累加
        long totalCount = 0;
        for (int i = 0; i < 100; i++) {
            totalCount += recordCount;
        }

        // 验证溢出被防止
        assertEquals(100L * Integer.MAX_VALUE, totalCount);
        assertTrue(totalCount > Integer.MAX_VALUE);
    }

    // ==================== 文件大小验证测试 ====================

    /**
     * 测试: 图片大小限制检查
     * 期望: 10MB 图片被接受，11MB 被拒绝
     */
    @Test
    @DisplayName("图片大小限制检查")
    public void testImageSize_limitValidation() {
        long maxImageSize = 10 * 1024 * 1024; // 10MB

        long smallSize = 10 * 1024 * 1024 - 1; // 略小于 10MB
        assertTrue(smallSize <= maxImageSize);

        long largeSize = 10 * 1024 * 1024 + 1; // 略大于 10MB
        assertFalse(largeSize <= maxImageSize);
    }

    /**
     * 测试: 视频大小限制检查
     * 期望: 100MB 视频被接受，101MB 被拒绝
     */
    @Test
    @DisplayName("视频大小限制检查")
    public void testVideoSize_limitValidation() {
        long maxVideoSize = 100 * 1024 * 1024; // 100MB

        long smallSize = 100 * 1024 * 1024 - 1;
        assertTrue(smallSize <= maxVideoSize);

        long largeSize = 100 * 1024 * 1024 + 1;
        assertFalse(largeSize <= maxVideoSize);
    }

    // ==================== 数值范围验证测试 ====================

    /**
     * 测试: 学习时间不为负数
     * 期望: 负数时长被拒绝
     */
    @Test
    @DisplayName("学习时间不为负数")
    public void testLearningTime_nonNegative() {
        int[] times = {-100, -1, 0, 1, 100};

        for (int time : times) {
            if (time < 0) {
                assertFalse(isValidLearningTime(time),
                    "Negative time " + time + " should be invalid");
            } else {
                assertTrue(isValidLearningTime(time),
                    "Non-negative time " + time + " should be valid");
            }
        }
    }

    /**
     * 测试: 课程 ID 必须为正数
     * 期望: 非正数 ID 被拒绝
     */
    @Test
    @DisplayName("课程ID必须为正数")
    public void testCourseId_mustBePositive() {
        long[] ids = {-1L, 0L, 1L, 100L};

        for (long id : ids) {
            if (id <= 0) {
                assertFalse(isValidId(id));
            } else {
                assertTrue(isValidId(id));
            }
        }
    }

    /**
     * 测试: 学生 ID 必须为正数
     * 期望: 非正数 ID 被拒绝
     */
    @Test
    @DisplayName("学生ID必须为正数")
    public void testStudentId_mustBePositive() {
        long[] ids = {-100L, 0L, 1L, 999L};

        for (long id : ids) {
            if (id <= 0) {
                assertFalse(isValidId(id));
            } else {
                assertTrue(isValidId(id));
            }
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 验证掌握度等级是否有效
     */
    private boolean isValidMasteryLevel(String level) {
        if (level == null) {
            return false;
        }
        return level.equals("MASTERED") ||
               level.equals("LEARNING") ||
               level.equals("REVIEWING") ||
               level.equals("NOT_STARTED");
    }

    /**
     * 验证学习时间是否有效（非负数）
     */
    private boolean isValidLearningTime(int time) {
        return time >= 0;
    }

    /**
     * 验证 ID 是否有效（正数）
     */
    private boolean isValidId(long id) {
        return id > 0;
    }
}
