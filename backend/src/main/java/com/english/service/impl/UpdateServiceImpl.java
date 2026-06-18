package com.english.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.english.datasource.UseMaster;
import com.english.entity.UpdateConfig;
import com.english.exception.BusinessException;
import com.english.mapper.UpdateConfigMapper;
import com.english.service.UpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateServiceImpl implements UpdateService {

    private static final Pattern GIT_BRANCH_PATTERN = Pattern.compile("^[a-zA-Z0-9._/-]{1,100}$");
    private static final String DEPLOYER_WORKSPACE = "/project";

    private final UpdateConfigMapper updateConfigMapper;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);

    @Value("${app.update.hot-deploy-script:scripts/hot-deploy.sh}")
    private String hotDeployScript;

    @Value("${app.update.supported:true}")
    private boolean updateSupported;

    @Value("${app.update.unsupported-reason:Self-update is not supported in the current deployment mode.}")
    private String unsupportedReason;

    @Value("${app.update.docker-socket:/var/run/docker.sock}")
    private String dockerSocket;

    @Value("${app.update.project-host-path:${PROJECT_HOST_PATH:}}")
    private String projectHostPath;

    @Value("${app.update.deployer-image:langhack-deployer:latest}")
    private String deployerImage;

    @Value("${app.update.git-token:}")
    private String gitToken;

    /** 宿主机 SSH key 目录（用于 git@github.com SSH remote），挂载到容器 /root/.ssh */
    @Value("${app.update.git-ssh-key-dir:}")
    private String gitSshKeyDir;

    @Value("${app.update.network:langhack_langhack-network}")
    private String updateNetwork;

    /** 与 HotDeployServiceImpl 共用的 .done / .log 目录（容器内挂载点） */
    @Value("${app.hot-deploy.log-dir:/app/hot-deploy-logs}")
    private String hotDeployLogDir;

    private static final String CURRENT_DEPLOY_FILE = ".current-sse-deploy.id";

    private String networkName() {
        return updateNetwork;
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "deploy-worker");
        thread.setDaemon(true);
        return thread;
    });

    @PostConstruct
    void cleanupStaleStatus() {
        try {
            UpdateConfig config = getConfig();
            if (!"RUNNING".equals(config.getLastRunStatus()) || config.getId() == null) {
                return;
            }
            // 后端被 Step 7 杀掉重启时，deployer 可能还在跑。先在后台等 .done 出现，
            // 再修正状态；最多等 120s（覆盖 Step 8/9 的健康检查时间）
            Long configId = config.getId();
            Thread t = new Thread(() -> reconcileAfterRestart(configId), "deploy-reconcile");
            t.setDaemon(true);
            t.start();
        } catch (Exception e) {
            log.debug("Skipping stale status cleanup: {}", e.getMessage());
        }
    }

    private void reconcileAfterRestart(Long configId) {
        long deadline = System.currentTimeMillis() + 120_000L;
        String reconciled = null;
        while (System.currentTimeMillis() < deadline) {
            reconciled = reconcileFromDoneFile();
            if (reconciled != null) break;
            try { Thread.sleep(2000); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        String finalStatus = reconciled != null ? reconciled : "INTERRUPTED";
        log.warn("Stale RUNNING status reconciled to {} (deployId from sentinel)", finalStatus);
        UpdateConfig update = new UpdateConfig();
        update.setId(configId);
        update.setLastRunStatus(finalStatus);
        try {
            updateConfigMapper.updateById(update);
        } catch (Exception e) {
            log.warn("Failed to persist reconciled status: {}", e.getMessage());
        }
    }

    /** 读 sentinel 文件里上次 SSE 部署的 deployId，再读它的 .done 决定真实终态 */
    private String reconcileFromDoneFile() {
        try {
            java.nio.file.Path sentinel = Paths.get(hotDeployLogDir, CURRENT_DEPLOY_FILE);
            if (!Files.exists(sentinel)) return null;
            String deployId = Files.readString(sentinel).trim();
            if (deployId.isEmpty()) return null;
            java.nio.file.Path done = Paths.get(hotDeployLogDir, deployId + ".done");
            if (!Files.exists(done)) return null;
            String body = Files.readString(done);
            java.util.regex.Matcher m = Pattern.compile("\"status\"\\s*:\\s*\"([^\"]*)\"").matcher(body);
            if (m.find()) {
                String s = m.group(1);
                return s.startsWith("FAILED") ? "FAILED" : "SUCCESS";
            }
        } catch (Exception e) {
            log.debug("reconcileFromDoneFile failed: {}", e.getMessage());
        }
        return null;
    }

    /** 把当前 SSE 部署的 deployId 落到固定文件，重启后由 reconcileFromDoneFile 读取 */
    private void writeCurrentDeploySentinel(String deployId) {
        try {
            java.nio.file.Path dir = Paths.get(hotDeployLogDir);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(CURRENT_DEPLOY_FILE), deployId);
        } catch (Exception e) {
            log.warn("Failed to write deploy sentinel: {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("Deploy executor did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @UseMaster
    public UpdateConfig getConfig() {
        UpdateConfig config = updateConfigMapper.selectOne(
                new LambdaQueryWrapper<UpdateConfig>()
                        .orderByAsc(UpdateConfig::getId)
                        .last("LIMIT 1"));
        return config != null ? config : createDefaultConfig();
    }

    @Override
    @UseMaster
    public UpdateConfig saveConfig(UpdateConfig config) {
        if (config == null) {
            throw new BusinessException("Update config is required.");
        }

        String cronExpr = normalizeToNull(config.getCronExpr());
        String gitBranch = normalizeToNull(config.getGitBranch());

        if (Boolean.TRUE.equals(config.getEnabled()) && cronExpr == null) {
            throw new BusinessException("Cron expression is required when scheduled deploy is enabled.");
        }

        if (gitBranch != null && !GIT_BRANCH_PATTERN.matcher(gitBranch).matches()) {
            throw new BusinessException("Invalid git branch name.");
        }

        if (cronExpr != null) {
            try {
                new CronTrigger(cronExpr);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("Invalid cron expression: " + cronExpr);
            }
        }

        UpdateConfig existing = getConfig();
        boolean hasPersistedRecord = existing.getId() != null;

        if (cronExpr != null) {
            existing.setCronExpr(cronExpr);
        }
        if (config.getEnabled() != null) {
            existing.setEnabled(config.getEnabled());
        }
        if (gitBranch != null) {
            existing.setGitBranch(gitBranch);
        }

        if (normalizeToNull(existing.getCronExpr()) == null) {
            existing.setCronExpr(createDefaultConfig().getCronExpr());
        }
        if (normalizeToNull(existing.getGitBranch()) == null) {
            existing.setGitBranch(createDefaultConfig().getGitBranch());
        }
        if (existing.getEnabled() == null) {
            existing.setEnabled(false);
        }
        if (normalizeToNull(existing.getLastRunStatus()) == null) {
            existing.setLastRunStatus("NEVER");
        }

        if (hasPersistedRecord) {
            updateConfigMapper.updateById(existing);
        } else {
            updateConfigMapper.insert(existing);
        }
        return existing;
    }

    @Override
    @UseMaster
    public SseEmitter trigger(SseEmitter emitter, String branch, boolean skipBuild, boolean skipPull, boolean dryRun) {
        ensureSupported();

        if (!isRunning.compareAndSet(false, true)) {
            throw new BusinessException("热部署正在进行中，请等待完成后再试。");
        }

        UpdateConfig config = getConfig();
        try {
            executor.submit(() -> runInteractive(config, emitter, branch, skipBuild, skipPull, dryRun));
        } catch (java.util.concurrent.RejectedExecutionException e) {
            isRunning.set(false);
            log.error("Deploy task submission rejected", e);
            throw new BusinessException("部署服务正在关闭，请稍后重试。");
        }
        return emitter;
    }

    @Override
    @UseMaster
    public void run() {
        if (!isSupported()) {
            log.warn("Skipping hot deploy: {}", getUnsupportedReason());
            return;
        }

        UpdateConfig config = getConfig();
        if (!Boolean.TRUE.equals(config.getEnabled())) {
            return;
        }
        if (!isRunning.compareAndSet(false, true)) {
            log.warn("Skipping scheduled hot deploy because another deploy is running");
            return;
        }

        setRunning(config.getId());
        boolean success = false;
        String deployId = "hd-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String containerName = "langhack-deployer-sched-" + deployId;
        writeCurrentDeploySentinel(deployId);
        try {
            List<String> startCmd = buildDeployerCommand(config, null, false, false, false, containerName, deployId);
            log.info("Scheduled deploy command: {}", startCmd);
            ProcessBuilder startBuilder = new ProcessBuilder(startCmd);
            startBuilder.redirectErrorStream(true);
            Process startProcess = startBuilder.start();
            startProcess.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int startExit = startProcess.waitFor();
            if (startExit != 0) {
                log.error("Failed to start deployer container (exit={})", startExit);
                return;
            }
            String containerId = getContainerIdByName(containerName);
            if (containerId == null || containerId.isBlank()) {
                log.error("Deployer container not found: {}", containerName);
                return;
            }
            log.info("Deployer container started: {} -> {}", containerName, containerId);

            ProcessBuilder waitBuilder = new ProcessBuilder("docker", "wait", containerId);
            Process waitProcess = waitBuilder.start();
            String exitCodeStr;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(waitProcess.getInputStream()))) {
                exitCodeStr = reader.readLine();
            }
            waitProcess.waitFor(600, TimeUnit.SECONDS);
            success = "0".equals(exitCodeStr != null ? exitCodeStr.trim() : "");
            log.info("Scheduled deploy finished, success={}", success);
        } catch (Exception e) {
            log.error("Scheduled deploy failed", e);
        } finally {
            finishRun(config.getId(), success);
            isRunning.set(false);
        }
    }

    @Override
    public boolean isSupported() {
        return resolveUnsupportedReason() == null;
    }

    @Override
    public String getUnsupportedReason() {
        String reason = resolveUnsupportedReason();
        return reason == null ? "" : reason;
    }

    private void runInteractive(UpdateConfig config, SseEmitter emitter,
                                String branch, boolean skipBuild, boolean skipPull, boolean dryRun) {
        setRunning(config.getId());
        boolean success = false;
        boolean knownOutcome = false; // true 表示已经确定 success 真值；false 表示未知（让 reconcile 兜底）
        boolean asyncCompletion = false; // 防止 finally 和异步任务重复调用 finishRun
        String deployId = "hd-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String containerName = "langhack-deployer-" + deployId;
        writeCurrentDeploySentinel(deployId);
        try {
            // Phase 1: Start deployer container (detached — survives backend restart)
            List<String> startCmd = buildDeployerCommand(config, branch, skipBuild, skipPull, dryRun, containerName, deployId);
            log.info("Interactive deploy — starting deployer: {}", startCmd);
            ProcessBuilder startBuilder = new ProcessBuilder(startCmd);
            startBuilder.redirectErrorStream(true);
            Process startProcess = startBuilder.start();
            // 直接排空所有输出，不读管道。用 docker ps 拿容器 ID，彻底避免 SIGPIPE (exit=141)
            startProcess.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            int startExit = startProcess.waitFor();
            if (startExit != 0) {
                emitter.send(SseEmitter.event().name("done")
                        .data("HOT_DEPLOY_FAILED:Failed to start deployer container (exit=" + startExit + ")"));
                emitter.complete();
                knownOutcome = true; // deployer 都没起来，确定失败
                return;
            }
            String containerId = getContainerIdByName(containerName);
            if (containerId == null || containerId.isBlank()) {
                emitter.send(SseEmitter.event().name("done")
                        .data("HOT_DEPLOY_FAILED:Container started but not found: " + containerName));
                emitter.complete();
                knownOutcome = true;
                return;
            }
            log.info("Deployer container started: {} -> {}", containerName, containerId);

            // Phase 2: Stream deployer logs (killing this does NOT kill the deployer)
            ProcessBuilder logsBuilder = new ProcessBuilder("docker", "logs", "-f", containerId);
            logsBuilder.redirectErrorStream(true);
            Process logsProcess = logsBuilder.start();

            boolean clientDisconnected = false;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(logsProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    try {
                        emitter.send(SseEmitter.event().data(line));
                    } catch (Exception ignored) {
                        clientDisconnected = true;
                        logsProcess.destroyForcibly();
                        break;
                    }
                }
            }

            if (!clientDisconnected) {
                ProcessBuilder waitBuilder = new ProcessBuilder("docker", "wait", containerId);
                Process waitProcess = waitBuilder.start();
                String exitCodeStr;
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(waitProcess.getInputStream()))) {
                    exitCodeStr = reader.readLine();
                }
                waitProcess.waitFor(600, TimeUnit.SECONDS);
                success = "0".equals(exitCodeStr != null ? exitCodeStr.trim() : "");
                knownOutcome = true;
                emitter.send(SseEmitter.event().name("done")
                        .data(success ? "HOT_DEPLOY_SUCCESS" : "HOT_DEPLOY_FAILED:" + exitCodeStr));
                emitter.complete();
            } else {
                // SSE 断连（后端重启导致），deployer 容器独立运行不受影响
                // 异步等待 deployer 完成，避免前端误判为失败
                log.info("SSE client disconnected, deployer {} still running in background", containerId);
                asyncCompletion = true;
                executor.submit(() -> {
                    try {
                        ProcessBuilder waitBuilder = new ProcessBuilder("docker", "wait", containerId);
                        Process waitProcess = waitBuilder.start();
                        String exitCodeStr;
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(waitProcess.getInputStream()))) {
                            exitCodeStr = reader.readLine();
                        }
                        waitProcess.waitFor(600, TimeUnit.SECONDS);
                        boolean deployOk = "0".equals(exitCodeStr != null ? exitCodeStr.trim() : "");
                        log.info("Deployer {} finished: success={}", containerId, deployOk);
                        finishRun(config.getId(), deployOk);
                        isRunning.set(false);
                    } catch (Exception e) {
                        log.error("Background deployer wait failed for {}", containerId, e);
                        // 不写 FAILED — 由重启后的 reconcileFromDoneFile 决定真实状态
                        isRunning.set(false);
                    }
                });
                emitter.complete();
            }
        } catch (Exception e) {
            log.error("Interactive deploy failed", e);
            try {
                emitter.send(SseEmitter.event().name("done")
                        .data("HOT_DEPLOY_ERROR:Deployer launch failed. Check server logs."));
                emitter.complete();
            } catch (Exception ignored) { }
            // 异常路径下结果未知，让 reconcile 决定。但如果 deployer 都没起来 (knownOutcome 没机会设)，
            // 这里也保持 RUNNING — 重启时 reconcile 找不到 .done 会兜到 INTERRUPTED
        } finally {
            if (!asyncCompletion) {
                if (knownOutcome) {
                    finishRun(config.getId(), success);
                }
                // !knownOutcome：JVM 被 Step 7 杀掉等场景，保留 RUNNING，重启后由 reconcile 修正
                isRunning.set(false);
            }
        }
    }

    private List<String> buildDeployerCommand(UpdateConfig config, String branch,
                                              boolean skipBuild, boolean skipPull, boolean dryRun,
                                              String containerName, String deployId) {
        String effectiveBranch = (branch != null && !branch.isBlank()) ? branch.trim() : defaultBranch(config);
        if (!GIT_BRANCH_PATTERN.matcher(effectiveBranch).matches()) {
            throw new BusinessException("无效的 git 分支名: " + effectiveBranch);
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-d");
        cmd.add("--name");
        cmd.add(containerName);
        cmd.add("-v");
        cmd.add(dockerSocket + ":/var/run/docker.sock");
        cmd.add("-v");
        cmd.add(projectHostPath + ":" + DEPLOYER_WORKSPACE);
        cmd.add("-w");
        cmd.add(DEPLOYER_WORKSPACE);
        cmd.add("-e");
        cmd.add("HOST_PROJECT_DIR=" + projectHostPath);
        if (gitToken != null && !gitToken.isBlank()) {
            cmd.add("-e");
            cmd.add("GIT_TOKEN=" + gitToken);
        }
        if (gitSshKeyDir != null && !gitSshKeyDir.isBlank()) {
            // 挂载宿主机 SSH key 目录（只读），并禁用 StrictHostKeyChecking 避免首次连接卡住
            cmd.add("-v");
            cmd.add(gitSshKeyDir + ":/root/.ssh:ro");
            cmd.add("-e");
            cmd.add("GIT_SSH_COMMAND=ssh -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null");
        }
        cmd.add("--network");
        cmd.add(networkName());
        cmd.add(deployerImage);
        cmd.add("bash");
        cmd.add(hotDeployScript);
        cmd.add("--branch");
        cmd.add(effectiveBranch);
        if (deployId != null && !deployId.isBlank()) {
            cmd.add("--deploy-id");
            cmd.add(deployId);
        }
        if (skipBuild) cmd.add("--skip-build");
        if (skipPull)  cmd.add("--skip-pull");
        if (dryRun) cmd.add("--dry-run");
        return cmd;
    }

    private void setRunning(Long id) {
        if (id == null) return;
        UpdateConfig update = new UpdateConfig();
        update.setId(id);
        update.setLastRunAt(LocalDateTime.now());
        update.setLastRunStatus("RUNNING");
        updateConfigMapper.updateById(update);
    }

    private void finishRun(Long id, boolean success) {
        if (id == null) return;
        UpdateConfig update = new UpdateConfig();
        update.setId(id);
        update.setLastRunAt(LocalDateTime.now());
        update.setLastRunStatus(success ? "SUCCESS" : "FAILED");
        updateConfigMapper.updateById(update);
    }

    private UpdateConfig createDefaultConfig() {
        UpdateConfig config = new UpdateConfig();
        config.setEnabled(false);
        config.setCronExpr("0 0 2 ? * SUN");
        config.setGitBranch("master");
        config.setLastRunStatus("NEVER");
        return config;
    }

    private void ensureSupported() {
        String reason = resolveUnsupportedReason();
        if (reason != null) {
            throw new BusinessException(reason);
        }
    }

    private String normalizeToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultBranch(UpdateConfig config) {
        String branch = config == null ? null : normalizeToNull(config.getGitBranch());
        return branch != null ? branch : createDefaultConfig().getGitBranch();
    }

    /**
     * 用 docker ps 按容器名精确查找容器 ID，比读 Process stdout 更可靠。
     */
    private String getContainerIdByName(String name) {
        try {
            ProcessBuilder pb = new ProcessBuilder("docker", "ps", "-a", "-q", "--filter", "name=^" + name + "$");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String id = r.readLine();
                while (r.readLine() != null) { } // 排空
                p.waitFor();
                return id != null ? id.trim() : null;
            }
        } catch (Exception e) {
            log.error("getContainerIdByName({}) failed", name, e);
            return null;
        }
    }

    private String resolveUnsupportedReason() {
        if (!updateSupported) {
            return unsupportedReason;
        }
        if (projectHostPath == null || projectHostPath.isBlank()) {
            return "PROJECT_HOST_PATH environment variable is not set. "
                 + "Set it to the project's absolute path on the Docker host.";
        }
        if (!Files.exists(Paths.get(dockerSocket))) {
            return "Docker socket not found at " + dockerSocket
                 + ". Mount /var/run/docker.sock into the backend container.";
        }
        if (!isDeployerImagePresent()) {
            return "Deployer image '" + deployerImage + "' not found. "
                 + "Run: docker compose --profile deploy build deployer";
        }
        return null;
    }

    protected boolean isDeployerImagePresent() {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    "docker", "image", "inspect", "--format", "{{.Id}}", deployerImage);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
            return p.waitFor() == 0;
        } catch (Exception e) {
            log.warn("Could not check deployer image presence: {}", e.getMessage());
            return true; // 无法判断时放行，让 docker run 自然报错
        }
    }
}
