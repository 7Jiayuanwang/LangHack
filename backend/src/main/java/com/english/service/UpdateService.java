package com.english.service;

import com.english.entity.UpdateConfig;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface UpdateService {

    UpdateConfig getConfig();

    UpdateConfig saveConfig(UpdateConfig config);

    SseEmitter trigger(SseEmitter emitter, String branch, boolean skipBuild, boolean dryRun);

    void run();

    boolean isSupported();

    String getUnsupportedReason();
}
