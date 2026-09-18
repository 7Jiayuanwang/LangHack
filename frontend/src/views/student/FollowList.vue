<template>
  <div class="follow-list-page">
    <div class="page-header">
      <button class="back-btn" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <h1 class="page-title">{{ activeType === 'following' ? '关注列表' : '粉丝列表' }}</h1>
    </div>

    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>

    <div v-else-if="userList.length === 0" class="empty-state">
      <p>{{ activeType === 'following' ? '还没有关注任何人' : '还没有粉丝' }}</p>
    </div>

    <div v-else class="user-list">
      <div
        v-for="user in userList"
        :key="user.id"
        class="user-item"
        @click="goToProfile(user.id)"
      >
        <img
          class="user-avatar"
          :src="user.avatar || DEFAULT_AVATAR"
          :alt="user.nickname || user.username"
        />
        <div class="user-info">
          <span class="user-name">{{ user.nickname || user.realName || user.username }}</span>
          <span class="user-role">{{ getRoleLabel(user.role) }}</span>
        </div>
      </div>

      <div v-if="hasMore" class="load-more" @click="loadMore">
        加载更多
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElIcon } from 'element-plus'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { getFollowingList, getFollowersList } from '@/api/follow'

const route = useRoute()
const router = useRouter()

const DEFAULT_AVATAR = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'

const activeType = computed(() => {
  if (route.name === 'StudentFollowersList' || route.query.type === 'followers') return 'followers'
  return 'following'
})
const loading = ref(false)
const userList = ref([])
const currentPage = ref(1)
const totalPages = ref(0)

const hasMore = computed(() => currentPage.value < totalPages.value)

const loadList = async () => {
  loading.value = true
  try {
    const userId = route.params.id
    const res = activeType.value === 'following'
      ? await getFollowingList(userId, currentPage.value)
      : await getFollowersList(userId, currentPage.value)

    if (res?.data) {
      if (currentPage.value === 1) {
        userList.value = res.data.records
      } else {
        userList.value.push(...res.data.records)
      }
      totalPages.value = res.data.pages
    }
  } catch (err) {
    console.error('Load follow list failed:', err)
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  currentPage.value++
  loadList()
}

const goToProfile = (userId) => {
  router.push(`/student/author/${userId}`)
}

const getRoleLabel = (role) => {
  const map = { TEACHER: '教师', STUDENT: '学生', USER: '用户', ADMIN: '管理员' }
  return map[role] || role
}

onMounted(() => {
  loadList()
})
</script>

<style scoped>
.follow-list-page {
  min-height: 100vh;
  background: #f5f7fa;
  padding-bottom: 80px;
}

.page-header {
  background: #fff;
  padding: 12px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
  position: sticky;
  top: 0;
  z-index: 10;
}

.page-title {
  flex: 1;
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #303133;
  text-align: center;
}

.back-btn {
  background: none;
  border: none;
  font-size: 20px;
  cursor: pointer;
  color: #303133;
  padding: 4px;
}

.loading-wrapper {
  display: flex;
  justify-content: center;
  padding: 48px 0;
  color: #606266;
}

.empty-state {
  text-align: center;
  padding: 48px 24px;
  color: #909399;
}

.user-list {
  padding: 12px 16px;
}

.user-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  border-radius: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
}

.user-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
}

.user-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  object-fit: cover;
}

.user-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.user-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.user-role {
  font-size: 12px;
  color: #909399;
}

.load-more {
  text-align: center;
  padding: 16px;
  color: var(--xy-brand-600);
  font-size: 14px;
  cursor: pointer;
}

.load-more:hover {
  color: #66b1ff;
}
</style>
