package com.english.service;

import java.io.File;

public interface MediaTranscodeService {

    ProbeResult probe(File file);

    boolean needsTranscode(ProbeResult probe);

    void transcodeAsync(Long mediaId);

    void retry(Long mediaId);

    final class ProbeResult {
        public final String videoCodec;
        public final String pixelFormat;
        public final String containerFormatName;
        public final int width;
        public final int height;

        public ProbeResult(String videoCodec, String pixelFormat, String containerFormatName, int width, int height) {
            this.videoCodec = videoCodec;
            this.pixelFormat = pixelFormat;
            this.containerFormatName = containerFormatName;
            this.width = width;
            this.height = height;
        }
    }
}
