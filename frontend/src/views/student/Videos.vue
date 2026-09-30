<template>
  <div class="videos-mobile-page">
    <!-- Sticky Header -->
    <header class="mobile-header">
      <div class="header-content">
        <h1 class="header-title">发现视频</h1>
        <p class="header-subtitle">Video Hub</p>
      </div>
      <div class="header-actions">
        <button class="icon-btn" @click="loadVideos">
          <el-icon><Refresh /></el-icon>
        </button>
      </div>
    </header>

    <!-- Main Content -->
    <main class="page-main">
      <div class="page-content-wrapper">
        <!-- Video Feed -->
        <div class="videos-feed">
          <!-- Skeleton -->
          <template v-if="loading">
            <div v-for="n in 3" :key="'skel-' + n" class="video-card skeleton-card">
              <div class="skeleton-img shimmer"></div>
              <div class="skeleton-body">
                <div class="skeleton-title shimmer"></div>
                <div class="skeleton-text shimmer"></div>
              </div>
            </div>
          </template>

          <!-- Empty -->
          <div v-else-if="videos.length === 0" class="empty-state">
            <el-icon :size="48"><VideoCamera /></el-icon>
            <h3>暂无推荐视频</h3>
            <p>稍后再来看看吧</p>
            <button class="refresh-btn" @click="loadVideos">重新加载</button>
          </div>

          <!-- Video Cards -->
          <template v-else>
            <article
              v-for="video in videos"
              :key="video.id"
              class="video-card"
              @click="playVideo(video)"
            >
              <div class="video-cover">
                <div
                  class="cover-image"
                  :style="{
                    background: video.coverMediaId
                      ? `url(${apiBase}/api/media/${video.coverMediaId}/download)`
                      : video.coverGradient,
                    backgroundSize: 'cover',
                    backgroundPosition: 'center'
                  }"
                ></div>
                <div class="play-overlay">
                  <div class="play-icon">
                    <el-icon :size="32"><VideoPlay /></el-icon>
                  </div>
                </div>
                <div class="view-badge">
                  <el-icon :size="12"><View /></el-icon>
                  <span>{{ video.viewCount || 0 }}</span>
                </div>
              </div>

              <div class="video-content">
                <h2 class="video-title">{{ video.title }}</h2>
                <p v-if="video.description" class="video-snippet">
                  {{ video.description.substring(0, 80) }}{{ video.description.length > 80 ? '...' : '' }}
                </p>
                <p v-if="video.author" class="card-author">
                  {{ video.author.displayName }}
                  <span v-if="video.author.roleLabel && !video.author.official" class="card-author-role">{{ video.author.roleLabel }}</span>
                </p>
              </div>

              <div class="video-footer">
                <div v-if="video.tags" class="video-tag">
                  {{ video.tags.split(',')[0] }}
                </div>
                <div class="like-count">
                  <el-icon :size="14"><Star /></el-icon>
                  <span>{{ video.likeCount || 0 }}</span>
                </div>
              </div>
            </article>
          </template>
        </div>

        <div class="bottom-spacer"></div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Refresh, VideoPlay, View, Star } from '@element-plus/icons-vue'
import { getFeaturedVideos } from '@/api/video'

const VideoCamera = VideoPlay // alias

const router = useRouter()
const loading = ref(true)
const videos = ref([])
const apiBase = import.meta.env.VITE_API_BASE_URL || ''

const coverGradients = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
]

const loadVideos = async () => {
  loading.value = true
  try {
    const res = await getFeaturedVideos({ count: 20 })
    if (res.code === 200) {
      videos.value = (Array.isArray(res.data) ? res.data : []).map(v => ({
        ...v,
        coverGradient: coverGradients[Math.floor(Math.random() * coverGradients.length)]
      }))
    }
  } catch (e) {
    console.error('加载视频失败:', e)
  } finally {
    loading.value = false
  }
}

const playVideo = (video) => {
  router.push({
    path: '/student/video-player',
    query: { videoId: video.id }
  })
}

onMounted(() => { loadVideos() })
</script>

<style scoped>
.videos-mobile-page {
  height: 100vh;
  height: 100dvh;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  background: #f6f7f8;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: #0f172a;
}

.mobile-header {
  position: sticky;
  top: 0;
  z-index: 50;
  background: rgba(255,255,255,0.85);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(226,232,240,0.6);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
}

.header-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  background: linear-gradient(to right, #0f172a, rgba(23, 110, 81,0.8));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.header-subtitle {
  margin: 2px 0 0;
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #64748b;
}

.icon-btn {
  width: 44px; height: 44px;
  border-radius: 50%;
  border: none;
  background: transparent;
  color: #475569;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.icon-btn:hover { background: #f1f5f9; }

.videos-feed {
  padding: 16px 20px;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 20px;
}

.video-card {
  background: white;
  border-radius: 16px;
  overflow: hidden;
  box-shadow: 0 4px 20px -2px rgba(0,0,0,0.05);
  transition: transform 0.2s, box-shadow 0.2s;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  border: 1px solid #f1f5f9;
}

.video-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 30px -4px rgba(0,0,0,0.08);
}

.video-cover {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  transition: transform 0.7s;
}

.video-card:hover .cover-image {
  transform: scale(1.05);
}

.play-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0,0,0,0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.3s;
}

.video-card:hover .play-overlay {
  opacity: 1;
}

.play-icon {
  width: 56px; height: 56px;
  background: rgba(255,255,255,0.9);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--xy-brand-700);
  box-shadow: 0 4px 16px rgba(0,0,0,0.2);
}

.view-badge {
  position: absolute;
  bottom: 8px;
  right: 8px;
  background: rgba(0,0,0,0.6);
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.video-content {
  padding: 14px 16px 8px;
  flex: 1;
}

.video-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 700;
  line-height: 1.3;
  color: #0f172a;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.video-snippet {
  margin: 0;
  font-size: 13px;
  color: #64748b;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-author {
  margin: 8px 0 0;
  font-size: 12px;
  color: #475569;
  display: flex;
  align-items: center;
  gap: 6px;
}

.card-author-role {
  padding: 0 6px;
  border-radius: 8px;
  font-size: 11px;
  color: var(--xy-brand-600);
  background: var(--xy-brand-50);
}

.video-footer {
  padding: 0 16px 14px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.video-tag {
  background: rgba(23, 110, 81,0.08);
  color: var(--xy-brand-700);
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.like-count {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #94a3b8;
  font-size: 12px;
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 60px 20px;
  text-align: center;
  color: #64748b;
}
.empty-state h3 { margin: 16px 0 8px; font-size: 18px; color: #0f172a; }
.empty-state p { margin: 0 0 24px; font-size: 14px; }
.refresh-btn {
  background: white; border: 1px solid #e2e8f0; color: #0f172a;
  padding: 10px 24px; border-radius: 999px; font-weight: 600; font-size: 14px; cursor: pointer;
}

.skeleton-card { cursor: default; }
.skeleton-img { width: 100%; aspect-ratio: 16/9; }
.skeleton-body { padding: 14px 16px; }
.skeleton-title { height: 18px; width: 80%; border-radius: 4px; margin-bottom: 8px; }
.skeleton-text { height: 14px; width: 60%; border-radius: 4px; }
.shimmer {
  background: #f1f5f9;
  background-image: linear-gradient(90deg, #f1f5f9 0px, #f8fafc 40px, #f1f5f9 80px);
  background-size: 600px;
  animation: shimmer 1.5s infinite linear;
}
@keyframes shimmer {
  0% { background-position: -200px 0; }
  100% { background-position: 400px 0; }
}

.bottom-spacer { height: 80px; }

@media (max-width: 640px) {
  .videos-feed { grid-template-columns: 1fr; padding: 12px 16px; gap: 16px; }
}
</style>
