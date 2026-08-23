<template>
  <section v-if="userId" class="follow-summary">
    <div class="summary-heading">
      <div>
        <h2>我的社交</h2>
        <p>关注学习伙伴，及时查看动态</p>
      </div>
      <button class="profile-link" type="button" @click="goToProfile">个人主页</button>
    </div>

    <div class="summary-stats">
      <button type="button" class="summary-stat" @click="goToList('following')">
        <strong>{{ counts.followingCount }}</strong>
        <span>关注</span>
      </button>
      <button type="button" class="summary-stat" @click="goToList('followers')">
        <strong>{{ counts.followersCount }}</strong>
        <span>粉丝</span>
      </button>
    </div>
  </section>
</template>

<script setup>
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store/user'
import { getFollowCounts } from '@/api/follow'

const router = useRouter()
const userStore = useUserStore()
const userId = userStore.userInfo?.id
const counts = reactive({ followingCount: 0, followersCount: 0 })

const goToProfile = () => router.push(`/student/author/${userId}`)
const goToList = (type) => router.push(`/student/${type}/${userId}`)

onMounted(async () => {
  try {
    const response = await getFollowCounts(userId)
    if (response?.data) {
      counts.followingCount = response.data.followingCount || 0
      counts.followersCount = response.data.followersCount || 0
    }
  } catch (error) {
    console.error('加载关注统计失败:', error)
  }
})
</script>

<style scoped>
.follow-summary {
  margin: 18px 0 24px;
  padding: 20px 24px;
  border: 1px solid #e5e7eb;
  border-radius: 14px;
  background: #fff;
}

.summary-heading,
.summary-stats {
  display: flex;
  align-items: center;
}

.summary-heading {
  justify-content: space-between;
  gap: 16px;
}

.summary-heading h2 {
  margin: 0;
  color: #1f2937;
  font-size: 18px;
}

.summary-heading p {
  margin: 5px 0 0;
  color: #9ca3af;
  font-size: 13px;
}

.profile-link {
  border: 1px solid #d1d5db;
  border-radius: 8px;
  background: #fff;
  color: #374151;
  padding: 7px 12px;
  cursor: pointer;
}

.summary-stats {
  gap: 12px;
  margin-top: 18px;
}

.summary-stat {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 8px;
  border: 0;
  border-radius: 10px;
  padding: 13px 16px;
  background: #f8fafc;
  text-align: left;
  cursor: pointer;
}

.summary-stat:hover {
  background: #eff6ff;
}

.summary-stat strong {
  color: #2563eb;
  font-size: 24px;
}

.summary-stat span {
  color: #6b7280;
  font-size: 14px;
}

@media (max-width: 480px) {
  .follow-summary { padding: 16px; }
  .summary-heading p { display: none; }
}
</style>
