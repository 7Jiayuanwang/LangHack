# 听文章（首版）

阅读页显示独立的文章播放器。选择不同阅读版本时，播放器跟随切换；已有人工上传的音频仅用于原始版本，并优先于自动生成的音频。

## 首次播放与缓存

1. `GET /api/student/learning/texts/{id}/audio?version=...` 查询当前版本是否已有音频。
2. 首次播放且没有音频时，`POST` 同一路径按需生成；服务器生成完成后保存 WAV 文件，再返回音频信息。
3. 前端通过带 JWT 的 `GET /api/student/learning/texts/{id}/audio/file?version=...&hash=...` 下载文件并播放。服务端每次重新校验文章访问权限、版本和正文哈希。
4. 文件默认位于 `./uploads/article-audio/{文章ID}/{版本}/{SHA-256}.wav`。Docker 的 `/app/uploads` 已挂载为持久目录。可用 `ARTICLE_AUDIO_STORAGE_DIR` 修改位置。正文变化后哈希变化，旧音频不再被选中；旧文件暂不自动删除。

生成任务在同一进程内按文章版本去重。播放器支持进度拖动、0.75～2 倍速，以及累计播放 10/20/30/60 分钟后自动暂停；暂停时睡眠倒计时也暂停。切换文章或版本会停止旧音频。

## 播完后的知识点续播

学生开始听文章时，播放器预取小芽为当前版本、当前学习层级整理的知识点；自然播完后自动衔接知识点音频。知识点沿用现有 `article_learning_insights` 缓存，不再另生成一套讲解内容；如果当时仍在提取，会短暂等待并显示状态，超时或不可用时保留手动重试入口。手动暂停、取消续播、睡眠定时停止及切换文章/版本都不会误触发续播。

知识点音频分段朗读：英语表达和原句用英文声线，中文解释和练习建议用中文声线。生成的 WAV 存于 `./uploads/article-audio/insights/{用户ID}/{文章ID}/{内容哈希}.wav`，讲解内容变化时重新生成；文件只可由当前已登录学生通过受保护接口取得。倍速和睡眠倒计时跨文章与知识点音频延续。听完音频不会自动把阅读记录标为完成，也不会仅因预取而产生小芽未读消息。

听后知识点接口：`GET/POST /api/student/learning/texts/{id}/audio/insights?version=...` 用于查询/生成，`GET /api/student/learning/texts/{id}/audio/insights/file?version=...&hash=...` 用于下载。接口沿用文章权限校验，只对学生/普通用户开放。

## 临时语音提供器

当前 `ArticleSpeechProvider` 由 `OfflineArticleSpeechProvider` 实现：Docker/Linux 使用 `espeak-ng`，macOS 本地使用系统 `say` + `afconvert`。这是真正的离线英文朗读，不消耗现有硅基流动短文本 TTS 额度，但音色较机械。Docker 镜像需重建以安装 `espeak-ng`。本地开发环境若没有对应命令，已有音频仍可播放，但首次生成会失败并给出提示。

将来接入文章级 TTS 时，实现或替换 `ArticleSpeechProvider` 即可。更换提供器时应变更 `cacheIdentity()`，以免沿用旧音频；输出目前要求 WAV。前端倍速只改变播放速度，不额外生成文件。现有 `/api/tts/synthesize` 短文本接口保持原样，文章播放器不会调用它。

首版单篇正文上限为 30,000 个清理后字符；生成是同步请求，可能需要等待。后续可加入后台任务、分段生成、压缩格式、旧文件回收和跨实例锁。
