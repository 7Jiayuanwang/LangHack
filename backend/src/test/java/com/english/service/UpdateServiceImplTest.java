package com.english.service;

import com.english.mapper.UpdateConfigMapper;
import com.english.service.impl.UpdateServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class UpdateServiceImplTest {

    @Mock
    private UpdateConfigMapper updateConfigMapper;

    private final String originalOsName = System.getProperty("os.name");
    private final String originalUserDir = System.getProperty("user.dir");

    @AfterEach
    void tearDown() {
        if (originalOsName != null) {
            System.setProperty("os.name", originalOsName);
        }
        if (originalUserDir != null) {
            System.setProperty("user.dir", originalUserDir);
        }
    }

    @Test
    @DisplayName("Self-update is disabled on Windows hosts even when config flag is enabled")
    void windowsHostIsReportedUnsupported() {
        UpdateServiceImpl service = createService("scripts/auto-update.sh", true, "configured unsupported");
        System.setProperty("os.name", "Windows 11");

        assertFalse(service.isSupported());
        assertTrue(service.getUnsupportedReason().contains("Linux deployments"));
    }

    @Test
    @DisplayName("Missing update script is reported as unsupported")
    void missingScriptIsReportedUnsupported() throws Exception {
        UpdateServiceImpl service = createService("scripts/missing-update.sh", true, "configured unsupported");
        Path tempDir = Files.createTempDirectory("update-service-test");
        System.setProperty("os.name", "Linux");
        System.setProperty("user.dir", tempDir.toString());

        assertFalse(service.isSupported());
        assertTrue(service.getUnsupportedReason().contains("Self-update script not found"));
    }

    @Test
    @DisplayName("Configured unsupported reason still takes precedence")
    void configuredUnsupportedReasonTakesPrecedence() {
        UpdateServiceImpl service = createService("scripts/auto-update.sh", false, "disabled by config");

        assertFalse(service.isSupported());
        assertEquals("disabled by config", service.getUnsupportedReason());
    }

    private UpdateServiceImpl createService(String script, boolean supported, String reason) {
        UpdateServiceImpl service = new UpdateServiceImpl(updateConfigMapper);
        ReflectionTestUtils.setField(service, "updateScript", script);
        ReflectionTestUtils.setField(service, "updateSupported", supported);
        ReflectionTestUtils.setField(service, "unsupportedReason", reason);
        return service;
    }
}
