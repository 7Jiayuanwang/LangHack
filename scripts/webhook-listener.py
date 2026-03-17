#!/usr/bin/env python3
"""
LangHack GitHub Webhook Listener
接收 GitHub push 事件，验证签名后触发 auto-update.sh 自动部署。

安全模型:
  - 仅绑定 127.0.0.1（由 nginx 代理，不直接暴露）
  - HMAC-SHA256 签名验证（防伪造）
  - Payload 大小限制（防 DoS）
  - 事件/分支过滤（防误触发）

用法:
  WEBHOOK_SECRET=your_secret python3 scripts/webhook-listener.py
"""

import hashlib
import hmac
import json
import logging
import os
import subprocess
import sys
from http.server import HTTPServer, BaseHTTPRequestHandler
from logging.handlers import RotatingFileHandler
from pathlib import Path

# ── 配置 ────────────────────────────────────────────────────
WEBHOOK_SECRET = os.environ.get("WEBHOOK_SECRET", "")
DEPLOY_BRANCH = os.environ.get("DEPLOY_BRANCH", "master")
PROJECT_ROOT = os.environ.get("PROJECT_ROOT", str(Path(__file__).resolve().parent.parent))
WEBHOOK_PORT = int(os.environ.get("WEBHOOK_PORT", "9000"))
MAX_PAYLOAD_SIZE = 10 * 1024 * 1024  # 10 MB

# ── 日志 ────────────────────────────────────────────────────
LOG_DIR = Path(PROJECT_ROOT) / "logs"
LOG_DIR.mkdir(exist_ok=True)

logger = logging.getLogger("webhook")
logger.setLevel(logging.INFO)

# 文件日志: 轮转 5MB × 3 份
file_handler = RotatingFileHandler(
    LOG_DIR / "webhook.log", maxBytes=5 * 1024 * 1024, backupCount=3, encoding="utf-8"
)
file_handler.setFormatter(logging.Formatter("[%(asctime)s] %(levelname)s %(message)s"))
logger.addHandler(file_handler)

# 控制台日志
console_handler = logging.StreamHandler()
console_handler.setFormatter(logging.Formatter("[%(asctime)s] %(levelname)s %(message)s"))
logger.addHandler(console_handler)


def verify_signature(payload: bytes, signature_header: str) -> bool:
    """HMAC-SHA256 签名验证（常量时间比较，防时序攻击）"""
    if not WEBHOOK_SECRET:
        logger.warning("WEBHOOK_SECRET 未设置，跳过签名验证（仅限开发环境！）")
        return True
    if not signature_header:
        return False
    # GitHub 格式: sha256=xxxx
    if not signature_header.startswith("sha256="):
        return False
    expected = hmac.new(
        WEBHOOK_SECRET.encode("utf-8"), payload, hashlib.sha256
    ).hexdigest()
    actual = signature_header[7:]  # 去掉 "sha256=" 前缀
    return hmac.compare_digest(expected, actual)


def trigger_deploy():
    """后台启动 auto-update.sh（非阻塞，GitHub 有 10 秒超时要求）"""
    script = Path(PROJECT_ROOT) / "scripts" / "auto-update.sh"
    if not script.exists():
        logger.error("部署脚本不存在: %s", script)
        return False
    try:
        subprocess.Popen(
            ["bash", str(script)],
            cwd=PROJECT_ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            start_new_session=True,  # 脱离父进程
        )
        logger.info("部署脚本已启动: %s", script)
        return True
    except Exception as e:
        logger.error("启动部署脚本失败: %s", e)
        return False


class WebhookHandler(BaseHTTPRequestHandler):
    """处理 GitHub Webhook 请求"""

    def log_message(self, format, *args):
        """覆盖默认日志，使用 logger"""
        logger.info("HTTP %s", format % args)

    def _send_json(self, status: int, data: dict):
        """发送 JSON 响应"""
        body = json.dumps(data).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        """健康检查端点"""
        if self.path == "/webhook/health":
            self._send_json(200, {"status": "ok", "branch": DEPLOY_BRANCH})
        else:
            self._send_json(404, {"error": "not found"})

    def do_POST(self):
        """处理 GitHub Webhook POST 请求"""
        if self.path != "/webhook/github":
            self._send_json(404, {"error": "not found"})
            return

        # ── 读取并验证 Payload 大小 ──
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length > MAX_PAYLOAD_SIZE:
            logger.warning("Payload 过大: %d bytes (limit: %d)", content_length, MAX_PAYLOAD_SIZE)
            self._send_json(413, {"error": "payload too large"})
            return

        payload = self.rfile.read(content_length)

        # ── HMAC 签名验证 ──
        signature = self.headers.get("X-Hub-Signature-256", "")
        if not verify_signature(payload, signature):
            logger.warning("签名验证失败, remote: %s", self.client_address[0])
            self._send_json(403, {"error": "invalid signature"})
            return

        # ── 事件类型过滤 ──
        event = self.headers.get("X-GitHub-Event", "")

        if event == "ping":
            logger.info("收到 GitHub ping 事件 — pong!")
            self._send_json(200, {"message": "pong"})
            return

        if event != "push":
            logger.info("忽略非 push 事件: %s", event)
            self._send_json(200, {"message": "ignored", "event": event})
            return

        # ── 解析 Payload ──
        try:
            data = json.loads(payload)
        except json.JSONDecodeError:
            logger.warning("无效的 JSON payload")
            self._send_json(400, {"error": "invalid JSON"})
            return

        # ── 分支过滤 ──
        ref = data.get("ref", "")
        target_ref = f"refs/heads/{DEPLOY_BRANCH}"
        if ref != target_ref:
            logger.info("忽略非目标分支: %s (目标: %s)", ref, target_ref)
            self._send_json(200, {"message": "ignored", "ref": ref, "target": target_ref})
            return

        # ── 提取推送信息 ──
        pusher = data.get("pusher", {}).get("name", "unknown")
        head_commit = data.get("head_commit", {})
        commit_id = head_commit.get("id", "unknown")[:8]
        commit_msg = head_commit.get("message", "").split("\n")[0][:80]
        commits_count = len(data.get("commits", []))

        logger.info(
            "收到 push 事件: branch=%s, pusher=%s, commits=%d, head=%s '%s'",
            DEPLOY_BRANCH, pusher, commits_count, commit_id, commit_msg,
        )

        # ── 触发部署（非阻塞）──
        if trigger_deploy():
            self._send_json(200, {
                "message": "deploy triggered",
                "branch": DEPLOY_BRANCH,
                "commit": commit_id,
            })
        else:
            self._send_json(500, {"error": "failed to trigger deploy"})


def main():
    if not WEBHOOK_SECRET:
        logger.warning("=" * 60)
        logger.warning("  警告: WEBHOOK_SECRET 未设置！")
        logger.warning("  生产环境必须设置此变量以启用签名验证。")
        logger.warning("=" * 60)

    server = HTTPServer(("127.0.0.1", WEBHOOK_PORT), WebhookHandler)
    logger.info("Webhook listener 启动: http://127.0.0.1:%d", WEBHOOK_PORT)
    logger.info("  部署分支: %s", DEPLOY_BRANCH)
    logger.info("  项目根目录: %s", PROJECT_ROOT)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        logger.info("收到终止信号，正在关闭...")
        server.server_close()
        sys.exit(0)


if __name__ == "__main__":
    main()
