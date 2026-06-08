<template>
  <div class="vocabulary">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的生词本</span>
          <div class="header-actions">
            <el-input
              v-model="searchWord"
              placeholder="搜索单词"
              style="width: 220px"
              clearable
              @keyup.enter="handleSearch"
            />
            <el-button type="primary" @click="handleSearch">搜索</el-button>
          </div>
        </div>
      </template>

      <div class="stats-bar">
        <div class="stat-item">
          <span class="label">总词汇量</span>
          <span class="value">{{ stats.totalWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">新词</span>
          <span class="value new">{{ stats.newWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">学习中</span>
          <span class="value learning">{{ stats.learningWords }}</span>
        </div>
        <div class="stat-item">
          <span class="label">已掌握</span>
          <span class="value mastered">{{ stats.masteredWords }}</span>
        </div>
      </div>

      <div class="filter-bar">
        <el-form :inline="true">
          <el-form-item label="掌握程度">
            <el-select
              v-model="filterForm.masteryLevel"
              clearable
              @change="handleSearch"
            >
              <el-option label="全部" value="" />
              <el-option label="新词" value="NEW" />
              <el-option label="学习中" value="LEARNING" />
              <el-option label="已掌握" value="MASTERED" />
            </el-select>
          </el-form-item>
          <el-form-item label="排序">
            <el-select v-model="filterForm.sortBy" @change="handleSearch">
              <el-option label="添加时间" value="addedTime" />
              <el-option label="复习次数" value="reviewCount" />
              <el-option label="最后复习" value="lastReviewed" />
              <el-option label="单词" value="word" />
            </el-select>
          </el-form-item>
          <el-form-item label="难度">
            <el-select
              v-model="filterForm.difficulty"
              clearable
              @change="handleSearch"
            >
              <el-option label="全部" value="" />
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button
              type="primary"
              @click="startReview"
              :disabled="vocabulary.length === 0"
              >开始复习</el-button
            >
            <el-button
              @click="startWordTest"
              :disabled="vocabulary.length === 0"
              >单词测试</el-button
            >
            <el-button
              type="danger"
              @click="batchDelete"
              :disabled="selectedWords.length === 0"
              >批量删除</el-button
            >
          </el-form-item>
        </el-form>
      </div>

       <div class="sm2-toolbar">
 <el-button
 @click="sm2SettingsDrawerVisible = true"
 :icon="SetUp"
 >SM-2 设置</el-button>
 <el-tag
 v-if="sm2CurrentMode"
 :type="sm2CurrentMode === 'standard' ? 'info' : 'warning'"
 size="small"
 effect="plain"
 >
 <el-icon style="vertical-align: middle; margin-right: 2px;"><Sunny /></el-icon>
 模式: {{ sm2CurrentMode === 'standard' ? '标准' : sm2CurrentMode === 'hard' ? '困难词' : sm2CurrentMode === 'overload' ? '过载保护' : '混合' }}
 </el-tag>
 </div>

 <el-tabs v-model="activeTab" class="vocabulary-tabs">
 <el-tab-pane name="list">
 <template #label>
 <span><el-icon><VideoPlay /></el-icon> 单词列表</span>
 </template>
 <div class="vocabulary-list" v-loading="loading">
        <div class="list-header">
          <el-checkbox
            v-model="selectAll"
            :indeterminate="isIndeterminate"
            @change="handleSelectAll"
          >
            全选
          </el-checkbox>
          <span v-if="selectedWords.length > 0" class="selected-count"
            >已选择 {{ selectedWords.length }} 个单词</span
          >
        </div>

        <div
          v-for="item in vocabulary"
          :key="item.id"
          class="vocabulary-item"
          :class="{ selected: selectedWords.includes(item.id) }"
          @click="showWordDetail(item)"
        >
          <div class="word-checkbox" @click.stop>
            <el-checkbox v-model="selectedWords" :value="item.id" />
          </div>

          <div class="word-main">
            <div class="word-header">
              <h3 class="word">{{ item.word }}</h3>
              <div class="word-meta">
                <el-tag :type="getMasteryType(item.masteryLevel)" size="small">
                  {{ getMasteryText(item.masteryLevel) }}
                </el-tag>
                <el-tag :type="getDifficultyType(item.difficulty)" size="small">
                  {{ getDifficultyText(item.difficulty) }}
                </el-tag>
              </div>
            </div>

            <div class="pronunciation" v-if="item.pronunciation">
              {{ item.pronunciation }}
            </div>
            <div class="definition">{{ item.definition }}</div>
            <div class="example" v-if="item.exampleSentence">
              <span class="example-label">例句：</span
              >{{ item.exampleSentence }}
            </div>
            <div class="word-info">
              <span>添加时间：{{ formatDate(item.addedTime) }}</span>
              <span v-if="item.lastReviewed"
                >最后复习：{{ formatDate(item.lastReviewed) }}</span
              >
            </div>
          </div>

          <div class="word-actions">
            <el-button
              size="small"
              @click.stop="playAudio(item)"
              :disabled="!item.audioUrl"
            >
              <el-icon><VideoPlay /></el-icon>
            </el-button>
            <el-button
              size="small"
              @click.stop="markAsLearned(item)"
              v-if="item.masteryLevel !== 'MASTERED'"
            >
              <el-icon><Check /></el-icon>
            </el-button>
            <el-button size="small" @click.stop="deleteWord(item)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>

        <el-empty
          v-if="!loading && vocabulary.length === 0"
          description="暂无生词"
        >
          <el-button type="primary" @click="router.push('/user')"
            >去学习</el-button
          >
        </el-empty>
 </div>
 </el-tab-pane>

 <el-tab-pane name="analytics">s">
 <template #label>
 <span><el-icon><DataAnalysis /></el-icon> 数据分析</span>
 </template>
 <div class="tab-pane-content">
 <SM2AnalyticsDashboard :words="vocabulary" />
 </div>
 </el-tab-pane>

 <el-tab-pane name="comparison">
 <template #label>
 <span><el-icon><DataAnalysis /></el-icon> 模式对比</span>
 </template>
 <div class="tab-pane-content">
 <SM2ComparisonPanel :words="vocabulary" />
 </div>
 </el-tab-pane>
 </el-tabs>
 <div v-if="total > 0" class="pagination">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadVocabulary"
          @current-change="loadVocabulary"
        />
      </div>
    </el-card>

    <el-dialog v-model="wordDialogVisible" title="单词详解" width="500px">
      <div v-if="selectedWord" class="word-detail">
        <div class="word-detail-header">
          <h2>{{ selectedWord.word }}</h2>
          <el-button
            @click="playAudio(selectedWord)"
            :disabled="!selectedWord.audioUrl"
          >
            <el-icon><VideoPlay /></el-icon> 播放
          </el-button>
        </div>
        <p class="pronunciation">{{ selectedWord.pronunciation }}</p>
        <div class="definition">{{ selectedWord.definition }}</div>
        <div class="example" v-if="selectedWord.exampleSentence">
          <span class="example-label">例句：</span
          >{{ selectedWord.exampleSentence }}
        </div>
        <div class="detail-tags">
          <el-tag>{{ selectedWord.partOfSpeech || "未知词性" }}</el-tag>
          <el-tag :type="getDifficultyType(selectedWord.difficulty)">
            {{ getDifficultyText(selectedWord.difficulty) }}
          </el-tag>
          <el-tag :type="getMasteryType(selectedWord.masteryLevel)">
            {{ getMasteryText(selectedWord.masteryLevel) }}
          </el-tag>
        </div>
        <div class="detail-actions">
          <el-button
            v-if="selectedWord.masteryLevel !== 'MASTERED'"
            @click="markAsLearned(selectedWord)"
          >
            标记为已掌握
          </el-button>
          <el-button
            v-else
            type="warning"
            @click="resetToLearning(selectedWord)"
            >重置为学习中</el-button
          >
          <el-button type="danger" @click="deleteWord(selectedWord)"
            >删除单词</el-button
          >
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="reviewDialogVisible"
      title="生词复习"
      width="800px"
      :close-on-click-modal="false"
    >
      <div v-if="reviewWords.length > 0" class="review-session">
        <div class="review-progress">
          <span
            >进度：{{ currentReviewIndex + 1 }} / {{ reviewWords.length }}</span
          >
          <el-progress :percentage="reviewProgress" />
        </div>

        <div class="review-card">
          <div v-if="!showAnswer" class="review-front">
            <h2 class="word">{{ currentReviewWord.word }}</h2>
            <div class="review-controls">
              <el-button
                @click="playAudio(currentReviewWord)"
                :disabled="!currentReviewWord.audioUrl"
              >
                <el-icon><VideoPlay /></el-icon> 播放
              </el-button>
              <el-button type="primary" @click="showAnswer = true"
                >显示答案</el-button
              >
            </div>
          </div>

          <div v-else class="review-back">
            <h2 class="word">{{ currentReviewWord.word }}</h2>
            <p class="pronunciation">{{ currentReviewWord.pronunciation }}</p>
            <div class="definition">{{ currentReviewWord.definition }}</div>
            <div class="example" v-if="currentReviewWord.exampleSentence">
              <span class="example-label">例句：</span
              >{{ currentReviewWord.exampleSentence }}
            </div>
            <div class="review-actions">
              <el-button type="danger" @click="submitReview('UNKNOWN')"
                >不认识</el-button
              >
              <el-button type="warning" @click="submitReview('UNCERTAIN')"
                >模糊</el-button
              >
              <el-button type="success" @click="submitReview('KNOWN')"
                >认识</el-button
              >
            </div>
          </div>
        </div>
      </div>

      <div v-else class="review-complete">
        <h3>复习完成</h3>
        <div class="review-summary">
          <div class="summary-item">
            <span>总复习单词：</span
            ><strong>{{ reviewSummary.totalWords }}</strong>
          </div>
          <div class="summary-item">
            <span>认识：</span
            ><strong class="known">{{ reviewSummary.knownWords }}</strong>
          </div>
          <div class="summary-item">
            <span>模糊：</span
            ><strong class="uncertain">{{
              reviewSummary.uncertainWords
            }}</strong>
          </div>
          <div class="summary-item">
            <span>不认识：</span
            ><strong class="unknown">{{ reviewSummary.unknownWords }}</strong>
          </div>
        </div>
        <el-button type="primary" @click="reviewDialogVisible = false"
          >完成</el-button
        >
      </div>
    </el-dialog>
   <SM2SettingsDrawer v-model="sm2SettingsDrawerVisible" @settings-changed="loadSm2Settings" />
 </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage, ElMessageBox } from "element-plus";
import { Check, Delete, SetUp, DataAnalysis, Sunny } from "@element-plus/icons-vue";
import {
  batchOperateVocabulary,
  getVocabulary as getVocabularyApi,
  removeFromVocabulary as removeFromVocabularyApi,
  updateVocabularyMastery,
} from "@/api/student";
import { getSettings } from "@/utils/sm2-settings";
import SM2SettingsDrawer from "@/components/vocabulary/SM2SettingsDrawer.vue";
import SM2AnalyticsDashboard from "@/components/vocabulary/SM2AnalyticsDashboard.vue";
import SM2ComparisonPanel from "@/components/vocabulary/SM2ComparisonPanel.vue";

const router = useRouter();

const loading = ref(false);
const searchWord = ref("");
const vocabulary = ref([]);
const total = ref(0);
const currentPage = ref(1);
const pageSize = ref(20);
const selectedWords = ref([]);
const selectAll = ref(false);

const stats = reactive({
  totalWords: 0,
  newWords: 0,
  learningWords: 0,
  masteredWords: 0,
});

const filterForm = reactive({
  masteryLevel: "",
  sortBy: "addedTime",
  difficulty: "",
});

const wordDialogVisible = ref(false);
const selectedWord = ref(null);

const activeTab = ref("list");
const sm2SettingsDrawerVisible = ref(false);
const sm2CurrentMode = ref("standard");

const loadSm2Settings = () => {
 try {
 const settings = getSettings();
 sm2CurrentMode.value = settings.mode || "standard";
 } catch (e) {
 console.warn("加载 SM-2 设置失败:", e);
 }
};

const reviewDialogVisible = ref(false);
const reviewWords = ref([]);
const currentReviewIndex = ref(0);
const showAnswer = ref(false);
const reviewSummary = reactive({
  totalWords: 0,
  knownWords: 0,
  uncertainWords: 0,
  unknownWords: 0,
});

const isIndeterminate = computed(() => {
  const selectedCount = selectedWords.value.length;
  const totalCount = vocabulary.value.length;
  return selectedCount > 0 && selectedCount < totalCount;
});

const currentReviewWord = computed(
  () => reviewWords.value[currentReviewIndex.value] || {},
);
const reviewProgress = computed(() => {
  if (reviewWords.value.length === 0) return 100;
  return Math.round(
    (currentReviewIndex.value / reviewWords.value.length) * 100,
  );
});

const resolveWordId = (word) => word?.wordId || word?.id;

const updateStats = () => {
  stats.totalWords = total.value;
  stats.newWords = vocabulary.value.filter(
    (item) => item.masteryLevel === "NEW",
  ).length;
  stats.learningWords = vocabulary.value.filter(
    (item) => item.masteryLevel === "LEARNING",
  ).length;
  stats.masteredWords = vocabulary.value.filter(
    (item) => item.masteryLevel === "MASTERED",
  ).length;
};

const loadVocabulary = async () => {
  loading.value = true;
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
    };
    if (searchWord.value) params.keyword = searchWord.value;
    if (filterForm.masteryLevel) params.masteryLevel = filterForm.masteryLevel;
    if (filterForm.sortBy) params.sortBy = filterForm.sortBy;
    if (filterForm.difficulty) params.difficulty = filterForm.difficulty;

    const response = await getVocabularyApi(params);
    vocabulary.value = response?.data?.records || [];
    total.value = response?.data?.total || 0;
    updateStats();
  } catch (error) {
    console.error("加载生词本失败:", error);
    ElMessage.error("加载生词本失败");
    vocabulary.value = [];
    total.value = 0;
    updateStats();
  } finally {
    loading.value = false;
  }
};

const handleSearch = () => {
  currentPage.value = 1;
  loadVocabulary();
};

const showWordDetail = (word) => {
  selectedWord.value = word;
  wordDialogVisible.value = true;
};

const markAsLearned = async (word) => {
  try {
    await updateVocabularyMastery(resolveWordId(word), {
      masteryLevel: "MASTERED",
      isCorrect: true,
    });
    ElMessage.success("已标记为已掌握");
    await loadVocabulary();
  } catch (error) {
    console.error("更新掌握程度失败:", error);
    ElMessage.error("更新掌握程度失败");
  }
};

const resetToLearning = async (word) => {
  try {
    await updateVocabularyMastery(resolveWordId(word), {
      masteryLevel: "LEARNING",
      isCorrect: false,
    });
    ElMessage.success("已重置为学习中");
    await loadVocabulary();
  } catch (error) {
    console.error("重置掌握程度失败:", error);
    ElMessage.error("重置掌握程度失败");
  }
};

const deleteWord = async (word) => {
  try {
    await ElMessageBox.confirm(
      `确定要删除单词 "${word.word}" 吗？`,
      "删除确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    await removeFromVocabularyApi(resolveWordId(word));
    ElMessage.success("删除成功");
    if (selectedWord.value?.id === word.id) {
      selectedWord.value = null;
      wordDialogVisible.value = false;
    }
    await loadVocabulary();
  } catch (error) {
    if (error !== "cancel") {
      console.error("删除失败:", error);
      ElMessage.error("删除失败");
    }
  }
};

const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(
      `确定要删除选中的 ${selectedWords.value.length} 个单词吗？`,
      "批量删除确认",
      {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      },
    );

    const wordIds = vocabulary.value
      .filter((item) => selectedWords.value.includes(item.id))
      .map(resolveWordId)
      .filter(Boolean);

    await batchOperateVocabulary({
      operation: "delete",
      wordIds,
    });
    selectedWords.value = [];
    selectAll.value = false;
    ElMessage.success("批量删除成功");
    await loadVocabulary();
  } catch (error) {
    if (error !== "cancel") {
      console.error("批量删除失败:", error);
      ElMessage.error("批量删除失败");
    }
  }
};

const handleSelectAll = (checked) => {
  selectedWords.value = checked ? vocabulary.value.map((item) => item.id) : [];
};

const startReview = () => {
  const reviewableWords = vocabulary.value.filter(
    (word) => word.masteryLevel !== "MASTERED",
  );
  if (reviewableWords.length === 0) {
    ElMessage.info("没有需要复习的单词");
    return;
  }

  reviewWords.value = reviewableWords;
  currentReviewIndex.value = 0;
  showAnswer.value = false;
  Object.assign(reviewSummary, {
    totalWords: reviewWords.value.length,
    knownWords: 0,
    uncertainWords: 0,
    unknownWords: 0,
  });
  reviewDialogVisible.value = true;
};

const submitReview = async (result) => {
  const currentWord = reviewWords.value[currentReviewIndex.value];
  if (!currentWord) return;

  if (result === "KNOWN") reviewSummary.knownWords++;
  if (result === "UNCERTAIN") reviewSummary.uncertainWords++;
  if (result === "UNKNOWN") reviewSummary.unknownWords++;

  try {
    await updateVocabularyMastery(resolveWordId(currentWord), {
      masteryLevel: result === "KNOWN" ? "MASTERED" : "LEARNING",
      isCorrect: result === "KNOWN",
    });
  } catch (error) {
    console.error("提交复习结果失败:", error);
  }

  currentReviewIndex.value++;
  showAnswer.value = false;
  if (currentReviewIndex.value >= reviewWords.value.length) {
    reviewWords.value = [];
    loadVocabulary();
  }
};

const startWordTest = () => {
  startReview();
};

let currentAudio = null;

const playAudio = (word) => {
  if (!word?.audioUrl) {
    ElMessage.warning("暂无音频可播放");
    return;
  }

  if (currentAudio) {
    currentAudio.pause();
    currentAudio.src = "";
  }

  currentAudio = new Audio(word.audioUrl);
  currentAudio.play().catch((error) => {
    console.error("播放音频失败:", error);
    ElMessage.error("播放音频失败");
  });
};

const getMasteryType = (level) =>
  ({
    NEW: "info",
    LEARNING: "warning",
    MASTERED: "success",
  })[level] || "info";

const getMasteryText = (level) =>
  ({
    NEW: "新词",
    LEARNING: "学习中",
    MASTERED: "已掌握",
  })[level] || "未知";

const getDifficultyType = (difficulty) =>
  ({
    EASY: "success",
    MEDIUM: "warning",
    HARD: "danger",
  })[difficulty] || "info";

const getDifficultyText = (difficulty) =>
  ({
    EASY: "简单",
    MEDIUM: "中等",
    HARD: "困难",
  })[difficulty] || "未知";

const formatDate = (date) => {
  if (!date) return "";
  return new Date(date).toLocaleString("zh-CN", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
  });
};

onMounted(() => {
  loadSm2Settings();
 loadVocabulary();
});

onBeforeUnmount(() => {
  if (currentAudio) {
    currentAudio.pause();
    currentAudio.src = "";
    currentAudio = null;
  }
});
</script>

<style scoped>
.vocabulary {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.stats-bar {
  display: flex;
  gap: 30px;
  margin-bottom: 20px;
  padding: 15px;
  background: #f8f9fa;
  border-radius: 8px;
}

.stat-item {
  display: flex;
  align-items: center;
  gap: 8px;
}

.label {
  color: #666;
  font-size: 14px;
}

.value {
  font-weight: bold;
  font-size: 16px;
}

.value.new {
  color: #409eff;
}

.value.learning {
  color: #e6a23c;
}

.value.mastered {
  color: #67c23a;
}

.filter-bar {
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid #f0f0f0;
}

.vocabulary-list {
  min-height: 400px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 0;
  border-bottom: 1px solid #f0f0f0;
  margin-bottom: 10px;
}

.selected-count {
  color: #409eff;
  font-size: 14px;
}

.vocabulary-item {
  padding: 15px;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: flex-start;
  cursor: pointer;
  transition: all 0.3s;
}

.vocabulary-item:hover {
  background-color: #f8f9fa;
}

.vocabulary-item.selected {
  background-color: #e6f7ff;
  border-color: #409eff;
}

.word-checkbox {
  margin-right: 15px;
  padding-top: 5px;
}

.word-main {
  flex: 1;
}

.word-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.word {
  margin: 0;
  color: #333;
  font-size: 18px;
}

.word-meta {
  display: flex;
  gap: 10px;
}

.pronunciation {
  color: #666;
  font-style: italic;
  margin: 8px 0;
}

.definition {
  color: #333;
  line-height: 1.5;
  margin: 8px 0;
}

.example {
  color: #666;
  font-size: 14px;
  margin-top: 8px;
}

.example-label {
  font-weight: bold;
}

.word-info {
  display: flex;
  gap: 20px;
  margin-top: 10px;
  font-size: 12px;
  color: #888;
}

.word-actions {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-left: 15px;
}

.pagination {
  margin-top: 20px;
  text-align: center;
}

.word-detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.detail-tags {
  display: flex;
  gap: 10px;
  margin-top: 15px;
}

.detail-actions {
  display: flex;
  justify-content: center;
  gap: 10px;
  margin-top: 20px;
}

.review-session {
  padding: 20px;
}

.review-progress {
  margin-bottom: 30px;
  text-align: center;
}

.review-progress span {
  display: block;
  margin-bottom: 10px;
}

.review-card {
  min-height: 300px;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  padding: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.review-front,
.review-back {
  width: 100%;
  text-align: center;
}

.review-controls,
.review-actions {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin-top: 30px;
}

.review-complete {
  text-align: center;
  padding: 40px;
}

.review-summary {
  margin-bottom: 30px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  padding: 10px 0;
  font-size: 16px;
}

.summary-item .known {
  color: #67c23a;
}

.summary-item .uncertain {
  color: #e6a23c;
}

.summary-item .unknown {
  color: #f56c6c;
}

.sm2-toolbar {
 display: flex;
 align-items: center;
 gap: 12px;
 margin-bottom: 12px;
 padding: 10px 12px;
 background: linear-gradient(90deg, #f0f9ff 0%, #fefce8 100%);
 border-radius: 8px;
 border: 1px solid #e0f2fe;
}

.vocabulary-tabs {
 margin-top: 4px;
}

.vocabulary-tabs :deep(.el-tabs__nav-wrap::after) {
 height: 1px;
}

.vocabulary-tabs :deep(.el-tabs__item) {
 font-size: 14px;
}

.tab-pane-content {
 padding: 16px 4px;
}
</style>
