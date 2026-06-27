"""
阶段 2:把 download.py 落盘的视频上传到 LangHack
- 可在开代理状态下运行;脚本会忽略系统代理并直连 isa.imau.edu.cn / langhack.imau.edu.cn
- 严格按 API 文档的两步流程:
    1) POST /api/media/upload (multipart/form-data, mediaType=VIDEO) -> mediaId
    2) POST /api/user/videos/upload (JSON, 引用 mediaId) -> videoId
- Token 从 config.TOKEN 读;若缺失给出明确指引。
- 如果文件超过网关可接受阈值,会先用 ffmpeg 自动压缩再上传。
"""
import json
import subprocess
import sys
import time
from pathlib import Path

import requests

import config
import token_manager


# 不走系统代理(避免 CAS 看到的是代理出口 IP,触发 SSO 失败)
SESSION = requests.Session()
SESSION.trust_env = False
SESSION.headers.update({
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0",
})


def file_size_mb(path: str | Path) -> float:
    return Path(path).stat().st_size / 1024 / 1024


def check_token() -> bool:
    token = token_manager.get_valid_access_token()
    if not token:
        print("❌ 访问 token 未配置或无效。")
        print("   请先在 config.py 填好对应平台的 access/refresh token。")
        return False
    return True




def ensure_uploadable_video(file_path: str) -> str:
    p = Path(file_path)
    size_mb = file_size_mb(p)

    # 统一把压缩产物放回"视频根目录/compressed"，避免 repeated run 生成 compressed/compressed/...
    root_dir = p.parent.parent if p.parent.name == "compressed" else p.parent
    compressed_dir = root_dir / "compressed"
    compressed_dir.mkdir(exist_ok=True)

    # 已经是压缩产物且大小达标时，直接复用
    if p.suffix.lower() == ".mp4" and size_mb <= config.UPLOAD_MAX_MB and p.parent == compressed_dir:
        return str(p)

    # 新逻辑：根据文件大小决定是否压缩
    print(f"   📊 视频大小: {size_mb:.2f} MB")

    # 1. 小于 50MB：直接上传，不压缩
    if size_mb < config.UPLOAD_PROMPT_COMPRESS_MB:
        print(f"   ✅ 视频小于 {config.UPLOAD_PROMPT_COMPRESS_MB} MB，直接上传原视频")
        return str(p)

    # 2. 50MB - 100MB：提示用户选择
    elif size_mb <= config.UPLOAD_MAX_MB:
        if config.AUTO_COMPRESS:
            print(f"   ⚠️  视频大小 {size_mb:.2f} MB，自动压缩模式已启用")
            should_compress = True
        else:
            print(f"   ⚠️  视频大小 {size_mb:.2f} MB，是否需要压缩？")
            print(f"      [1] 直接上传原视频 ({size_mb:.2f} MB)")
            print(f"      [2] 压缩后上传 (目标 ≤{config.UPLOAD_PROMPT_COMPRESS_MB} MB)")
            choice = input("   请选择 (1/2，默认 1): ").strip()
            should_compress = (choice == "2")

        if not should_compress:
            print(f"   ✅ 用户选择直接上传原视频")
            return str(p)
        else:
            print(f"   🔄 开始压缩视频...")

    # 3. 超过 100MB：必须压缩
    else:
        print(f"   ⚠️  视频超过 {config.UPLOAD_MAX_MB} MB，必须压缩")

    print(f"   ⚠️  准备统一转为 h264+aac，并控制到 ≤{config.UPLOAD_PROMPT_COMPRESS_MB} MB")

    for profile in config.UPLOAD_COMPRESS_PROFILES:
        output = compressed_dir / f"{root_dir.name}.{profile['label']}.mp4"
        cmd = [
            config.FFMPEG,
            "-y",
            "-i", str(p),
            "-vf", f"scale={profile['scale']}",
            "-c:v", "libx264",
            "-preset", "veryfast",
            "-pix_fmt", "yuv420p",
            "-movflags", "+faststart",
            "-profile:v", "main",
            "-level", "3.1",
            "-crf", str(profile["crf"]),
            "-c:a", "aac",
            "-b:a", profile["audio_bitrate"],
            str(output),
        ]
        print(f"   ▶️  压缩配置 {profile['label']}: {' '.join(cmd)}")
        result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
        if result.returncode != 0 or not output.exists():
            print(f"   ⚠️  压缩失败: {profile['label']}")
            continue
        output_mb = file_size_mb(output)
        print(f"   ✅ 压缩完成: {output} ({output_mb:.2f} MB)")
        if output_mb <= config.UPLOAD_PROMPT_COMPRESS_MB:
            return str(output)

    raise RuntimeError(
        f"自动压缩/转码后仍不满足上传要求(大小≤{config.UPLOAD_PROMPT_COMPRESS_MB} MB)"
    )



def upload_cover_image(image_path: str) -> int | None:
    """上传一张封面图，返回 mediaId。失败/不存在返回 None。"""
    p = Path(image_path)
    if not p.exists():
        return None
    with open(p, "rb") as f:
        files = {"file": (p.name, f, "image/jpeg")}
        data = {"mediaType": "IMAGE"}
        try:
            r = token_manager.request_upload(
                "/api/media/upload",
                files=files,
                data=data,
                timeout=120,
            )
        except Exception:
            return None
    if r.status_code == 413:
        return None
    try:
        body = r.json()
    except Exception:
        return None
    if body.get("code") == 200 and body.get("data", {}).get("mediaId"):
        print(f"   ✅ 封面上传成功 mediaId={body['data']['mediaId']}")
        return body["data"]["mediaId"]
    return None


def _try_make_thumbnail(video_path: Path) -> Path | None:
    """从视频里抽一张 1 秒处帧保存为 jpg 封面。失败返回 None。"""
    out_dir = video_path.parent if video_path.parent.name == "compressed" else video_path.parent
    out = out_dir / f"{Path(video_path).stem}.cover.jpg"
    cmd = [
        config.FFMPEG,
        "-y",
        "-ss", "1",
        "-i", str(video_path),
        "-frames:v", "1",
        "-q:v", "3",
        str(out),
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
    if result.returncode == 0 and out.exists() and out.stat().st_size < 5 * 1024 * 1024:
        return out
    return None


def upload_media(file_path: str, media_type: str) -> int:
    """返回 mediaId。文档明确要求 mediaType=IMAGE/VIDEO,缺一返回 500。"""
    p = Path(file_path)
    if not p.exists():
        raise FileNotFoundError(file_path)

    mime = "video/mp4" if media_type == "VIDEO" else "image/jpeg"
    with open(p, "rb") as f:
        files = {"file": (p.name, f, mime)}
        data  = {"mediaType": media_type}
        r = token_manager.request_upload(
            "/api/media/upload",
            files=files,
            data=data,
            timeout=1800,
        )

    if r.status_code == 413:
        raise RuntimeError(
            f"413 上传体积超限: {p.name} ({file_size_mb(p):.2f} MB)。"
            f" 注意: config.UPLOAD_MAX_MB={config.UPLOAD_MAX_MB} MB 只是本地压缩目标,"
            f" 不是服务器真实限制;当前网关上限可能低于该值。"
        )

    r.raise_for_status()
    body = r.json()
    if body.get("code") != 200:
        raise RuntimeError(f"媒体上传失败: {body}")
    media_id = body["data"]["mediaId"]
    print(f"   ✅ 媒体已上传 mediaId={media_id},file={p.name} "
          f"({file_size_mb(p):.1f} MB)")
    return media_id


def create_video_entry(
    title: str,
    media_id: int,
    description: str = "",
    tag: str = config.DEFAULT_TAG,
    subtitle_content: str | None = None,
    subtitle_content_zh: str | None = None,
    cover_media_id: int | None = None,
    is_public: bool = True,
) -> int:
    """返回 videoId。注意文档里 isPublic 默认 0、status 默认 PENDING。
    tag: 单选标签字符串,直接传给后端 tags 字段(后端按字符串存储)。"""
    payload = {
        "title": title,
        "mediaId": media_id,
        "description": description,
        "tags": tag,
        "isPublic": 1 if is_public else 0,
    }
    if subtitle_content:
        payload["subtitleContent"] = subtitle_content
    if subtitle_content_zh:
        payload["subtitleContentZh"] = subtitle_content_zh
    if cover_media_id:
        payload["coverMediaId"] = cover_media_id

    r = token_manager.request_json(
        "POST",
        "/api/user/videos/upload",
        json=payload,
        timeout=60,
    )
    r.raise_for_status()
    body = r.json()
    if body.get("code") != 200:
        raise RuntimeError(f"视频条目创建失败: {body}")
    video_id = body["data"]["videoId"]
    print(f"   ✅ 视频条目已创建 videoId={video_id}")
    return video_id


def load_subtitle(path: str | None) -> str | None:
    if not path or not Path(path).exists():
        return None
    return Path(path).read_text(encoding="utf-8")


def upload_one(item: dict, tag_override: str = "", desc_override: str = "") -> dict:
    if not item.get("video_file"):
        print(f"⏭️  跳过(无视频文件): {item.get('title')}")
        return {"title": item.get("title"), "skipped": True}

    title = item["title"]
    source_file = item["video_file"]
    print(f"\n{'='*60}\n>>> 上传: {title}\n   文件: {source_file}\n{'='*60}")

    upload_file = ensure_uploadable_video(source_file)
    if upload_file != source_file:
        print(f"   使用压缩文件上传: {upload_file}")

    media_id = upload_media(upload_file, "VIDEO")

    # 抽帧 → 上传封面 → 避免前端播放器因 coverMediaId=null 触发 404
    cover_path = _try_make_thumbnail(Path(upload_file))
    cover_media_id = upload_cover_image(str(cover_path)) if cover_path else None

    sub_en = load_subtitle(item.get("subs_en"))
    sub_zh = load_subtitle(item.get("subs_zh"))

    video_id = create_video_entry(
        title=title,
        media_id=media_id,
        description=desc_override or item.get("description") or f"YouTube video: {title}",
        tag=tag_override or item.get("tag") or config.DEFAULT_TAG,
        subtitle_content=sub_en,
        subtitle_content_zh=sub_zh,
        cover_media_id=cover_media_id,
        is_public=True,
    )

    return {
        "title": title,
        "video_id": video_id,
        "media_id": media_id,
        "uploaded_file": upload_file,
        "skipped": False,
    }


def main():
    if not check_token():
        sys.exit(1)

    manifest_path = Path("download_manifest.json")
    if not manifest_path.exists():
        print("❌ 找不到 download_manifest.json,请先运行 download.py")
        sys.exit(1)

    items = json.loads(manifest_path.read_text(encoding="utf-8"))
    results = []
    for item in items:
        try:
            results.append(upload_one(item))
            time.sleep(1)
        except Exception as e:
            print(f"   ❌ 出错: {e}")
            results.append({"title": item.get("title"), "error": str(e)})

    out = Path("upload_manifest.json")
    out.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\n📋 已写入 {out}")
    print("\n📊 汇总:")
    for r in results:
        if r.get("skipped"):
            print(f"   ⏭️  {r['title']}")
        elif r.get("error"):
            print(f"   ❌ {r['title']}: {r['error']}")
        else:
            print(f"   ✅ {r['title']} -> videoId={r['video_id']}")


if __name__ == "__main__":
    main()
