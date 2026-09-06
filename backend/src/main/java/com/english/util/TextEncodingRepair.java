package com.english.util;

import com.english.entity.Text;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Repairs UTF-8 text that was accidentally decoded as Latin-1 or GBK. */
public final class TextEncodingRepair {
    private static final Charset GBK = Charset.forName("GBK");

    private TextEncodingRepair() {
    }

    public static String repair(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String best = value;
        int bestBadness = badness(value);
        int bestCjkCount = cjkCount(value);
        for (Charset sourceCharset : List.of(StandardCharsets.ISO_8859_1, GBK)) {
            String candidate;
            try {
                candidate = new String(value.getBytes(sourceCharset), StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                continue;
            }
            if (candidate.equals(value) || candidate.indexOf('\uFFFD') >= 0) {
                continue;
            }

            int candidateBadness = badness(candidate);
            int candidateCjkCount = cjkCount(candidate);
            boolean improvesCjk = candidateCjkCount > bestCjkCount
                    && candidateBadness <= bestBadness;
            boolean removesMojibake = bestBadness >= 2
                    && candidateBadness < bestBadness;
            if (improvesCjk || removesMojibake) {
                best = candidate;
                bestBadness = candidateBadness;
                bestCjkCount = candidateCjkCount;
            }
        }
        return best;
    }

    public static void repair(Text text) {
        if (text == null) {
            return;
        }
        text.setTitle(repair(text.getTitle()));
        text.setContent(repair(text.getContent()));
        text.setCet4Content(repair(text.getCet4Content()));
        text.setCet6Content(repair(text.getCet6Content()));
        text.setAcademicContent(repair(text.getAcademicContent()));
        text.setChineseParallelContent(repair(text.getChineseParallelContent()));
        text.setMarkdownContent(repair(text.getMarkdownContent()));
        text.setCet4MarkdownContent(repair(text.getCet4MarkdownContent()));
        text.setCet6MarkdownContent(repair(text.getCet6MarkdownContent()));
        text.setAcademicMarkdownContent(repair(text.getAcademicMarkdownContent()));
        text.setChineseParallelMarkdownContent(repair(text.getChineseParallelMarkdownContent()));
        text.setTags(repair(text.getTags()));
        text.setCategory(repair(text.getCategory()));
    }

    public static void repairAll(List<Text> texts) {
        if (texts != null) {
            texts.forEach(TextEncodingRepair::repair);
        }
    }

    private static int cjkCount(String value) {
        int count = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if ((c >= '\u4E00' && c <= '\u9FFF')
                    || (c >= '\u3400' && c <= '\u4DBF')
                    || (c >= '\uF900' && c <= '\uFAFF')) {
                count++;
            }
        }
        return count;
    }

    private static int badness(String value) {
        int score = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\uFFFD' || (c >= '\u0080' && c <= '\u009F')
                    || (c >= '\uE000' && c <= '\uF8FF')) {
                score += 3;
            } else if (c == 'Ã' || c == 'Â' || c == 'â' || c == 'ð'
                    || c == 'æ' || c == 'å' || c == 'ç' || c == 'é') {
                score++;
            }
        }
        return score;
    }
}
