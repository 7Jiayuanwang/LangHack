# 部署脚本全面审查报告

**审查日期**: 2026-05-05  
**审查范围**: `scripts/deploy.sh` (1966行) + `scripts/auto-update.sh` (1534行)  
**审查目标**: 确保脚本健壮性、错误处理、边界情况处理、用户输入验证

---

## 执行摘要

两个脚本整体质量**优秀**，已实现：
- ✅ 完善的错误处理（`set -euo pipefail` + trap cleanup）
- ✅ 超时保护（git fetch/pull、Docker build、健康检查）
- ✅ 自动回滚机制（构建失败、健康检查失败）
- ✅ 维护模式 + 数据库备份
- ✅ 离线部署能力（使用本地缓存的远程分支）
- ✅ 详细的诊断日志和恢复提示

**发现问题**: 13个（7个中等优先级，6个低优先级）  
**建议改进**: 8个（增强用户体验和可维护性）

---

## 问题清单

### 🟡 中等优先级（建议修复）

#### 1. **deploy.sh:98-103** - `ask_value` 函数输入清理不完整
**问题**: 使用 `sed` 清理 ANSI 转义序列和控制字符，但未处理 shell 元字符（如 `$`, `` ` ``, `!`）  
**风险**: 用户输入包含 shell 元字符时，可能在后续 `printf -v` 或字符串拼接中触发意外行为  
**影响**: 低（仅影响配置向导交互，不影响已部署系统）  
**建议**:
```bash
# 在 sed 清理后增加 shell 元字符转义
input=$(echo "$input" | LC_ALL=C sed 's/\x1b\[[0-9;]*[A-Za-z]//g; s/[[:cntrl:]]//g' | sed 's/[$`!\\]/\\&/g')
```

#### 2. **deploy.sh:286-304** - `check_port` 函数端口范围验证后未重新检查
**问题**: 端口号范围验证（1-65535）在 `check_port` 中执行，但 `ask_port` 调用后未再次验证用户输入  
**风险**: 用户在"换一个"提示时输入非法端口号（如 `99999`），会绕过验证  
**影响**: 低（Docker Compose 启动时会报错，但用户体验差）  
**建议**:
```bash
# ask_port 函数中增加循环验证
while true; do
    ask_value "$prompt (换一个)" "$suggest" "$var_name"
    port="${!var_name}"
    if [[ "$port" =~ ^[0-9]+$ ]] && [ "$port" -ge 1 ] && [ "$port" -le 65535 ]; then
        break
    fi
    echo "  [ERROR] 端口号必须在 1-65535 范围内"
done
```

#### 3. **deploy.sh:540-546** - HiAgent 凭证校验逻辑不完整
**问题**: 只检查 API Key 相同，未检查 App ID 为空的情况  
**风险**: 用户输入 API Key 但未输入 App ID，导致运行时调用失败  
**影响**: 中（功能静默失败，用户难以排查）  
**建议**:
```bash
if [ -n "$CFG_HIAGENT_QA_KEY" ]; then
    ask_value "HiAgent 问答 App ID" "$(load_env_value "HIAGENT_QA_APP_ID" "")" CFG_HIAGENT_QA_APP_ID
    # 新增：App ID 必填校验
    while [ -z "$CFG_HIAGENT_QA_APP_ID" ]; do
        echo "  [ERROR] HiAgent 问答 App ID 不能为空"
        ask_value "HiAgent 问答 App ID" "" CFG_HIAGENT_QA_APP_ID
    done
    # 原有校验...
fi
```

#### 4. **deploy.sh:1436-1453** - Flyway 版本同步检查逻辑不严谨
**问题**: 只检查 `flyway_count > merged_count`，未检查 `<` 的情况（合并脚本包含已删除的 Flyway 文件）  
**风险**: 删除旧 Flyway 文件后，合并脚本未同步更新，导致版本不一致但未被检测  
**影响**: 低（实际场景中 Flyway 文件只增不减）  
**建议**:
```bash
if [ "$flyway_count" -ne "$merged_count" ]; then
    echo "[ERROR] 迁移脚本版本不同步，部署终止"
    echo "[ERROR]   Flyway 文件数: ${flyway_count}"
    echo "[ERROR]   合并脚本版本: ${merged_count}"
    # 详细对比...
    exit 1
fi
```

#### 5. **deploy.sh:1520-1535** - 种子数据导入缺少错误检测
**问题**: `zcat` 和 `docker exec` 管道失败时，只用 `|| true` 吞掉错误，未检查导入是否成功  
**风险**: 导入失败但脚本继续执行，导致数据库为空但部署显示成功  
**影响**: 中（首次部署时数据库为空，用户体验极差）  
**建议**:
```bash
case "$seed_file" in
    *.sql.gz)
        if ! zcat "$seed_file" | docker exec -i "$mysql_container" mysql -uroot -p"$db_root_pw" "$db_name" 2>&1 | grep -v "Using a password"; then
            echo "[ERROR] 全量备份导入失败: $(basename "$seed_file")"
            echo "[ERROR] 请检查备份文件完整性或数据库连接"
            exit 1
        fi
        ;;
esac
```

#### 6. **auto-update.sh:540-547** - `pre_update_backup` 管道失败处理不当
**问题**: 使用子 shell 隔离 pipefail，但未检查 `mysqldump` 实际退出码  
**风险**: mysqldump 失败但 gzip 成功，生成空的 `.sql.gz` 文件，误以为备份成功  
**影响**: 高（回滚时发现备份无效，数据丢失风险）  
**建议**:
```bash
# 使用 PIPESTATUS 检查管道中每个命令的退出码
if docker compose -f "$COMPOSE_FILE" exec -T "$mysql_svc" \
    mysqldump -u root -p"$db_password" --single-transaction \
    --routines --triggers english_learning 2>/dev/null \
    | gzip > "$backup_file"; then
    # 检查 PIPESTATUS[0] (mysqldump 退出码)
    if [ "${PIPESTATUS[0]}" -ne 0 ]; then
        log "ERROR: mysqldump 失败，备份无效"
        rm -f "$backup_file"
        return 1
    fi
fi
```

#### 7. **auto-update.sh:1369-1393** - Docker build 超时后未清理 BUILD_LOG
**问题**: 超时时 `rm -f "$BUILD_LOG"` 在 `exit 1` 之前，但 `exit` 会触发 `trap cleanup`，可能导致日志被清理两次  
**风险**: 低（重复删除不会报错，但逻辑不清晰）  
**影响**: 低（代码可读性）  
**建议**:
```bash
if [ $BUILD_ELAPSED -ge $BUILD_TIMEOUT ]; then
    log "ERROR: 构建超时 (${BUILD_TIMEOUT}s)，终止构建"
    kill "$BUILD_PID" 2>/dev/null || true
    wait "$BUILD_PID" 2>/dev/null || true
    # 不在此处删除 BUILD_LOG，交给 cleanup 函数统一处理
    git checkout "$PRE_PULL_COMMIT" --force --quiet 2>/dev/null || true
    disable_maintenance
    notify "自动更新失败: Docker 构建超时，已回退代码"
    ops_log "ERROR" "${PRE_PULL_COMMIT:0:8}" "构建超时，已回退代码"
    SCRIPT_STATE="FAILED"
    exit 1
fi
```

---

### 🟢 低优先级（可选优化）

#### 8. **deploy.sh:196-223** - `probe_public_image` 函数超时实现不一致
**问题**: 有 `timeout` 命令时用 `timeout 30`，无 `timeout` 时手动实现超时，但手动实现的逻辑复杂且未处理 `docker pull` 卡死的情况  
**影响**: 低（仅影响镜像源探测，失败后会降级到本地 registry）  
**建议**: 统一使用 `timeout` 命令，如果不存在则跳过探测（直接返回失败）

#### 9. **deploy.sh:617-621** - 自签名证书 SAN 扩展未处理多域名/IP
**问题**: 只支持单个域名或 IP，实际部署中可能需要多个 SAN（如 `DNS:example.com,DNS:www.example.com,IP:192.168.1.100`）  
**影响**: 低（大多数场景单域名足够）  
**建议**: 增加"是否添加额外 SAN"的交互提示

#### 10. **deploy.sh:1562-1573** - ECDICT 导入未检查 CSV 文件完整性
**问题**: 直接 `LOAD DATA` 导入，未检查 CSV 行数或文件大小  
**风险**: CSV 文件损坏或不完整时，导入成功但数据量异常  
**影响**: 低（ECDICT 是静态数据，损坏概率低）  
**建议**:
```bash
# 导入前检查 CSV 行数
expected_lines=770000  # ECDICT 约 77 万词
actual_lines=$(wc -l < "$ECDICT_CSV_PATH" | tr -d ' ')
if [ "$actual_lines" -lt "$expected_lines" ]; then
    echo "[WARN] ECDICT CSV 行数异常（预期 ${expected_lines}，实际 ${actual_lines}），可能不完整"
    ask_yn "是否继续导入？" "n" CONTINUE_IMPORT
    [ "$CONTINUE_IMPORT" = false ] && return 0
fi
```

#### 11. **deploy.sh:1705-1741** - GTID 跳过逻辑未记录跳过的事务 ID
**问题**: 跳过错误事务后只输出计数，未记录具体跳过了哪些 GTID  
**影响**: 低（排查问题时难以追溯）  
**建议**: 将跳过的 GTID 写入日志文件 `logs/replication-skip.log`

#### 12. **auto-update.sh:1163-1173** - SSH 认证失败提示不完整
**问题**: 只提示切换 HTTPS 或生成 SSH key，未提示检查 `~/.ssh/config` 或 `ssh-agent`  
**影响**: 低（用户体验）  
**建议**: 增加更详细的排查步骤

#### 13. **auto-update.sh:1314-1343** - 变更检测逻辑未处理文件重命名
**问题**: `git diff --name-only` 只显示新旧文件名，重命名操作会被识别为"删除+新增"  
**风险**: 文件重命名可能触发不必要的重建  
**影响**: 低（重命名场景少，且重建是安全的）  
**建议**: 使用 `git diff --name-status` 并过滤 `R` 状态（重命名）

---

## 建议改进（非问题）

### 1. **增强日志结构化**
当前日志格式：`[$(date)] 消息`  
建议：增加日志级别和模块标识
```bash
log() {
    local level="${1:-INFO}"
    shift
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [$level] $*"
}
# 使用: log INFO "消息" / log ERROR "错误"
```

### 2. **deploy.sh 增加 `--dry-run` 模式**
允许用户预览部署操作而不实际执行，便于调试和验证配置

### 3. **auto-update.sh 增加 `--force` 参数**
跳过"已是最新版本"检查，强制重新部署当前版本（用于修复损坏的部署）

### 4. **统一错误码**
当前两个脚本都用 `exit 1` 表示所有错误，建议：
- `exit 1`: 配置错误
- `exit 2`: 构建失败
- `exit 3`: 健康检查失败
- `exit 4`: 回滚失败

### 5. **deploy.sh 增加部署前检查清单**
在步骤 4 确认部署前，显示：
- 磁盘空间（Docker 镜像需要 2-5GB）
- 内存可用量（MySQL + Redis + Backend 需要 4GB+）
- 端口占用情况（3306/6379/8080/80）

### 6. **auto-update.sh 增加更新通知钩子**
允许用户配置自定义通知脚本（如发送邮件、Slack、企业微信）

### 7. **增加配置文件验证工具**
独立脚本 `scripts/validate-config.sh`，验证 `.env` 文件完整性和合法性

### 8. **deploy.sh 增加"仅配置"模式**
允许用户只运行配置向导（步骤 1-4），不执行实际部署（步骤 5）

---

## 测试建议

### 边界情况测试清单

#### deploy.sh
- [ ] 端口冲突（3306/6379/8080/80 已被占用）
- [ ] 磁盘空间不足（Docker 构建失败）
- [ ] 网络断开（git fetch 超时、Docker pull 失败）
- [ ] MySQL 初始化失败（密码错误、权限不足）
- [ ] 从库复制失败（GTID 错误、binlog 位置错误）
- [ ] ECDICT CSV 文件缺失或损坏
- [ ] 用户输入特殊字符（`$`, `` ` ``, `!`, `\n`）
- [ ] 中断信号（Ctrl+C）在不同阶段的处理

#### auto-update.sh
- [ ] git fetch 超时（3 次重试后失败）
- [ ] Docker build 超时（30 分钟）
- [ ] 健康检查超时（120 秒）
- [ ] 回滚失败（旧版本构建失败）
- [ ] 并发执行（锁文件机制）
- [ ] 迁移脚本版本不同步
- [ ] 从库复制延迟过大（>30 秒）
- [ ] 维护模式残留（上次部署异常中断）

---

## 安全审查

### 已实现的安全措施 ✅
1. **密码保护**: `.env` 文件权限设置为 600
2. **输入清理**: 使用 `sed` 清理 ANSI 转义序列和控制字符
3. **SQL 注入防护**: 使用 heredoc 传递 SQL，避免字符串拼接
4. **命令注入防护**: 使用 `printf -v` 而非 `eval`
5. **锁文件机制**: 防止并发执行导致的竞态条件

### 潜在风险 ⚠️
1. **deploy.sh:98-103**: 用户输入未完全转义 shell 元字符（见问题 #1）
2. **deploy.sh:1520-1535**: 备份文件路径未验证，可能被路径遍历攻击（如 `../../etc/passwd`）
   - **建议**: 使用 `basename` 提取文件名，拒绝包含 `..` 的路径

---

## 性能优化建议

### 1. **并行化独立操作**
当前串行执行的操作可以并行化：
- `deploy.sh:1476-1591`: 种子数据导入（全量备份、3Blue1Brown、ECDICT）可并行
- `auto-update.sh:1358-1361`: backend 和 frontend 构建可并行（`docker compose build backend frontend`）

### 2. **增量备份优化**
`auto-update.sh:540-547` 的 mysqldump 每次全量备份，建议：
- 检查上次备份时间，<1 小时则跳过
- 使用 `--where` 只备份关键表（users, system_config, texts）

### 3. **Docker 镜像缓存优化**
`deploy.sh:1186-1227` 全量构建时使用 `--no-cache`，建议：
- 只在 Dockerfile 变更时使用 `--no-cache`
- 其他情况使用增量构建（利用 Docker layer cache）

---

## 总结

### 脚本质量评分: **8.5/10**

**优点**:
- 完善的错误处理和回滚机制
- 详细的日志和诊断信息
- 支持离线部署和网络故障降级
- 维护模式 + 数据库备份保障数据安全

**改进空间**:
- 输入验证和边界检查可以更严格
- 错误处理可以更细粒度（区分不同错误类型）
- 日志结构化和错误码标准化
- 增加更多用户友好的功能（dry-run、force、配置验证）

### 修复优先级建议

**立即修复** (影响功能或数据安全):
- 问题 #5: 种子数据导入缺少错误检测
- 问题 #6: 备份管道失败处理不当

**近期修复** (影响用户体验):
- 问题 #1: 输入清理不完整
- 问题 #2: 端口范围验证不完整
- 问题 #3: HiAgent 凭证校验不完整

**长期优化** (代码质量和可维护性):
- 问题 #4, #7, #8-#13
- 建议改进 #1-#8

---

**审查人**: Claude (Kiro AI)  
**审查完成时间**: 2026-05-05 23:45 UTC+8
