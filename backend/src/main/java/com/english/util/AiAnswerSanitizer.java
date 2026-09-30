package com.english.util;

import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Removes model-internal scaffolding before an answer reaches a learner. */
public final class AiAnswerSanitizer {

    private static final Pattern HIDDEN_BLOCK = Pattern.compile(
            "(?is)<(?:think|thinking|analysis|reasoning)>.*?</(?:think|thinking|analysis|reasoning)>|"
                    + "\\[(?:think|thinking|analysis|reasoning)\\].*?\\[/(?:think|thinking|analysis|reasoning)\\]");
    private static final Pattern META_SENTENCE = Pattern.compile(
            "(?is)^\\s*(?:用户|这位用户|学生)(?:现在|当前|想要|需要|希望|是在|正在).*?[。！？!?]+\\s*");
    private static final Pattern META_REVIEW_SENTENCE = Pattern.compile(
            "(?is)(?:这样就可以|检查(?:一下|下)|符合(?:要求|.*需求)|首先直接回答|"
                    + "只有一个追问|有没有涉及其他无关|都是英语学习|下一步|对吧)");
    private static final Pattern EMBEDDED_REVIEW = Pattern.compile(
            "(?is)(?:不对[,，]|等等[,，]|不过用户|用户要详细|专业对吧|这样就可以|"
                    + "等下有没有|符合限制|都是中文|必要的英文标注).*$");
    private static final Pattern META_PARAGRAPH = Pattern.compile(
            "(?is)^\\s*(?:首先|然后|接下来|我需要|让我先|好的[,， ]*我|先要|需要先)"
                    + ".{0,500}?(?:用户|学生|分析|计划|步骤|结合.*情况|对吧|下一步|提问).{0,500}$");

    private AiAnswerSanitizer() {
    }

    public static String sanitize(String answer) {
        if (answer == null || answer.isBlank()) {
            return answer;
        }

        String cleaned = HIDDEN_BLOCK.matcher(answer).replaceAll("");
        cleaned = cleaned.replace("\r\n", "\n").replace('\r', '\n').trim();

        // Internal planning is normally emitted as one or more leading paragraphs.
        String[] paragraphs = cleaned.split("\\n\\s*\\n");
        int firstAnswer = 0;
        while (firstAnswer < paragraphs.length - 1 && isMetaParagraph(paragraphs[firstAnswer])) {
            firstAnswer++;
        }
        if (firstAnswer > 0) {
            cleaned = String.join("\n\n", java.util.Arrays.copyOfRange(
                    paragraphs, firstAnswer, paragraphs.length)).trim();
        }

        cleaned = removeEmbeddedReviewAndDuplicates(cleaned);

        // A common compact form is: "用户现在需要...对吧？真正答案...".
        cleaned = META_SENTENCE.matcher(cleaned).replaceFirst("").trim();
        cleaned = stripLeadingReview(cleaned);
        return cleaned.isBlank() ? answer.trim() : cleaned;
    }

    private static String removeEmbeddedReviewAndDuplicates(String text) {
        String[] paragraphs = text.split("\\n\\s*\\n");
        StringBuilder result = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String paragraph : paragraphs) {
            String current = paragraph.trim();
            if (current.isEmpty()) {
                continue;
            }
            java.util.regex.Matcher review = EMBEDDED_REVIEW.matcher(current);
            if (review.find()) {
                current = current.substring(0, review.start()).trim();
            }
            if (current.isEmpty()) {
                continue;
            }
            String fingerprint = current.replaceAll("\\s+", " ").trim();
            if (fingerprint.length() > 3 && !seen.add(fingerprint)) {
                continue;
            }
            if (result.length() > 0) {
                result.append("\n\n");
            }
            result.append(current);
        }
        return result.toString().trim();
    }

    /** Used by the SSE bridge to avoid flushing a still-growing planning preface. */
    public static boolean hasLeadingMeta(String answer) {
        if (answer == null || answer.isBlank()) {
            return false;
        }
        String prefix = answer.length() > 900 ? answer.substring(0, 900) : answer;
        return META_SENTENCE.matcher(prefix).find()
                || META_REVIEW_SENTENCE.matcher(prefix).find()
                || isMetaParagraph(prefix);
    }

    private static String stripLeadingReview(String text) {
        String[] sentences = text.split("(?<=[。！？!?])\\s*");
        if (sentences.length < 2) {
            return text;
        }
        int cut = 0;
        boolean removed = false;
        for (String sentence : sentences) {
            if (!META_REVIEW_SENTENCE.matcher(sentence).find()) {
                break;
            }
            cut += sentence.length();
            removed = true;
        }
        return removed && cut < text.length() ? text.substring(cut).trim() : text;
    }

    private static boolean isMetaParagraph(String paragraph) {
        String normalized = paragraph.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        return META_PARAGRAPH.matcher(paragraph).matches()
                || normalized.startsWith("用户现在")
                || normalized.startsWith("用户当前")
                || normalized.startsWith("这位用户")
                || normalized.startsWith("我需要先")
                || normalized.startsWith("让我先")
                || normalized.startsWith("好的，我来")
                || normalized.startsWith("接下来我会")
                || normalized.startsWith("首先我会");
    }
}
