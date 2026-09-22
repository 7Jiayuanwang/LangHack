package com.english.service.articleaudio;

import com.english.dto.learner.ArticleLearningInsightDto;
import com.english.dto.learner.ArticleLearningInsightDto.KnowledgePoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class OfflineArticleSpeechProviderTest {
    @TempDir Path directory;

    @Test
    void createsPlayableWavWhenLocalEngineIsInstalled() throws Exception {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        assumeTrue(mac || Files.isExecutable(Path.of("/usr/bin/espeak-ng")));
        Path output = directory.resolve("article.wav");
        new OfflineArticleSpeechProvider().synthesize("Reading English opens new doors.", output);
        byte[] bytes = Files.readAllBytes(output);
        assertTrue(bytes.length > 44);
        assertTrue(new String(bytes, 0, 4).equals("RIFF"));
        assertTrue(new String(bytes, 8, 4).equals("WAVE"));
    }

    @Test
    void createsChineseExplanationAudioWhenLocalEngineIsInstalled() throws Exception {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        assumeTrue(mac || Files.isExecutable(Path.of("/usr/bin/espeak-ng")));
        Path output = directory.resolve("explanation.wav");
        new OfflineArticleSpeechProvider().synthesize("这是文章里的一个知识点。", "zh", output);
        byte[] bytes = Files.readAllBytes(output);
        assertTrue(bytes.length > 44);
        assertTrue(new String(bytes, 0, 4).equals("RIFF"));
        assertTrue(new String(bytes, 8, 4).equals("WAVE"));
    }

    @Test
    void combinesRealEnglishAndChineseSpeechIntoOneKnowledgeAudioFile() throws Exception {
        boolean mac = System.getProperty("os.name", "").toLowerCase().contains("mac");
        assumeTrue(mac || Files.isExecutable(Path.of("/usr/bin/espeak-ng")));
        KnowledgePoint point = new KnowledgePoint();
        point.setType("VOCABULARY");
        point.setExpression("resilience");
        point.setExplanation("意思是韧性。");
        point.setSourceSentence("Resilience helps us grow.");
        ArticleLearningInsightDto insight = new ArticleLearningInsightDto();
        insight.setStatus("READY");
        insight.setPoints(List.of(point));
        ArticleInsightAudioService service = new ArticleInsightAudioService(
                new OfflineArticleSpeechProvider(), directory.toString());
        ArticleInsightAudioService.AudioInfo audio = service.generate(7L, 8L, insight);
        Path file = service.existingFile(7L, 8L, insight, audio.hash());
        assertTrue(Files.size(file) > 44);
        try (var stream = javax.sound.sampled.AudioSystem.getAudioInputStream(file.toFile())) {
            assertTrue(stream.getFrameLength() > 22050);
        }
    }
}
