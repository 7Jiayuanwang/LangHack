package com.english.service;

import com.english.entity.UpdateConfig;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface UpdateService {

    UpdateConfig getConfig();

    UpdateConfig saveConfig(UpdateConfig config);

    SseEmitter triggerUpdate(SseEmitter emitter);

    void runUpdate();

    boolean isSupported();

    String getUnsupportedReason();
}
