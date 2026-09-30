package com.english.service.articleaudio;

import com.english.dto.learner.ArticleLearningInsightDto;
import com.english.dto.learner.ArticleLearningInsightDto.KnowledgePoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.IOException;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Persist a bilingual spoken summary of the existing Xiaoya knowledge points. */
@Service
public class ArticleInsightAudioService {
    private final ArticleSpeechProvider speechProvider;
    private final Path storageDir;
    private final Map<String, Object> generationLocks = new ConcurrentHashMap<>();

    public ArticleInsightAudioService(ArticleSpeechProvider speechProvider,
                                      @Value("${article-audio.storage-dir:./uploads/article-audio}") String storageDir) {
        this.speechProvider = speechProvider;
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public AudioInfo status(Long userId, Long textId, ArticleLearningInsightDto insight) {
        Script script = script(insight);
        Path file = filePath(userId, textId, script.hash());
        return new AudioInfo(Files.isRegularFile(file), script.hash());
    }

    public AudioInfo generate(Long userId, Long textId, ArticleLearningInsightDto insight) throws Exception {
        Script script = script(insight);
        Path file = filePath(userId, textId, script.hash());
        if (Files.isRegularFile(file)) return new AudioInfo(true, script.hash());
        String key = userId + ":" + textId + ":" + script.hash();
        Object lock = generationLocks.computeIfAbsent(key, ignored -> new Object());
        try {
            synchronized (lock) {
                if (Files.isRegularFile(file)) return new AudioInfo(true, script.hash());
                Files.createDirectories(file.getParent());
                List<Path> segments = new ArrayList<>();
                Path output = Files.createTempFile(file.getParent(), "insight-audio-", ".wav");
                try {
                    for (SpeechPart part : script.parts()) {
                        Path segment = Files.createTempFile(file.getParent(), "insight-part-", ".wav");
                        segments.add(segment);
                        speechProvider.synthesize(part.text(), part.language(), segment);
                    }
                    combineWav(segments, output);
                    if (Files.size(output) < 44) throw new IOException("知识点语音内容为空");
                    try {
                        Files.move(output, file, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(output, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(output);
                    for (Path segment : segments) Files.deleteIfExists(segment);
                }
                return new AudioInfo(true, script.hash());
            }
        } finally {
            generationLocks.remove(key, lock);
        }
    }

    public Path existingFile(Long userId, Long textId, ArticleLearningInsightDto insight, String hash) {
        Script script = script(insight);
        if (hash == null || !script.hash().equals(hash)) return null;
        Path file = filePath(userId, textId, hash);
        return Files.isRegularFile(file) ? file : null;
    }

    private Script script(ArticleLearningInsightDto insight) {
        if (insight == null || !"READY".equals(insight.getStatus())
                || insight.getPoints() == null || insight.getPoints().isEmpty()) {
            throw new IllegalArgumentException("知识点尚未准备好");
        }
        List<SpeechPart> parts = new ArrayList<>();
        String levelName = switch (insight.getTargetLevel() == null ? "" : insight.getTargetLevel()) {
            case "CET4" -> "四级";
            case "CET6" -> "六级";
            case "POSTGRADUATE" -> "考研";
            default -> "英语";
        };
        parts.add(new SpeechPart("小芽为你整理了这篇文章中适合" + levelName + "学习的知识点。", "zh"));
        int index = 0;
        for (KnowledgePoint point : insight.getPoints()) {
            if (point == null) continue;
            index++;
            String type = switch (point.getType() == null ? "" : point.getType()) {
                case "VOCABULARY" -> "词汇";
                case "GRAMMAR" -> "语法";
                case "SENTENCE" -> "句型";
                default -> "英语";
            };
            parts.add(new SpeechPart("第" + index + "个知识点，" + type + "。", "zh"));
            addIfPresent(parts, point.getExpression(), "en");
            addIfPresent(parts, point.getExplanation(), "zh");
            if (point.getSourceSentence() != null && !point.getSourceSentence().isBlank()) {
                parts.add(new SpeechPart("请听文章中的原句。", "zh"));
                addIfPresent(parts, point.getSourceSentence(), "en");
            }
            addIfPresent(parts, point.getPracticeTip(), "zh");
        }
        if (index == 0) throw new IllegalArgumentException("知识点尚未准备好");
        String canonical = speechProvider.cacheIdentity() + "|insights-bilingual-v1|" +
                (insight.getTargetLevel() == null ? "" : insight.getTargetLevel()) + "|" + parts;
        if (canonical.length() > 15_000) throw new IllegalArgumentException("知识点内容过长，无法朗读");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return new Script(parts, HexFormat.of().formatHex(digest));
        } catch (Exception e) {
            throw new IllegalStateException("知识点音频哈希计算失败", e);
        }
    }

    private void addIfPresent(List<SpeechPart> parts, String value, String language) {
        if (value != null && !value.isBlank()) parts.add(new SpeechPart(value.trim(), language));
    }

    private void combineWav(List<Path> segments, Path output) throws Exception {
        List<AudioInputStream> streams = new ArrayList<>();
        try {
            AudioFormat format = null;
            long frames = 0;
            for (Path segment : segments) {
                AudioInputStream stream = AudioSystem.getAudioInputStream(segment.toFile());
                streams.add(stream);
                if (format == null) format = stream.getFormat();
                else if (!format.matches(stream.getFormat())) throw new IOException("知识点音频片段格式不一致");
                frames += stream.getFrameLength();
            }
            if (format == null) throw new IOException("知识点没有可朗读的内容");
            try (AudioInputStream combined = new AudioInputStream(
                    new SequenceInputStream(Collections.enumeration(streams)), format, frames)) {
                AudioSystem.write(combined, AudioFileFormat.Type.WAVE, output.toFile());
            }
        } finally {
            for (AudioInputStream stream : streams) stream.close();
        }
    }

    private Path filePath(Long userId, Long textId, String hash) {
        if (userId == null || userId <= 0 || textId == null || textId <= 0) {
            throw new IllegalArgumentException("文章或用户不存在");
        }
        return storageDir.resolve("insights").resolve(userId.toString()).resolve(textId.toString())
                .resolve(hash + ".wav");
    }

    public record AudioInfo(boolean ready, String hash) { }
    private record Script(List<SpeechPart> parts, String hash) { }
    private record SpeechPart(String text, String language) { }
}
