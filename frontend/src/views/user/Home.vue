<template>
  <div class="user-home">
    <el-card class="welcome-card">
      <h2>
        欢迎回来，{{
          userStore.userInfo?.realName || userStore.userInfo?.username
        }}
      </h2>
      <p>继续你的英语学习旅程</p>
    </el-card>

    <el-row :gutter="20" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-number">{{ dashboardData.enrolledCourses }}</div>
          <div class="stat-label">在学课程</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-number">{{ dashboardData.vocabularyCount }}</div>
          <div class="stat-label">生词数量</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-number">{{ dashboardData.pendingAssignments }}</div>
          <div class="stat-label">待办任务</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-number">{{ dashboardData.todayStudyTime }}</div>
          <div class="stat-label">今日学习(分钟)</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="quick-actions-card">
      <template #header>
        <span>快捷入口</span>
      </template>
      <div class="quick-actions">
        <el-button type="primary" @click="router.push('/user/courses')"
          >浏览课程</el-button
        >
        <el-button @click="router.push('/user/texts')">学习课文</el-button>
        <el-button @click="router.push('/user/user-texts')">上传内容</el-button>
        <el-button @click="router.push('/user/vocabulary')">生词本</el-button>
      </div>
    </el-card>

    <el-row :gutter="20" class="content-row">
      <el-col :span="12">
        <el-card v-loading="loading">
          <template #header>
            <div class="card-header">
              <span>我的课程</span>
              <el-button type="text" @click="router.push('/user/courses')"
                >更多</el-button
              >
            </div>
          </template>
          <div
            v-if="dashboardData.myClasses.length === 0"
            class="empty-content"
          >
            <el-empty description="暂无课程" />
          </div>
          <div v-else class="list">
            <div
              v-for="course in dashboardData.myClasses"
              :key="course.id"
              class="list-item"
              @click="enterCourse(course)"
            >
              <div>
                <div class="item-title">{{ course.courseName }}</div>
                <div class="item-meta">
                  {{ course.className }} · {{ course.teacherName }}
                </div>
              </div>
              <div class="item-extra">{{ getCourseProgress(course) }}%</div>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card v-loading="loading">
          <template #header>
            <div class="card-header">
              <span>待办任务</span>
              <el-button
                type="text"
                @click="router.push('/student/assignments')"
                >更多</el-button
              >
            </div>
          </template>
          <div
            v-if="dashboardData.pendingAssignmentsList.length === 0"
            class="empty-content"
          >
            <el-empty description="暂无待办任务" />
          </div>
          <div v-else class="list">
            <div
              v-for="assignment in dashboardData.pendingAssignmentsList"
              :key="assignment.id"
              class="list-item"
              @click="router.push('/student/assignments')"
            >
              <div>
                <div class="item-title">{{ assignment.title }}</div>
                <div class="item-meta">{{ assignment.className }}</div>
              </div>
              <div class="item-extra">
                {{ formatDate(assignment.deadline) }}
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="content-row">
      <el-col :span="12">
        <el-card>
          <template #header>
            <span>今日统计</span>
          </template>
          <div class="stats-list">
            <div class="stats-line">
              <span>学习单词</span
              ><strong>{{ learningStats.wordsLearned }}</strong>
            </div>
            <div class="stats-line">
              <span>复习次数</span
              ><strong>{{ learningStats.reviewCount }}</strong>
            </div>
            <div class="stats-line">
              <span>学习时长</span
              ><strong>{{ learningStats.studyTime }}</strong>
            </div>
            <div class="stats-line">
              <span>平均正确率</span
              ><strong>{{ learningStats.accuracy }}%</strong>
            </div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <span>学习推荐</span>
          </template>
          <div
            v-if="dashboardData.learningRecommendation"
            class="recommendation"
            @click="handleRecommendationClick"
          >
            <div class="item-title">
              {{ dashboardData.learningRecommendation.title }}
            </div>
            <div class="item-meta">
              {{ dashboardData.learningRecommendation.content }}
            </div>
            <div
              v-if="dashboardData.learningRecommendation.pronunciation"
              class="item-meta"
            >
              {{ dashboardData.learningRecommendation.pronunciation }}
            </div>
          </div>
          <el-empty v-else description="暂无推荐内容" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { useUserStore } from "@/store/user";
import { getStudentDashboard, getTodayLearningStats } from "@/api/student";

const router = useRouter();
const userStore = useUserStore();

const loading = ref(false);
const dashboardData = reactive({
  enrolledCourses: 0,
  vocabularyCount: 0,
  pendingAssignments: 0,
  todayStudyTime: 0,
  myClasses: [],
  pendingAssignmentsList: [],
  learningRecommendation: null,
});

const learningStats = reactive({
  wordsLearned: 0,
  reviewCount: 0,
  studyTime: 0,
  accuracy: 0,
});

const loadDashboard = async () => {
  loading.value = true;
  try {
    const response = await getStudentDashboard();
    if (response?.code === 200 && response.data) {
      Object.assign(dashboardData, {
        enrolledCourses: response.data.enrolledCourses || 0,
        vocabularyCount: response.data.vocabularyCount || 0,
        pendingAssignments: response.data.pendingAssignments || 0,
        todayStudyTime: response.data.todayStudyTime || 0,
        myClasses: Array.isArray(response.data.myClasses)
          ? response.data.myClasses
          : [],
        pendingAssignmentsList: Array.isArray(
          response.data.pendingAssignmentsList,
        )
          ? response.data.pendingAssignmentsList
          : [],
        learningRecommendation: response.data.learningRecommendation || null,
      });
    }
  } finally {
    loading.value = false;
  }
};

const loadLearningStats = async () => {
  const response = await getTodayLearningStats();
  if (response?.code === 200 && response.data) {
    Object.assign(learningStats, {
      wordsLearned: response.data.wordsLearned || 0,
      reviewCount: response.data.reviewCount || 0,
      studyTime: response.data.studyTime || 0,
      accuracy: Number(response.data.accuracy || 0).toFixed(1),
    });
  }
};

const enterCourse = (course) => {
  router.push(`/user/courses?courseId=${course.courseId}`);
};

const handleRecommendationClick = () => {
  const recommendation = dashboardData.learningRecommendation;
  if (!recommendation) return;
  if (recommendation.type === "word") {
    router.push("/user/vocabulary");
  } else {
    router.push(`/article/${recommendation.id}`);
  }
};

const getCourseProgress = (course) => {
  const progress =
    typeof course?.progress === "number"
      ? course.progress
      : parseFloat(course?.progress);
  if (Number.isFinite(progress)) {
    return Math.round(progress <= 1 ? progress * 100 : progress);
  }
  return 0;
};

const formatDate = (dateString) => {
  if (!dateString) return "";
  const date = new Date(dateString);
  return Number.isNaN(date.getTime()) ? "" : date.toLocaleDateString();
};

onMounted(async () => {
  await Promise.all([loadDashboard(), loadLearningStats()]);
});
</script>

<style scoped>
:deep(.el-card) {
  border-radius: 12px;
}

.user-home {
  padding: 20px;
}

.welcome-card {
  margin-bottom: 20px;
}

.stats-row,
.content-row {
  margin-bottom: 20px;
}

.stat-card {
  text-align: center;
  border-radius: 12px;
  min-height: 80px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

:deep(.el-card__body) {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.stat-number {
  font-size: 28px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  color: #666;
  margin-top: 8px;
}

.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px solid #f0f0f0;
  cursor: pointer;
}

.item-title {
  font-weight: 600;
  color: #333;
}

.item-meta {
  color: #666;
  font-size: 13px;
  margin-top: 4px;
}

.item-extra {
  color: #409eff;
  font-weight: 600;
}

.stats-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.stats-line {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.recommendation {
  cursor: pointer;
}

.empty-content {
  padding: 20px 0;
}
</style>
