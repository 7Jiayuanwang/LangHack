<template>
  <div class="weakness-analysis-page">
    <el-row :gutter="20">
      <!-- 左侧：薄弱点列表 -->
      <el-col :span="14">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>薄弱点分析</span>
              <el-button type="primary" @click="analyzeWeakness" :loading="analyzing">
                <el-icon><Refresh /></el-icon>
                重新分析
              </el-button>
            </div>
          </template>

          <el-table :data="weaknessList" stripe v-loading="loading">
            <el-table-column prop="knowledgePoint" label="知识点" min-width="150" />
            <el-table-column label="正确率" width="120">
              <template #default="scope">
                <el-progress
                  :percentage="scope.row.accuracy"
                  :color="getAccuracyColor(scope.row.accuracy)"
                  :stroke-width="8"
                />
              </template>
            </el-table-column>
            <el-table-column prop="wrongCount" label="错题数" width="100" align="center">
              <template #default="scope">
                <el-tag type="danger">{{ scope.row.wrongCount }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalCount" label="总题数" width="100" align="center" />
            <el-table-column label="薄弱等级" width="120" align="center">
              <template #default="scope">
                <el-tag :type="getWeaknessType(scope.row.level)">
                  {{ getWeaknessText(scope.row.level) }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="center">
              <template #default="scope">
                <el-button
                  type="primary"
                  size="small"
                  @click="practiceWeakness(scope.row)"
                >
                  针对性练习
                </el-button>
                <el-button
                  size="small"
                  @click="viewDetail(scope.row)"
                >
                  详情
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <el-empty v-if="weaknessList.length === 0 && !loading" description="暂无薄弱点数据">
            <el-button type="primary" @click="analyzeWeakness">开始分析</el-button>
          </el-empty>
        </el-card>
      </el-col>

      <!-- 右侧：统计图表 -->
      <el-col :span="10">
        <el-card>
          <template #header>
            <span>薄弱点分布</span>
          </template>
          <div ref="chartRef" class="chart-container"></div>
        </el-card>

        <el-card class="mt-20">
          <template #header>
            <span>改善趋势</span>
          </template>
          <div ref="trendChartRef" class="chart-container"></div>
        </el-card>

        <el-card class="mt-20">
          <template #header>
            <span>学习建议</span>
          </template>
          <div class="suggestions">
            <el-alert
              v-for="suggestion in suggestions"
              :key="suggestion.id"
              :title="suggestion.title"
              :type="suggestion.type"
              :closable="false"
              show-icon
              class="suggestion-item"
            >
              <p>{{ suggestion.content }}</p>
            </el-alert>
          </div>
          <div class="suggestion-actions">
            <el-button type="primary" @click="goToLearningSuggestion">
              查看详细建议
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailDialogVisible"
      :title="currentWeakness.knowledgePoint + ' - 详细信息'"
      width="800px"
    >
      <div class="weakness-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="知识点">
            {{ currentWeakness.knowledgePoint }}
          </el-descriptions-item>
          <el-descriptions-item label="薄弱等级">
            <el-tag :type="getWeaknessType(currentWeakness.level)">
              {{ getWeaknessText(currentWeakness.level) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="正确率">
            {{ currentWeakness.accuracy }}%
          </el-descriptions-item>
          <el-descriptions-item label="错题数">
            {{ currentWeakness.wrongCount }} / {{ currentWeakness.totalCount }}
          </el-descriptions-item>
          <el-descriptions-item label="最近练习时间" :span="2">
            {{ formatDate(currentWeakness.lastPracticeTime) }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="wrong-questions">
          <h4>错题列表</h4>
          <el-table :data="currentWeakness.wrongQuestions" stripe>
            <el-table-column prop="question" label="题目" min-width="200" />
            <el-table-column prop="yourAnswer" label="你的答案" width="100" align="center" />
            <el-table-column prop="correctAnswer" label="正确答案" width="100" align="center" />
            <el-table-column label="操作" width="150" align="center">
              <template #default="scope">
                <el-button size="small" @click="reviewQuestion(scope.row)">
                  复习
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import {
  analyzeWeakness as analyzeWeaknessApi,
  getWeaknessList,
  getWeaknessDetail,
  getWeaknessChartData,
  getImprovementTrend,
  getTargetedPractice
} from '@/api/weakness'

const router = useRouter()

// 响应式数据
const loading = ref(false)
const analyzing = ref(false)
const weaknessList = ref([])
const chartRef = ref(null)
const trendChartRef = ref(null)
const detailDialogVisible = ref(false)
const currentWeakness = reactive({
  knowledgePoint: '',
  level: '',
  accuracy: 0,
  wrongCount: 0,
  totalCount: 0,
  lastPracticeTime: null,
  wrongQuestions: []
})

const suggestions = ref([
  {
    id: 1,
    title: '重点关注时态问题',
    type: 'warning',
    content: '您在时态相关的题目中正确率较低，建议加强时态的系统学习。'
  },
  {
    id: 2,
    title: '坚持每日练习',
    type: 'success',
    content: '保持每天至少30分钟的练习时间，可以有效提升学习效果。'
  }
])

let chart = null
let trendChart = null

// 方法
const analyzeWeakness = async () => {
  try {
    analyzing.value = true
    // await analyzeWeaknessApi()
    await loadWeaknessList()
    ElMessage.success('分析完成')
  } catch (error) {
    ElMessage.error('分析失败')
  } finally {
    analyzing.value = false
  }
}

const loadWeaknessList = async () => {
  try {
    loading.value = true
    // const response = await getWeaknessList()
    // weaknessList.value = response.data

    // 模拟数据
    weaknessList.value = [
      {
        id: 1,
        knowledgePoint: '动词时态',
        accuracy: 45,
        wrongCount: 11,
        totalCount: 20,
        level: 'SEVERE',
        lastPracticeTime: new Date().toISOString()
      },
      {
        id: 2,
        knowledgePoint: '主谓一致',
        accuracy: 60,
        wrongCount: 8,
        totalCount: 20,
        level: 'MODERATE',
        lastPracticeTime: new Date().toISOString()
      },
      {
        id: 3,
        knowledgePoint: '词汇运用',
        accuracy: 72,
        wrongCount: 6,
        totalCount: 21,
        level: 'MILD',
        lastPracticeTime: new Date().toISOString()
      },
      {
        id: 4,
        knowledgePoint: '阅读理解',
        accuracy: 85,
        wrongCount: 3,
        totalCount: 20,
        level: 'GOOD',
        lastPracticeTime: new Date().toISOString()
      },
      {
        id: 5,
        knowledgePoint: '语法结构',
        accuracy: 55,
        wrongCount: 9,
        totalCount: 20,
        level: 'MODERATE',
        lastPracticeTime: new Date().toISOString()
      }
    ]

    await nextTick()
    initChart()
    initTrendChart()
  } catch (error) {
    ElMessage.error('加载薄弱点列表失败')
  } finally {
    loading.value = false
  }
}

const practiceWeakness = async (weakness) => {
  try {
    // const response = await getTargetedPractice(weakness.id)
    ElMessage.success(`开始针对"${weakness.knowledgePoint}"的练习`)
    router.push({ path: '/student/practice', query: { knowledgePoint: weakness.knowledgePoint } })
  } catch (error) {
    ElMessage.error('获取练习题目失败')
  }
}

const viewDetail = async (weakness) => {
  try {
    // const response = await getWeaknessDetail(weakness.id)
    // Object.assign(currentWeakness, response.data)

    // 模拟数据
    Object.assign(currentWeakness, {
      ...weakness,
      wrongQuestions: [
        {
          id: 1,
          question: 'What _____ you doing yesterday?',
          yourAnswer: 'is',
          correctAnswer: 'were'
        },
        {
          id: 2,
          question: 'She _____ to school every day.',
          yourAnswer: 'go',
          correctAnswer: 'goes'
        }
      ]
    })

    detailDialogVisible.value = true
  } catch (error) {
    ElMessage.error('获取详情失败')
  }
}

const reviewQuestion = (question) => {
  ElMessage.info(`复习题目：${question.question}`)
}

const goToLearningSuggestion = () => {
  router.push('/student/learning-suggestion')
}

const initChart = () => {
  if (!chartRef.value) return

  chart = echarts.init(chartRef.value)

  const option = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: {
        formatter: '{value}%'
      }
    },
    yAxis: {
      type: 'category',
      data: weaknessList.value.map(item => item.knowledgePoint)
    },
    series: [
      {
        name: '正确率',
        type: 'bar',
        data: weaknessList.value.map(item => ({
          value: item.accuracy,
          itemStyle: {
            color: getAccuracyColor(item.accuracy)
          }
        })),
        label: {
          show: true,
          position: 'right',
          formatter: '{c}%'
        }
      }
    ]
  }

  chart.setOption(option)
}

const initTrendChart = () => {
  if (!trendChartRef.value) return

  trendChart = echarts.init(trendChartRef.value)

  // 模拟趋势数据
  const dates = []
  const accuracyData = []
  for (let i = 6; i >= 0; i--) {
    const date = new Date()
    date.setDate(date.getDate() - i)
    dates.push(date.toLocaleDateString())
    accuracyData.push(Math.round(45 + i * 5 + Math.random() * 5))
  }

  const option = {
    tooltip: {
      trigger: 'axis'
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        rotate: 30
      }
    },
    yAxis: {
      type: 'value',
      max: 100,
      axisLabel: {
        formatter: '{value}%'
      }
    },
    series: [
      {
        name: '平均正确率',
        type: 'line',
        data: accuracyData,
        smooth: true,
        itemStyle: {
          color: '#409EFF'
        },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(64, 158, 255, 0.3)' },
            { offset: 1, color: 'rgba(64, 158, 255, 0.05)' }
          ])
        },
        label: {
          show: true,
          formatter: '{c}%'
        }
      }
    ]
  }

  trendChart.setOption(option)
}

// 工具方法
const getWeaknessType = (level) => {
  const types = {
    'SEVERE': 'danger',
    'MODERATE': 'warning',
    'MILD': '',
    'GOOD': 'success'
  }
  return types[level] || ''
}

const getWeaknessText = (level) => {
  const texts = {
    'SEVERE': '严重',
    'MODERATE': '中度',
    'MILD': '轻度',
    'GOOD': '良好'
  }
  return texts[level] || level
}

const getAccuracyColor = (accuracy) => {
  if (accuracy >= 80) return '#67c23a'
  if (accuracy >= 60) return '#e6a23c'
  return '#f56c6c'
}

const formatDate = (dateString) => {
  if (!dateString) return '-'
  return new Date(dateString).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadWeaknessList()
})
</script>

<style scoped>
.weakness-analysis-page {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.mt-20 {
  margin-top: 20px;
}

.chart-container {
  width: 100%;
  height: 300px;
}

.suggestions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.suggestion-item {
  margin-bottom: 0;
}

.suggestion-item p {
  margin: 8px 0 0;
  line-height: 1.6;
}

.suggestion-actions {
  margin-top: 15px;
  text-align: center;
}

.weakness-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.wrong-questions h4 {
  margin: 0 0 15px;
  color: #303133;
}
</style>
