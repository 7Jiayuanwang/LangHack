<template>
  <div class="user-texts">
    <div class="page-header">
      <h2>课文学习</h2>
      <p>选择课文开始学习</p>
    </div>

    <el-card class="filter-card">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-select
            v-model="selectedCourse"
            placeholder="选择课程"
            clearable
            @change="handleCourseChange"
          >
            <el-option
              v-for="course in courses"
              :key="course.id"
              :label="course.name"
              :value="course.id"
            />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select
            v-model="selectedUnit"
            placeholder="选择单元"
            clearable
            @change="loadTexts"
          >
            <el-option
              v-for="unit in units"
              :key="unit.id"
              :label="unit.name"
              :value="unit.id"
            />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-select
            v-model="selectedDifficulty"
            placeholder="选择难度"
            clearable
            @change="loadTexts"
          >
            <el-option label="简单" value="EASY" />
            <el-option label="中等" value="MEDIUM" />
            <el-option label="困难" value="HARD" />
          </el-select>
        </el-col>
        <el-col :span="6">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索课文"
            clearable
            @keyup.enter="loadTexts"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-col>
      </el-row>
    </el-card>

    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="3" animated />
    </div>

    <div v-else-if="texts.length === 0" class="empty-container">
      <el-empty description="暂无课文内容" />
    </div>

    <div v-else class="texts-grid">
      <el-card
        v-for="text in texts"
        :key="text.id"
        class="text-card"
        @click="studyText(text)"
      >
        <div class="text-header">
          <h3 class="text-title">{{ text.title }}</h3>
          <div class="text-meta">
            <el-tag :type="getDifficultyType(text.difficulty)" size="small">
              {{ getDifficultyText(text.difficulty) }}
            </el-tag>
            <span v-if="text.unitName" class="unit-tag"
              >第{{ text.orderNum }}单元</span
            >
          </div>
        </div>

        <div class="text-content">
          <p>{{ getContentPreview(text.content) }}</p>
        </div>

        <div class="text-footer">
          <div class="text-info">
            <span v-if="text.courseName">{{ text.courseName }}</span>
            <span v-if="text.unitName">{{ text.unitName }}</span>
          </div>
          <div class="text-actions">
            <el-button size="small" type="primary">
              <el-icon><Reading /></el-icon>
              开始学习
            </el-button>
          </div>
        </div>
      </el-card>
    </div>

    <!-- 无限滚动加载提示 -->
    <div ref="sentinel" class="scroll-sentinel">
      <div v-if="loadingMore" class="load-more-hint">
        <span class="load-more-spinner"></span>
        加载中...
      </div>
      <div v-else-if="hasMore && texts.length > 0" class="load-more-hint">
        上滑加载更多
      </div>
      <div v-else-if="!hasMore && texts.length > 0" class="load-more-hint">
        — 已经到底了 —
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Search, Reading } from "@element-plus/icons-vue";
import request from "@/utils/request";

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const courses = ref([]);
const units = ref([]);
const texts = ref([]);
const allTexts = ref([]);
const displayCount = ref(12);
const loadingMore = ref(false);
const hasMore = computed(() => displayCount.value < allTexts.value.length);
const sentinel = ref(null);
let observer = null;

const BATCH_SIZE = 12;

const selectedCourse = ref("");
const selectedUnit = ref("");
const selectedDifficulty = ref("");
const searchKeyword = ref("");

const normalizeCourses = (payload) => {
  let courseList = [];
  if (Array.isArray(payload?.courses)) {
    courseList = payload.courses;
  } else if (Array.isArray(payload)) {
    courseList = payload;
  } else if (Array.isArray(payload?.records)) {
    courseList = payload.records;
  }

  return courseList.map((course) => ({
    ...course,
    id: course?.id || course?.courseId,
    name: course?.name || course?.courseName || "未知课程",
    description: course?.description || course?.courseDescription || "暂无描述",
  }));
};

const normalizeUnits = (payload) => {
  if (Array.isArray(payload?.units)) {
    return payload.units;
  }
  if (Array.isArray(payload)) {
    return payload;
  }
  return [];
};

const loadCourses = async () => {
  try {
    const response = await request({
      url: "/api/student/learning/courses",
      method: "get",
    });

    if (response?.code === 200 && response.data) {
      courses.value = normalizeCourses(response.data);
    }
  } catch (error) {
    console.error("获取课程列表失败:", error);
  }
};

const loadUnits = async (courseId) => {
  if (!courseId) {
    units.value = [];
    return;
  }

  try {
    const response = await request({
      url: `/api/student/learning/courses/${courseId}/units`,
      method: "get",
    });

    if (response?.code === 200 && response.data) {
      units.value = normalizeUnits(response.data);
    } else {
      units.value = [];
    }
  } catch (error) {
    console.error("获取单元列表失败:", error);
    units.value = [];
  }
};

const loadTexts = async () => {
  loading.value = true;
  try {
    const params = {
      page: 1,
      size: 999,
    };
    if (selectedDifficulty.value) params.difficulty = selectedDifficulty.value;
    if (selectedCourse.value) params.courseId = selectedCourse.value;
    if (selectedUnit.value) params.unitId = selectedUnit.value;
    if (searchKeyword.value) params.keyword = searchKeyword.value;

    const response = await request({
      url: "/api/public/texts",
      method: "get",
      params,
    });

    if (response?.code === 200 && response.data) {
      if (Array.isArray(response.data)) {
        allTexts.value = response.data;
      } else if (Array.isArray(response.data?.records)) {
        allTexts.value = response.data.records;
      } else {
        allTexts.value = [];
      }
    } else {
      allTexts.value = [];
    }
    displayCount.value = BATCH_SIZE;
    texts.value = allTexts.value.slice(0, displayCount.value);
  } catch (error) {
    ElMessage.error("获取课文列表失败");
    console.error(error);
    allTexts.value = [];
    texts.value = [];
  } finally {
    loading.value = false;
  }
};

// 无限滚动：触底加载更多
const loadMore = () => {
  if (loadingMore.value || !hasMore.value) return
  loadingMore.value = true
  setTimeout(() => {
    displayCount.value = Math.min(displayCount.value + BATCH_SIZE, allTexts.value.length)
    texts.value = allTexts.value.slice(0, displayCount.value)
    loadingMore.value = false
  }, 300)
}

const setupIntersectionObserver = () => {
  if (!sentinel.value) return
  if (observer) observer.disconnect()
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting) {
      loadMore()
    }
  }, { threshold: 0.1 })
  observer.observe(sentinel.value)
}

const studyText = (text) => {
  router.push({
    path: `/article/${text.id}`,
    query: {
      unitId: text.unitId,
      courseId: text.courseId,
    },
  });
};

const getContentPreview = (content) => {
  if (!content) return "";
  return content.length > 80 ? content.substring(0, 80) + "..." : content;
};

const getDifficultyType = (difficulty) => {
  const types = {
    EASY: "success",
    MEDIUM: "warning",
    HARD: "danger",
  };
  return types[difficulty] || "info";
};

const getDifficultyText = (difficulty) => {
  const difficultyMap = {
    EASY: "简单",
    MEDIUM: "中等",
    HARD: "困难",
  };
  return difficultyMap[difficulty] || difficulty;
};

const handleCourseChange = async (courseId) => {
  selectedUnit.value = "";
  displayCount.value = BATCH_SIZE;
  await loadUnits(courseId);
  await loadTexts();
};

onMounted(async () => {
  await loadCourses();

  const courseId = Number(route.query.courseId);
  const unitId = Number(route.query.unitId);
  if (courseId) {
    selectedCourse.value = courseId;
    await loadUnits(courseId);
  }
  if (unitId) {
    selectedUnit.value = unitId;
  }

  await loadTexts();
  // 数据加载完毕后设置无限滚动哨兵
  setTimeout(() => setupIntersectionObserver(), 100);
});

onBeforeUnmount(() => {
  if (observer) {
    observer.disconnect();
    observer = null;
  }
});
</script>

<style scoped>
.user-texts {
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

.filter-card {
  margin-bottom: 20px;
}

.loading-container {
  padding: 20px;
}

.empty-container {
  padding: 40px;
  text-align: center;
}

.texts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 20px;
  margin-bottom: 30px;
}

.text-card {
  cursor: pointer;
  transition: all 0.3s;
  height: 100%;
}

.text-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 6px 20px rgba(0, 0, 0, 0.1);
}

.text-header {
  margin-bottom: 15px;
}

.text-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: #333;
  line-height: 1.4;
}

.text-meta {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}

.unit-tag {
  font-size: 12px;
  color: #666;
  background-color: #f0f0f0;
  padding: 2px 6px;
  border-radius: 3px;
}

.text-content {
  margin-bottom: 15px;
  flex: 1;
}

.text-content p {
  margin: 0;
  color: #666;
  line-height: 1.5;
  font-size: 14px;
}

.text-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 15px;
  border-top: 1px solid #f0f0f0;
}

.text-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
  color: #999;
}

.text-actions {
  flex-shrink: 0;
}

.pagination-container {
  display: flex;
  justify-content: center;
  margin-top: 30px;
}

@media (max-width: 768px) {
  .texts-grid {
    grid-template-columns: 1fr;
  }

  .filter-card .el-row .el-col {
    margin-bottom: 10px;
  }

  .text-footer {
    flex-direction: column;
    gap: 10px;
    align-items: stretch;
  }

  .text-actions {
    text-align: center;
  }
}
</style>
