<template>
  <div class="video-container">
    <el-card class="video-card">
      <template #header>
        <div class="card-header">
          <span>AI 实时字幕视频播放器</span>
        </div>
      </template>

      <!-- 视频链接输入区 -->
      <div class="input-section">
        <el-input
          v-model="videoUrl"
          placeholder="支持：B站 / YouTube / 腾讯视频 / 优酷 / 抖音 / 西瓜视频"
          clearable
        >
          <template #append>
            <el-button @click="loadVideo" type="primary">播放</el-button>
          </template>
        </el-input>
      </div>

      <!-- 视频播放与字幕叠加区 -->
      <div class="player-wrapper" v-if="currentUrl">
        <!-- iframe 嵌入（支持所有已知平台） -->
        <iframe
          v-if="embedUrl"
          class="video-player"
          :src="embedUrl"
          scrolling="no"
          frameborder="0"
          allowfullscreen
          allow="autoplay; fullscreen"
        ></iframe>

        <!-- 不支持的链接提示 -->
        <div v-else class="unsupported-hint">
          <el-empty description="暂不支持该平台，请粘贴 B站 / YouTube / 腾讯视频 / 优酷 / 抖音 / 西瓜视频 的链接" />
        </div>

        <!-- 字幕叠加层 -->
        <div class="subtitle-overlay" v-if="showSubtitleOverlay">
          <div class="subtitle-source" v-if="currentSubtitleSource">{{ currentSubtitleSource }}</div>
          <div class="subtitle-target" v-if="currentSubtitleTarget">{{ currentSubtitleTarget }}</div>
          <div class="subtitle-listening" v-if="captureActive && !currentSubtitleSource">正在监听...</div>
        </div>
      </div>

      <!-- 控制面板 -->
      <div class="controls-section" v-if="currentUrl">

        <!-- 翻译方向 -->
        <div class="control-row">
          <span class="control-label">翻译方向：</span>
          <el-radio-group v-model="translateDirection">
            <el-radio value="en-to-zh">英译中</el-radio>
            <el-radio value="zh-to-en">中译英</el-radio>
          </el-radio-group>
        </div>

        <!-- ── B站专区：字幕获取 + 同步 ── -->
        <div v-if="isBilibili" class="bilibili-subtitle-section">
          <div class="section-label">B 站 CC 字幕翻译</div>

          <!-- 获取字幕按钮 -->
          <div class="control-row" v-if="!subtitleData.length">
            <el-button
              type="primary"
              @click="fetchSubtitles"
              :loading="subtitleLoading"
            >
              获取视频字幕
            </el-button>
            <span class="control-hint" v-if="!subtitleLoading">点击后自动抓取并翻译 CC 字幕</span>
          </div>

          <!-- 加载提示 -->
          <div v-if="subtitleLoading" class="loading-hint">
            正在获取字幕并翻译，请稍候...（首次可能需要 10~30 秒）
          </div>

          <!-- 错误提示 -->
          <el-alert
            v-if="subtitleError"
            :title="subtitleError"
            type="warning"
            show-icon
            :closable="false"
          />

          <!-- 字幕已加载：同步控制 -->
          <template v-if="subtitleData.length">
            <div class="subtitle-loaded-info">
              已加载 {{ subtitleData.length }} 条字幕
              <el-button size="small" text @click="clearSubtitles">重新获取</el-button>
            </div>

            <!-- 同步操作说明 -->
            <el-alert
              type="info"
              show-icon
              :closable="false"
              title="使用方法：先在上方 B 站播放器里点击播放，然后立即点击下方「开始同步」按钮，字幕将自动与视频对应。"
            />

            <div class="control-row sync-row">
              <el-button
                v-if="!syncActive"
                type="success"
                @click="startSync"
              >
                ▶ 开始同步字幕
              </el-button>
              <template v-else>
                <el-button type="warning" @click="pauseSync" v-if="!syncPaused">⏸ 暂停</el-button>
                <el-button type="success" @click="resumeSync" v-else>▶ 继续</el-button>
                <el-button type="danger" plain @click="stopSync">■ 停止</el-button>
                <span class="elapsed-time">{{ formattedElapsed }}</span>
              </template>
            </div>
          </template>
        </div>

        <!-- ── 非 B站：标签页音频捕获实时识别 ── -->
        <template v-if="!isBilibili">
          <div class="control-row">
            <el-button v-if="!captureActive" type="success" @click="startTabCapture">
              捕获标签页音频（实时识别+翻译）
            </el-button>
            <el-button v-else type="danger" @click="stopTabCapture">停止捕获</el-button>
            <el-tag v-if="captureActive" type="success" effect="light">识别中...</el-tag>
          </div>
        </template>

        <!-- 文字翻译面板（通用） -->
        <div class="translate-panel">
          <div class="panel-title">
            文字翻译
            <span class="panel-subtitle">（{{ translateDirection === 'en-to-zh' ? '英文输入 → 中文结果' : '中文输入 → 英文结果' }}）</span>
          </div>
          <el-input
            v-model="translateInput"
            type="textarea"
            :rows="4"
            :placeholder="translateDirection === 'en-to-zh' ? '请输入英文文本...' : '请输入中文文本...'"
          />
          <div class="translate-actions">
            <el-button type="primary" @click="doTranslate" :loading="translating">翻译</el-button>
            <el-button @click="clearTranslate">清空</el-button>
          </div>
          <div class="translate-result" v-if="translateResult">
            <div class="result-label">{{ translateDirection === 'en-to-zh' ? '中文翻译结果：' : '英文翻译结果：' }}</div>
            <div class="result-text">{{ translateResult }}</div>
          </div>
        </div>

      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'

// ─── 基础状态 ──────────────────────────────────────────────
const videoUrl    = ref('')
const currentUrl  = ref('')
const translateDirection = ref('en-to-zh')

// ─── 平台识别 & embed URL ──────────────────────────────────
const detectedPlatform = computed(() => {
  const url = currentUrl.value
  if (/bilibili\.com\/video\/(BV[\w]+)/i.test(url))                         return 'bilibili'
  if (/(?:youtube\.com\/(?:watch|shorts\/)|youtu\.be\/)/i.test(url))         return 'youtube'
  if (/v\.qq\.com\/x\/(?:cover|page)/i.test(url))                            return 'qq'
  if (/v\.youku\.com\/v_show\/id_/i.test(url))                               return 'youku'
  if (/(?:www\.douyin\.com\/video\/)\d+/i.test(url))                         return 'douyin'
  if (/ixigua\.com\/\d+/i.test(url))                                         return 'xigua'
  return 'unknown'
})

const isBilibili = computed(() => detectedPlatform.value === 'bilibili')

const embedUrl = computed(() => {
  const url = currentUrl.value
  switch (detectedPlatform.value) {
    case 'bilibili': {
      const m = url.match(/bilibili\.com\/video\/(BV[\w]+)/i)
      return m ? `https://player.bilibili.com/player.html?bvid=${m[1]}&high_quality=1&danmaku=0` : ''
    }
    case 'youtube': {
      const m = url.match(/(?:youtube\.com\/(?:watch\?(?:.*&)?v=|shorts\/)|youtu\.be\/)([A-Za-z0-9_-]{11})/)
      return m ? `https://www.youtube.com/embed/${m[1]}?rel=0` : ''
    }
    case 'qq': {
      const m = url.match(/v\.qq\.com\/x\/(?:cover\/[^/]+\/|page\/)([A-Za-z0-9]+)\.html/)
      return m ? `https://v.qq.com/iframe/player.html?vid=${m[1]}&auto=0` : ''
    }
    case 'youku': {
      const m = url.match(/v\.youku\.com\/v_show\/id_([A-Za-z0-9=_]+)\.html/)
      return m ? `https://player.youku.com/embed/${m[1]}` : ''
    }
    case 'douyin': {
      const m = url.match(/douyin\.com\/video\/(\d+)/)
      return m ? `https://www.douyin.com/video/${m[1]}` : ''
    }
    case 'xigua': {
      const m = url.match(/ixigua\.com\/(\d+)/)
      return m ? `https://www.ixigua.com/iframe/${m[1]}?autoplay=0` : ''
    }
    default:
      return ''
  }
})

const bvid = computed(() => {
  const m = currentUrl.value.match(/bilibili\.com\/video\/(BV[\w]+)/i)
  return m ? m[1] : ''
})

// ─── 字幕叠加层 ────────────────────────────────────────────
const currentSubtitleSource = ref('')
const currentSubtitleTarget = ref('')

const showSubtitleOverlay = computed(() =>
  captureActive.value || syncActive.value
)

// ─── B站字幕：获取与同步 ──────────────────────────────────
const subtitleData    = ref([])   // [{from, to, source, translated}]
const subtitleLoading = ref(false)
const subtitleError   = ref('')

// 同步计时器
const syncActive  = ref(false)
const syncPaused  = ref(false)
const syncElapsed = ref(0)        // 已播放秒数
let syncInterval  = null
let syncPauseAt   = 0             // 暂停时的 elapsed，用于继续

const formattedElapsed = computed(() => {
  const s = Math.floor(syncElapsed.value)
  const m = Math.floor(s / 60)
  return `${String(m).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`
})

const fetchSubtitles = async () => {
  if (!bvid.value) return
  subtitleLoading.value = true
  subtitleError.value   = ''
  subtitleData.value    = []
  try {
    // Step 1: 通过后端代理获取 cid（避免浏览器直连 B 站的 CORS/DNS 问题）
    const viewRes  = await fetch(`/api/bilibili/view?bvid=${bvid.value}`)
    if (!viewRes.ok) throw new Error('B站视频信息请求失败，HTTP ' + viewRes.status)
    const viewJson = await viewRes.json()
    if (viewJson.code !== 0) throw new Error('B站API错误：' + viewJson.message)
    const cid = viewJson.data?.cid
    if (!cid) throw new Error('B站视频数据异常：无法获取 cid')

    // Step 2: 通过后端代理获取字幕列表
    const playerRes  = await fetch(`/api/bilibili/player?bvid=${bvid.value}&cid=${cid}`)
    if (!playerRes.ok) throw new Error('获取字幕列表请求失败，HTTP ' + playerRes.status)
    const playerJson = await playerRes.json()
    if (playerJson.code !== 0) throw new Error('获取字幕列表失败：' + (playerJson.message || '未知错误'))
    const list = playerJson.data?.subtitle?.list
    if (!list || list.length === 0) throw new Error('该视频暂无可用字幕（视频可能未开启 CC 字幕）')

    // Step 3: 优先选择与翻译方向匹配的字幕语言
    const preferLan = translateDirection.value.startsWith('en') ? 'en' : 'zh'
    const best = list.find(s => s.lan?.startsWith(preferLan)) || list[0]
    let subtitleUrl = best.subtitle_url || ''
    if (subtitleUrl.startsWith('//')) subtitleUrl = 'https:' + subtitleUrl
    if (!subtitleUrl) throw new Error('字幕文件链接为空')

    // Step 4: 通过后端代理下载字幕文件
    const subRes  = await fetch(`/api/bilibili/subtitle-file?url=${encodeURIComponent(subtitleUrl)}`)
    if (!subRes.ok) throw new Error('字幕文件请求失败，HTTP ' + subRes.status)
    const subJson = await subRes.json()
    if (subJson.code === -1) throw new Error(subJson.message || '字幕文件下载失败')
    const body    = subJson.body
    if (!body || !body.length) throw new Error('字幕内容为空')

    // Step 5: 调用后端批量翻译
    const texts    = body.map(item => item.content || '')
    const transRes = await fetch('/api/translate/batch', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify({ texts, direction: translateDirection.value })
    })
    if (!transRes.ok) throw new Error('翻译请求失败，HTTP ' + transRes.status)
    const transJson = await transRes.json()
    if (transJson.code !== 200) throw new Error(transJson.message || '翻译失败')

    // Step 6: 组装字幕数据
    const translations = Array.isArray(transJson.data) ? transJson.data : []
    subtitleData.value = body.map((item, i) => ({
      from:       item.from,
      to:         item.to,
      source:     item.content || '',
      translated: translations[i] || ''
    }))
    ElMessage.success(`字幕加载完成，共 ${subtitleData.value.length} 条`)
  } catch (e) {
    subtitleError.value = '获取字幕失败，请稍后重试'
    console.error('字幕获取错误:', e)
  } finally {
    subtitleLoading.value = false
  }
}

const startSync = () => {
  syncElapsed.value = 0
  syncActive.value  = true
  syncPaused.value  = false
  const startAt = Date.now()
  syncInterval = setInterval(() => {
    syncElapsed.value = (Date.now() - startAt) / 1000
    updateSyncSubtitle()
  }, 300)
}

const pauseSync = () => {
  syncPaused.value = true
  syncPauseAt = syncElapsed.value
  clearInterval(syncInterval)
}

const resumeSync = () => {
  syncPaused.value = false
  clearInterval(syncInterval)
  const resumeAt = Date.now() - syncPauseAt * 1000
  syncInterval = setInterval(() => {
    syncElapsed.value = (Date.now() - resumeAt) / 1000
    updateSyncSubtitle()
  }, 300)
}

const stopSync = () => {
  syncActive.value  = false
  syncPaused.value  = false
  syncElapsed.value = 0
  clearInterval(syncInterval)
  currentSubtitleSource.value = ''
  currentSubtitleTarget.value = ''
}

const clearSubtitles = () => {
  stopSync()
  subtitleData.value  = []
  subtitleError.value = ''
}

const updateSyncSubtitle = () => {
  const t = syncElapsed.value
  const item = subtitleData.value.find(s => s.from <= t && t < s.to)
  if (item) {
    currentSubtitleSource.value = item.source     || ''
    currentSubtitleTarget.value = item.translated || ''
  } else {
    currentSubtitleSource.value = ''
    currentSubtitleTarget.value = ''
  }
}

// ─── 翻译 API ──────────────────────────────────────────────
const callTranslateApi = async (text) => {
  const endpoint = translateDirection.value === 'en-to-zh'
    ? '/api/translate/en-to-cn'
    : '/api/translate/cn-to-en'
  const res  = await fetch(endpoint, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
    },
    body: JSON.stringify({ text })
  })
  if (!res.ok) throw new Error('翻译请求失败，HTTP ' + res.status)
  const json = await res.json()
  if (json.code !== 200) throw new Error(json.message || '翻译失败')
  return json.data
}

// ─── 文字翻译面板 ──────────────────────────────────────────
const translateInput  = ref('')
const translateResult = ref('')
const translating     = ref(false)

const doTranslate = async () => {
  if (!translateInput.value.trim()) { ElMessage.warning('请输入要翻译的文本'); return }
  translating.value = true
  translateResult.value = ''
  try {
    translateResult.value = await callTranslateApi(translateInput.value.trim())
  } catch (e) {
    ElMessage.error('翻译失败，请稍后重试')
    console.error('翻译API错误:', e)
  } finally {
    translating.value = false
  }
}

const clearTranslate = () => { translateInput.value = ''; translateResult.value = '' }

// ─── 标签页音频捕获（普通视频用） ──────────────────────────
const captureActive = ref(false)
let captureStream   = null
let captureRecorder = null

const startTabCapture = async () => {
  if (!navigator.mediaDevices?.getDisplayMedia) {
    ElMessage.error('当前浏览器不支持 getDisplayMedia，请使用 Chrome 最新版')
    return
  }
  try {
    const stream = await navigator.mediaDevices.getDisplayMedia({ audio: true, video: { width: 1, height: 1 } })
    stream.getVideoTracks().forEach(t => t.stop())
    const audioTracks = stream.getAudioTracks()
    if (!audioTracks.length) { ElMessage.warning('未捕获到音频，请在弹窗中勾选"分享标签页音频"'); return }
    captureStream = new MediaStream(audioTracks)
    captureActive.value = true
    audioTracks[0].addEventListener('ended', stopTabCapture)
    runRecordingCycle()
  } catch (e) {
    if (e.name !== 'NotAllowedError') {
      ElMessage.error('捕获失败，请重试')
      console.error('音频捕获错误:', e)
    }
  }
}

const stopTabCapture = () => {
  captureActive.value = false
  if (captureRecorder?.state === 'recording') captureRecorder.stop()
  captureStream?.getTracks().forEach(t => {
    t.removeEventListener('ended', stopTabCapture)
    t.stop()
  })
  captureStream = null
  captureRecorder = null
  currentSubtitleSource.value = ''
  currentSubtitleTarget.value = ''
}

const runRecordingCycle = () => {
  if (!captureActive.value || !captureStream) return
  const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : 'audio/webm'
  const chunks = []
  const recorder = new MediaRecorder(captureStream, { mimeType })
  captureRecorder = recorder
  recorder.ondataavailable = e => { if (e.data.size > 0) chunks.push(e.data) }
  recorder.onstop = () => {
    if (captureActive.value) runRecordingCycle()
    if (chunks.length) processAudioChunk(new Blob(chunks, { type: 'audio/webm' }))
  }
  recorder.start()
  setTimeout(() => { if (recorder.state === 'recording') recorder.stop() }, 5000)
}

const processAudioChunk = (blob) => {
  const reader = new FileReader()
  reader.onloadend = async () => {
    if (!captureActive.value) return
    if (!reader.result || typeof reader.result !== 'string') return
    const base64 = reader.result.split(',')[1]
    if (!base64) return
    try {
      const asrLang = translateDirection.value === 'en-to-zh' ? 'en' : 'zh'
      const res  = await fetch('/api/asr/recognize', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${localStorage.getItem('token') || ''}` },
        body: JSON.stringify({ audioData: base64, format: 'webm', lang: asrLang })
      })
      if (!res.ok) return
      const json = await res.json()
      if (json.code !== 200 || !json.data) return
      currentSubtitleSource.value = json.data
      currentSubtitleTarget.value = await callTranslateApi(json.data)
    } catch (e) { console.error('ASR/翻译失败：', e) }
  }
  reader.readAsDataURL(blob)
}

const loadVideo = () => {
  if (!videoUrl.value) { ElMessage.warning('请输入有效的视频链接'); return }
  currentUrl.value = videoUrl.value
  currentSubtitleSource.value = ''
  currentSubtitleTarget.value = ''
  subtitleData.value = []
  subtitleError.value = ''
  stopSync()
  stopTabCapture()
}

onUnmounted(() => {
  stopTabCapture()
  stopSync()
})
</script>

<style scoped>
.video-container {
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
.card-header { font-weight: bold; font-size: 1.1em; }
.input-section { margin-bottom: 20px; }

.player-wrapper {
  position: relative;
  width: 100%;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 12px rgba(0,0,0,.1);
  display: flex;
  justify-content: center;
}
.video-player { width: 100%; height: 560px; outline: none; border: none; }
.unsupported-hint {
  width: 100%;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 8px;
}

/* 字幕叠加 */
.subtitle-overlay {
  position: absolute;
  bottom: 60px;
  left: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  pointer-events: none;
}
.subtitle-source, .subtitle-target {
  max-width: 82%;
  padding: 4px 14px;
  background: rgba(0,0,0,.72);
  color: #fff;
  border-radius: 4px;
  font-size: 18px;
  text-shadow: 1px 1px 2px #000;
  backdrop-filter: blur(2px);
  text-align: center;
  white-space: pre-wrap;
}
.subtitle-target { font-size: 16px; color: #fceea7; }
.subtitle-listening { padding: 4px 14px; background: rgba(0,0,0,.55); color: #a0cfff; border-radius: 4px; font-size: 14px; }

/* 控制面板 */
.controls-section {
  margin-top: 20px;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.control-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.control-label { font-size: 14px; color: #606266; white-space: nowrap; }
.control-hint { font-size: 13px; color: #909399; }

/* B站字幕区 */
.bilibili-subtitle-section {
  border: 1px solid #dbeafe;
  border-radius: 6px;
  padding: 12px 14px;
  background: #f0f7ff;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.section-label { font-size: 14px; font-weight: 600; color: #1d4ed8; }
.loading-hint { font-size: 13px; color: #6b7280; }
.subtitle-loaded-info {
  font-size: 13px;
  color: #059669;
  display: flex;
  align-items: center;
  gap: 8px;
}
.sync-row { flex-wrap: wrap; }
.elapsed-time { font-family: monospace; font-size: 15px; color: #374151; }

/* 文字翻译面板 */
.translate-panel {
  border-top: 1px solid #e4e7ed;
  padding-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.panel-title { font-size: 14px; font-weight: 600; color: #303133; }
.panel-subtitle { font-weight: normal; color: #909399; font-size: 13px; }
.translate-actions { display: flex; gap: 8px; }
.translate-result { padding: 12px; background: #fff; border: 1px solid #e4e7ed; border-radius: 6px; }
.result-label { font-size: 13px; color: #909399; margin-bottom: 6px; }
.result-text { font-size: 15px; color: #303133; line-height: 1.7; white-space: pre-wrap; }
</style>
