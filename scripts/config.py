# LangHack 配置(请勿提交到 git)
# ======================================================================
# Token 获取方法(任选其一,推荐第 1 种):
#   1. 浏览器登录 https://isa.imau.edu.cn -> F12 -> Console ->
#      localStorage.getItem('token') -> 复制返回值
#   2. POST /api/auth/sso/login-url 拿跳转链接 -> 浏览器登录 ->
#      地址栏 ?code=xxx -> POST /api/auth/sso/token 换 JWT
# Token 有效期 24 小时,过期重新获取。
# 注:isa.imau.edu.cn 与 langhack.imau.edu.cn 共用同一套 API,改域名即可切换。
# ======================================================================

# 平台域名(单选,每次只放开一个,注释另一个即可切换)
# 平台1:内蒙古农大校内站点
#LANGHACK_URL = "https://isa.imau.edu.cn"
# 平台2:Langhack 主站
LANGHACK_URL = "https://langhack.imau.edu.cn"

# 双 token 配置(分别填写两平台自己的 access/refresh)
#ISA_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiU1RVREVOVCIsInN1YiI6IjIwMjUxMTIxMDQxNjAiLCJpYXQiOjE3ODE3NTQ2OTQsImV4cCI6MTc4NDM0NjY5NH0.Xzh_zpT8Y2xhAVWKFz-uC2rJk4cX9z4-e2O1LIMEnNA"
#ISA_REFRESH_TOKEN = ""
LH_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJyb2xlIjoiU1RVREVOVCIsInR5cGUiOiJhY2Nlc3MiLCJzdWIiOiIyMDI1MTEyMTA0MTYwIiwiaWF0IjoxNzgxODQ1OTgyLCJleHAiOjE3ODE4NTMxODJ9.XytE1_h9djg3icUOeze7Rh6pxsbE5tm8XPu4oafP_C8"
LH_REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.eyJ0eXBlIjoicmVmcmVzaCIsInN1YiI6IjIwMjUxMTIxMDQxNjAiLCJpYXQiOjE3ODE4NDU5ODIsImV4cCI6MTc4NDQzNzk4Mn0.Tr8UGxdjUTLwj1R9dQwSnVYuUcYJrM2WJhH1vQWwPo8"

# 兼容旧配置:如果你当前只拿到一套 access_token,可先临时填这里
TOKEN = ""

# access_token 提前 30 分钟自动刷新
TOKEN_REFRESH_THRESHOLD = 30 * 60

# 兼容旧名(脚本内部统一使用 BASE_URL)
BASE_URL = LANGHACK_URL

# yt-dlp 路径(相对当前目录)
YT_DLP = "yt-dlp.exe"
DENO  = "deno.exe"
FFMPEG = "ffmpeg.exe"
COOKIES = "cookies.txt"

# 队列文件(每行一条: URL | 标题(可空) | 分类标识 | description(可空))
QUEUE_FILE = "video_queue.txt"

# 平台预选标签(单选,前端下拉预设,后端 tags 字段也只接受这些标签之一)
TAG_MAP = {
    "daily": "日常对话",
    "business": "商务英语",
    "academic": "学术英语",
    "tech": "科技文章",
    "news": "新闻资讯",
    "literature": "文学小说",
    "security": "网络安全",
    "ai": "人工智能",
    "english_song": "英文歌曲",
    "world_cup": "世界杯",
    "cet": "四六级",
}
# 未在 TAG_MAP 中识别分类时,允许的兜底单选标签
DEFAULT_TAG = "日常对话"
DEFAULT_DESCRIPTION = "Educational video from YouTube"

# 上传保护阈值(本地目标值,不是服务器真实上限)
# 当视频超过此大小时,会提示用户选择是否压缩
UPLOAD_MAX_MB = 100  # 提高阈值,允许上传更大的视频
UPLOAD_PROMPT_COMPRESS_MB = 50  # 超过此大小时提示用户选择是否压缩

# 是否自动压缩(False = 总是询问用户, True = 超过阈值自动压缩)
AUTO_COMPRESS = False

UPLOAD_COMPRESS_PROFILES = [
    {"label": "480p", "scale": "-2:480", "crf": 30, "audio_bitrate": "96k"},
    {"label": "360p", "scale": "-2:360", "crf": 32, "audio_bitrate": "80k"},
    {"label": "240p", "scale": "-2:240", "crf": 34, "audio_bitrate": "64k"},
    {"label": "180p", "scale": "-2:180", "crf": 36, "audio_bitrate": "48k"},
    {"label": "144p", "scale": "-2:144", "crf": 38, "audio_bitrate": "32k"},
]

# 下载压缩阈值(下载后自动压缩到此值以内,避免大批量时占用磁盘)
# 推荐 20-25MB:5-10 分钟视频会优先停在 360p,画质明显优于 240p
DOWNLOAD_MAX_MB = 100
HEIGHT_LIMIT = 720

