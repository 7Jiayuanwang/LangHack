<template>
  <div class="video-player-page">
    <!-- Header -->
    <header class="player-header">
      <button class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </button>
      <h1 class="header-title">{{ video?.title || '视频播放' }}</h1>
      <button 
        v-if="video"
        class="favorite-btn"
        :class="{ 'is-favorite': isFavorite }"
        @click="toggleFavorite"
        :disabled="favoriteLoading"
        title="收藏/取消收藏"
      >
        <el-icon><StarFilled v-if="isFavorite" /><Star v-else /></el-icon>
      </button>
    </header>

    <!-- Loading -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <!-- Error -->
    <div v-else-if="error" class="error-container">
      <el-empty description="视频加载失败">
        <el-button type="primary" @click="loadVideo">重新加载</el-button>
      </el-empty>
    </div>

    <!-- Player Content -->
    <div v-else-if="video" class="player-content">
      <!-- ArtPlayer Container -->
      <div class="player-wrapper">
        <div v-show="!playerError" ref="artPlayerRef" class="art-player-container"></div>
        <video v-if="playerError" :src="videoUrl" controls class="native-video"></video>
        <div v-if="playerError" class="player-error-text">
          播放器组件加载失败，已切换至内置播放器。错误: {{ playerErrorMsg }}
        </div>
      </div>

      <!-- Video Info -->
      <div class="video-info">
        <div class="video-header-top">
          <!-- Left: Title and Metadata -->
          <div class="header-left">
            <div class="title-row">
              <h2 class="video-title">{{ video.title }}</h2>
            </div>
            <div class="video-meta">
              <span class="meta-item">
                <el-icon><View /></el-icon>
                {{ video.viewCount || 0 }} 次观看
              </span>
              <span class="meta-item">
                <el-icon><Star /></el-icon>
                {{ video.likeCount || 0 }} 收藏
              </span>
            </div>
          </div>

          <!-- Right: Mode toggle aligned with title -->
          <div v-if="hasSubtitle && subtitleCues.length" class="right-controls">
            <div class="mode-toggle">
              <button
                class="mode-btn"
                :class="{ active: transcriptMode === 'video' }"
                @click="transcriptMode = 'video'"
              >
                <el-icon><VideoPlay /></el-icon>
                视频
              </button>
              <button
                class="mode-btn"
                :class="{ active: transcriptMode === 'article' }"
                @click="transcriptMode = 'article'"
              >
                <el-icon><Reading /></el-icon>
                文章
              </button>
            </div>
          </div>
        </div>

        <!-- Actions Container (Keep only associated article if exists) -->
        <div v-if="video.associatedTextId" class="video-actions-container">
          <div class="video-actions">
            <el-button
              type="primary"
              round
              size="small"
              @click="goToArticle"
            >
              <el-icon><Reading /></el-icon>
              查看关联文章
            </el-button>
          </div>
        </div>

        <!-- Tags -->
        <div v-if="video.tags" class="video-tags">
          <el-tag
            v-for="tag in video.tags.split(',')"
            :key="tag"
            size="small"
            type="info"
            class="tag-item"
          >
            {{ tag.trim() }}
          </el-tag>
        </div>

        <!-- Description -->
        <div v-if="video.description" class="video-description">
          <h3>视频简介</h3>
          <p :class="{ 'desc-collapsed': !descExpanded }">
            {{ video.description }}
          </p>
          <button v-if="video.description && video.description.length > 80" class="desc-toggle" @click="descExpanded = !descExpanded">
            {{ descExpanded ? '收起' : '展开' }}
          </button>
        </div>

        <!-- ═══ Transcript Content Area ═══ -->
        <div v-if="hasSubtitle && subtitleCues.length" class="transcript-section">
          <div class="transcript-header">
            <h3>
              <el-icon><Document /></el-icon>
              {{ transcriptMode === 'video' ? '互动字幕文稿' : '文章阅读模式' }}
            </h3>
            <div class="transcript-header-right">
              <!-- Display format toggle moved here -->
              <div class="display-toggle">
                <button
                  class="display-btn"
                  :class="{ active: displayFormat === 'subtitle' }"
                  @click="displayFormat = 'subtitle'"
                >
                  <el-icon><ChatLineSquare /></el-icon>
                  字幕显示
                </button>
                <button
                  class="display-btn"
                  :class="{ active: displayFormat === 'flow' }"
                  @click="displayFormat = 'flow'"
                >
                  <el-icon><Document /></el-icon>
                  文章显示
                </button>
              </div>
            </div>
          </div>

          <!-- Video Mode Content -->
          <template v-if="transcriptMode === 'video'">
            <!-- Subtitle display: each line as a block -->
            <div v-if="displayFormat === 'subtitle'" ref="transcriptRef" class="transcript-body subtitle-list">
              <div
                v-for="(cue, index) in subtitleCues"
                :key="index"
                :class="['subtitle-item', { active: index === activeCueIndex }]"
                :data-index="index"
                @click="seekToCue(cue)"
              >
                <span class="subtitle-time">{{ formatTimestamp(cue.startTime) }}</span>
                <span class="subtitle-text">{{ cue.text }}</span>
                <span v-if="activeSubtitleLang === 'both' && subtitleCuesZh[index]" class="subtitle-text-zh">{{ subtitleCuesZh[index].text }}</span>
              </div>
            </div>

            <!-- Flow display: continuous text -->
            <div v-else ref="transcriptRef" class="transcript-body flow-text">
              <span
                v-for="(cue, index) in subtitleCues"
                :key="index"
                :class="['transcript-cue', { active: index === activeCueIndex }]"
                :data-index="index"
                @click="seekToCue(cue)"
              >
                <span class="cue-timestamp">{{ formatTimestamp(cue.startTime) }}</span>
                <span class="cue-text">{{ cue.text }}</span>
              </span>
            </div>
          </template>

          <!-- Article Mode Content -->
          <template v-else>
            <!-- Subtitle display in article mode -->
            <div v-if="displayFormat === 'subtitle'" class="article-body article-subtitle-list">
              <div
                v-for="(cue, index) in subtitleCues"
                :key="index"
                class="article-subtitle-item"
              >
                <span class="article-subtitle-time">{{ formatTimestamp(cue.startTime) }}</span>
                <span class="article-subtitle-text">{{ cue.text }}</span>
                <span v-if="activeSubtitleLang === 'both' && subtitleCuesZh[index]" class="article-subtitle-text-zh">{{ subtitleCuesZh[index].text }}</span>
              </div>
            </div>

            <!-- Flow display in article mode (InteractiveText) -->
            <div v-else class="article-body">
              <InteractiveText
                :text="subtitleFullText"
                :text-id="0"
                :unit-id="0"
                @word-click="handleWordClick"
              />
            </div>
          </template>
        </div>
      </div>
    </div>

    <!-- 文本选择菜单 -->
    <SelectionMenu
      v-if="transcriptMode === 'article'"
      :visible="showSelectionMenu"
      :position="selectionPosition"
      underline-label="划线"
      @copy="handleCopy"
      @underline="handleUnderline"
      @ai-qa="handleAiQa"
    />

    <!-- Bottom Spacer -->
    <div class="bottom-spacer"></div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, View, Star, StarFilled, Reading, ChatLineSquare, Document, VideoPlay } from '@element-plus/icons-vue'
import Artplayer from 'artplayer'
import { getPublicVideoDetail, getUserVideoDetail } from '@/api/video'
import { getAdminVideoDetail } from '@/api/admin'
import { getMediaDetail } from '@/api/media'
import { checkArticleFavorite, toggleArticleFavorite } from '@/api/student'
import InteractiveText from '@/components/InteractiveText.vue'
import SelectionMenu from '@/components/SelectionMenu.vue'

const router = useRouter()
const route = useRoute()

const VIDEO_MIME_TYPES = ['video/mp4', 'video/webm', 'video/ogg', 'video/quicktime', 'video/x-msvideo', 'video/x-matroska']

const artPlayerRef = ref(null)
const transcriptRef = ref(null)
const video = ref(null)
const loading = ref(true)
const error = ref(false)
const isFavorite = ref(false)
const favoriteLoading = ref(false)
const playerError = ref(false)
const playerErrorMsg = ref('')
const subtitleVisible = ref(true)
const subtitleCues = ref([])
const activeCueIndex = ref(-1)
const transcriptMode = ref('video') // 'video' | 'article'
const displayFormat = ref('subtitle') // 'subtitle' | 'flow'
const descExpanded = ref(false)

const showSelectionMenu = ref(false)
const selectionPosition = reactive({ x: 0, y: 0 })
const selectedTextContent = ref('')
let selectionMenuHideTimerId = null

let artInstance = null

const videoUrl = computed(() => {
  if (!video.value) return ''
  const apiBase = import.meta.env.VITE_API_BASE_URL || ''
  return `${apiBase}/api/media/${video.value.mediaId}/download`
})

const hasSubtitle = computed(() => {
  const v = video.value
  return !!((v?.subtitleContent && v.subtitleContent.trim()) || (v?.subtitleContentZh && v.subtitleContentZh.trim()))
})

const availableSubtitleLangs = computed(() => {
  const langs = []
  const hasEn = !!video.value?.subtitleContent?.trim()
  const hasZh = !!video.value?.subtitleContentZh?.trim()
  if (hasEn) langs.push('en')
  if (hasZh) langs.push('zh')
  if (hasEn && hasZh) langs.push('both')
  return langs
})

const activeSubtitleLang = ref('off')

const subtitleCuesZh = ref([])

function generateBilingualVTT() {
  const enCues = parseVTT(video.value?.subtitleContent || '')
  const zhCues = parseVTT(video.value?.subtitleContentZh || '')
  let vtt = 'WEBVTT\n\n'
  for (let i = 0; i < enCues.length; i++) {
    const en = enCues[i]
    const zh = zhCues[i]
    const start = formatVTTTime(en.startTime)
    const end = formatVTTTime(en.endTime)
    const zhText = zh ? zh.text : ''
    vtt += `${start} --> ${end}\n${en.text}\n${zhText}\n\n`
  }
  return vtt
}

function formatVTTTime(seconds) {
  const h = String(Math.floor(seconds / 3600)).padStart(2, '0')
  const m = String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')
  const s = String(Math.floor(seconds % 60)).padStart(2, '0')
  const ms = String(Math.round((seconds % 1) * 1000)).padStart(3, '0')
  return `${h}:${m}:${s}.${ms}`
}

const activeSubtitleContent = computed(() => {
  if (activeSubtitleLang.value === 'en') return video.value?.subtitleContent || ''
  if (activeSubtitleLang.value === 'zh') return video.value?.subtitleContentZh || ''
  if (activeSubtitleLang.value === 'both') return generateBilingualVTT()
  return ''
})

const subtitleBtnLabel = computed(() => {
  if (activeSubtitleLang.value === 'en') return '英文字幕'
  if (activeSubtitleLang.value === 'zh') return '中文字幕'
  if (activeSubtitleLang.value === 'both') return '双语字幕'
  return '字幕关'
})

// Combine all subtitle cues into a single text for article mode
const subtitleFullText = computed(() => {
  if (!subtitleCues.value.length) return ''
  return subtitleCues.value.map(cue => cue.text).join(' ')
})

// ─── VTT Parser ────────────────────────────────────────────────
function parseVTT(vttText) {
  const cues = []
  if (!vttText) return cues

  const blocks = vttText.replace(/\r\n/g, '\n').split(/\n\n+/)

  for (const block of blocks) {
    const lines = block.trim().split('\n')
    let timestampLineIdx = -1
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].includes('-->')) {
        timestampLineIdx = i
        break
      }
    }
    if (timestampLineIdx === -1) continue

    const timeParts = lines[timestampLineIdx].split('-->')
    if (timeParts.length < 2) continue

    const startTime = parseTimestamp(timeParts[0].trim())
    const endTime = parseTimestamp(timeParts[1].trim().split(/\s/)[0])

    const text = lines.slice(timestampLineIdx + 1).join(' ').trim()
    if (!text) continue

    cues.push({ startTime, endTime, text })
  }

  return cues
}

function parseTimestamp(ts) {
  const parts = ts.split(':')
  let hours = 0, minutes = 0, seconds = 0

  if (parts.length === 3) {
    hours = parseInt(parts[0], 10)
    minutes = parseInt(parts[1], 10)
    seconds = parseFloat(parts[2])
  } else if (parts.length === 2) {
    minutes = parseInt(parts[0], 10)
    seconds = parseFloat(parts[1])
  }

  return hours * 3600 + minutes * 60 + seconds
}

function formatTimestamp(seconds) {
  const m = Math.floor(seconds / 60)
  const s = Math.floor(seconds % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}

// ─── Subtitle Cleaner ──────────────────────────────────────────

function cleanSubtitle(raw) {
  if (!raw || !raw.trim()) return raw

  let text = raw.trim()

  // 已有标准时间轴（含 -->），只做标签清理
  if (text.includes('-->')) {
    text = text.replace(/<[^>]+>/g, '')
    if (!text.startsWith('WEBVTT')) {
      text = 'WEBVTT\n\n' + text
    }
    return text
  }

  // 检测内联时间戳 <HH:MM:SS.mmm> 或 <c> 标签
  const hasInline = /<\d{1,2}:\d{2}:\d{2}[.,]\d{3}>/.test(text)
  const hasCTag = /<\/?c\s*>/i.test(text)
  if (!hasInline && !hasCTag) return text

  // 去掉 <c> </c>
  text = text.replace(/<\/?c\s*>/gi, '')

  // 提取时间戳和对应文本
  const regex = /<(\d{1,2}:\d{2}:\d{2}[.,]\d{3})>/g
  const segments = []
  let lastIdx = 0
  let match
  while ((match = regex.exec(text)) !== null) {
    const ts = match[1]
    const start = match.index + match[0].length
    // 找下一个时间戳位置
    regex.lastIndex = 0 // reset for next search
    const nextMatch = regex.exec(text.substring(start))
    const end = nextMatch ? start + nextMatch.index : text.length
    const word = text.substring(start, end).trim()
    if (word) {
      segments.push({ time: parseTimestamp(ts), word })
    }
    regex.lastIndex = start + (end - start)
  }

  // 第一个时间戳之前的文本（可能无时间戳前缀）
  const firstTsIdx = text.search(/<\d{1,2}:\d{2}:\d{2}[.,]\d{3}>/)
  if (firstTsIdx > 0) {
    const prefix = text.substring(0, firstTsIdx).trim()
    if (prefix && segments.length > 0) {
      segments.unshift({ time: Math.max(0, segments[0].time - 2), word: prefix })
    }
  }

  if (!segments.length) return text

  // 按句子边界分组（句号/问号/感叹号 或 超过 10 词）
  const cues = []
  let buf = []
  let cueStart = segments[0].time
  for (let i = 0; i < segments.length; i++) {
    const seg = segments[i]
    buf.push(seg.word)
    const isEnd = /[.!?]$/.test(seg.word)
    if (isEnd || buf.length >= 10 || i === segments.length - 1) {
      const endTime = i + 1 < segments.length ? segments[i + 1].time : seg.time + 2
      cues.push({ start: cueStart, end: endTime, text: buf.join(' ') })
      buf = []
      if (i + 1 < segments.length) cueStart = segments[i + 1].time
    }
  }

  // 组装 VTT
  let vtt = 'WEBVTT\n\n'
  for (const cue of cues) {
    vtt += `${formatVTTTime(cue.start)} --> ${formatVTTTime(cue.end)}\n${cue.text}\n\n`
  }
  return vtt.trim()
}

// ─── Navigation ────────────────────────────────────────────────
const goBack = () => {
  const isAdminPreview = route.query.preview === 'admin'
  if (isAdminPreview) {
    // 管理员预览模式（新标签页打开的）→ 关闭标签页或回管理页
    window.close()
    // 如果 window.close() 被浏览器阻止，则跳转到内容管理
    setTimeout(() => { window.location.href = '/admin/content' }, 100)
  } else if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/student/articles')
  }
}

// ─── Video Loading ─────────────────────────────────────────────────────
const loadVideo = async () => {
  const videoId = route.query.videoId || route.query.id
  const isAdminPreview = route.query.preview === 'admin'
  const isUserPreview = route.query.preview === 'user'
  if (!videoId) {
    error.value = true
    loading.value = false
    return
  }

  loading.value = true
  error.value = false
  try {
    // 管理员预览 / 用户预览自己的上传 使用专有 API（不检查 isPublic，允许未审核内容）
    const res = isAdminPreview
      ? await getAdminVideoDetail(videoId)
      : isUserPreview
        ? await getUserVideoDetail(videoId)
        : await getPublicVideoDetail(videoId)
    if (res.code === 200 && res.data) {
      video.value = res.data

      // 清洗字幕内容（去除 <c> 标签、内联时间戳等 SRT 转 VTT 污染）
      if (video.value.subtitleContent) {
        video.value.subtitleContent = cleanSubtitle(video.value.subtitleContent)
      }
      if (video.value.subtitleContentZh) {
        video.value.subtitleContentZh = cleanSubtitle(video.value.subtitleContentZh)
      }

      if (video.value.subtitleContent) {
        subtitleCues.value = parseVTT(video.value.subtitleContent)
      }
      if (video.value.subtitleContentZh) {
        subtitleCuesZh.value = parseVTT(video.value.subtitleContentZh)
      }
      if (availableSubtitleLangs.value.length > 0) {
        activeSubtitleLang.value = availableSubtitleLangs.value[0]
      }

      if (video.value.mediaId) {
        try {
          const mediaRes = await getMediaDetail(video.value.mediaId)
          if (mediaRes.code === 200 && mediaRes.data) {
            const mimeType = mediaRes.data.mimeType
            if (mimeType && !VIDEO_MIME_TYPES.includes(mimeType)) {
              playerError.value = true
              playerErrorMsg.value = `文件类型为 ${mimeType}，不是可播放的视频格式`
              loading.value = false
              return
            }
          }
        } catch (e) {
          console.warn('获取媒体详情失败，跳过 MIME 校验:', e)
        }
      }

      if (!isAdminPreview && video.value.associatedTextId) {
        try {
          const favRes = await checkArticleFavorite(video.value.associatedTextId)
          isFavorite.value = favRes.data.isFavorite
        } catch (e) {
          console.error('检查收藏状态失败', e)
        }
      }

      loading.value = false
      await nextTick()
      initPlayer()
      return
    }
    error.value = true
  } catch (e) {
    console.error('加载视频失败:', e)
    error.value = true
  } finally {
    loading.value = false
  }
}

// ─── Player Init ───────────────────────────────────────────────
const initPlayer = () => {
  if (!video.value) return

  if (!artPlayerRef.value) {
    playerError.value = true
    playerErrorMsg.value = '播放器容器未就绪'
    return
  }

  if (!video.value.mediaId || !videoUrl.value) {
    playerError.value = true
    playerErrorMsg.value = '视频文件不存在或已被删除'
    return
  }

  playerError.value = false
  playerErrorMsg.value = ''

  try {
    const apiBase = import.meta.env.VITE_API_BASE_URL || ''

    const options = {
      container: artPlayerRef.value,
      url: videoUrl.value,
      volume: 0.7,
      autoplay: false,
      pip: true,
      autoSize: false,
      autoMini: true,
      screenshot: true,
      setting: true,
      loop: false,
      flip: true,
      playbackRate: true,
      aspectRatio: true,
      fullscreen: true,
      fullscreenWeb: true,
      miniProgressBar: true,
      mutex: true,
      backdrop: true,
      theme: '#1d4ed8',
      lang: navigator.language.startsWith('zh') ? 'zh-cn' : 'en',
    }

    const initSubContent = activeSubtitleContent.value
    if (initSubContent && activeSubtitleLang.value !== 'off') {
      const subtitleBlob = new Blob([initSubContent], { type: 'text/vtt' })
      const subtitleUrl = URL.createObjectURL(subtitleBlob)
      options.subtitle = {
        url: subtitleUrl,
        type: 'vtt',
        style: {
          color: '#fff',
          fontSize: '18px',
          textShadow: '0 2px 4px rgba(0,0,0,0.8)',
          bottom: '20px',
        },
        encoding: 'utf-8',
      }
    }

    if (video.value.coverMediaId) {
      options.poster = `${apiBase}/api/media/${video.value.coverMediaId}/download`
    }

    artInstance = new Artplayer(options)

    if (hasSubtitle.value) {
      const langs = availableSubtitleLangs.value
      const defaultLang = activeSubtitleLang.value

      const selectorItems = []
      if (langs.includes('en')) selectorItems.push({ html: '英文字幕', value: 'en', default: defaultLang === 'en' })
      if (langs.includes('zh')) selectorItems.push({ html: '中文字幕', value: 'zh', default: defaultLang === 'zh' })
      if (langs.includes('both')) selectorItems.push({ html: '双语字幕', value: 'both', default: defaultLang === 'both' })
      selectorItems.push({ html: '关闭字幕', value: 'off', default: defaultLang === 'off' })

      artInstance.controls.add({
        position: 'right',
        html: subtitleBtnLabel.value,
        style: {
          padding: '0 10px',
        },
        selector: selectorItems,
        onSelect: function (item) {
          switchSubtitleLang(item.value)
          return subtitleBtnLabel.value
        },
      })
    }

    artInstance.on('video:timeupdate', () => {
      updateActiveCue()
    })

    artInstance.on('video:error', () => {
      const videoEl = artInstance?.video
      if (videoEl?.error) {
        const code = videoEl.error.code
        if (code === 3) {
          playerErrorMsg.value = '视频文件格式不支持或文件已损坏'
        } else if (code === 4) {
          playerErrorMsg.value = '视频源不可用或格式不受支持'
        } else if (code === 2) {
          playerErrorMsg.value = '网络错误，视频加载失败'
        }
      }
      playerError.value = true
    })

    artInstance.on('error', (err) => {
      console.error('ArtPlayer runtime error:', err)
      if (!playerError.value) {
        playerError.value = true
        playerErrorMsg.value = playerErrorMsg.value || String(err?.message || err)
      }
    })
  } catch (err) {
    console.error('ArtPlayer init failed:', err)
    playerError.value = true
    playerErrorMsg.value = String(err?.message || err)
  }
}

// ─── Subtitle Language Switch ─────────────────────────────────
const switchSubtitleLang = (lang) => {
  activeSubtitleLang.value = lang

  if (lang !== 'off') {
    if (lang === 'en') {
      subtitleCues.value = parseVTT(video.value?.subtitleContent || '')
    } else if (lang === 'zh') {
      subtitleCues.value = parseVTT(video.value?.subtitleContentZh || '')
    } else if (lang === 'both') {
      subtitleCues.value = parseVTT(video.value?.subtitleContent || '')
      subtitleCuesZh.value = parseVTT(video.value?.subtitleContentZh || '')
    }

    subtitleVisible.value = true
    const playerContent = activeSubtitleContent.value
    if (artInstance && playerContent) {
      const blob = new Blob([playerContent], { type: 'text/vtt' })
      const url = URL.createObjectURL(blob)
      artInstance.subtitle.switch(url, { type: 'vtt' })
      artInstance.subtitle.show = true
    }
  } else {
    subtitleVisible.value = false
    if (artInstance) {
      artInstance.subtitle.show = false
    }
  }

  updateSubtitleSettingUI()
}

function updateSubtitleSettingUI() {
  if (!artInstance) return
}

// ─── Transcript Sync (Video Mode) ──────────────────────────────
function updateActiveCue() {
  if (!artInstance || !subtitleCues.value.length) return
  if (transcriptMode.value !== 'video') return // Only sync in video mode

  const currentTime = artInstance.currentTime
  let newIndex = -1

  for (let i = 0; i < subtitleCues.value.length; i++) {
    const cue = subtitleCues.value[i]
    if (currentTime >= cue.startTime && currentTime < cue.endTime) {
      newIndex = i
      break
    }
  }

  if (newIndex !== activeCueIndex.value) {
    activeCueIndex.value = newIndex
    scrollToActiveCue()
  }
}

// Scroll active cue to the TOP of the transcript container
function scrollToActiveCue() {
  if (activeCueIndex.value < 0 || !transcriptRef.value) return

  const container = transcriptRef.value
  const el = container.querySelector(`[data-index="${activeCueIndex.value}"]`)
  if (!el) return

  // Calculate position relative to the scroll container
  const containerRect = container.getBoundingClientRect()
  const elRect = el.getBoundingClientRect()

  // Scroll to put the active element at the TOP with a small padding
  const elTopInContainer = elRect.top - containerRect.top + container.scrollTop
  const targetScroll = elTopInContainer - 8 // 8px top padding

  container.scrollTo({
    top: Math.max(0, targetScroll),
    behavior: 'smooth'
  })
}

function seekToCue(cue) {
  if (artInstance) {
    artInstance.currentTime = cue.startTime
    artInstance.play()
  }
}

// ─── Article Mode: Word Click ──────────────────────────────────
const handleWordClick = ({ word }) => {
  // The InteractiveText component handles everything internally
  // (word dialog, translation, vocabulary, etc.)
  // This is just for optional custom handling
}

// ─── Actions ───────────────────────────────────────────────────
const toggleFavorite = async () => {
  if (!video.value?.associatedTextId) {
    ElMessage.warning('该视频暂无关联文章可以收藏')
    return
  }
  favoriteLoading.value = true
  try {
    const res = await toggleArticleFavorite(video.value.associatedTextId)
    if (res.code === 200) {
      isFavorite.value = res.data.isFavorite
      if (res.data.isFavorite) {
        video.value.likeCount = (video.value.likeCount || 0) + 1
      } else {
        video.value.likeCount = Math.max(0, (video.value.likeCount || 1) - 1)
      }
      ElMessage.success(isFavorite.value ? '已添加到我的收藏' : '已取消收藏')
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    ElMessage.error('收藏请求失败')
  } finally {
    favoriteLoading.value = false
  }
}

const goToArticle = () => {
  if (video.value?.associatedTextId) {
    router.push({
      path: '/student/text-learning',
      query: { textId: video.value.associatedTextId, from: 'video' }
    })
  }
}

// ─── Text Selection Menu (AI QA, Copy, etc) ────────────────
const handleSelectionChange = () => {
  if (transcriptMode.value !== 'article') {
    showSelectionMenu.value = false
    return
  }

  if (selectionMenuHideTimerId) {
    clearTimeout(selectionMenuHideTimerId)
    selectionMenuHideTimerId = null
  }

  const selection = window.getSelection()
  const text = selection ? selection.toString().trim() : ''

  if (text.length > 0 && selection && selection.rangeCount > 0) {
    selectedTextContent.value = text
    
    // 短暂延迟计算位置，确保 UI 已更新
    selectionMenuHideTimerId = setTimeout(() => {
      try {
        const range = selection.getRangeAt(0)
        const rect = range.getBoundingClientRect()
        
        if (rect.width > 0 && rect.height > 0) {
          selectionPosition.x = rect.left + rect.width / 2
          selectionPosition.y = rect.top - 50 // 显示在选区上方
          
          if (selectionPosition.y < 60) {
            selectionPosition.y = rect.bottom + 10 // 如果上方空间不足，显示在下方
          }
          
          showSelectionMenu.value = true
        } else {
          showSelectionMenu.value = false
        }
      } catch (e) {
        showSelectionMenu.value = false
      }
      selectionMenuHideTimerId = null
    }, 150)
  } else {
    showSelectionMenu.value = false
    selectedTextContent.value = ''
  }
}

const handleCopy = () => {
  if (!selectedTextContent.value) return
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(selectedTextContent.value).then(() => {
      ElMessage.success('复制成功')
    }).catch(() => {
      ElMessage.error('复制失败，请手动选择复制')
    })
  } else {
    ElMessage.success('选中内容已可复制')
  }
  showSelectionMenu.value = false
}

const handleUnderline = () => {
  ElMessage.info('划线功能即将上线')
  showSelectionMenu.value = false
  window.getSelection()?.removeAllRanges()
}

const handleAiQa = () => {
  let context = ''
  try {
    const selection = window.getSelection()
    if (selection && selection.rangeCount > 0) {
      const range = selection.getRangeAt(0)
      const container = range.commonAncestorContainer
      const fullText = container.textContent || ''
      context = fullText.substring(Math.max(0, range.startOffset - 100), Math.min(fullText.length, range.endOffset + 100))
    }
  } catch (e) {
    console.error('Failed to get context:', e)
  }

  router.push({
    path: '/student/ai-qa',
    query: {
      textId: video.value?.associatedTextId || video.value?.id || 0,
      text: selectedTextContent.value,
      context: context,
      articleTitle: video.value?.title || '视频互动字幕'
    }
  })
}

onMounted(() => {
  loadVideo()
  document.addEventListener('selectionchange', handleSelectionChange)
})

onBeforeUnmount(() => {
  document.removeEventListener('selectionchange', handleSelectionChange)
  if (artInstance) {
    artInstance.destroy()
    artInstance = null
  }
})
</script>

<style scoped>
.video-player-page {
  min-height: 100vh;
  background: #f6f7f8;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

.player-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255,255,255,0.9);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(226,232,240,0.6);
  display: flex;
  align-items: center;
  padding: 12px 20px;
  gap: 12px;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: none;
  border: none;
  color: #475569;
  font-size: 14px;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  transition: all 0.2s;
}
.back-btn:hover { background: #f1f5f9; color: #0f172a; }

.header-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.favorite-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  border: none;
  background: none;
  color: #cbd5e1;
  font-size: 20px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
}

.favorite-btn:hover:not(:disabled) {
  background: #f1f5f9;
  color: #f59e0b;
}

.favorite-btn.is-favorite {
  color: #f59e0b;
}

.favorite-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.loading-container, .error-container { padding: 60px 20px; }

.player-content { padding-bottom: 80px; }

.player-wrapper {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  background: #000;
  min-height: 200px;
}

.art-player-container, .native-video {
  width: 100%;
  aspect-ratio: 16 / 9;
  min-height: 200px;
  max-height: 70vh;
  display: block;
}

.player-error-text {
  color: red;
  text-align: center;
  padding: 10px;
}

.video-info {
  padding: 20px;
  max-width: 960px;
  margin: 0 auto;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.video-title {
  margin: 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

.star-like-btn {
  flex-shrink: 0;
}

.video-header-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 4px;
}

.header-left {
  flex: 1;
  min-width: 0; /* allows truncation if needed */
}

.video-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 12px;
  color: #64748b;
  font-size: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.video-actions-container {
  display: flex;
  justify-content: flex-start;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e2e8f0;
}

.video-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  align-items: center;
}

.right-controls {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8px;
}

.controls-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.video-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.video-description {
  margin-bottom: 12px;
}

.video-description h3 {
  margin: 0 0 4px 0;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.video-description p {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
}

.video-description p.desc-collapsed {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.desc-toggle {
  background: none;
  border: none;
  color: #1d4ed8;
  font-size: 12px;
  cursor: pointer;
  padding: 2px 0;
  margin-top: 2px;
}

.mode-toggle {
  display: inline-flex;
  background: #f1f5f9;
  border-radius: 12px;
  padding: 4px;
  gap: 4px;
}

.mode-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 20px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #64748b;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.25s ease;
  white-space: nowrap;
}

.mode-btn:hover {
  color: #334155;
}

.mode-btn.active {
  background: #ffffff;
  color: #1d4ed8;
  font-weight: 600;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}

/* ─── Transcript Section (shared container) ────────────────── */
.transcript-section {
  margin-top: 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 24px -4px rgba(0,0,0,0.06);
}

.transcript-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  background: linear-gradient(135deg, #f8fafc 0%, #f1f5f9 100%);
  border-bottom: 1px solid #e2e8f0;
  gap: 12px;
}

.transcript-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}



.transcript-hint {
  font-size: 12px;
  color: #94a3b8;
  font-weight: 400;
  white-space: nowrap;
}

.transcript-header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ─── Display Format Sub-Toggle ─── */
.display-toggle {
  display: inline-flex;
  background: #e2e8f0;
  border-radius: 8px;
  padding: 2px;
  gap: 2px;
}

.display-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 12px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: #64748b;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
}

.display-btn:hover {
  color: #334155;
}

.display-btn.active {
  background: #ffffff;
  color: #1d4ed8;
  font-weight: 600;
  box-shadow: 0 1px 4px rgba(0,0,0,0.1);
}

/* ─── Transcript Body (shared) ──────────────────────────────── */
.transcript-body {
  max-height: 480px;
  overflow-y: auto;
  padding: 16px 20px;
}

.transcript-body::-webkit-scrollbar {
  width: 6px;
}

.transcript-body::-webkit-scrollbar-track {
  background: transparent;
}

.transcript-body::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.transcript-body::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

/* ─── Subtitle List Display ─────────────────────────────────── */
.subtitle-list {
  padding: 8px 0;
}

.subtitle-item {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 20px;
  cursor: pointer;
  transition: all 0.2s ease;
  border-left: 3px solid transparent;
  font-size: 17px;
  line-height: 1.6;
  color: #475569;
}

.subtitle-item:hover {
  background: rgba(29,78,216,0.04);
  border-left-color: rgba(29,78,216,0.3);
}

.subtitle-item.active {
  background: linear-gradient(90deg, rgba(29,78,216,0.08) 0%, rgba(29,78,216,0.02) 100%);
  border-left-color: #1d4ed8;
  color: #1d4ed8;
  font-weight: 600;
}

.subtitle-time {
  flex-shrink: 0;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  margin-top: 2px;
  min-width: 48px;
  text-align: center;
}

.subtitle-item.active .subtitle-time {
  color: #ffffff;
  background: #1d4ed8;
}

.subtitle-text {
  flex: 1;
  min-width: 0;
}

.subtitle-text-zh {
  width: 100%;
  padding-left: 72px;
  font-size: 15px;
  color: #64748b;
  line-height: 1.5;
}
.subtitle-item.active .subtitle-text-zh {
  color: #3b82f6;
}

/* ─── Flow Text Display ─────────────────────────────────────── */
.flow-text {
  padding: 16px 20px;
}

.transcript-cue {
  display: inline;
  cursor: pointer;
  padding: 2px 0;
  border-radius: 4px;
  transition: all 0.3s ease;
  line-height: 2;
  font-size: 18px;
  color: #475569;
}

.transcript-cue:hover {
  color: #1d4ed8;
  background: rgba(29,78,216,0.06);
}

.transcript-cue.active {
  color: #1d4ed8;
  background: rgba(29,78,216,0.1);
  font-weight: 600;
  border-radius: 4px;
  padding: 2px 4px;
  margin: 0 -4px;
}

.transcript-cue .cue-timestamp {
  display: none;
  font-size: 11px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: #94a3b8;
  margin-right: 6px;
  font-weight: 400;
  vertical-align: middle;
}

.transcript-cue.active .cue-timestamp {
  display: inline;
  color: #1d4ed8;
}

.transcript-cue + .transcript-cue::before {
  content: ' ';
}

/* ─── Article Mode Body ─────────────────────────────────────── */
.article-body {
  padding: 12px 20px 20px;
  max-height: 600px;
  overflow-y: auto;
}

.article-body::-webkit-scrollbar {
  width: 6px;
}

.article-body::-webkit-scrollbar-track {
  background: transparent;
}

.article-body::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

/* ─── Article Mode Subtitle List ───────────────────────────── */
.article-subtitle-list {
  padding: 12px 0;
}

.article-subtitle-item {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 14px;
  padding: 12px 20px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 16px;
  line-height: 1.7;
  color: #334155;
  transition: background 0.2s;
}

.article-subtitle-item:last-child {
  border-bottom: none;
}

.article-subtitle-item:hover {
  background: #f8fafc;
}

.article-subtitle-time {
  flex-shrink: 0;
  font-size: 11px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  color: #94a3b8;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
  margin-top: 3px;
  min-width: 48px;
  text-align: center;
}

.article-subtitle-text {
  flex: 1;
  min-width: 0;
  user-select: text;
  -webkit-user-select: text;
}

.article-subtitle-text-zh {
  width: 100%;
  padding-left: 76px;
  font-size: 14px;
  color: #64748b;
  line-height: 1.6;
  user-select: text;
  -webkit-user-select: text;
}

.bottom-spacer { height: 80px; }

@media (min-width: 768px) {
  .video-info { padding: 24px; }
  .art-player-container { max-height: 60vh; }
}

@media (max-width: 640px) {
  .video-header-top {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
  }
  .header-left {
    margin-bottom: 0;
  }
  .right-controls { 
    align-items: flex-end; 
  }
  .title-row {
    margin-bottom: 6px;
  }
  .video-title {
    font-size: 18px;
  }
  .controls-row { 
    justify-content: flex-end;
  }
  .video-actions-container { 
    flex-direction: column; 
    align-items: stretch; 
  }
  .transcript-header {
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  .transcript-header h3 {
    font-size: 14px;
  }
  .transcript-header-right {
    width: auto;
    justify-content: flex-end;
  }
  .display-btn {
    font-size: 11px;
    padding: 4px 8px;
  }
  .transcript-body { max-height: 320px; padding: 12px 16px; }
  .transcript-cue { font-size: 16px; }
  .mode-toggle { width: auto; }
  .mode-btn { padding: 6px 14px; font-size: 12px; }
}
</style>
