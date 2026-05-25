package com.english.service.impl;

import com.english.dto.HotDeployRequest;
import com.english.service.HotDeployService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class HotDeployServiceImpl implements HotDeployService {

    /** 与 docker-compose.yml 中 backend 服务挂载点一致 */
    @Value("${app.hot-deploy.log-dir:/app/hot-deploy-logs}")
    private String logDirInContainer;

    /** 宿主机上的项目根路径，由 compose env HOT_DEPLOY_HOST_PROJECT 注入 */
    @Value("${app.hot-deploy.host-project:${HOT_DEPLOY_HOST_PROJECT:}}")
    private String hostProjectPath;

    /** deployer 容器使用的镜像（自带 docker-cli + bash + git） */
    @Value("${app.hot-deploy.deployer-image:docker:24-cli}")
    private String deployerImage;

    /** docker-compose 创建的网络名（与 deploy.sh / hot-deploy.sh 中 NETWORK_NAME 一致） */
    @Value("${app.hot-deploy.network:langhack_langhack-network}")
    private String networkName;

    /** 读宿主机 docker socket 的容器内路径 */
    private static final String DOCKER_SOCK = "/var/run/docker.sock";

    private static final Pattern STEP_PATTERN = Pattern.compile("══ Step (\\d+):");

    private String unsupportedReason;

    @PostConstruct
    public void init() {
        unsupportedReason = resolveUnsupportedReason();
        if (unsupportedReason != null) {
            log.warn("HotDeploy disabled: {}", unsupportedReason);
        } else {
            log.info("HotDeploy enabled. host-project={}, log-dir={}", hostProjectPath, logDirInContainer);
            try {
                Files.createDirectories(Paths.get(logDirInContainer));
            } catch (IOException e) {
                log.warn("Failed to create hot-deploy log dir {}: {}", logDirInContainer, e.getMessage());
            }
        }
    }

    private String resolveUnsupportedReason() {
        if (!new File(DOCKER_SOCK).exists()) {
            return "未挂载 /var/run/docker.sock，后端无法启动 deployer 容器";
        }
        if (hostProjectPath == null || hostProjectPath.isBlank()) {
            return "未配置 HOT_DEPLOY_HOST_PROJECT 环境变量（宿主机项目根路径）";
        }
        if (logDirInContainer == null || logDirInContainer.isBlank()) {
            return "未配置 hot-deploy 日志目录";
        }
        return null;
    }

    @Override
    public boolean isSupported() {
        return unsupportedReason == null;
    }

    @Override
    public String getUnsupportedReason() {
        return unsupportedReason == null ? "" : unsupportedReason;
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("supported", isSupported());
        data.put("unsupportedReason", getUnsupportedReason());
        data.put("blueHealthy", isContainerHealthy("langhack-backend"));
        data.put("greenHealthy", isContainerHealthy("langhack-backend-green"));
        data.put("currentCommit", readCurrentCommit());
        data.put("lastDeployTime", readLastDeployTime());
        return data;
    }

    private boolean isContainerHealthy(String name) {
        // 直接查 docker inspect。容器不存在或不健康都返回 false。
        // green 容器在常规状态下并不存在，这是预期的。
        try {
            String out = exec(List.of(
                    "docker", "inspect", "-f",
                    "{{.State.Health.Status}}", name), 5);
            if (out == null) return false;
            return out.trim().equals("healthy");
        } catch (Exception e) {
            return false;
        }
    }

    private String readCurrentCommit() {
        // backend 容器没有挂源码（只有 jar），改用宿主机 git 命令通过临时 deployer 容器获取，
        // 成本太高；这里直接读已有的 hot-deploy 日志末次记录或 docker inspect 的 image label。
        // 简化实现：从最近一次 done 标记里提取
        try {
            File dir = new File(logDirInContainer);
            File[] dones = dir.listFiles((d, n) -> n.endsWith(".done"));
            if (dones == null || dones.length == 0) return "";
            File latest = dones[0];
            for (File f : dones) {
                if (f.lastModified() > latest.lastModified()) latest = f;
            }
            String body = Files.readString(latest.toPath(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"commit\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
            // best-effort
        }
        return "";
    }

    private String readLastDeployTime() {
        try {
            File dir = new File(logDirInContainer);
            File[] dones = dir.listFiles((d, n) -> n.endsWith(".done"));
            if (dones == null || dones.length == 0) return "";
            File latest = dones[0];
            for (File f : dones) {
                if (f.lastModified() > latest.lastModified()) latest = f;
            }
            String body = Files.readString(latest.toPath(), StandardCharsets.UTF_8);
            Matcher m = Pattern.compile("\"finishedAt\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
            // best-effort
        }
        return "";
    }

    @Override
    public String trigger(HotDeployRequest req) {
        if (!isSupported()) {
            return null;
        }

        String deployId = "hd-" + UUID.randomUUID().toString().substring(0, 8);
        String containerName = "langhack-deployer-" + deployId;
        Path logFile = Paths.get(logDirInContainer, deployId + ".log");

        try {
            // 写一行启动头，前端从第 0 行能立刻看到反馈
            Files.writeString(logFile,
                    String.format("[%s] HotDeploy 触发，deployId=%s%n", nowIso(), deployId),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.error("Failed to create deploy log file {}", logFile, e);
            return null;
        }

        List<String> dockerArgs = buildDeployerCommand(req, deployId, containerName);
        try {
            String cid = exec(dockerArgs, 30);
            if (cid == null || cid.isBlank()) {
                appendLog(logFile, "[ERROR] docker run 未返回容器 ID，触发失败");
                writeFailedDoneMarker(deployId, "DOCKER_RUN_FAILED");
                return deployId;
            }
            log.info("HotDeploy deployer started: id={} container={} cid={}", deployId, containerName, cid.trim());
        } catch (Exception e) {
            log.error("Failed to start deployer container", e);
            appendLog(logFile, "[ERROR] " + e.getMessage());
            writeFailedDoneMarker(deployId, "EXEC_ERROR");
            return deployId;
        }

        return deployId;
    }

    private List<String> buildDeployerCommand(HotDeployRequest req, String deployId, String containerName) {
        // docker run -d --rm --name langhack-deployer-<id>
        //   --network langhack-network
        //   -v /var/run/docker.sock:/var/run/docker.sock
        //   -v <HOST_PROJECT>:/workspace
        //   -w /workspace
        //   --entrypoint sh
        //   docker:24-cli
        //   -c "apk add --no-cache bash git curl >/dev/null 2>&1 && bash scripts/hot-deploy.sh --deploy-id <id> ..."
        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("-d");
        cmd.add("--rm");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("--network");
        cmd.add(networkName);
        cmd.add("-v");
        cmd.add(DOCKER_SOCK + ":" + DOCKER_SOCK);
        cmd.add("-v");
        cmd.add(hostProjectPath + ":/workspace");
        cmd.add("-w");
        cmd.add("/workspace");
        cmd.add("--entrypoint");
        cmd.add("sh");
        cmd.add(deployerImage);
        cmd.add("-c");
        cmd.add(buildScriptInvocation(req, deployId));
        return cmd;
    }

    private String buildScriptInvocation(HotDeployRequest req, String deployId) {
        StringBuilder sb = new StringBuilder();
        // alpine docker:cli 基镜像缺 bash/git/curl，先补齐再跑脚本。日志重定向到挂载目录里的 .log。
        sb.append("apk add --no-cache bash git curl docker-cli-compose >/tmp/apk.log 2>&1; ");
        sb.append("bash /workspace/scripts/hot-deploy.sh");
        sb.append(" --deploy-id ").append(shellEscape(deployId));
        if (req != null) {
            if (req.getBranch() != null && !req.getBranch().isBlank()) {
                sb.append(" --branch ").append(shellEscape(req.getBranch()));
            }
            if (Boolean.TRUE.equals(req.getSkipBuild())) sb.append(" --skip-build");
            if (Boolean.TRUE.equals(req.getSkipPull()))  sb.append(" --skip-pull");
            if (Boolean.TRUE.equals(req.getDryRun()))    sb.append(" --dry-run");
            if (req.getRemote() != null && !req.getRemote().isBlank()) {
                sb.append(" --remote ").append(shellEscape(req.getRemote()));
            }
        }
        // tee 到挂载日志目录（容器内 /workspace/logs/hot-deploy/<id>.log
        // 与 backend 容器中 /app/hot-deploy-logs/<id>.log 对应同一个宿主机目录）
        sb.append(" >> /workspace/logs/hot-deploy/").append(deployId).append(".log 2>&1");
        return sb.toString();
    }

    /**
     * 拉取实时日志 + 当前 step 索引 + 状态。
     * fromLine: 客户端已收到的最大行号（0 起算），仅返回更新部分，避免重复推送。
     */
    @Override
    public Map<String, Object> getLogs(String deployId, int fromLine) {
        Map<String, Object> data = new HashMap<>();
        data.put("deployId", deployId);

        Path logFile = Paths.get(logDirInContainer, deployId + ".log");
        Path doneFile = Paths.get(logDirInContainer, deployId + ".done");

        List<String> all;
        try {
            all = Files.exists(logFile) ? Files.readAllLines(logFile, StandardCharsets.UTF_8) : Collections.emptyList();
        } catch (IOException e) {
            log.warn("Failed to read deploy log {}: {}", logFile, e.getMessage());
            all = Collections.emptyList();
        }

        int total = all.size();
        int from = Math.max(0, Math.min(fromLine, total));
        List<String> tail = total > from ? new ArrayList<>(all.subList(from, total)) : Collections.emptyList();

        // 解析当前 step（从全量里查最后一个 ══ Step N: 标记）
        int currentStep = 0;
        for (int i = total - 1; i >= 0; i--) {
            Matcher m = STEP_PATTERN.matcher(all.get(i));
            if (m.find()) {
                try {
                    currentStep = Integer.parseInt(m.group(1));
                } catch (NumberFormatException ignored) {
                    // ignore
                }
                break;
            }
        }

        String status = "RUNNING";
        if (Files.exists(doneFile)) {
            try {
                String body = Files.readString(doneFile, StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
                if (m.find()) {
                    status = m.group(1);
                } else {
                    status = "SUCCESS";
                }
            } catch (IOException e) {
                status = "SUCCESS"; // 读失败但 done 存在，按成功处理
            }
        }

        data.put("lines", tail);
        data.put("totalLines", total);
        data.put("currentStep", currentStep);
        data.put("status", status);
        return data;
    }

    private void appendLog(Path logFile, String line) {
        try {
            Files.writeString(logFile,
                    line + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private void writeFailedDoneMarker(String deployId, String reason) {
        Path done = Paths.get(logDirInContainer, deployId + ".done");
        String json = String.format(
                "{\"deployId\":\"%s\",\"status\":\"FAILED:%s\",\"finishedAt\":\"%s\"}",
                deployId, reason, nowIso());
        try {
            Files.writeString(done, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static String nowIso() {
        return java.time.OffsetDateTime.now().toString();
    }

    private static String shellEscape(String s) {
        if (s == null) return "''";
        // 用单引号包起来，里面的单引号转 '\''
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * 同步执行命令，返回 stdout（合并 stderr）。timeoutSec 内必须返回，否则强制销毁。
     */
    private static String exec(List<String> cmd, int timeoutSec) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process proc = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new IOException("命令执行超时(" + timeoutSec + "s): " + String.join(" ", cmd));
        }
        if (proc.exitValue() != 0) {
            throw new IOException("命令退出码 " + proc.exitValue() + ": " + out.toString().trim());
        }
        return out.toString().trim();
    }
}
