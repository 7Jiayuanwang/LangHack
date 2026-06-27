import base64
import json
import time
from pathlib import Path

import requests

import config

TOKENS_FILE = Path("token_state.json")
SESSION = requests.Session()
SESSION.trust_env = False
SESSION.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0",
})


def _platform_key() -> str:
    return "isa" if "isa.imau.edu.cn" in config.LANGHACK_URL else "lh"


def _config_tokens() -> dict:
    if _platform_key() == "isa":
        return {
            "access_token": config.ISA_ACCESS_TOKEN or config.TOKEN,
            "refresh_token": config.ISA_REFRESH_TOKEN,
        }
    return {
        "access_token": config.LH_ACCESS_TOKEN or config.TOKEN,
        "refresh_token": config.LH_REFRESH_TOKEN,
    }


def _load_state() -> dict:
    if TOKENS_FILE.exists():
        try:
            return json.loads(TOKENS_FILE.read_text(encoding="utf-8"))
        except Exception:
            return {}
    return {}


def _save_state(state: dict) -> None:
    TOKENS_FILE.write_text(json.dumps(state, ensure_ascii=False, indent=2), encoding="utf-8")


def _jwt_exp(token: str) -> int | None:
    try:
        payload = token.split(".")[1]
        payload += "=" * (-len(payload) % 4)
        data = json.loads(base64.urlsafe_b64decode(payload.encode("utf-8")).decode("utf-8"))
        return int(data.get("exp")) if data.get("exp") else None
    except Exception:
        return None


def _get_current_tokens() -> dict:
    state = _load_state()
    platform = _platform_key()
    persisted = state.get(platform, {})
    cfg = _config_tokens()
    return {
        "access_token": persisted.get("access_token") or cfg.get("access_token") or "",
        "refresh_token": persisted.get("refresh_token") or cfg.get("refresh_token") or "",
    }


def _persist_tokens(access_token: str, refresh_token: str | None = None) -> None:
    state = _load_state()
    platform = _platform_key()
    current = state.get(platform, {})
    state[platform] = {
        "access_token": access_token,
        "refresh_token": refresh_token or current.get("refresh_token") or _config_tokens().get("refresh_token") or "",
        "base_url": config.LANGHACK_URL,
        "updated_at": int(time.time()),
    }
    _save_state(state)


def refresh_access_token() -> str | None:
    tokens = _get_current_tokens()
    refresh_token = tokens.get("refresh_token")
    if not refresh_token:
        return None

    url = f"{config.LANGHACK_URL}/api/auth/refresh"
    resp = SESSION.post(url, json={"refreshToken": refresh_token}, timeout=30)
    try:
        body = resp.json()
    except Exception:
        body = {}

    if resp.status_code == 200 and body.get("code") == 200 and body.get("data"):
        data = body["data"]
        new_access = data.get("accessToken") or data.get("access_token") or data.get("token")
        new_refresh = data.get("refreshToken") or data.get("refresh_token") or refresh_token
        if new_access:
            _persist_tokens(new_access, new_refresh)
            print("✅ Token 自动刷新成功")
            return new_access
    print("❌ 刷新 token 失败,请重新登录获取新的 access/refresh token")
    return None


def get_valid_access_token(force_refresh: bool = False) -> str | None:
    tokens = _get_current_tokens()
    access_token = tokens.get("access_token")
    if not access_token:
        return None
    if force_refresh:
        return refresh_access_token() or access_token

    exp = _jwt_exp(access_token)
    if exp is None:
        return access_token
    if exp - int(time.time()) <= config.TOKEN_REFRESH_THRESHOLD:
        return refresh_access_token() or access_token
    return access_token


def auth_headers(json_content: bool = True, force_refresh: bool = False) -> dict:
    access_token = get_valid_access_token(force_refresh=force_refresh)
    if not access_token:
        return {}
    headers = {"Authorization": f"Bearer {access_token}"}
    if json_content:
        headers["Content-Type"] = "application/json"
    return headers


def request_json(method: str, path: str, retry_on_401: bool = True, **kwargs):
    headers = kwargs.pop("headers", {})
    merged = auth_headers(json_content=True)
    merged.update(headers)
    resp = SESSION.request(method, f"{config.LANGHACK_URL}{path}", headers=merged, timeout=kwargs.pop("timeout", 60), **kwargs)
    if resp.status_code == 401 and retry_on_401:
        refreshed = auth_headers(json_content=True, force_refresh=True)
        if refreshed:
            refreshed.update(headers)
            return SESSION.request(method, f"{config.LANGHACK_URL}{path}", headers=refreshed, timeout=kwargs.pop("timeout", 60), **kwargs)
    return resp


def request_upload(path: str, *, files, data=None, retry_on_401: bool = True, timeout: int = 1800):
    headers = auth_headers(json_content=False)
    resp = SESSION.post(f"{config.LANGHACK_URL}{path}", headers=headers, files=files, data=data, timeout=timeout)
    if resp.status_code == 401 and retry_on_401:
        refreshed = auth_headers(json_content=False, force_refresh=True)
        if refreshed:
            return SESSION.post(f"{config.LANGHACK_URL}{path}", headers=refreshed, files=files, data=data, timeout=timeout)
    return resp
