package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.english.dto.learner.ArticleLearningInsightDto.KnowledgePoint;
import com.english.entity.ECDICTWord;
import com.english.exception.BusinessException;
import com.english.mapper.ECDICTWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 本地词典与可核对句型规则生成；不向外部服务发送文章或个人成绩。 */
@Service
@RequiredArgsConstructor
public class ArticleInsightGenerationService {
    private static final Pattern WORD = Pattern.compile("[A-Za-z][A-Za-z'-]{4,}");
    private static final Pattern SENTENCE_WORD = Pattern.compile("[A-Za-z]+");
    private static final List<GrammarRule> GRAMMAR_RULES = List.of(
            new GrammarRule(Pattern.compile("\\bnot only\\b.{1,120}\\bbut also\\b", Pattern.CASE_INSENSITIVE),
                    "not only ... but also ...", "并列强调结构：把两个同等重要的内容连接起来。"),
            new GrammarRule(Pattern.compile("\\bthe more\\b.{1,120}\\bthe more\\b", Pattern.CASE_INSENSITIVE),
                    "the more ..., the more ...", "比较句型：表达两个变化之间的关联。"),
            new GrammarRule(Pattern.compile("^\\s*although\\b[^,]{5,120},", Pattern.CASE_INSENSITIVE),
                    "Although ..., ...", "让步状语从句：先承认一种情况，再说明主句的结果。"),
            new GrammarRule(Pattern.compile("^\\s*if\\b[^,]{5,120},", Pattern.CASE_INSENSITIVE),
                    "If ..., ...", "条件状语从句：说明主句成立的条件。"),
            new GrammarRule(Pattern.compile(",\\s*which\\b", Pattern.CASE_INSENSITIVE),
                    ", which ...", "非限制性定语从句：用逗号后的 which 补充前面的信息。")
    );

    private final ECDICTWordMapper dictionaryMapper;

    public List<KnowledgePoint> generate(String content, String level) {
        String plain = normalizeArticle(content);
        if (plain.length() < 80) throw new BusinessException("文章正文太短，暂时无法提炼知识点");
        List<String> sentences = sentenceCandidates(plain);
        List<KnowledgePoint> result = new ArrayList<>();

        Set<String> candidateWords = new LinkedHashSet<>();
        Matcher matcher = WORD.matcher(plain);
        while (matcher.find() && candidateWords.size() < 120) {
            candidateWords.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        if (!candidateWords.isEmpty()) {
            List<ECDICTWord> entries = new ArrayList<>(dictionaryMapper.selectList(new QueryWrapper<ECDICTWord>()
                    .in("word", candidateWords).isNotNull("translation")
                    .select("word", "translation", "tag")));
            entries.sort(Comparator.comparingInt((ECDICTWord word) -> priority(word, level)).reversed()
                    .thenComparing(ECDICTWord::getWord, String.CASE_INSENSITIVE_ORDER));
            for (ECDICTWord entry : entries) {
                if (result.size() >= 2 || priority(entry, level) <= 0) break;
                if (entry.getTranslation() == null || !entry.getTranslation().matches("(?s).*?[\\u4e00-\\u9fa5].*")) continue;
                String sentence = sentenceContaining(sentences, entry.getWord());
                if (sentence == null) continue;
                KnowledgePoint point = new KnowledgePoint();
                point.setType("VOCABULARY");
                point.setExpression(entry.getWord());
                point.setExplanation("词典释义：" + shorten(entry.getTranslation().replaceAll("\\s+", " "), 100)
                        + "。结合原句判断这里的具体含义。");
                point.setSourceSentence(sentence);
                point.setPracticeTip("用 “" + entry.getWord() + "” 写一句与你有关的英文句子。");
                result.add(point);
            }
        }

        for (String sentence : sentences) {
            for (GrammarRule rule : GRAMMAR_RULES) {
                if (rule.pattern().matcher(sentence).find()) {
                    KnowledgePoint point = new KnowledgePoint();
                    point.setType("GRAMMAR");
                    point.setExpression(rule.expression());
                    point.setExplanation(rule.explanation());
                    point.setSourceSentence(sentence);
                    point.setPracticeTip("模仿原句，用 “" + rule.expression() + "” 写一个新句子。");
                    result.add(point);
                    return result;
                }
            }
        }
        if (!sentences.isEmpty() && result.size() < 3) {
            String sentence = sentences.stream().max(Comparator.comparingInt(String::length)).orElse(sentences.get(0));
            KnowledgePoint point = new KnowledgePoint();
            point.setType("SENTENCE");
            point.setExpression("句型拆解");
            point.setExplanation("试着先找出主语和谓语，再分析其余短语如何补充信息。");
            point.setSourceSentence(sentence);
            point.setPracticeTip("不看原文，用英文复述这句话的核心意思。");
            result.add(point);
        }
        if (result.isEmpty()) throw new BusinessException("这篇文章暂时没有可核对的知识点");
        return result;
    }

    private int priority(ECDICTWord entry, String level) {
        if (entry.getWord() == null || entry.getTag() == null) return 0;
        String tag = " " + entry.getTag().toLowerCase(Locale.ROOT) + " ";
        boolean cet4 = tag.contains(" cet4 ");
        boolean cet6 = tag.contains(" cet6 ");
        boolean postgraduate = tag.contains(" ky ");
        if ("POSTGRADUATE".equals(level)) return postgraduate ? (cet4 || cet6 ? 1 : 2) : 0;
        if ("CET6".equals(level)) return cet6 ? (cet4 ? 1 : 2) : 0;
        return cet4 ? 1 : 0;
    }

    private List<String> sentenceCandidates(String plain) {
        List<String> sentences = new ArrayList<>();
        for (String candidate : plain.split("(?<=[.!?])\\s+")) {
            String sentence = candidate.trim();
            if (sentence.length() < 45 || sentence.length() > 340) continue;
            if (SENTENCE_WORD.matcher(sentence).results().count() < 8) continue;
            sentences.add(sentence);
            if (sentences.size() >= 80) break;
        }
        return sentences;
    }

    private String sentenceContaining(List<String> sentences, String word) {
        Pattern pattern = Pattern.compile("(?i)\\b" + Pattern.quote(word) + "\\b");
        for (String sentence : sentences) {
            if (pattern.matcher(sentence).find()) return sentence;
        }
        return null;
    }

    private String normalizeArticle(String content) {
        String excerpt = content.length() > 16000 ? content.substring(0, 16000) : content;
        return excerpt.replaceAll("<[^>]+>", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("[*_`#>]", "")
                .replaceAll("\\s+", " ").trim();
    }

    private String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }

    private record GrammarRule(Pattern pattern, String expression, String explanation) { }
}
