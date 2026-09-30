package com.english.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiAnswerSanitizerTest {

    @Test
    void removesLeadingUserPlanningParagraph() {
        String answer = "用户现在需要深度理解倒装句，我要先结合他的情况制定计划，对吧？\n\n"
                + "倒装句是把助动词或谓语放到主语前面的句式。";

        assertEquals("倒装句是把助动词或谓语放到主语前面的句式。",
                AiAnswerSanitizer.sanitize(answer));
    }

    @Test
    void removesThinkTagsButKeepsTeachingContent() {
        String answer = "<think>先分析用户水平</think>\n\n"
                + "## 解释\n\n这是一个例子。";

        assertEquals("## 解释\n\n这是一个例子。", AiAnswerSanitizer.sanitize(answer));
    }

    @Test
    void keepsNormalAnswerStartingWithFirst() {
        String answer = "首先，倒装句常用于疑问句。\n\n例如：Where are you?";

        assertEquals(answer, AiAnswerSanitizer.sanitize(answer));
    }

    @Test
    void removesEmbeddedReviewAndDuplicateParagraphs() {
        String answer = "这句话表示他会继续寻找零件。\n\n"
                + "重点内容解析：\n\n"
                + "你可以试着用这个短语造句吗？不对，不过用户要详细，符合限制吗？都是中文。\n\n"
                + "这句话表示他会继续寻找零件。\n\n"
                + "重点内容解析：";

        assertEquals("这句话表示他会继续寻找零件。\n\n重点内容解析：\n\n"
                        + "你可以试着用这个短语造句吗？",
                AiAnswerSanitizer.sanitize(answer));
    }
}
