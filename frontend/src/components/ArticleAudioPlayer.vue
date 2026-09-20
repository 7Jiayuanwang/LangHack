<template>
  <section class="article-audio-player" aria-label="听文章">
    <div class="audio-heading">
      <div>
        <strong>🎧 {{ playbackStage === 'insights' ? '听小芽讲知识点' : '听文章' }}</strong>
        <span>{{ playbackStage === 'insights' ? '接着听文章里的知识点' : audioInfo?.source === 'uploaded' ? '文章原声' : '离线英文朗读' }}</span>
      </div>
      <span v-if="audioInfo?.ready" class="audio-ready">音频已保存</span>
    </div>

    <div class="audio-main-controls">
      <button type="button" class="audio-play-button" :disabled="busy" :aria-label="awaitingInsights ? '取消知识点续播' : isPlaying ? '暂停音频' : '播放音频'" @click="togglePlayback">
        {{ busy ? '生成中…' : awaitingInsights ? '取消续播' : isPlaying ? '暂停' : '播放' }}
      </button>
      <span class="audio-time">{{ formatTime(currentTime) }}</span>
      <input
        class="audio-progress" type="range" min="0" :max="Math.max(duration, 1)" step="1"
        :value="currentTime" :disabled="!duration" aria-label="文章音频播放进度" @input="seek"
      />
      <span class="audio-time">{{ formatTime(duration) }}</span>
    </div>

    <div v-if="enableInsights" class="insight-queue" aria-live="polite">
      <span v-if="awaitingInsights">文章已播完，小芽正在准备知识点音频…</span>
      <span v-else-if="insightLoading && playbackStage === 'article'">小芽正在准备听后知识点</span>
      <span v-else-if="insightMessage">{{ insightMessage }}</span>
      <span v-else-if="insightInfo?.status === 'READY' && playbackStage === 'article'">播完文章后将自动播放 {{ insightInfo.points?.length || 0 }} 个知识点</span>
      <button v-if="playbackStage === 'insights'" type="button" @click="replayArticle">重听文章</button>
      <button v-else-if="insightPlayableUrl && !isPlaying && !awaitingInsights" type="button" @click="playInsights">播放知识点</button>
      <button v-else-if="insightMessage && !awaitingInsights && !insightLoading" type="button" @click="playInsights">重试准备知识点</button>
    </div>
    <div v-if="playbackStage === 'insights' && insightInfo?.points?.length" class="insight-list">
      <strong>本篇知识点</strong>
      <article v-for="(point, index) in insightInfo.points" :key="index">
        <b>{{ index + 1 }}. {{ point.expression }}</b>
        <span>{{ point.explanation }}</span>
        <small v-if="point.sourceSentence">原句：{{ point.sourceSentence }}</small>
        <small v-if="point.practiceTip">{{ point.practiceTip }}</small>
      </article>
    </div>

    <div class="audio-options">
      <label>倍速
        <select v-model.number="playbackRate" aria-label="播放速度" @change="applySpeed">
          <option v-for="rate in speeds" :key="rate" :value="rate">{{ rate }}×</option>
        </select>
      </label>
      <label>睡眠模式
        <select v-model.number="sleepMinutes" aria-label="睡眠模式定时停止" @change="changeSleepTimer">
          <option :value="0">关闭</option>
          <option :value="10">10 分钟</option>
          <option :value="20">20 分钟</option>
          <option :value="30">30 分钟</option>
          <option :value="60">60 分钟</option>
        </select>
      </label>
      <span v-if="sleepMinutes > 0" class="sleep-remaining">{{ sleepExpired ? '已自动停止' : `剩余 ${formatTime(sleepRemaining)}` }}</span>
    </div>

    <p v-if="errorMessage" class="audio-error" role="alert">{{ errorMessage }}</p>
    <p v-else-if="audioInfo?.source !== 'uploaded'" class="audio-note">首次播放会离线生成并保存音频；目前音色较基础，不使用计费 TTS 服务。</p>
    <audio ref="audioElement" :src="playableUrl || undefined" preload="metadata"
      @loadedmetadata="onMetadata" @timeupdate="onTimeUpdate" @play="onPlay" @pause="onPause"
      @ended="onEnded" @error="onAudioError" />
  </section>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import {
  downloadArticleAudio, generateArticleAudio, getArticleAudioStatus,
  getListeningInsightAudioStatus, generateListeningInsightAudio, downloadListeningInsightAudio
} from '@/api/articleAudio'

const props = defineProps({
  textId: { type: Number, required: true },
  version: { type: String, default: 'original' },
  assignmentId: { type: [String, Number], default: null },
  enableInsights: { type: Boolean, default: true }
})

const speeds = [0.75, 1, 1.25, 1.5, 2]
const audioElement = ref(null)
const audioInfo = ref(null)
const playableUrl = ref('')
const articlePlayableUrl = ref('')
const insightPlayableUrl = ref('')
const insightInfo = ref(null)
const insightLoading = ref(false)
const insightMessage = ref('')
const awaitingInsights = ref(false)
const continuationCancelled = ref(false)
const playbackStage = ref('article')
const busy = ref(false)
const isPlaying = ref(false)
const currentTime = ref(0)
const duration = ref(0)
const playbackRate = ref(1)
const sleepMinutes = ref(0)
const sleepRemaining = ref(0)
const sleepExpired = ref(false)
const errorMessage = ref('')
let articleObjectUrl = ''
let insightObjectUrl = ''
let requestSerial = 0
let sleepInterval = null
let lastTickAt = 0
let insightTask = null
let continuationTask = null

const formatTime = seconds => {
  const total = Math.max(0, Math.floor(Number(seconds) || 0))
  return `${Math.floor(total / 60)}:${String(total % 60).padStart(2, '0')}`
}

const clearSleepInterval = () => {
  if (sleepInterval) clearInterval(sleepInterval)
  sleepInterval = null
}

const tickSleep = () => {
  if (!lastTickAt || !isPlaying.value || sleepMinutes.value === 0) return
  const now = Date.now()
  sleepRemaining.value = Math.max(0, sleepRemaining.value - (now - lastTickAt) / 1000)
  lastTickAt = now
  if (sleepRemaining.value <= 0) {
    clearSleepInterval()
    sleepExpired.value = true
    isPlaying.value = false
    audioElement.value?.pause()
  }
}

const onPlay = () => {
  isPlaying.value = true
  if (playbackStage.value === 'article' && props.enableInsights && !insightPlayableUrl.value) {
    void prepareInsights(requestSerial)
  }
  if (sleepMinutes.value > 0) {
    if (sleepRemaining.value <= 0) sleepRemaining.value = sleepMinutes.value * 60
    sleepExpired.value = false
    lastTickAt = Date.now()
    clearSleepInterval()
    sleepInterval = setInterval(tickSleep, 1000)
  }
}

const onPause = () => {
  tickSleep()
  isPlaying.value = false
  lastTickAt = 0
  clearSleepInterval()
}

const onEnded = () => {
  onPause()
  currentTime.value = duration.value
  if (playbackStage.value === 'article' && props.enableInsights && !sleepExpired.value) {
    continuationCancelled.value = false
    awaitingInsights.value = true
    void continueWithInsights(requestSerial)
  }
}

const changeSleepTimer = () => {
  clearSleepInterval()
  sleepRemaining.value = sleepMinutes.value * 60
  sleepExpired.value = false
  lastTickAt = 0
  if (isPlaying.value && sleepMinutes.value > 0) {
    lastTickAt = Date.now()
    sleepInterval = setInterval(tickSleep, 1000)
  }
}

const applySpeed = () => {
  if (audioElement.value) audioElement.value.playbackRate = playbackRate.value
}

const onMetadata = () => {
  duration.value = Number.isFinite(audioElement.value?.duration) ? audioElement.value.duration : 0
  applySpeed()
}

const onTimeUpdate = () => { currentTime.value = audioElement.value?.currentTime || 0 }
const seek = event => {
  if (!audioElement.value || !duration.value) return
  audioElement.value.currentTime = Number(event.target.value)
  onTimeUpdate()
}

const onAudioError = () => {
  if (playableUrl.value) errorMessage.value = '音频播放失败，请稍后重试'
}

const releaseAudio = () => {
  if (audioElement.value) {
    audioElement.value.pause()
    audioElement.value.removeAttribute('src')
    audioElement.value.load()
  }
  if (articleObjectUrl) URL.revokeObjectURL(articleObjectUrl)
  if (insightObjectUrl) URL.revokeObjectURL(insightObjectUrl)
  articleObjectUrl = ''
  insightObjectUrl = ''
  playableUrl.value = ''
  articlePlayableUrl.value = ''
  insightPlayableUrl.value = ''
  insightInfo.value = null
  insightLoading.value = false
  insightMessage.value = ''
  awaitingInsights.value = false
  continuationCancelled.value = false
  playbackStage.value = 'article'
  insightTask = null
  continuationTask = null
  isPlaying.value = false
  currentTime.value = 0
  duration.value = 0
  lastTickAt = 0
  clearSleepInterval()
}

watch(() => [props.textId, props.version, props.assignmentId], async () => {
  const requestId = ++requestSerial
  releaseAudio()
  audioInfo.value = null
  errorMessage.value = ''
  busy.value = false
  changeSleepTimer()
  if (!props.textId) return
  try {
    const result = await getArticleAudioStatus(props.textId, props.version, props.assignmentId)
    if (requestId !== requestSerial) return
    audioInfo.value = result.data
    if (result.data?.source === 'uploaded' && result.data.ready) {
      articlePlayableUrl.value = result.data.audioUrl
      playableUrl.value = result.data.audioUrl
    }
  } catch (error) {
    if (requestId === requestSerial) errorMessage.value = error.message || '无法检查文章音频'
  }
}, { immediate: true })

const togglePlayback = async () => {
  if (busy.value || !audioElement.value) return
  if (awaitingInsights.value) {
    awaitingInsights.value = false
    continuationCancelled.value = true
    insightMessage.value = '已取消自动续播，可稍后手动播放知识点'
    return
  }
  if (isPlaying.value) {
    audioElement.value.pause()
    return
  }
  if (playbackStage.value === 'insights') {
    try {
      await audioElement.value.play()
    } catch {
      errorMessage.value = '知识点音频已就绪，请再点一次播放'
    }
    return
  }
  const requestId = requestSerial
  busy.value = true
  errorMessage.value = ''
  try {
    let info = audioInfo.value
    if (!info?.ready) {
      const result = await generateArticleAudio(props.textId, props.version, props.assignmentId)
      if (requestId !== requestSerial) return
      info = result.data
      audioInfo.value = info
    }
    if (!info?.ready) throw new Error('文章音频尚未就绪')
    if (!articlePlayableUrl.value) {
      if (info.source === 'uploaded') {
        articlePlayableUrl.value = info.audioUrl
      } else {
        const response = await downloadArticleAudio(props.textId, props.version, info.hash, props.assignmentId)
        if (requestId !== requestSerial) return
        articleObjectUrl = URL.createObjectURL(response.data)
        articlePlayableUrl.value = articleObjectUrl
      }
    }
    playableUrl.value = articlePlayableUrl.value
    await nextTick()
    if (requestId !== requestSerial) return
    applySpeed()
    await audioElement.value.play()
  } catch (error) {
    if (requestId === requestSerial) {
      errorMessage.value = error.name === 'NotAllowedError'
        ? '音频已就绪，请再点一次播放'
        : (error.message || '文章音频暂时无法播放')
    }
  } finally {
    if (requestId === requestSerial) busy.value = false
  }
}

const prepareInsights = requestId => {
  if (!props.enableInsights) return Promise.resolve(false)
  if (insightPlayableUrl.value) return Promise.resolve(true)
  if (insightTask) return insightTask
  insightLoading.value = true
  insightTask = (async () => {
    for (let attempt = 0; attempt < 16; attempt++) {
      const response = await getListeningInsightAudioStatus(props.textId, props.version, props.assignmentId)
      if (requestId !== requestSerial) return false
      let info = response.data
      insightInfo.value = info
      if (info?.status === 'READY') {
        if (!info.audioReady) {
          const generated = await generateListeningInsightAudio(props.textId, props.version, props.assignmentId)
          if (requestId !== requestSerial) return false
          info = generated.data
          insightInfo.value = info
        }
        if (info?.audioReady && info.hash) {
          const audio = await downloadListeningInsightAudio(props.textId, props.version, info.hash, props.assignmentId)
          if (requestId !== requestSerial) return false
          insightObjectUrl = URL.createObjectURL(audio.data)
          insightPlayableUrl.value = insightObjectUrl
          if (!continuationCancelled.value) insightMessage.value = ''
          return true
        }
      }
      if (info?.status === 'UNAVAILABLE') {
        insightMessage.value = info.message || '这篇文章暂时没有可播放的知识点'
        return false
      }
      if (attempt < 15) await new Promise(resolve => setTimeout(resolve, 2000))
      if (requestId !== requestSerial) return false
    }
    insightMessage.value = '知识点仍在准备中，可稍后重试'
    return false
  })().catch(error => {
    if (requestId === requestSerial) insightMessage.value = error.message || '暂时无法准备知识点音频'
    return false
  }).finally(() => {
    if (requestId === requestSerial) {
      insightLoading.value = false
      insightTask = null
    }
  })
  return insightTask
}

const startInsightPlayback = async requestId => {
  if (requestId !== requestSerial || !insightPlayableUrl.value || !audioElement.value) return
  audioElement.value.pause()
  playbackStage.value = 'insights'
  playableUrl.value = insightPlayableUrl.value
  currentTime.value = 0
  duration.value = 0
  await nextTick()
  if (requestId !== requestSerial) return
  audioElement.value.load()
  applySpeed()
  try {
    await audioElement.value.play()
    errorMessage.value = ''
  } catch {
    insightMessage.value = '知识点音频已就绪，请点“播放”继续听'
  }
}

const continueWithInsights = requestId => {
  if (continuationTask) return continuationTask
  continuationTask = (async () => {
    const ready = await prepareInsights(requestId)
    if (requestId !== requestSerial || !awaitingInsights.value) return
    awaitingInsights.value = false
    if (ready) await startInsightPlayback(requestId)
  })().finally(() => {
    if (requestId === requestSerial) continuationTask = null
  })
  return continuationTask
}

const playInsights = async () => {
  const requestId = requestSerial
  continuationCancelled.value = false
  insightMessage.value = ''
  if (insightLoading.value && insightTask) await insightTask
  const ready = insightPlayableUrl.value || await prepareInsights(requestId)
  if (ready && requestId === requestSerial) await startInsightPlayback(requestId)
}

const replayArticle = async () => {
  const requestId = requestSerial
  if (!articlePlayableUrl.value || !audioElement.value) return
  audioElement.value.pause()
  awaitingInsights.value = false
  continuationCancelled.value = false
  playbackStage.value = 'article'
  playableUrl.value = articlePlayableUrl.value
  currentTime.value = 0
  duration.value = 0
  await nextTick()
  if (requestId !== requestSerial) return
  audioElement.value.load()
  applySpeed()
  try {
    await audioElement.value.play()
  } catch {
    errorMessage.value = '文章音频已就绪，请再点一次播放'
  }
}

onBeforeUnmount(() => {
  requestSerial++
  releaseAudio()
})
</script>

<style scoped>
.article-audio-player { margin: 16px 0 6px; padding: 16px; border: 1px solid var(--xy-border, #dbe7de); border-radius: 16px; background: var(--xy-surface, #f7fbf6); }
.audio-heading, .audio-heading > div, .audio-main-controls, .audio-options { display: flex; align-items: center; gap: 12px; }
.audio-heading { justify-content: space-between; margin-bottom: 12px; }
.audio-heading > div { flex-wrap: wrap; }
.audio-heading strong { color: var(--xy-ink, #1d3b2a); font-size: 16px; }
.audio-heading span, .audio-note, .sleep-remaining { color: #68786c; font-size: 12px; }
.audio-ready { color: #24764b !important; }
.audio-play-button { min-width: 76px; padding: 8px 14px; border: 0; border-radius: 999px; background: #26794d; color: white; font-weight: 600; cursor: pointer; }
.audio-play-button:disabled { opacity: .65; cursor: wait; }
.audio-progress { flex: 1; min-width: 40px; accent-color: #26794d; }
.audio-time { color: #536456; font-size: 12px; font-variant-numeric: tabular-nums; }
.audio-options { flex-wrap: wrap; margin-top: 12px; }
.audio-options label { display: flex; align-items: center; gap: 6px; color: #526558; font-size: 13px; }
.audio-options select { padding: 5px 7px; border: 1px solid #c9d9cc; border-radius: 7px; background: white; color: #284531; }
.audio-note, .audio-error { margin: 10px 0 0; line-height: 1.5; }
.audio-error { color: #b23838; font-size: 13px; }
.insight-queue { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-top: 12px; color: #486657; font-size: 12px; }
.insight-queue button { border: 0; background: none; color: #18794b; font-size: 12px; font-weight: 700; cursor: pointer; }
.insight-list { display: grid; gap: 8px; margin-top: 10px; color: #536456; font-size: 12px; }
.insight-list strong, .insight-list b { color: #245c3a; }
.insight-list article { display: grid; gap: 4px; padding: 9px 10px; border-radius: 9px; background: #ffffffb8; }
.insight-list small { line-height: 1.45; }
@media (max-width: 560px) { .article-audio-player { padding: 12px; } .audio-main-controls { gap: 6px; } .audio-play-button { min-width: 64px; padding: 8px 10px; } }
</style>
