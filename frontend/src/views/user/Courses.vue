<template>
  <div class="user-courses">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2>课程学习</h2>
      <p>选择您想学习的课程</p>
    </div>

    <!-- 课程列表 -->
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="courses.length === 0" class="empty-container">
      <el-empty description="暂无可用课程" />
    </div>

    <div v-else class="courses-grid">
      <el-card
        v-for="course in courses"
        :key="course.id"
        class="course-card"
        @click="enterCourse(course)"
      >
        <div class="course-cover">
          <img v-if="course.coverImage" :src="course.coverImage" :alt="course.name" />
          <div v-else class="default-cover">
            <el-icon><Reading /></el-icon>
          </div>
        </div>

        <div class="course-info">
          <h3 class="course-title">{{ course.name }}</h3>
          <p class="course-description">{{ course.description || '暂无描述' }}</p>

          <div class="course-meta">
            <div class="course-stats">
              <span><el-icon><Document /></el-icon> {{ course.textCount || 0 }} 个课文</span>
              <span><el-icon><Notebook /></el-icon> {{ course.wordCount || 0 }} 个单词</span>
            </div>

            <div class="course-status">
              <el-tag type="success" size="small">可学习</el-tag>
            </div>
          </div>
        </div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Reading, Document, Notebook } from '@element-plus/icons-vue'
import request from '@/utils/request'

const router = useRouter()
const loading = ref(false)
const courses = ref([])

const loadCourses = async () => {
  loading.value = true
  try {
    const response = await request({
      url: '/api/student/learning/courses',
      method: 'get'
    })

    if (response.code === 200) {
      // 普通用户无权访问 /api/admin/** 接口，直接使用课程列表，统计数据以默认值填充
      const data = response.data
      courses.value = (Array.isArray(data) ? data : (Array.isArray(data?.records) ? data.records : [])).map(course => ({
        ...course,
        textCount: course.textCount || 0,
        wordCount: course.wordCount || 0
      }))
    } else {
      ElMessage.error(response.message || '获取课程列表失败')
    }
  } catch (error) {
    ElMessage.error('获取课程列表失败')
    console.error(error)
  } finally {
    loading.value = false
  }
}

const enterCourse = async (course) => {
  try {
    // 获取课程下的单元列表
    const response = await request({
      url: `/api/units/course/${course.id}`,
      method: 'get'
    })

    if (response.code === 200 && Array.isArray(response.data) && response.data.length > 0) {
      // 如果有单元，跳转到单元列表页面
      router.push({
        path: '/user/units',
        query: { courseId: course.id, courseName: course.name }
      })
    } else {
      ElMessage.warning('该课程暂无单元内容')
    }
  } catch (error) {
    console.error('获取单元列表失败:', error)
    ElMessage.error('获取单元列表失败')
  }
}

onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.user-courses {
  padding: 0;
}

.page-header {
  text-align: center;
  margin-bottom: 30px;
}

.page-header h2 {
  margin: 0 0 10px;
  color: #333;
  font-size: 28px;
}

.page-header p {
  margin: 0;
  color: #666;
  font-size: 16px;
}

.loading-container {
  padding: 20px;
}

.empty-container {
  padding: 40px;
  text-align: center;
}

.courses-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
  gap: 20px;
}

.course-card {
  cursor: pointer;
  transition: all 0.3s;
  overflow: hidden;
}

.course-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 25px rgba(0, 0, 0, 0.1);
}

.course-cover {
  height: 180px;
  overflow: hidden;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
}

.course-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.default-cover {
  color: white;
  font-size: 48px;
}

.course-info {
  padding: 20px;
}

.course-title {
  margin: 0 0 10px;
  font-size: 18px;
  font-weight: 600;
  color: #333;
  line-height: 1.4;
}

.course-description {
  margin: 0 0 15px;
  color: #666;
  font-size: 14px;
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.course-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-stats {
  display: flex;
  gap: 15px;
  font-size: 13px;
  color: #666;
}

.course-stats span {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .courses-grid {
    grid-template-columns: 1fr;
  }

  .page-header h2 {
    font-size: 24px;
  }

  .course-meta {
    flex-direction: column;
    gap: 10px;
    align-items: flex-start;
  }
}
</style>