package com.english.util;

import com.english.entity.Text;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextEncodingRepairTest {

    @Test
    void repairsUtf8DecodedWithTheWrongCharset() {
        assertEquals("\u9A6C\u62C9\u677E\u8BAD\u7EC3\u8BA1\u5212",
                TextEncodingRepair.repair("\u00E9\u00A9\u00AC\u00E6\u008B\u0089\u00E6\u009D\u00BE\u00E8\u00AE\u00AD\u00E7\u00BB\u0083\u00E8\u00AE\u00A1\u00E5\u0088\u0092"));
        assertEquals("\u201CThat\u2019s fine.\u201D",
                TextEncodingRepair.repair("\u00E2\u0080\u009CThat\u00E2\u0080\u0099s fine.\u00E2\u0080\u009D"));
        assertEquals("\u8BFE\u5916", TextEncodingRepair.repair("\u7487\u60E7\uE63B"));
    }

    @Test
    void leavesNormalTextUntouched() {
        assertEquals("A normal English article.", TextEncodingRepair.repair("A normal English article."));
        assertEquals("\u4E2D\u6587\u6807\u9898", TextEncodingRepair.repair("\u4E2D\u6587\u6807\u9898"));
    }

    @Test
    void repairsEntityFieldsAndVersions() {
        Text text = new Text();
        text.setTitle("\u00E9\u00A9\u00AC\u00E6\u008B\u0089\u00E6\u009D\u00BE");
        text.setContent("\u00E2\u0080\u009CHello\u00E2\u0080\u009D");
        TextEncodingRepair.repairAll(List.of(text));
        assertEquals("\u9A6C\u62C9\u677E", text.getTitle());
        assertEquals("\u201CHello\u201D", text.getContent());
    }
}
