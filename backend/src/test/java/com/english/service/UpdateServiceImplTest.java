package com.english.service;

import com.english.mapper.UpdateConfigMapper;
import com.english.service.impl.UpdateServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class UpdateServiceImplTest {

    @Mock
    private UpdateConfigMapper updateConfigMapper;

    @Test
    @DisplayName("Configured unsupported reason takes precedence over other checks")
    void configuredUnsupportedReasonTakesPrecedence() {
        UpdateServiceImpl service = createService(false, "disabled by config", "", "/project");

        assertFalse(service.isSupported());
        assertEquals("disabled by config", service.getUnsupportedReason());
    }

    @Test
    @DisplayName("Missing PROJECT_HOST_PATH is reported as unsupported")
    void missingProjectHostPathIsUnsupported() {
        UpdateServiceImpl service = createService(true, "", "/tmp/fake.sock", "");

        assertFalse(service.isSupported());
        assertTrue(service.getUnsupportedReason().contains("PROJECT_HOST_PATH"));
    }

    @Test
    @DisplayName("Missing Docker socket is reported as unsupported")
    void missingDockerSocketIsUnsupported() {
        UpdateServiceImpl service = createService(true, "", "/tmp/nonexistent.sock", "/project");

        assertFalse(service.isSupported());
        assertTrue(service.getUnsupportedReason().contains("Docker socket not found"));
    }

    @Test
    @DisplayName("All preconditions met — reported as supported")
    void allPreconditionsMetIsSupported(@TempDir Path tempDir) throws IOException {
        Path fakeSocket = tempDir.resolve("docker.sock");
        Files.createFile(fakeSocket);

        UpdateServiceImpl service = createService(true, "", fakeSocket.toString(), "/project");

        assertTrue(service.isSupported());
        assertEquals("", service.getUnsupportedReason());
    }

    private UpdateServiceImpl createService(boolean supported, String reason,
                                            String dockerSocket, String projectHostPath) {
        UpdateServiceImpl service = new UpdateServiceImpl(updateConfigMapper);
        ReflectionTestUtils.setField(service, "hotDeployScript", "scripts/hot-deploy.sh");
        ReflectionTestUtils.setField(service, "updateSupported", supported);
        ReflectionTestUtils.setField(service, "unsupportedReason", reason);
        ReflectionTestUtils.setField(service, "dockerSocket", dockerSocket);
        ReflectionTestUtils.setField(service, "projectHostPath", projectHostPath);
        ReflectionTestUtils.setField(service, "deployerImage", "langhack-deployer:latest");
        return service;
    }
}
