package com.english.service.articleaudio;

import com.english.entity.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class ArticleAudioServiceTest {
    @TempDir Path storage;

    @Test
    void generatesOnceAndRegeneratesAfterArticleEdit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ArticleAudioService service = new ArticleAudioService(provider(calls), storage.toString());
        Text article = article("A first paragraph.");

        assertFalse(service.status(article, "original").ready());
        ArticleAudioService.AudioInfo first = service.generate(article, "original");
        assertTrue(first.ready());
        assertEquals("offline", first.source());
        assertEquals(first, service.generate(article, "original"));
        assertEquals(1, calls.get());
        assertNotNull(service.existingFile(article, "original", first.hash()));

        article.setContent("A revised paragraph.");
        assertFalse(service.status(article, "original").ready());
        assertNull(service.existingFile(article, "original", first.hash()));
        assertNotEquals(first.hash(), service.generate(article, "original").hash());
        assertEquals(2, calls.get());
    }

    @Test
    void keepsAudioSeparateForVersionsAndPrefersUploadedOriginal() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ArticleAudioService service = new ArticleAudioService(provider(calls), storage.toString());
        Text article = article("Original content.");
        article.setCet4Content("Simplified content.");
        article.setAudioUrl("/api/media/42/download");

        ArticleAudioService.AudioInfo original = service.generate(article, "original");
        assertEquals("uploaded", original.source());
        assertEquals("/api/media/42/download", original.audioUrl());
        assertEquals(0, calls.get());

        ArticleAudioService.AudioInfo adapted = service.generate(article, "cet4");
        assertEquals("offline", adapted.source());
        assertEquals(1, calls.get());
        assertTrue(adapted.audioUrl().contains("version=cet4"));
        assertNull(service.existingFile(article, "original", adapted.hash()));
    }

    @Test
    void rejectsUnknownVersionAndMissingBody() {
        ArticleAudioService service = new ArticleAudioService(provider(new AtomicInteger()), storage.toString());
        Text article = article("Original content.");
        assertThrows(IllegalArgumentException.class, () -> service.status(article, "../../other"));
        assertThrows(IllegalArgumentException.class, () -> service.status(article, "cet6"));
    }

    private Text article(String content) {
        Text article = new Text();
        article.setId(7L);
        article.setContentType("TEXT");
        article.setContent(content);
        return article;
    }

    private ArticleSpeechProvider provider(AtomicInteger calls) {
        return new ArticleSpeechProvider() {
            @Override public String cacheIdentity() { return "test-provider-v1"; }
            @Override public void synthesize(String text, Path wavOutput) throws Exception {
                calls.incrementAndGet();
                byte[] wav = new byte[44];
                System.arraycopy("RIFF".getBytes(), 0, wav, 0, 4);
                System.arraycopy("WAVE".getBytes(), 0, wav, 8, 4);
                Files.write(wavOutput, wav);
            }
        };
    }
}
