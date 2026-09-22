package com.english.service.articleaudio;

import java.nio.file.Path;

/** Replace this provider when a production article-length TTS service is available. */
public interface ArticleSpeechProvider {
    String cacheIdentity();

    void synthesize(String text, Path wavOutput) throws Exception;

    default void synthesize(String text, String language, Path wavOutput) throws Exception {
        synthesize(text, wavOutput);
    }
}
