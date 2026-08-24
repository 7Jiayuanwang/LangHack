<template>
  <div class="author-profile-page">
    <!-- Loading State -->
    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载中...</p>
    </div>

    <!-- Error State -->
    <div v-else-if="error" class="error-state">
      <el-icon :size="48"><Warning /></el-icon>
      <p>{{ error }}</p>
      <button class="retry-btn" @click="loadAuthorProfile">重试</button>
    </div>

    <!-- Author Profile Content -->
    <div v-else-if="authorData" class="profile-content">
      <!-- Author Header -->
      <div class="author-header">
        <div class="author-avatar-large">
          <img :src="authorData.author.avatar || DEFAULT_AVATAR" :alt="authorData.author.displayName" />
        </div>
        <div class="author-info">
          <h1 class="author-name">{{ authorData.author.displayName }}</h1>
          <span v-if="authorData.author.roleLabel" class="author-role-badge">{{ authorData.author.roleLabel }}</span>
        </div>
        <div class="author-stats">
          <div class="stat-item" @click="goToFollowList('following')" style="cursor:pointer">
            <span class="stat-value">{{ followCounts.followingCount ?? authorData.followingCount ?? 0 }}</span>
            <span class="stat-label">关注</span>
          </div>
          <div class="stat-item" @click="goToFollowList('followers')" style="cursor:pointer">
            <span class="stat-value">{{ followCounts.followersCount ?? authorData.followersCount ?? 0 }}</span>
            <span class="stat-label">粉丝</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ authorData.totalTexts }}</span>
            <span class="stat-label">文章</span>
          </div>
          <div class="stat-item">
            <span class="stat-value">{{ authorData.totalVideos }}</span>
            <span class="stat-label">视频</span>
          </div>
        </div>
        <div v-if="!isOwnProfile" class="follow-btn-wrapper">
          <button
            class="follow-btn"
            :class="{ 'is-following': isFollowing }"
            :disabled="followLoading"
            @click="handleFollow"
          >
            {{ isFollowing ? '已关注' : '+ 关注' }}
          </button>
        </div>
      </div>

      <!-- Achievements Section -->
      <div v-if="unlockedAchievements.length > 0" class="achievements-section">
        <div class="achievements-header">
          <h3>学习成就</h3>
          <span class="achievements-count">
            已解锁 {{ unlockedAchievements.length }} / {{ totalAchievements }} 项
          </span>
        </div>
        <div class="achievements-list">
          <el-tooltip
            v-for="ach in unlockedAchievements"
            :key="ach.key"
            :content="ach.desc"
            placement="top"
          >
            <div class="achievement-badge">
              <span class="achievement-badge-icon">{{ ach.icon }}</span>
              <span class="achievement-badge-title">{{ ach.title }}</span>
            </div>
          </el-tooltip>
        </div>
      </div>

      <!-- Content Tabs -->
      <div class="content-tabs">
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'texts' }"
          @click="activeTab = 'texts'"
        >
          文章 ({{ authorData.totalTexts }})
        </button>
        <button
          class="tab-btn"
          :class="{ active: activeTab === 'videos' }"
          @click="activeTab = 'videos'"
        >
          视频 ({{ authorData.totalVideos }})
        </button>
      </div>

      <!-- Texts Tab -->
      <div v-show="activeTab === 'texts'" class="content-grid">
        <div v-if="authorData.texts.length === 0" class="empty-state">
          <el-icon :size="40"><Document /></el-icon>
          <p>该作者还没有发布文章</p>
        </div>
        <article
          v-for="text in authorData.texts"
          :key="'text-' + text.id"
          class="content-card"
          @click="handleTextClick(text)"
        >
          <div class="card-cover" :style="getCoverStyle(text)"></div>
          <div class="card-body">
            <h3 class="card-title">{{ text.title }}</h3>
            <p class="card-snippet">{{ getTextSnippet(text.content) }}</p>
            <div class="card-footer">
              <span class="difficulty-badge" :class="text.difficulty">
                {{ getDifficultyText(text.difficulty) }}
              </span>
              <span class="card-meta">
                <el-icon :size="14"><View /></el-icon>
                {{ text.viewCount || 0 }}
              </span>
            </div>
          </div>
        </article>
      </div>

      <!-- Videos Tab -->
      <div v-show="activeTab === 'videos'" class="content-grid">
        <div v-if="authorData.videos.length === 0" class="empty-state">
          <el-icon :size="40"><VideoPlay /></el-icon>
          <p>该作者还没有发布视频</p>
        </div>
        <article
          v-for="video in authorData.videos"
          :key="'video-' + video.id"
          class="content-card is-video"
          @click="handleVideoClick(video)"
        >
          <div class="card-cover" :style="getVideoCoverStyle(video)">
            <div class="video-play-icon">
              <el-icon :size="32"><VideoPlay /></el-icon>
            </div>
          </div>
          <div class="card-body">
            <h3 class="card-title">{{ video.title }}</h3>
            <p class="card-snippet">{{ video.description || '暂无简介' }}</p>
            <div class="card-footer">
              <span v-if="video.tags" class="video-tag">{{ video.tags.split(',')[0] }}</span>
              <span class="card-meta">
                <el-icon :size="14"><View /></el-icon>
                {{ video.viewCount || 0 }}
              </span>
            </div>
          </div>
        </article>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage, ElIcon } from 'element-plus'
import { Loading, Warning, Document, VideoPlay, View } from '@element-plus/icons-vue'
import { followUser, unfollowUser, getFollowStatus, getFollowCounts } from '@/api/follow'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const error = ref(null)
const authorData = ref(null)
const activeTab = ref('texts')

const DEFAULT_AVATAR = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const apiBase = import.meta.env.VITE_API_BASE_URL || ''

// Follow state
const isFollowing = ref(false)
const followLoading = ref(false)
const followCounts = ref({ followingCount: 0, followersCount: 0 })
const isOwnProfile = computed(() => {
  const userId = route.params.id
  return userStore.userInfo?.id && String(userStore.userInfo.id) === String(userId)
})

const unlockedAchievements = computed(() => {
  if (!authorData.value?.achievements?.achievementList) return []
  return authorData.value.achievements.achievementList.filter(a => a.unlocked)
})

const totalAchievements = computed(() => {
  return authorData.value?.achievements?.totalAchievements
    || authorData.value?.achievements?.achievementList?.length
    || 0
})

// 加载作者主页数据
const loadAuthorProfile = async () => {
  loading.value = true
  error.value = null
  try {
    const userId = route.params.id
    const res = await axios.get(`${apiBase}/api/public/authors/${userId}`)
    if (res.data.code === 200) {
      authorData.value = res.data.data
      followCounts.value = {
        followingCount: res.data.data.followingCount || 0,
        followersCount: res.data.data.followersCount || 0
      }
    } else {
      error.value = res.data.message || '加载失败'
    }
  } catch (err) {
    console.error('加载作者主页失败:', err)
    error.value = '网络错误，请稍后重试'
  } finally {
    loading.value = false
  }
}

// 文章封面样式
const getCoverStyle = (text) => {
  if (text.coverUrl) {
    return { backgroundImage: `url(${text.coverUrl})` }
  }
  return {
    background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)'
  }
}

// 视频封面样式
const getVideoCoverStyle = (video) => {
  if (video.coverMediaId) {
    return { backgroundImage: `url(${apiBase}/api/media/${video.coverMediaId}/download)` }
  }
  if (video.coverUrl) {
    return { backgroundImage: `url(${video.coverUrl})` }
  }
  return {
    background: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)'
  }
}

// 文章摘要
const getTextSnippet = (content) => {
  if (!content) return '暂无内容'
  const plain = content.replace(/<[^>]+>/g, '').trim()
  return plain.length > 80 ? plain.substring(0, 80) + '...' : plain
}

// 难度文本
const getDifficultyText = (diff) => {
  const map = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return map[diff] || diff
}

// 点击文章卡片
const handleTextClick = (text) => {
  router.push(`/article/${text.id}`)
}

// 点击视频卡片
const handleVideoClick = (video) => {
  router.push({
    path: '/student/video-player',
    query: { videoId: video.id }
  })
}

// Follow/Unfollow handlers
const handleFollow = async () => {
  if (followLoading.value) return
  followLoading.value = true
  try {
    const userId = route.params.id
    if (isFollowing.value) {
      await unfollowUser(userId)
      isFollowing.value = false
      followCounts.value.followersCount = Math.max(0, followCounts.value.followersCount - 1)
      ElMessage.success('已取消关注')
    } else {
      await followUser(userId)
      isFollowing.value = true
      followCounts.value.followersCount += 1
      ElMessage.success('关注成功')
    }
  } catch (err) {
    ElMessage.error(err.message || '操作失败')
  } finally {
    followLoading.value = false
  }
}

const loadFollowData = async () => {
  const userId = route.params.id
  try {
    const [statusRes, countsRes] = await Promise.all([
      getFollowStatus(userId).catch(() => null),
      getFollowCounts(userId)
    ])
    if (statusRes?.data) {
      isFollowing.value = statusRes.data.isFollowing
    }
    if (countsRes?.data) {
      followCounts.value = countsRes.data
    }
  } catch (err) {
    console.error('加载关注数据失败:', err)
  }
}

const goToFollowList = (type) => {
  const userId = route.params.id
  router.push(`/student/${type === 'followers' ? 'followers' : 'following'}/${userId}`)
}

onMounted(() => {
  loadAuthorProfile()
  loadFollowData()
})
</script>

<style scoped>
.author-profile-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 80px;
}

.loading-wrapper {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 12px;
  color: #606266;
}

.error-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 60vh;
  gap: 16px;
  color: #909399;
  padding: 0 24px;
  text-align: center;
}

.retry-btn {
  margin-top: 8px;
  padding: 8px 24px;
  border: none;
  border-radius: 20px;
  background: #409eff;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.retry-btn:hover {
  background: #66b1ff;
}

.profile-content {
  max-width: 1200px;
  margin: 0 auto;
  padding: 0 16px;
}

/* Author Header */
.author-header {
  background: #fff;
  border-radius: 16px;
  padding: 32px 24px;
  margin: 16px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.author-avatar-large {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  margin-bottom: 16px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
}

.author-avatar-large img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.author-info {
  text-align: center;
  margin-bottom: 20px;
}

.author-name {
  font-size: 22px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 8px 0;
}

.author-role-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #2563eb;
  background: #eff6ff;
}

.author-stats {
  display: flex;
  gap: 32px;
}

.follow-btn-wrapper {
  margin-top: 16px;
}

.follow-btn {
  padding: 8px 32px;
  border: none;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  background: #409eff;
  color: #fff;
}

.follow-btn:hover {
  background: #66b1ff;
}

.follow-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.follow-btn.is-following {
  background: #f0f0f0;
  color: #606266;
}

.follow-btn.is-following:hover {
  background: #fef0f0;
  color: #f56c6c;
}

.achievements-section {
  background: #fff;
  border-radius: 16px;
  padding: 20px 24px;
  margin: 16px 0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.achievements-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.achievements-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.achievements-count {
  font-size: 13px;
  color: #909399;
}

.achievements-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.achievement-badge {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 14px;
  background: #f5f7fa;
  border-radius: 20px;
  font-size: 13px;
  color: #303133;
  cursor: default;
}

.achievement-badge-icon {
  font-size: 16px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
}

.stat-label {
  font-size: 13px;
  color: #909399;
}

/* Content Tabs */
.content-tabs {
  display: flex;
  gap: 8px;
  margin: 24px 0 16px;
  padding: 0 4px;
}

.tab-btn {
  flex: 1;
  padding: 12px;
  border: none;
  border-radius: 12px;
  background: #fff;
  color: #606266;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.tab-btn.active {
  background: #409eff;
  color: #fff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.3);
}

/* Content Grid */
.content-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
  margin-top: 16px;
}

.content-card {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.content-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.12);
}

.card-cover {
  width: 100%;
  height: 160px;
  background-size: cover;
  background-position: center;
  position: relative;
}

.content-card.is-video .card-cover {
  display: flex;
  align-items: center;
  justify-content: center;
}

.video-play-icon {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #409eff;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.2);
}

.card-body {
  padding: 16px;
}

.card-title {
  font-size: 16px;
  font-weight: 700;
  color: #303133;
  margin: 0 0 8px 0;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.card-snippet {
  font-size: 13px;
  color: #606266;
  line-height: 1.5;
  margin: 0 0 12px 0;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.difficulty-badge {
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
}

.difficulty-badge.EASY {
  color: #67c23a;
  background: #f0f9ff;
}

.difficulty-badge.MEDIUM {
  color: #e6a23c;
  background: #fef5e7;
}

.difficulty-badge.HARD {
  color: #f56c6c;
  background: #fef0f0;
}

.video-tag {
  padding: 3px 10px;
  border-radius: 10px;
  font-size: 12px;
  font-weight: 600;
  color: #409eff;
  background: #ecf5ff;
}

.card-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: #909399;
}

.empty-state {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48px 24px;
  color: #909399;
  gap: 12px;
}
</style>
