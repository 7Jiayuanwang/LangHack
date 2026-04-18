<template>
  <div class="video-player-page">
    <!-- Header -->
    <header class="player-header">
      <button class="back-btn" @click="goBack">
        <el-icon><ArrowLeft /></el-icon>
        <span>返回</span>
      </button>
      <h1 class="header-title">{{ video?.title || '视频播放' }}</h1>
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
        <div v-if="playerError" class="player-error-text" style="color:red; text-align:center; padding: 10px;">
          播放器组件加载失败，已切换至内置播放器。错误: {{ playerErrorMsg }}
        </div>
      </div>

      <!-- Video Info -->
      <div class="video-info">
        <h2 class="video-title">{{ video.title }}</h2>
        <div class="video-meta">
          <span class="meta-item">
            <el-icon><View /></el-icon>
            {{ video.viewCount || 0 }} 次观看
          </span>
          <span class="meta-item">
            <el-icon><Star /></el-icon>
            {{ video.likeCount || 0 }} 赞
          </span>
        </div>

        <!-- Actions -->
        <div class="video-actions">
          <el-button @click="handleLike" :loading="likeLoading" round>
            <el-icon><Star /></el-icon>
            点赞
          </el-button>
          <el-button
            v-if="video.associatedTextId"
            type="primary"
            round
            @click="goToArticle"
          >
            <el-icon><Reading /></el-icon>
            查看关联文章
          </el-button>
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
          <p>{{ video.description }}</p>
        </div>

        <!-- Subtitle Preview -->
        <div v-if="video.subtitleContent" class="subtitle-section">
          <h3>字幕内容</h3>
          <pre class="subtitle-preview">{{ video.subtitleContent.substring(0, 500) }}{{ video.subtitleContent.length > 500 ? '...' : '' }}</pre>
        </div>
      </div>
    </div>

    <!-- Bottom Spacer -->
    <div class="bottom-spacer"></div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, View, Star, Reading } from '@element-plus/icons-vue'
import Artplayer from 'artplayer'
import { getPublicVideoDetail } from '@/api/video'
import { likeVideo } from '@/api/video'
import { getMediaDetail } from '@/api/media'

const router = useRouter()
const route = useRoute()

const VIDEO_MIME_TYPES = ['video/mp4', 'video/webm', 'video/ogg']

const artPlayerRef = ref(null)
const video = ref(null)
const loading = ref(true)
const error = ref(false)
const likeLoading = ref(false)
const playerError = ref(false)
const playerErrorMsg = ref('')
const videoUrl = computed(() => {
  if (!video.value) return ''
  const apiBase = import.meta.env.VITE_API_BASE_URL || ''
  return `${apiBase}/api/media/${video.value.mediaId}/download`
})

let artInstance = null

const goBack = () => {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/student/articles')
  }
}

const loadVideo = async () => {
  const videoId = route.query.videoId
  if (!videoId) {
    error.value = true
    loading.value = false
    return
  }

  loading.value = true
  error.value = false
  try {
    const res = await getPublicVideoDetail(videoId)
    if (res.code === 200 && res.data) {
      video.value = res.data

      // Validate media MIME type before initializing player
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

const initPlayer = () => {
  if (!video.value) return

  // 容器未就绪时降级到 native <video>，避免静默黑屏
  if (!artPlayerRef.value) {
    playerError.value = true
    playerErrorMsg.value = '播放器容器未就绪'
    return
  }

  // 如果没有 mediaId 或 URL 为空，直接显示错误
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

  // 加载字幕
  if (video.value.subtitleContent) {
    const subtitleBlob = new Blob([video.value.subtitleContent], { type: 'text/vtt' })
    const subtitleUrl = URL.createObjectURL(subtitleBlob)
    options.subtitle = {
      url: subtitleUrl,
      type: 'vtt',
      style: {
        color: '#fff',
        fontSize: '18px',
        textShadow: '0 2px 4px rgba(0,0,0,0.8)',
      },
      encoding: 'utf-8',
    }
  }

    // 封面
    if (video.value.coverMediaId) {
      options.poster = `${apiBase}/api/media/${video.value.coverMediaId}/download`
    }

    artInstance = new Artplayer(options)

    // 视频解码错误（文件损坏或格式不支持）
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

const handleLike = async () => {
  if (!video.value?.id) return
  likeLoading.value = true
  try {
    const res = await likeVideo(video.value.id)
    if (res.code === 200) {
      ElMessage.success('点赞成功')
      video.value.likeCount = (video.value.likeCount || 0) + 1
    } else {
      ElMessage.error(res.message || '点赞失败')
    }
  } catch (e) {
    ElMessage.error('点赞失败')
  } finally {
    likeLoading.value = false
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

onMounted(() => {
  loadVideo()
})

onBeforeUnmount(() => {
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

.loading-container, .error-container { padding: 60px 20px; }

.player-content { padding-bottom: 80px; }

.player-wrapper {
  width: 100%;
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

.video-info {
  padding: 20px;
  max-width: 960px;
  margin: 0 auto;
}

.video-title {
  margin: 0 0 12px 0;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.3;
}

.video-meta {
  display: flex;
  gap: 16px;
  margin-bottom: 16px;
  color: #64748b;
  font-size: 14px;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.video-actions {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e2e8f0;
}

.video-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.video-description {
  margin-bottom: 20px;
}

.video-description h3,
.subtitle-section h3 {
  margin: 0 0 8px 0;
  font-size: 16px;
  font-weight: 600;
  color: #0f172a;
}

.video-description p {
  margin: 0;
  font-size: 14px;
  line-height: 1.7;
  color: #475569;
}

.subtitle-preview {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  font-size: 13px;
  line-height: 1.6;
  color: #475569;
  white-space: pre-wrap;
  word-break: break-word;
  max-height: 200px;
  overflow-y: auto;
}

.bottom-spacer { height: 80px; }

@media (min-width: 768px) {
  .video-info { padding: 24px; }
  .art-player-container { max-height: 60vh; }
}
</style>
