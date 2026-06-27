"""
阶段 1:下载 YouTube 视频 + 英中字幕
- 必须挂代理(校园网出口拦截 YouTube)
- 输出文件名格式: <video_id>.mp4 / <video_id>.en.vtt / <video_id>.zh-Hans.vtt
"""
import json
import subprocess
import sys
from pathlib import Path

import config


def download_one(url: str, title_hint: str = "", tag: str = "", description_hint: str = "") -> dict:
    """
    返回 {"video_id", "video_file", "subs_en", "subs_zh", "title", "tag", "description"}
    任一文件不存在则对应字段为 None。
    - title: 优先 title_hint,否则用 yt-dlp title
    - tag: 单选标签,直接传给后端 tags 字段
    - description: 优先 description_hint,否则用 yt-dlp description
    """
    print(f"\n{'='*60}\n>>> 下载: {url}\n{'='*60}")

    meta = _get_video_info(url)
    title = title_hint or meta.get("title") or "untitled"
    description = description_hint or meta.get("description") or config.DEFAULT_DESCRIPTION
    video_id = meta.get("id") or _get_video_id(url)
    print(f"   标题: {title}")
    print(f"   ID:   {video_id}")
    print(f"   标签: {tag or config.DEFAULT_TAG}")

    # 模板输出
    outtmpl = str(Path(video_id) / "%(id)s.%(ext)s")  # 放进同名子目录,避免多个视频混在一起

    # 主命令: 限制 720p + 优先 mp4 + 烧入字幕到视频流(playback 用),
    # 同时落盘 .en.vtt 和 .zh-Hans.vtt(上传用)
    cmd = [
        config.YT_DLP,
        "--js-runtime", config.DENO,
        # 反 bot 检测:用本地 cookies.txt 登录态 cookie
        "--cookies", config.COOKIES,
        # 模拟真实浏览器,降低被识别为 bot 的概率
        "--user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        # 优先 H.264(avc1) + m4a，避免下载到 AV1/VP9 导致平台无法播放
        "-f", f"bv*[height<={config.HEIGHT_LIMIT}][ext=mp4][vcodec^=avc1]+ba[ext=m4a]"
              f"/bv*[height<={config.HEIGHT_LIMIT}][vcodec^=avc1]+ba"
              f"/b[ext=mp4][vcodec^=avc1]"
              f"/b[ext=mp4]"
              f"/b",
        "--merge-output-format", "mp4",
        "--write-subs",
        "--write-auto-subs",
        "--sub-langs", "en,zh-Hans",
        "--convert-subs", "vtt",       # 统一 vtt 方便后续上传
        "-o", outtmpl,
        # 应对 YouTube 429 限流:重试 + 退避 + 字幕专用延迟
        "--retries", "10",
        "--fragment-retries", "10",
        "--retry-sleep", "5",
        "--sleep-subtitles", "3",      # 每个字幕请求间隔 3 秒,显著降低 429 概率
        "--no-playlist",
        "--no-mtime",
        url,
    ]
    print(f"   命令: {' '.join(cmd)}")
    result = subprocess.run(cmd, encoding="utf-8", errors="replace")
    if result.returncode != 0:
        print(f"   ❌ 下载失败,退出码 {result.returncode}")
        return {
            "video_id": video_id,
            "title": title,
            "tag": tag or config.DEFAULT_TAG,
            "description": description,
            "video_file": None,
            "subs_en": None,
            "subs_zh": None,
        }

    # 下载成功:定位原始视频,按需压缩
    base = Path(video_id)
    source_video = _find_video(base)
    video_file = source_video

    if source_video and source_video.exists():
        original_size_mb = source_video.stat().st_size / 1024 / 1024
        print(f"\n📦 原始视频大小:{original_size_mb:.2f} MB,上限 {config.DOWNLOAD_MAX_MB} MB")
        if original_size_mb > config.DOWNLOAD_MAX_MB:
            print("⚠️ 文件超出大小限制,启动 FFmpeg 自动压缩")
            compressed = _compress_to_limit(source_video, config.DOWNLOAD_MAX_MB)
            if compressed is not None:
                video_file = compressed
                print(f"✅ 压缩完成,使用压缩文件:{video_file}")
            else:
                print("❌ 全部档位压缩后仍超限,保留原视频")

    subs_en = base / f"{video_id}.en.vtt"
    subs_zh = base / f"{video_id}.zh-Hans.vtt"

    out = {
        "video_id":   video_id,
        "title":      title,
        "tag":        tag or config.DEFAULT_TAG,
        "description": description,
        "video_file": str(video_file) if video_file else None,
        "subs_en":    str(subs_en) if subs_en.exists() else None,
        "subs_zh":    str(subs_zh) if subs_zh.exists() else None,
    }
    _print_result(out)
    return out


def _compress_to_limit(source: Path, max_mb: int) -> Path | None:
    """用 ffmpeg 把视频压到 max_mb 以内,返回压缩后文件路径或 None(失败)。"""
    compressed_dir = source.parent / "compressed"
    compressed_dir.mkdir(exist_ok=True)

    # 分辨率降级序列
    profiles = [
        {"label": "720p", "scale": "-2:720",  "crf": 25, "audio": "128k"},
        {"label": "480p", "scale": "-2:480",  "crf": 28, "audio": "96k"},
        {"label": "360p", "scale": "-2:360",  "crf": 30, "audio": "80k"},
        {"label": "240p", "scale": "-2:240",  "crf": 32, "audio": "64k"},
    ]

    for p in profiles:
        out = compressed_dir / f"{source.stem}.{p['label']}.mp4"
        cmd = [
            config.FFMPEG, "-y",
            "-i", str(source),
            "-vf", f"scale={p['scale']}",
            "-c:v", "libx264", "-preset", "veryfast",
            "-crf", str(p["crf"]),
            "-c:a", "aac", "-b:a", p["audio"],
            str(out),
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
        # 转码失败直接换下一档
        if result.returncode != 0 or not out.exists():
            continue
        # 判断体积是否达标
        file_size_mb = out.stat().st_size / 1024 / 1024
        if file_size_mb <= max_mb:
            print(f"   ✅ 预压到 {p['label']}: {out.name} ({file_size_mb:.1f} MB)")
            return out
    # 所有档位压缩后依然超限，返回None
    return None

def _find_video(base: Path) -> Path | None:
    for ext in ("mp4", "mkv", "webm"):
        p = base / f"{base.name}.{ext}"
        if p.exists():
            return p
    return None


def _get_video_info(url: str) -> dict:
    r = subprocess.run(
        [config.YT_DLP, "--js-runtime", config.DENO,
         "--cookies", config.COOKIES,
         "--dump-single-json", "--skip-download", "--no-playlist", url],
        capture_output=True, text=True, encoding="utf-8", errors="replace",
    )
    if r.returncode != 0:
        return {}
    try:
        return json.loads(r.stdout or "{}")
    except json.JSONDecodeError:
        return {}


def _get_title(url: str) -> str | None:
    info = _get_video_info(url)
    return info.get("title")


def _get_video_id(url: str) -> str:
    info = _get_video_info(url)
    return info.get("id") or "unknown"


def _print_result(out: dict):
    print(f"\n   ✅ 下载结果:")
    for k, v in out.items():
        if k in ("video_file", "subs_en", "subs_zh") and v:
            size = Path(v).stat().st_size / 1024 / 1024
            print(f"     {k}: {v}  ({size:.1f} MB)")
        else:
            print(f"     {k}: {v}")


def parse_queue(path: str) -> list[dict]:
    """解析队列文件。格式: URL | 标题(可空) | category_key | description(可空)
    - 标题空 → 自动用 yt-dlp title
    - 描述空 → 自动用 yt-dlp description
    - 分类键未识别 → 回退到 config.DEFAULT_TAG
    """
    items = []
    with open(path, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = [p.strip() for p in line.split("|")]
            category_key = parts[2] if len(parts) > 2 else ""
            if category_key and category_key in config.TAG_MAP:
                tag = config.TAG_MAP[category_key]
            else:
                tag = config.DEFAULT_TAG
                if category_key:
                    print(f"⚠️  未识别分类 {category_key!r},回退到默认标签: {tag}")
            items.append({
                "url": parts[0],
                "title_hint": parts[1] if len(parts) > 1 else "",
                "category_key": category_key,
                "tag": tag,
                "description_hint": parts[3] if len(parts) > 3 and parts[3] else "",
            })
    return items


if __name__ == "__main__":
    if len(sys.argv) > 1:
        # 单链接模式: python download.py <URL>
        urls = [{
            "url": sys.argv[1],
            "title_hint": "",
            "category_key": "",
            "tag": config.DEFAULT_TAG,
            "description_hint": "",
        }]
    else:
        urls = parse_queue(config.QUEUE_FILE)

    results = [
        download_one(u["url"], u["title_hint"], u["tag"], u["description_hint"])
        for u in urls
    ]


    with open("download_manifest.json", "w", encoding="utf-8") as f:
        json.dump(results, f, ensure_ascii=False, indent=2)
    print(f"\n📋 已写入 download_manifest.json,共 {len(results)} 条")
