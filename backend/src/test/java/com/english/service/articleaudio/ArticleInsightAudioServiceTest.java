package com.english.service.articleaudio;

import com.english.dto.learner.ArticleLearningInsightDto;
import com.english.dto.learner.ArticleLearningInsightDto.KnowledgePoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArticleInsightAudioServiceTest {
    @TempDir Path storage;

    @Test
    void cachesBilingualPointsAndRejectsStaleAudioAfterAnInsightChange() throws Exception {
        List<String> languages = new ArrayList<>();
        ArticleSpeechProvider provider = new ArticleSpeechProvider() {
            @Override public String cacheIdentity() { return "test-voice-v1"; }
            @Override public void synthesize(String text, Path output) throws Exception {
                synthesize(text, "en", output);
            }
            @Override public void synthesize(String text, String language, Path output) throws Exception {
                languages.add(language);
                AudioFormat format = new AudioFormat(22050, 16, 1, true, false);
                byte[] samples = new byte[2205 * 2];
                try (AudioInputStream audio = new AudioInputStream(
                        new ByteArrayInputStream(samples), format, 2205)) {
                    AudioSystem.write(audio, AudioFileFormat.Type.WAVE, output.toFile());
                }
            }
        };
        ArticleInsightAudioService service = new ArticleInsightAudioService(provider, storage.toString());
        ArticleLearningInsightDto insight = insight();

        assertFalse(service.status(7L, 8L, insight).ready());
        ArticleInsightAudioService.AudioInfo first = service.generate(7L, 8L, insight);
        assertTrue(first.ready());
        assertNotNull(service.existingFile(7L, 8L, insight, first.hash()));
        assertTrue(Files.size(service.existingFile(7L, 8L, insight, first.hash())) > 44);
        int generatedParts = languages.size();
        assertTrue(languages.contains("zh"));
        assertTrue(languages.contains("en"));
        assertEquals(first, service.generate(7L, 8L, insight));
        assertEquals(generatedParts, languages.size());

        insight.getPoints().get(0).setExplanation("更新后的讲解");
        assertNull(service.existingFile(7L, 8L, insight, first.hash()));
        assertNotEquals(first.hash(), service.generate(7L, 8L, insight).hash());
    }

    @Test
    void doesNotGenerateAudioBeforePointsAreReady() {
        ArticleSpeechProvider provider = new ArticleSpeechProvider() {
            @Override public String cacheIdentity() { return "test"; }
            @Override public void synthesize(String text, Path output) { fail("must not synthesize"); }
        };
        ArticleInsightAudioService service = new ArticleInsightAudioService(provider, storage.toString());
        ArticleLearningInsightDto pending = insight();
        pending.setStatus("PENDING");
        assertThrows(IllegalArgumentException.class, () -> service.generate(7L, 8L, pending));
    }

    private ArticleLearningInsightDto insight() {
        KnowledgePoint point = new KnowledgePoint();
        point.setType("VOCABULARY");
        point.setExpression("resilience");
        point.setExplanation("韧性；遇到困难后继续努力。");
        point.setSourceSentence("Resilience helps us learn from setbacks.");
        point.setPracticeTip("试着用这个词描述自己的学习经历。");
        ArticleLearningInsightDto insight = new ArticleLearningInsightDto();
        insight.setStatus("READY");
        insight.setTargetLevel("CET6");
        insight.setPoints(List.of(point));
        return insight;
    }
}
