package com.english.service.articleaudio;

import com.english.entity.Text;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ArticleAudioService {
    private final ArticleSpeechProvider speechProvider;
    private final Path storageDir;
    private final Map<String, Object> generationLocks = new ConcurrentHashMap<>();

    public ArticleAudioService(ArticleSpeechProvider speechProvider,
                               @Value("${article-audio.storage-dir:./uploads/article-audio}") String storageDir) {
        this.speechProvider = speechProvider;
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    public AudioInfo status(Text article, String version) {
        String selectedVersion = version == null || version.isBlank() ? "original" : version;
        if (article != null && "original".equals(selectedVersion) && article.getAudioUrl() != null
                && !article.getAudioUrl().isBlank()) {
            return new AudioInfo(true, "uploaded", article.getAudioUrl(), null);
        }
        Snapshot snapshot = snapshot(article, selectedVersion);
        Path file = filePath(article.getId(), snapshot);
        return new AudioInfo(Files.isRegularFile(file), "offline", fileUrl(article.getId(), snapshot), snapshot.hash);
    }

    public AudioInfo generate(Text article, String version) throws Exception {
        AudioInfo current = status(article, version);
        if (current.ready()) return current;
        Snapshot snapshot = snapshot(article, version);

        String lockKey = article.getId() + ":" + snapshot.version + ":" + snapshot.hash;
        Object lock = generationLocks.computeIfAbsent(lockKey, ignored -> new Object());
        try {
            synchronized (lock) {
                Path file = filePath(article.getId(), snapshot);
                if (Files.isRegularFile(file)) return status(article, snapshot.version);
                Files.createDirectories(file.getParent());
                Path temporary = Files.createTempFile(file.getParent(), "article-audio-", ".wav");
                try {
                    speechProvider.synthesize(snapshot.plainText, temporary);
                    if (Files.size(temporary) < 44 || !isWav(temporary)) {
                        throw new IOException("语音服务没有返回有效的 WAV 音频");
                    }
                    try {
                        Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE);
                    } catch (AtomicMoveNotSupportedException ex) {
                        Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                    }
                } finally {
                    Files.deleteIfExists(temporary);
                }
                return status(article, snapshot.version);
            }
        } finally {
            generationLocks.remove(lockKey, lock);
        }
    }

    public Path existingFile(Text article, String version, String hash) {
        Snapshot snapshot = snapshot(article, version);
        if (hash == null || !snapshot.hash.equals(hash)) return null;
        if ("original".equals(snapshot.version) && article.getAudioUrl() != null
                && !article.getAudioUrl().isBlank()) return null;
        Path file = filePath(article.getId(), snapshot);
        return Files.isRegularFile(file) ? file : null;
    }

    private Snapshot snapshot(Text article, String requestedVersion) {
        if (article == null || article.getId() == null) throw new IllegalArgumentException("文章不存在");
        String version = requestedVersion == null || requestedVersion.isBlank() ? "original" : requestedVersion;
        String content;
        boolean markdown = "MARKDOWN".equalsIgnoreCase(article.getContentType());
        switch (version) {
            case "original": content = markdown ? article.getMarkdownContent() : article.getContent(); break;
            case "cet4": content = markdown ? article.getCet4MarkdownContent() : article.getCet4Content(); break;
            case "cet6": content = markdown ? article.getCet6MarkdownContent() : article.getCet6Content(); break;
            case "academic": content = markdown ? article.getAcademicMarkdownContent() : article.getAcademicContent(); break;
            case "chinese_parallel": content = markdown ? article.getChineseParallelMarkdownContent() : article.getChineseParallelContent(); break;
            default: throw new IllegalArgumentException("不支持的文章版本");
        }
        if (content == null || content.isBlank()) throw new IllegalArgumentException("该版本暂无可朗读的正文");
        String plainText = toSpeechText(content);
        if (plainText.isBlank()) throw new IllegalArgumentException("该版本暂无可朗读的正文");
        if (plainText.length() > 30_000) throw new IllegalArgumentException("文章过长，暂不支持生成音频");
        String hash = sha256(speechProvider.cacheIdentity() + "\n" + version + "\n" + plainText);
        return new Snapshot(version, plainText, hash);
    }

    private String toSpeechText(String source) {
        return source.replaceAll("(?s)```.*?```", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "")
                .replaceAll("[`*_~]", "")
                .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replaceAll("\\s+", " ").trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean isWav(Path path) throws IOException {
        byte[] header = new byte[12];
        try (var input = Files.newInputStream(path)) {
            if (input.read(header) != header.length) return false;
        }
        return new String(header, 0, 4, StandardCharsets.US_ASCII).equals("RIFF")
                && new String(header, 8, 4, StandardCharsets.US_ASCII).equals("WAVE");
    }

    private Path filePath(Long articleId, Snapshot snapshot) {
        return storageDir.resolve(Long.toString(articleId)).resolve(snapshot.version)
                .resolve(snapshot.hash + ".wav");
    }

    private String fileUrl(Long articleId, Snapshot snapshot) {
        return "/api/student/learning/texts/" + articleId + "/audio/file?version="
                + snapshot.version + "&hash=" + snapshot.hash;
    }

    public record AudioInfo(boolean ready, String source, String audioUrl, String hash) { }
    private record Snapshot(String version, String plainText, String hash) { }
}
