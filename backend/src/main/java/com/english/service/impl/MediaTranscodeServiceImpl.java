package com.english.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.english.entity.Media;
import com.english.mapper.MediaMapper;
import com.english.service.MediaTranscodeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MediaTranscodeServiceImpl implements MediaTranscodeService {

    private static final Logger logger = LoggerFactory.getLogger(MediaTranscodeServiceImpl.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int STDERR_BUFFER_LIMIT = 4096;
    private static final int ERROR_FIELD_LIMIT = 500;

    @Autowired
    private MediaMapper mediaMapper;

    @Value("${media.transcode.enabled:true}")
    private boolean enabled;

    @Value("${media.transcode.ffmpeg-path:ffmpeg}")
    private String ffmpegPath;

    @Value("${media.transcode.ffprobe-path:ffprobe}")
    private String ffprobePath;

    @Value("${media.transcode.target-resolution-height:1080}")
    private int targetHeight;

    @Value("${media.transcode.preset:medium}")
    private String preset;

    @Value("${media.transcode.crf:23}")
    private int crf;

    @Value("${media.transcode.timeout-minutes:30}")
    private int timeoutMinutes;

    @PostConstruct
    public void recoverInterruptedJobs() {
        try {
            Media patch = new Media();
            patch.setTranscodeStatus("FAILED");
            patch.setTranscodeError("interrupted by restart");
            int updated = mediaMapper.update(patch,
                    Wrappers.<Media>lambdaUpdate().eq(Media::getTranscodeStatus, "PROCESSING"));
            if (updated > 0) {
                logger.warn("Reset {} stale PROCESSING transcode rows to FAILED on startup", updated);
            }
        } catch (Exception e) {
            logger.warn("Failed to reset stale PROCESSING transcode rows on startup", e);
        }
    }

    @Override
    public ProbeResult probe(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        List<String> argv = Arrays.asList(
                ffprobePath,
                "-v", "error",
                "-print_format", "json",
                "-show_streams",
                "-show_format",
                file.getAbsolutePath()
        );
        try {
            ProcessBuilder pb = new ProcessBuilder(argv).redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (out.length() < 1_000_000) {
                        out.append(line).append('\n');
                    }
                }
            }
            if (!p.waitFor(60, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                logger.warn("ffprobe timed out for {}", file.getAbsolutePath());
                return null;
            }
            if (p.exitValue() != 0) {
                logger.warn("ffprobe exited {} for {}", p.exitValue(), file.getAbsolutePath());
                return null;
            }
            JsonNode root = MAPPER.readTree(out.toString());
            JsonNode streams = root.path("streams");
            String videoCodec = null;
            String pixFmt = null;
            int width = 0;
            int height = 0;
            if (streams.isArray()) {
                for (JsonNode s : streams) {
                    if ("video".equals(s.path("codec_type").asText())) {
                        videoCodec = s.path("codec_name").asText(null);
                        pixFmt = s.path("pix_fmt").asText(null);
                        width = s.path("width").asInt(0);
                        height = s.path("height").asInt(0);
                        break;
                    }
                }
            }
            String formatName = root.path("format").path("format_name").asText("");
            return new ProbeResult(videoCodec, pixFmt, formatName, width, height);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            logger.warn("ffprobe failed for {}: {}", file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    @Override
    public boolean needsTranscode(ProbeResult probe) {
        if (probe == null) {
            return false;
        }
        if (probe.videoCodec == null || !"h264".equalsIgnoreCase(probe.videoCodec)) {
            return true;
        }
        if (probe.pixelFormat == null || !"yuv420p".equals(probe.pixelFormat)) {
            return true;
        }
        if (probe.containerFormatName == null || !probe.containerFormatName.contains("mp4")) {
            return true;
        }
        if (probe.height > targetHeight) {
            return true;
        }
        return false;
    }

    @Override
    @Async("transcodeExecutor")
    public void transcodeAsync(Long mediaId) {
        if (!enabled) {
            logger.info("Transcode disabled, skipping mediaId={}", mediaId);
            return;
        }
        Media media = mediaMapper.selectById(mediaId);
        if (media == null) {
            logger.warn("transcodeAsync: media not found, id={}", mediaId);
            return;
        }
        String inputPath = media.getFilePath();
        if (inputPath == null || inputPath.isBlank()) {
            logger.warn("transcodeAsync: empty filePath, id={}", mediaId);
            return;
        }

        // CAS: only proceed when current status is PENDING
        Media cas = new Media();
        cas.setTranscodeStatus("PROCESSING");
        cas.setTranscodeError(null);
        int locked = mediaMapper.update(cas,
                Wrappers.<Media>lambdaUpdate()
                        .eq(Media::getId, mediaId)
                        .eq(Media::getTranscodeStatus, "PENDING"));
        if (locked == 0) {
            logger.info("transcodeAsync: row not in PENDING, skip mediaId={}", mediaId);
            return;
        }

        File input = new File(inputPath);
        if (!input.isFile()) {
            markFailed(mediaId, "input file missing: " + inputPath);
            return;
        }

        Path inputAbs = input.toPath().toAbsolutePath().normalize();
        String dir = inputAbs.getParent().toString();
        String baseName = stripExt(inputAbs.getFileName().toString());
        Path tmpOut = Paths.get(dir, baseName + ".transcoding.mp4");
        Path finalOut = Paths.get(dir, baseName + ".mp4");

        // Defense-in-depth: paths must stay under storage root (best-effort sanity)
        if (!inputAbs.toString().contains(Paths.get(dir).toString())) {
            markFailed(mediaId, "input path outside expected directory");
            return;
        }

        try {
            Files.deleteIfExists(tmpOut);
        } catch (IOException ignored) {
        }

        List<String> argv = buildFfmpegArgv(input.getAbsolutePath(), tmpOut.toString());
        StringBuilder stderrBuf = new StringBuilder();
        Process process = null;
        Thread drain = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(argv)
                    .redirectErrorStream(false)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD);
            process = pb.start();
            final Process p = process;
            drain = new Thread(() -> drainStderr(p, stderrBuf), "transcode-stderr-" + mediaId);
            drain.setDaemon(true);
            drain.start();

            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                markFailed(mediaId, "transcode exceeded " + timeoutMinutes + " minute timeout");
                Files.deleteIfExists(tmpOut);
                return;
            }
            int exit = process.exitValue();
            if (exit != 0) {
                markFailed(mediaId, "ffmpeg exit " + exit + ": " + tail(stderrBuf, ERROR_FIELD_LIMIT));
                Files.deleteIfExists(tmpOut);
                return;
            }
            if (!Files.exists(tmpOut) || Files.size(tmpOut) == 0) {
                markFailed(mediaId, "ffmpeg produced no output");
                Files.deleteIfExists(tmpOut);
                return;
            }

            // Atomic swap: delete original (which may share final path), then move tmp → final
            if (inputAbs.equals(finalOut.toAbsolutePath().normalize())) {
                Files.deleteIfExists(inputAbs);
            }
            try {
                Files.move(tmpOut, finalOut, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Files.move(tmpOut, finalOut, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!inputAbs.equals(finalOut.toAbsolutePath().normalize())) {
                try {
                    Files.deleteIfExists(inputAbs);
                } catch (IOException e) {
                    logger.warn("Failed to delete original file {}: {}", inputAbs, e.getMessage());
                }
            }

            markSuccess(mediaId, finalOut.toString(), Files.size(finalOut), media.getFileName());
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            String msg = e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" : e.getMessage());
            String tail = tail(stderrBuf, ERROR_FIELD_LIMIT - msg.length() - 4);
            markFailed(mediaId, tail.isEmpty() ? msg : msg + " | " + tail);
            try {
                Files.deleteIfExists(tmpOut);
            } catch (IOException ignored) {
            }
        } finally {
            if (drain != null && drain.isAlive()) {
                drain.interrupt();
            }
        }
    }

    @Override
    public void retry(Long mediaId) {
        Media media = mediaMapper.selectById(mediaId);
        if (media == null) {
            return;
        }
        if (!"VIDEO".equals(media.getMediaType())) {
            return;
        }
        if ("PROCESSING".equals(media.getTranscodeStatus())) {
            return;
        }
        Media patch = new Media();
        patch.setTranscodeStatus("PENDING");
        patch.setTranscodeError(null);
        mediaMapper.update(patch, Wrappers.<Media>lambdaUpdate().eq(Media::getId, mediaId));
        transcodeAsync(mediaId);
    }

    private List<String> buildFfmpegArgv(String inputPath, String outputPath) {
        int targetW = (int) Math.round(targetHeight * 16.0 / 9.0);
        if (targetW % 2 != 0) targetW++;
        String vf = String.format(
                "scale='min(%d,iw)':'min(%d,ih)':force_original_aspect_ratio=decrease,scale=trunc(iw/2)*2:trunc(ih/2)*2",
                targetW, targetHeight);
        List<String> argv = new ArrayList<>();
        argv.add(ffmpegPath);
        argv.add("-y");
        argv.add("-hide_banner");
        argv.add("-loglevel"); argv.add("error");
        argv.add("-i"); argv.add(inputPath);
        argv.add("-map"); argv.add("0:v:0");
        argv.add("-map"); argv.add("0:a:0?");
        argv.add("-c:v"); argv.add("libx264");
        argv.add("-preset"); argv.add(preset);
        argv.add("-crf"); argv.add(String.valueOf(crf));
        argv.add("-pix_fmt"); argv.add("yuv420p");
        argv.add("-profile:v"); argv.add("high");
        argv.add("-level"); argv.add("4.0");
        argv.add("-vf"); argv.add(vf);
        argv.add("-c:a"); argv.add("aac");
        argv.add("-b:a"); argv.add("128k");
        argv.add("-ac"); argv.add("2");
        argv.add("-movflags"); argv.add("+faststart");
        argv.add("-max_muxing_queue_size"); argv.add("1024");
        argv.add(outputPath);
        return argv;
    }

    private void drainStderr(Process p, StringBuilder buf) {
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                synchronized (buf) {
                    buf.append(line).append('\n');
                    int over = buf.length() - STDERR_BUFFER_LIMIT;
                    if (over > 0) {
                        buf.delete(0, over);
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }

    private String tail(StringBuilder sb, int limit) {
        if (limit <= 0) return "";
        synchronized (sb) {
            if (sb.length() <= limit) return sb.toString().trim();
            return sb.substring(sb.length() - limit).trim();
        }
    }

    private static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    @Transactional
    @CacheEvict(value = "media", allEntries = true)
    public void markSuccess(Long mediaId, String filePath, long fileSize, String originalFileName) {
        Media patch = new Media();
        patch.setId(mediaId);
        patch.setFilePath(filePath);
        patch.setFileSize(fileSize);
        patch.setMimeType("video/mp4");
        patch.setTranscodeStatus("SUCCESS");
        patch.setTranscodeError(null);
        if (originalFileName != null) {
            patch.setFileName(stripExt(originalFileName) + ".mp4");
        }
        mediaMapper.updateById(patch);
        logger.info("Transcode success mediaId={} -> {}", mediaId, filePath);
    }

    @Transactional
    @CacheEvict(value = "media", allEntries = true)
    public void markFailed(Long mediaId, String error) {
        if (error != null && error.length() > ERROR_FIELD_LIMIT) {
            error = error.substring(0, ERROR_FIELD_LIMIT);
        }
        Media patch = new Media();
        patch.setId(mediaId);
        patch.setTranscodeStatus("FAILED");
        patch.setTranscodeError(error);
        mediaMapper.updateById(patch);
        logger.warn("Transcode failed mediaId={}: {}", mediaId, error);
    }
}
