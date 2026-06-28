#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
外网文章下载脚本 v2（浏览器版，对应 yt-dlp 视频下载的文章版）

适用于 NYT / Time / Newsweek 这类有反爬、需要登录的站点。
用真实浏览器加载页面，既绕过反爬，又能记住登录态。

============ 第一次使用：登录（只需做一次） ============
    python article_dl.py --login

  会弹出一个浏览器窗口。你在里面手动登录纽约时报 / Time / Newsweek
  （想登几个登几个），登好后直接把窗口关掉。
  登录信息会保存在同目录的 user_data/ 文件夹里，下次自动复用。

============ 之后每次：下载文章 ============
    python article_dl.py "文章链接"

  输出： <文章标题>.md  +  <文章标题>_images/（图片）

============ 一次下载多篇 ============
  把链接逐行写进 urls.txt，然后：
    python article_dl.py --batch urls.txt

============ 下载并直接上传到 LangHack 平台 ============
  先把平台 token 存进 token.txt：
    1) 浏览器登录平台： https://langhack.imau.edu.cn
    2) F12 → Console 输入： localStorage.getItem('token')
    3) 复制结果（不带引号）覆盖进 token.txt 保存
  然后：
    python article_dl.py --upload "文章链接"
    python article_dl.py --upload "文章链接" "学术英语" HARD          # 单个标签 + 难度
    python article_dl.py --upload "文章链接" "科技文章,人工智能" HARD  # 多个标签用逗号分隔
    python article_dl.py --upload-batch urls.txt                     # 批量下载并上传

  （脚本会在上传前自动校验 token，失效会立即提示重新登录，不会白下载。）
  如需切换到其它服务器，设环境变量即可，无需改代码：
    set LANGHACK_PLATFORM=https://别的地址

  标签必选，可多选（用英文逗号分隔），只能从下面这些里挑（不填默认「新闻资讯」）：
    网络安全 / 人工智能 / 英文歌曲 / 世界杯 / 四六级 /
    日常对话 / 商务英语 / 学术英语 / 科技文章 / 新闻资讯 / 文学小说
  难度：EASY / MEDIUM / HARD（不填默认 MEDIUM）

需要先安装一次依赖：
    pip install playwright trafilatura beautifulsoup4 lxml requests
    python -m playwright install chromium
"""

import sys
import os
import re
from urllib.parse import urljoin, urlparse

import trafilatura
import requests
from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright

HERE = os.path.dirname(os.path.abspath(__file__))
USER_DATA = os.path.join(HERE, "user_data")  # 保存登录态
TOKEN_FILE = os.path.join(HERE, "token.txt")  # 平台登录 token

# LangHack 平台地址
# 默认指向正式平台 langhack；如需临时切到别的服务器，
# 可设置环境变量 LANGHACK_PLATFORM，无需改代码。例如：
#   Windows:  set LANGHACK_PLATFORM=https://isa.imau.edu.cn
PLATFORM = os.environ.get("LANGHACK_PLATFORM", "https://langhack.imau.edu.cn").rstrip("/")
IMG_MAX_BYTES = 10 * 1024 * 1024  # 平台图片上限 10MB
IMG_OK_EXT = {".jpg", ".jpeg", ".png", ".gif", ".webp"}

# 平台 WAF 会拦截默认的 python-requests UA，统一用浏览器 UA
PLATFORM_HEADERS = {
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    )
}

# 平台预设标签（必选，至少一个）。支持一篇文章打多个标签，用英文逗号分隔。
# 标签名必须和平台上完全一致；平台新增/改名标签时，同步改这里即可。
PRESET_TAGS = [
    "网络安全", "人工智能", "英文歌曲", "世界杯", "四六级",
    "日常对话", "商务英语", "学术英语", "科技文章", "新闻资讯", "文学小说",
]
DEFAULT_TAG = "新闻资讯"

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/124.0.0.0 Safari/537.36"
)


def sanitize(name):
    name = re.sub(r'[\\/:*?"<>|\n\r\t]', " ", name).strip()
    name = re.sub(r"\s+", " ", name)
    return name[:120] or "article"


NOISE_LINES = {"advertisement", "advertisements", "sponsored content"}


def clean_body(body):
    """过滤正文里残留的广告占位行等噪声"""
    out = []
    for line in body.splitlines():
        if line.strip().lower() in NOISE_LINES:
            continue
        out.append(line)
    # 合并多余空行
    text = "\n".join(out)
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip()


def get_title(html, fallback_url):
    soup = BeautifulSoup(html, "lxml")
    if soup.title and soup.title.string:
        return soup.title.string.strip()
    h1 = soup.find("h1")
    if h1:
        return h1.get_text(strip=True)
    return urlparse(fallback_url).path.strip("/").replace("/", "_") or "article"


def do_login():
    """打开浏览器让用户手动登录，登录态存进 user_data/"""
    print("[*] 即将打开浏览器。请在窗口里登录你需要的网站（NYT / Time / Newsweek）。")
    print("[*] 登录完成后，直接关闭浏览器窗口即可，登录信息会自动保存。")
    with sync_playwright() as p:
        ctx = p.chromium.launch_persistent_context(
            USER_DATA, headless=False, user_agent=UA,
            args=["--disable-blink-features=AutomationControlled"],
        )
        page = ctx.pages[0] if ctx.pages else ctx.new_page()
        page.goto("https://www.nytimes.com/", wait_until="domcontentloaded")
        print("[*] 浏览器已打开，登录好后关闭窗口...")
        try:
            page.wait_for_event("close", timeout=0)
        except Exception:
            pass
        try:
            ctx.close()
        except Exception:
            pass
    print("[OK] 登录态已保存到 user_data/")


def fetch_rendered_html(ctx, url):
    """用浏览器打开页面，等渲染完，返回最终 HTML"""
    page = ctx.new_page()
    page.goto(url, wait_until="domcontentloaded", timeout=60000)
    # 滚动到底，触发懒加载图片
    try:
        for _ in range(8):
            page.mouse.wheel(0, 3000)
            page.wait_for_timeout(500)
    except Exception:
        pass
    page.wait_for_timeout(1500)
    html = page.content()
    page.close()
    return html


def download_images(ctx, markdown, base_url, img_dir, img_prefix):
    """用浏览器的网络栈下载图片（同样带登录态、绕反爬）"""
    os.makedirs(img_dir, exist_ok=True)
    img_pattern = re.compile(r"!\[([^\]]*)\]\(([^)]+)\)")
    count = 0
    api = ctx.request

    def replace(match):
        nonlocal count
        alt, src = match.group(1), match.group(2).strip()
        if src.startswith("data:"):
            return match.group(0)
        full = urljoin(base_url, src)
        try:
            r = api.get(full, timeout=30000)
            if not r.ok:
                return match.group(0)
            body = r.body()
        except Exception as e:
            print(f"    [!] 图片下载失败 {full}: {e}")
            return match.group(0)
        ext = os.path.splitext(urlparse(full).path)[1]
        if not ext or len(ext) > 5:
            ext = ".jpg"
        count += 1
        fname = f"img_{count:03d}{ext}"
        with open(os.path.join(img_dir, fname), "wb") as f:
            f.write(body)
        return f"![{alt}]({img_prefix}/{fname})"

    new_md = img_pattern.sub(replace, markdown)
    print(f"[+] 下载图片 {count} 张 -> {img_dir}/")
    return new_md


def save_article(ctx, url):
    print(f"\n[*] 目标文章: {url}")
    print("[*] 浏览器加载中...")
    html = fetch_rendered_html(ctx, url)

    title = get_title(html, url)
    safe = sanitize(title)
    print(f"[+] 标题: {title}")

    print("[*] 提取正文...")
    body = trafilatura.extract(
        html, output_format="markdown", include_images=True,
        include_links=True, with_metadata=False, url=url,
    )
    if not body:
        print("[!] 正文提取失败（可能未登录或该页面结构特殊）。已跳过。")
        return None

    body = clean_body(body)
    md_doc = f"# {title}\n\n> 来源: {url}\n\n{body}\n"
    img_dir = os.path.join(HERE, f"{safe}_images")
    md_doc = download_images(ctx, md_doc, url, img_dir, f"{safe}_images")

    out_path = os.path.join(HERE, f"{safe}.md")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write(md_doc)
    print(f"[OK] 完成: {safe}.md")
    return {"title": title, "safe": safe, "md": md_doc,
            "md_path": out_path, "img_dir": img_dir, "url": url}


def run_download(urls):
    if not os.path.exists(USER_DATA):
        print("[!] 还没登录过。建议先运行：python article_dl.py --login")
        print("    （也可以直接继续，免费文章可能也能抓到）\n")
    with sync_playwright() as p:
        ctx = p.chromium.launch_persistent_context(
            USER_DATA, headless=True, user_agent=UA,
            args=["--disable-blink-features=AutomationControlled"],
        )
        ok = 0
        for u in urls:
            try:
                if save_article(ctx, u):
                    ok += 1
            except Exception as e:
                print(f"[!] 出错，跳过 {u}: {e}")
        ctx.close()
        print(f"\n[完成] 成功 {ok}/{len(urls)} 篇。")


# ============ 上传到 LangHack 平台 ============

def load_token():
    """读取 token.txt 里的平台登录 token"""
    if not os.path.exists(TOKEN_FILE):
        print(f"[!] 没找到 {TOKEN_FILE}")
        return None
    with open(TOKEN_FILE, encoding="utf-8") as f:
        tok = f.read().strip()
    if not tok or "粘贴" in tok:
        print("[!] token.txt 里还没填入有效 token。")
        print("    请在浏览器登录平台后，按 F12 → Console 输入：")
        print("        localStorage.getItem('token')")
        print("    把得到的字符串粘贴进 token.txt 保存。")
        return None
    # 容错：去掉可能误带的引号 / "Bearer " 前缀
    tok = tok.strip().strip('"').strip("'")
    if tok.lower().startswith("bearer "):
        tok = tok[7:].strip()
    return tok


def verify_token(token):
    """上传前先验活 token：调一个受认证保护的只读接口确认服务器认这个登录态。
    判定逻辑只看是否被认证层拒绝（401「未登录/已过期」）——token 有效就一定
    不会收到这个响应，因此无需关心接口具体返回 200 还是别的。
    返回 True 表示有效；返回 False 时已打印清晰指引，调用方应直接中止。"""
    try:
        resp = requests.get(
            f"{PLATFORM}/api/user/profile",
            headers={"Authorization": f"Bearer {token}", **PLATFORM_HEADERS},
            timeout=30,
        )
    except Exception as e:
        print(f"[!] 无法连接平台 {PLATFORM}：{e}")
        print("    请检查网络，或确认平台地址是否正确。")
        return False

    # 解析平台统一返回体里的 code 字段（容错：解析失败就退回看 HTTP 状态码）
    rejected = resp.status_code == 401
    try:
        if resp.json().get("code") == 401:
            rejected = True
    except Exception:
        pass

    if rejected:
        print("=" * 56)
        print(f"[!] token 已失效，平台 {PLATFORM} 拒绝了当前登录态（HTTP 401）。")
        print("    本地已下载的文章不会上传，请先更新 token 再重试：")
        print()
        print("    1) 浏览器打开并登录： " + PLATFORM)
        print("    2) 按 F12 → Console（控制台），输入并回车：")
        print("           localStorage.getItem('token')")
        print("    3) 复制返回的字符串（不要带引号），整个覆盖进 token.txt 并保存。")
        print("    4) 重新运行本命令。")
        print()
        print("    若仍报失效：在 Console 先执行 localStorage.clear()，")
        print("    重新登录后再取一次 token（避免复制到缓存的旧令牌）。")
        print("=" * 56)
        return False

    # 没有被认证层拒绝 → token 至少通过了登录校验，放行
    return True


def upload_one_image(token, local_path):
    """上传单张图片到 /api/media/upload，返回平台访问 URL（失败返回 None）"""
    ext = os.path.splitext(local_path)[1].lower()
    if ext not in IMG_OK_EXT:
        print(f"    [!] 跳过不支持的图片格式: {os.path.basename(local_path)}")
        return None
    if os.path.getsize(local_path) > IMG_MAX_BYTES:
        print(f"    [!] 跳过超过 10MB 的图片: {os.path.basename(local_path)}")
        return None
    mime = {
        ".jpg": "image/jpeg", ".jpeg": "image/jpeg", ".png": "image/png",
        ".gif": "image/gif", ".webp": "image/webp",
    }[ext]
    try:
        with open(local_path, "rb") as f:
            resp = requests.post(
                f"{PLATFORM}/api/media/upload",
                headers={"Authorization": f"Bearer {token}", **PLATFORM_HEADERS},
                files={"file": (os.path.basename(local_path), f, mime)},
                data={"mediaType": "IMAGE"},
                timeout=60,
            )
        j = resp.json()
    except Exception as e:
        print(f"    [!] 图片上传异常 {os.path.basename(local_path)}: {e}")
        return None
    if j.get("code") == 200:
        mid = j["data"]["mediaId"]
        return f"/api/media/{mid}/download"
    print(f"    [!] 图片上传失败 {os.path.basename(local_path)}: {j.get('message')}")
    return None


def parse_tags(tags):
    """把用户输入的标签解析成合法标签列表（去重、保序、逐个校验）。
    支持英文逗号 / 中文逗号 / 顿号 / 空格分隔，例如：
        "科技文章,人工智能"  或  "科技文章、人工智能"  或  "科技文章 人工智能"
    非预设标签会被忽略并提示；全部非法时回退到默认标签。
    返回一个非空的合法标签列表。"""
    raw = re.split(r"[,，、\s]+", (tags or "").strip())
    valid, dropped = [], []
    for t in raw:
        t = t.strip()
        if not t:
            continue
        if t in PRESET_TAGS:
            if t not in valid:
                valid.append(t)
        else:
            dropped.append(t)
    if dropped:
        print(f"[!] 以下标签不在平台预设里，已忽略：{' / '.join(dropped)}")
        print(f"    可选标签：{' / '.join(PRESET_TAGS)}")
    if not valid:
        print(f"[!] 没有有效标签，已改用默认「{DEFAULT_TAG}」。")
        valid = [DEFAULT_TAG]
    return valid


def upload_article(token, art, tags="", difficulty="MEDIUM"):
    """把一篇已下载的文章（save_article 的返回）上传到平台"""
    # 标签必选，可多选；逐个校验后用英文逗号拼接（与平台前端一致）
    tag_list = parse_tags(tags)
    tag_str = ",".join(tag_list)
    print(f"[*] 使用标签：{' / '.join(tag_list)}")

    md = art["md"]
    safe = art["safe"]
    img_prefix = f"{safe}_images"

    # 1. 把本地图片逐张上传，换成平台 URL
    local_imgs = re.findall(rf"!\[[^\]]*\]\(({re.escape(img_prefix)}/[^)]+)\)", md)
    uniq = list(dict.fromkeys(local_imgs))
    if uniq:
        print(f"[*] 上传 {len(uniq)} 张图片到平台...")
    url_map = {}
    cover_url = None
    for rel in uniq:
        local_path = os.path.join(HERE, rel.replace("/", os.sep))
        if not os.path.exists(local_path):
            continue
        platform_url = upload_one_image(token, local_path)
        if platform_url:
            url_map[rel] = platform_url
            if cover_url is None:
                cover_url = platform_url  # 第一张图作封面

    for rel, platform_url in url_map.items():
        md = md.replace(f"]({rel})", f"]({platform_url})")
    if uniq:
        print(f"[+] 图片上传完成 {len(url_map)}/{len(uniq)} 张")

    # 2. 创建文章
    print("[*] 创建文章条目...")
    payload = {
        "title": art["title"],
        "content": "",
        "markdownContent": md,
        "contentType": "MARKDOWN",
        "difficulty": difficulty,
        "tags": tag_str,
    }
    if cover_url:
        payload["coverUrl"] = cover_url
    try:
        resp = requests.post(
            f"{PLATFORM}/api/user/texts/upload",
            headers={"Authorization": f"Bearer {token}",
                     "Content-Type": "application/json", **PLATFORM_HEADERS},
            json=payload, timeout=60,
        )
        j = resp.json()
    except Exception as e:
        print(f"[!] 文章上传异常: {e}")
        return False
    if j.get("code") == 200:
        tid = j["data"].get("textId")
        print(f"[OK] 文章已上传到平台！textId={tid}（状态：待审核）")
        return True
    print(f"[!] 文章上传失败: {j.get('message')}")
    return False


def run_upload(urls, tags=DEFAULT_TAG, difficulty="MEDIUM"):
    token = load_token()
    if not token:
        return
    # 上传前先验活，避免白下载一堆文章最后才发现 token 失效
    print(f"[*] 校验平台登录态（{PLATFORM}）...")
    if not verify_token(token):
        return
    print("[+] token 有效，开始处理。")
    if not os.path.exists(USER_DATA):
        print("[!] 还没登录过外网站点，免费文章仍可尝试。\n")
    with sync_playwright() as p:
        ctx = p.chromium.launch_persistent_context(
            USER_DATA, headless=True, user_agent=UA,
            args=["--disable-blink-features=AutomationControlled"],
        )
        ok = 0
        for u in urls:
            try:
                art = save_article(ctx, u)
                if art and upload_article(token, art, tags, difficulty):
                    ok += 1
            except Exception as e:
                print(f"[!] 出错，跳过 {u}: {e}")
        ctx.close()
        print(f"\n[完成] 成功下载并上传 {ok}/{len(urls)} 篇。")


def main():
    args = sys.argv[1:]
    if not args:
        print(__doc__)
        return

    if args[0] == "--login":
        do_login()
        return

    if args[0] == "--batch":
        if len(args) < 2:
            print("用法: python article_dl.py --batch urls.txt")
            return
        with open(args[1], encoding="utf-8") as f:
            urls = [ln.strip() for ln in f if ln.strip() and not ln.startswith("#")]
        if not urls:
            print("[!] urls.txt 里没有有效链接。")
            return
        run_download(urls)
        return

    if args[0] == "--upload":
        # python article_dl.py --upload "文章链接" [标签] [难度]
        rest = args[1:]
        if not rest:
            print('用法: python article_dl.py --upload "文章链接" [标签] [难度EASY/MEDIUM/HARD]')
            return
        url = rest[0]
        tags = rest[1] if len(rest) > 1 else DEFAULT_TAG
        difficulty = rest[2].upper() if len(rest) > 2 else "MEDIUM"
        run_upload([url], tags, difficulty)
        return

    if args[0] == "--upload-batch":
        if len(args) < 2:
            print("用法: python article_dl.py --upload-batch urls.txt")
            return
        with open(args[1], encoding="utf-8") as f:
            urls = [ln.strip() for ln in f if ln.strip() and not ln.startswith("#")]
        if not urls:
            print("[!] urls.txt 里没有有效链接。")
            return
        run_upload(urls)
        return

    run_download([args[0]])


if __name__ == "__main__":
    main()
