import subprocess
import os
import requests

# ==================== 配置区 ====================
LANGHACK_URL = "https://isa.imau.edu.cn"
USERNAME = "2025112104160"
PASSWORD = "zkx251227@"

# 在这里输入要测试的 YouTube 视频链接
TEST_VIDEO_URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"

YT_DLP = "yt-dlp.exe"
COOKIES = "cookies.txt"
# ================================================

session = requests.Session()
session.trust_env = False


def login():
    print(">>> 登录 LangHack...")
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/131.0.0.0",
        "Content-Type": "application/json",
    }
    try:
        resp = session.post(
            f"{LANGHACK_URL}/api/auth/login",
            json={"username": USERNAME, "password": PASSWORD},
            headers=headers,
            timeout=30
        )
        data = resp.json()
        print(f"服务器响应: {data}")

        if data.get("code") != 200:
            print(f"❌ 登录失败: {data.get('message')}")
            return False

        token = data["data"]["token"]
        session.headers["Authorization"] = f"Bearer {token}"
        print(f"✅ 登录成功: {data['data']['user']['username']}")
        return True
    except Exception as e:
        print(f"❌ 登录异常: {e}")
        return False


def download_video(url):
    print(f"\n>>> 下载视频: {url}")

    video_id = url.split("v=")[-1].split("&")[0]
    video_file = f"{video_id}.mp4"

    # 简化：只下载视频，不要字幕
    cmd = [
        YT_DLP,
        "--cookies", COOKIES,
        "-f", "best[ext=mp4]/best",
        "-o", video_file,
        url
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
        if result.returncode != 0:
            print(f"❌ 下载失败: {result.stderr[:500]}")
            return None
    except Exception as e:
        print(f"❌ 下载异常: {e}")
        return None

    if not os.path.exists(video_file):
        print(f"❌ 文件未找到: {video_file}")
        return None

    size_mb = os.path.getsize(video_file) / 1024 / 1024
    print(f"✅ 下载完成: {video_file} ({size_mb:.1f} MB)")
    return video_file


def upload_video(video_file):
    print(f"\n>>> 上传视频: {video_file}")

    try:
        with open(video_file, "rb") as f:
            resp = session.post(
                f"{LANGHACK_URL}/api/media/upload",
                files={"file": f},
                timeout=300
            )

        data = resp.json()
        print(f"上传响应: {data}")

        if data.get("code") != 200:
            print(f"❌ 上传失败: {data.get('message')}")
            return None

        video_url = data["data"]["url"]
        print(f"✅ 上传成功: {video_url}")
        return video_url
    except Exception as e:
        print(f"❌ 上传异常: {e}")
        return None


def create_video_record(video_url, title):
    print(f"\n>>> 创建视频记录...")

    payload = {
        "title": title,
        "videoUrl": video_url,
        "description": "Test video from YouTube",
        "tags": "test,youtube",
        "subtitles": []
    }

    try:
        resp = session.post(
            f"{LANGHACK_URL}/api/videos",
            json=payload,
            timeout=30
        )
        data = resp.json()
        print(f"创建响应: {data}")

        if data.get("code") != 200:
            print(f"❌ 创建失败: {data.get('message')}")
            return False

        print(f"✅ 创建成功: {data['data']['id']}")
        return True
    except Exception as e:
        print(f"❌ 创建异常: {e}")
        return False


def main():
    print("=" * 60)
    print("YouTube 单视频测试脚本")
    print("=" * 60)

    # 步骤 1: 登录
    if not login():
        print("\n❌ 登录失败，终止")
        return

    # 步骤 2: 下载视频
    video_file = download_video(TEST_VIDEO_URL)
    if not video_file:
        print("\n❌ 下载失败，终止")
        return

    # 步骤 3: 上传视频
    video_url = upload_video(video_file)
    if not video_url:
        print("\n❌ 上传失败，终止")
        return

    # 步骤 4: 创建记录
    title = os.path.splitext(video_file)[0]
    if create_video_record(video_url, title):
        print("\n" + "=" * 60)
        print("✅ 全部完成！")
        print("=" * 60)
    else:
        print("\n❌ 创建记录失败")


if __name__ == "__main__":
    main()
