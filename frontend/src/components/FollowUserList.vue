<template>
  <div class="follow-list-page">
    <header class="page-header">
      <button class="back-btn" type="button" aria-label="返回" @click="router.back()">
        <el-icon><ArrowLeft /></el-icon>
      </button>
      <h1 class="page-title">{{ title }}</h1>
    </header>

    <div v-if="loading" class="loading-wrapper">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
    </div>
    <div v-else-if="users.length === 0" class="empty-state">
      <p>{{ emptyText }}</p>
    </div>
    <div v-else class="user-list">
      <button
        v-for="user in users"
        :key="user.id"
        class="user-item"
        type="button"
        @click="router.push(`/student/author/${user.id}`)"
      >
        <img class="user-avatar" :src="user.avatar || DEFAULT_AVATAR" :alt="displayName(user)" />
        <span class="user-info">
          <span class="user-name">{{ displayName(user) }}</span>
          <span class="user-role">{{ roleLabel(user.role) }}</span>
        </span>
      </button>
      <button v-if="hasMore" class="load-more" type="button" @click="loadMore">加载更多</button>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Loading } from '@element-plus/icons-vue'
import { getFollowersList, getFollowingList } from '@/api/follow'

const props = defineProps({
  type: {
    type: String,
    required: true,
    validator: value => ['following', 'followers'].includes(value)
  }
})

const route = useRoute()
const router = useRouter()
const DEFAULT_AVATAR = 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'
const users = ref([])
const loading = ref(false)
const page = ref(1)
const totalPages = ref(0)

const title = computed(() => props.type === 'following' ? '关注列表' : '粉丝列表')
const emptyText = computed(() => props.type === 'following' ? '还没有关注任何人' : '还没有粉丝')
const hasMore = computed(() => page.value < totalPages.value)

const displayName = user => user.nickname || user.realName || user.username || '用户'
const roleLabel = role => ({ TEACHER: '教师', STUDENT: '学生', USER: '用户', ADMIN: '管理员' }[role] || role)

const loadUsers = async () => {
  loading.value = true
  try {
    const request = props.type === 'following' ? getFollowingList : getFollowersList
    const response = await request(route.params.id, page.value)
    if (response?.data) {
      users.value = page.value === 1
        ? response.data.records || []
        : [...users.value, ...(response.data.records || [])]
      totalPages.value = response.data.pages || 0
    }
  } catch (error) {
    console.error(`加载${title.value}失败:`, error)
  } finally {
    loading.value = false
  }
}

const loadMore = () => {
  page.value += 1
  loadUsers()
}

onMounted(loadUsers)
</script>

<style scoped>
.follow-list-page { min-height: 100vh; background: #f5f7fa; padding-bottom: 80px; }
.page-header { position: sticky; top: 0; z-index: 10; display: flex; align-items: center; min-height: 52px; padding: 0 16px; background: #fff; box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05); }
.back-btn { width: 36px; height: 36px; border: 0; background: transparent; color: #303133; cursor: pointer; }
.page-title { flex: 1; margin: 0 36px 0 0; color: #303133; font-size: 18px; text-align: center; }
.loading-wrapper, .empty-state { display: flex; justify-content: center; padding: 48px 24px; color: #909399; }
.user-list { padding: 12px 16px; }
.user-item { width: 100%; display: flex; align-items: center; gap: 12px; margin-bottom: 8px; padding: 12px 16px; border: 0; border-radius: 8px; background: #fff; text-align: left; cursor: pointer; }
.user-item:hover { background: #f8fafc; }
.user-avatar { width: 48px; height: 48px; border-radius: 50%; object-fit: cover; }
.user-info { display: flex; flex-direction: column; gap: 4px; }
.user-name { color: #303133; font-size: 15px; font-weight: 600; }
.user-role { color: #909399; font-size: 12px; }
.load-more { width: 100%; border: 0; background: transparent; color: #2563eb; padding: 16px; cursor: pointer; }
</style>
