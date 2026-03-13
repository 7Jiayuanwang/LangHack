<template>
  <div class="user-units">
    <el-card>
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <el-button @click="$router.back()" :icon="ArrowLeft" circle />
            <h2 style="margin: 0 0 0 15px;">{{ courseName || '单元学习' }}</h2>
          </div>
        </div>
      </template>

      <div v-loading="loading">
        <el-row :gutter="20" v-if="units.length > 0">
          <el-col
            v-for="unit in units"
            :key="unit.id"
            :xs="24"
            :sm="12"
            :md="8"
            :lg="6"
          >
            <el-card class="unit-card" @click="enterUnit(unit)">
              <div class="unit-header">
                <h3>{{ unit.name }}</h3>
                <el-tag v-if="unit.order" size="small">第{{ unit.order }}单元</el-tag>
              </div>
              <div class="unit-description" v-if="unit.description">
                {{ unit.description }}
              </div>
              <div class="unit-stats">
                <div class="stat-item">
                  <el-icon><Document /></el-icon>
                  <span>课文: {{ unit.textCount || 0 }}</span>
                </div>
                <div class="stat-item">
                  <el-icon><Notebook /></el-icon>
                  <span>单词: {{ unit.wordCount || 0 }}</span>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>

        <el-empty v-else description="该课程暂无单元内容" />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft, Document, Notebook } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const units = ref([])
const courseName = ref('')

const loadUnits = async () => {
  const courseId = route.query.courseId
  if (!courseId) {
    ElMessage.error('缺少课程ID')
    router.back()
    return
  }

  courseName.value = route.query.courseName || ''

  loading.value = true
  try {
    const response = await request({
      url: `/api/units/course/${courseId}`,
      method: 'get'
    })

    if (response?.code === 200 && response.data) {
      units.value = Array.isArray(response.data) ? response.data : []

      // 为每个单元获取统计信息（使用公开API，不依赖admin权限）
      for (const unit of units.value) {
        try {
          const contentResponse = await request({
            url: `/api/student/learning/units/${unit.id}/content`,
            method: 'get'
          }).catch(() => ({ 
            data: { texts: [], words: [] } 
          }))

          const data = contentResponse?.data || {}
          unit.textCount = Array.isArray(data.texts) ? data.texts.length : 0
          unit.wordCount = Array.isArray(data.words) ? data.words.length : 0
        } catch (error) {
          console.error(`获取单元 ${unit.id} 统计信息失败:`, error)
          unit.textCount = 0
          unit.wordCount = 0
        }
      }
    } else {
      ElMessage.error(response?.message || '获取单元列表失败')
    }
  } catch (error) {
    console.error('获取单元列表失败:', error)
    ElMessage.error('获取单元列表失败')
  } finally {
    loading.value = false
  }
}

const enterUnit = (unit) => {
  if (!unit?.id) {
    ElMessage.error('单元数据异常')
    return
  }
  router.push({
    path: '/user/texts',
    query: {
      unitId: unit.id,
      courseId: route.query.courseId
    }
  })
}

onMounted(() => {
  loadUnits()
})
</script>

<style scoped>
.user-units {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
}

.unit-card {
  cursor: pointer;
  transition: all 0.3s;
  margin-bottom: 20px;
}

.unit-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.unit-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.unit-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #333;
}

.unit-description {
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  margin-bottom: 15px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.unit-stats {
  display: flex;
  gap: 20px;
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px solid #f0f0f0;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 14px;
  color: #666;
}

.stat-item .el-icon {
  font-size: 16px;
}
</style>

