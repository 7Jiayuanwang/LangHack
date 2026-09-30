package com.english.service.articleaudio;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Temporary, quota-free provider. Linux containers use eSpeak NG; local macOS
 * development uses the system's say command. Neither calls the metered TTS API.
 */
@Service
public class OfflineArticleSpeechProvider implements ArticleSpeechProvider {
    private static final long TIMEOUT_SECONDS = 180;

    @Override
    public String cacheIdentity() {
        return "offline-article-en-v1";
    }

    @Override
    public void synthesize(String text, Path wavOutput) throws Exception {
        synthesize(text, "en", wavOutput);
    }

    @Override
    public void synthesize(String text, String language, Path wavOutput) throws Exception {
        boolean chinese = "zh".equals(language);
        Path input = Files.createTempFile(wavOutput.getParent(), "article-speech-", ".txt");
        Path aiff = null;
        Path errors = Files.createTempFile(wavOutput.getParent(), "article-speech-", ".log");
        try {
            Files.writeString(input, text, StandardCharsets.UTF_8);
            Files.deleteIfExists(wavOutput);
            if (System.getProperty("os.name", "").toLowerCase().contains("mac")) {
                aiff = Files.createTempFile(wavOutput.getParent(), "article-speech-", ".aiff");
                Files.deleteIfExists(aiff);
                run(List.of("say", "-v", chinese ? "Tingting" : "Samantha",
                        "-f", input.toString(), "-o", aiff.toString()), errors);
                run(List.of("afconvert", "-f", "WAVE", "-d", "LEI16@22050",
                        aiff.toString(), wavOutput.toString()), errors);
            } else {
                run(List.of("espeak-ng", "-v", chinese ? "cmn" : "en-us", "-s", "155", "-w", wavOutput.toString(),
                        "-f", input.toString()), errors);
            }
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(errors);
            if (aiff != null) Files.deleteIfExists(aiff);
        }
    }

    private void run(List<String> command, Path errors) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(errors.toFile())
                .start();
        if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("离线文章朗读生成超时");
        }
        if (process.exitValue() != 0) {
            String detail = Files.readString(errors, StandardCharsets.UTF_8);
            throw new IOException("离线文章朗读失败: " + detail.substring(0, Math.min(300, detail.length())));
        }
    }
}
