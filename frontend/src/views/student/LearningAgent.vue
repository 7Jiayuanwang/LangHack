<template>
  <div class="agent-page">
    <header class="agent-hero">
      <button class="back-button" type="button" @click="router.back()">← 返回</button>
      <div class="hero-content">
        <AgentMascot :size="96" />
        <div class="hero-copy">
          <span class="eyebrow">LANGHACK LEARNING COMPANION</span>
          <h1>你好，我是小芽</h1>
          <p>{{ context.greeting || '我会陪你把英语慢慢变成获取信息和探索兴趣的工具。' }}</p>
        </div>
      </div>
      <button
        v-if="context.onboardingCompleted && !editing"
        class="profile-button"
        type="button"
        @click="startEditing"
      >调整学习档案</button>
    </header>

    <main class="agent-main">
      <section v-if="loading" class="state-card">
        <el-skeleton :rows="6" animated />
      </section>

      <section v-else-if="loadError" class="state-card error-state">
        <h2>暂时没有读取到学习伙伴数据</h2>
        <p>{{ loadError }}</p>
        <el-button type="primary" @click="loadContext">重新加载</el-button>
      </section>

      <section v-else-if="!context.onboardingCompleted || editing" class="onboarding-card">
        <div class="section-heading">
          <span class="step-mark">01</span>
          <div>
            <h2>先让我认识你</h2>
            <p>这些信息可以随时修改。目标可以同时选择多个。</p>
          </div>
        </div>

        <div class="form-section">
          <label class="field-label">你学习英语主要为了什么？</label>
          <div class="choice-grid goal-grid">
            <button
              v-for="option in goalOptions"
              :key="option.value"
              type="button"
              class="choice-card"
              :class="{ selected: isGoalSelected(option.value) }"
              @click="toggleGoal(option)"
            >
              <span class="choice-icon">{{ option.icon }}</span>
              <span>{{ option.label }}</span>
              <small>{{ option.description }}</small>
            </button>
          </div>

          <div v-if="form.goals.length" class="selected-goals">
            <div v-for="goal in form.goals" :key="goal.goalType" class="selected-goal-row">
              <span>{{ goal.title }}</span>
              <label>重要程度 <el-rate v-model="goal.priority" :max="5" /></label>
              <el-date-picker
                v-model="goal.targetDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="目标日期（可选）"
                :clearable="true"
              />
            </div>
          </div>
        </div>

        <div class="two-column-form">
          <div class="form-section compact">
            <label class="field-label">你觉得自己目前是什么水平？</label>
            <el-select v-model="form.currentLevel" placeholder="请选择当前水平" size="large">
              <el-option v-for="level in levelOptions" :key="level.value" :label="level.label" :value="level.value" />
            </el-select>
          </div>

          <div class="form-section compact">
            <label class="field-label">每周大约能投入多少时间？</label>
            <div class="minutes-input">
              <el-slider v-model="form.weeklyMinutes" :min="30" :max="900" :step="30" />
              <strong>{{ form.weeklyMinutes }} 分钟</strong>
            </div>
          </div>
        </div>

        <div class="form-section">
          <label class="field-label">你平时对什么感兴趣？</label>
          <div class="chip-list">
            <button
              v-for="interest in interestOptions"
              :key="interest"
              type="button"
              class="select-chip"
              :class="{ selected: form.interests.includes(interest) }"
              @click="toggleInterest(interest)"
            >{{ interest }}</button>
          </div>
          <div class="custom-interest">
            <el-input v-model="customInterest" maxlength="40" placeholder="还可以添加自己的兴趣" @keyup.enter="addCustomInterest" />
            <el-button @click="addCustomInterest">添加</el-button>
          </div>
        </div>

        <div class="form-section">
          <label class="field-label">你更喜欢哪些学习方式？</label>
          <el-checkbox-group v-model="form.preferredFormats" class="format-list">
            <el-checkbox v-for="format in formatOptions" :key="format.value" :label="format.value" border>
              {{ format.label }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <div class="two-column-form">
          <div class="form-section compact">
            <label class="field-label">希望我怎样鼓励你？</label>
            <el-radio-group v-model="form.encouragementStyle">
              <el-radio-button label="GENTLE">温和陪伴</el-radio-button>
              <el-radio-button label="DIRECT">直接提醒</el-radio-button>
              <el-radio-button label="CHALLENGE">挑战模式</el-radio-button>
            </el-radio-group>
          </div>

          <div class="form-section compact memory-consent">
            <el-checkbox v-model="form.memoryConsent">
              允许平台从后续行为和会话中提取学习习惯候选记忆
            </el-checkbox>
            <p>候选记忆需要经过规则校验，并可以由你查看和删除。</p>
          </div>
        </div>

        <div class="form-actions">
          <el-button v-if="context.onboardingCompleted" size="large" @click="cancelEditing">取消</el-button>
          <el-button type="primary" size="large" :loading="saving" @click="submitOnboarding">
            保存并开始学习
          </el-button>
        </div>
      </section>

      <template v-else>
        <section class="companion-panel">
          <div class="panel-copy">
            <span class="panel-kicker">现在可以从这里开始</span>
            <h2>今天想让英语帮你做什么？</h2>
            <p>你可以问一个具体问题，也可以让我帮你拆解目标和安排学习。</p>
            <div class="quick-question-list">
              <button
                v-for="question in context.suggestedQuestions"
                :key="question"
                type="button"
                @click="openChat(question)"
              >{{ question }} <span>→</span></button>
            </div>
          </div>
          <button class="chat-cta" type="button" @click="openChat()">
            <span>和小芽聊聊</span>
            <small>进入学习伙伴对话</small>
          </button>
        </section>

        <div class="context-grid">
          <section class="context-card">
            <div class="card-heading">
              <h3>我的学习目标</h3>
              <span>{{ context.goals.length }} 项</span>
            </div>
            <div class="goal-list">
              <div v-for="goal in context.goals" :key="goal.id" class="goal-item">
                <span class="goal-dot"></span>
                <div>
                  <strong>{{ goal.title }}</strong>
                  <small>重要程度 {{ goal.priority }}/5<span v-if="goal.targetDate"> · {{ goal.targetDate }}</span></small>
                </div>
              </div>
            </div>
            <div v-if="context.interests.length" class="profile-chips">
              <span v-for="interest in context.interests" :key="interest">{{ interest }}</span>
            </div>
          </section>

          <section class="context-card">
            <div class="card-heading">
              <h3>今天的节奏</h3>
              <span>{{ context.weeklyMinutes }} 分钟/周</span>
            </div>
            <div class="schedule-summary">
              <strong>{{ context.schedule.todayCourseCount || 0 }}</strong>
              <span>门课程安排在今天</span>
            </div>
            <p v-if="context.schedule.todayCourseNames?.length">
              {{ context.schedule.todayCourseNames.join('、') }}
            </p>
            <p v-else>今天课表较轻，可以安排一次完整的英语输入。</p>
          </section>
        </div>

        <section class="content-recommendation-card">
          <div class="card-heading">
            <div>
              <span class="content-kicker">ENGLISH FOR YOUR WORLD</span>
              <h3>为你找到的英文内容</h3>
              <p>根据兴趣、专业和课表在本地匹配；难度将在后续阅读环节调整。</p>
            </div>
            <span v-if="contentRecommendations.length">{{ contentRecommendations.length }} 篇</span>
          </div>

          <div v-if="recommendationsLoading" class="recommendation-loading">
            <el-skeleton :rows="3" animated />
          </div>
          <div v-else-if="contentRecommendations.length" class="recommendation-grid">
            <article
              v-for="item in contentRecommendations"
              :key="item.id"
              class="recommendation-item"
            >
              <div class="recommendation-meta">
                <span>{{ item.sourceName }}</span>
                <span>{{ item.topic }}</span>
              </div>
              <h4>{{ item.title }}</h4>
              <p class="recommendation-summary">{{ item.summary || '打开原文继续探索这个主题。' }}</p>
              <p class="recommendation-reason">{{ item.reason }}</p>
              <div class="recommendation-actions">
                <button type="button" @click="openRecommendedContent(item)">
                  {{ item.external ? '打开英文原文' : '在平台阅读' }} →
                </button>
                <button type="button" class="quiet" @click="askAboutContent(item)">问问小芽</button>
              </div>
            </article>
          </div>
          <div v-else class="recommendation-empty">
            共享内容池正在准备中。你仍然可以先告诉小芽想探索的主题。
          </div>

          <div class="content-privacy-note">
            外部内容源只接收平台预设的通用主题，不会收到你的姓名、兴趣档案、专业或课表。
          </div>
        </section>

        <section class="memory-card">
          <div class="card-heading">
            <div>
              <h3>小芽记住的内容</h3>
              <p>这里的内容由你掌控，可以补充或删除。</p>
            </div>
            <span>{{ context.memories.length }} 条</span>
          </div>
          <div class="memory-list">
            <div v-for="memory in context.memories" :key="memory.id" class="memory-item">
              <span class="memory-type">{{ memoryTypeLabel(memory.memoryType) }}</span>
              <p>{{ memory.content }}</p>
              <button type="button" title="删除这条记忆" @click="removeMemory(memory.id)">删除</button>
            </div>
          </div>
          <div class="add-memory-row">
            <el-input v-model="newMemory" maxlength="500" placeholder="例如：我更喜欢早上做阅读训练" @keyup.enter="createMemory" />
            <el-button type="primary" :loading="memorySaving" @click="createMemory">记住这件事</el-button>
          </div>
          <div class="privacy-note">
            学习档案目前只用于平台内展示和确定性建议，不会默认随对话发送给外部 AI 服务。
          </div>
        </section>
      </template>
    </main>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import AgentMascot from '@/components/AgentMascot.vue'
import {
  addLearningAgentMemory,
  deleteLearningAgentMemory,
  getLearningAgentContentRecommendations,
  getLearningAgentContext,
  saveLearningAgentOnboarding
} from '@/api/learningAgent'

const router = useRouter()
const loading = ref(true)
const saving = ref(false)
const memorySaving = ref(false)
const recommendationsLoading = ref(false)
const editing = ref(false)
const loadError = ref('')
const customInterest = ref('')
const newMemory = ref('')
const contentRecommendations = ref([])

const context = reactive({
  onboardingCompleted: false,
  goals: [],
  interests: [],
  memories: [],
  preferredFormats: [],
  schedule: { totalCourses: 0, todayCourseCount: 0, todayCourseNames: [] },
  suggestedQuestions: [],
  greeting: ''
})

const form = reactive({
  goals: [],
  interests: [],
  currentLevel: 'INTERMEDIATE',
  weeklyMinutes: 210,
  preferredFormats: ['READING', 'VOCABULARY'],
  encouragementStyle: 'GENTLE',
  memoryConsent: false
})

const goalOptions = [
  { value: 'CET4', label: '大学英语四级', description: '词汇、阅读、听力与写作', icon: '4' },
  { value: 'CET6', label: '大学英语六级', description: '更高阶的综合能力训练', icon: '6' },
  { value: 'POSTGRADUATE', label: '考研英语', description: '长难句、阅读与写作', icon: '研' },
  { value: 'ACADEMIC_READING', label: '英文论文阅读', description: '服务专业学习与研究', icon: '研' },
  { value: 'COURSE', label: '大学英语课程', description: '结合课表和课堂任务', icon: '课' },
  { value: 'GENERAL', label: '兴趣与信息获取', description: '用英语探索更大的世界', icon: '∞' }
]

const levelOptions = [
  { value: 'BEGINNER', label: '刚起步，需要较多中文辅助' },
  { value: 'ELEMENTARY', label: '能理解简单句和常用词' },
  { value: 'INTERMEDIATE', label: '能阅读一般文章，但仍会卡顿' },
  { value: 'UPPER_INTERMEDIATE', label: '能较流畅地阅读和交流' },
  { value: 'ADVANCED', label: '希望提升学术和专业表达' }
]

const interestOptions = ['科技', '专业前沿', '影视', '音乐', '体育', '游戏', '旅行', '商业', '心理学', '文学']
const formatOptions = [
  { value: 'READING', label: '阅读有趣内容' },
  { value: 'VOCABULARY', label: '词汇复习' },
  { value: 'QUIZ', label: '做题与错题' },
  { value: 'WRITING', label: '作文修改' },
  { value: 'CONVERSATION', label: '对话练习' },
  { value: 'VIDEO', label: '视频学习' }
]

const applyContext = (data = {}) => {
  Object.assign(context, {
    onboardingCompleted: !!data.onboardingCompleted,
    currentLevel: data.currentLevel || null,
    weeklyMinutes: data.weeklyMinutes || 210,
    preferredFormats: Array.isArray(data.preferredFormats) ? data.preferredFormats : [],
    encouragementStyle: data.encouragementStyle || 'GENTLE',
    memoryConsent: !!data.memoryConsent,
    goals: Array.isArray(data.goals) ? data.goals : [],
    interests: Array.isArray(data.interests) ? data.interests : [],
    memories: Array.isArray(data.memories) ? data.memories : [],
    schedule: data.schedule || { totalCourses: 0, todayCourseCount: 0, todayCourseNames: [] },
    suggestedQuestions: Array.isArray(data.suggestedQuestions) ? data.suggestedQuestions : [],
    greeting: data.greeting || ''
  })
}

const hydrateForm = () => {
  form.goals = context.goals.map(goal => ({
    goalType: goal.goalType,
    title: goal.title,
    priority: goal.priority || 3,
    targetDate: goal.targetDate || null
  }))
  form.interests = [...context.interests]
  form.currentLevel = context.currentLevel || 'INTERMEDIATE'
  form.weeklyMinutes = context.weeklyMinutes || 210
  form.preferredFormats = context.preferredFormats.length ? [...context.preferredFormats] : ['READING', 'VOCABULARY']
  form.encouragementStyle = context.encouragementStyle || 'GENTLE'
  form.memoryConsent = !!context.memoryConsent
}

const loadContext = async () => {
  loading.value = true
  loadError.value = ''
  try {
    const response = await getLearningAgentContext()
    if (response?.code !== 200) throw new Error(response?.message || '读取失败')
    applyContext(response.data)
    hydrateForm()
    if (context.onboardingCompleted) loadRecommendations()
  } catch (error) {
    loadError.value = error?.message || '请确认智能体数据迁移已经执行。'
  } finally {
    loading.value = false
  }
}

const loadRecommendations = async () => {
  recommendationsLoading.value = true
  try {
    const response = await getLearningAgentContentRecommendations(6)
    contentRecommendations.value = response?.code === 200 && Array.isArray(response.data)
      ? response.data
      : []
  } catch {
    contentRecommendations.value = []
  } finally {
    recommendationsLoading.value = false
  }
}

const isGoalSelected = (type) => form.goals.some(goal => goal.goalType === type)

const toggleGoal = (option) => {
  const index = form.goals.findIndex(goal => goal.goalType === option.value)
  if (index >= 0) {
    form.goals.splice(index, 1)
  } else if (form.goals.length < 5) {
    form.goals.push({ goalType: option.value, title: option.label, priority: 3, targetDate: null })
  } else {
    ElMessage.warning('学习目标最多选择5个')
  }
}

const toggleInterest = (interest) => {
  const index = form.interests.indexOf(interest)
  if (index >= 0) form.interests.splice(index, 1)
  else if (form.interests.length < 12) form.interests.push(interest)
}

const addCustomInterest = () => {
  const value = customInterest.value.trim()
  if (!value) return
  if (form.interests.length >= 12) return ElMessage.warning('兴趣最多填写12项')
  if (!form.interests.includes(value)) form.interests.push(value)
  customInterest.value = ''
}

const submitOnboarding = async () => {
  if (!form.goals.length) return ElMessage.warning('请至少选择一个学习目标')
  if (!form.preferredFormats.length) return ElMessage.warning('请至少选择一种学习方式')
  saving.value = true
  try {
    const response = await saveLearningAgentOnboarding({
      goals: form.goals,
      interests: form.interests,
      currentLevel: form.currentLevel,
      weeklyMinutes: form.weeklyMinutes,
      preferredFormats: form.preferredFormats,
      encouragementStyle: form.encouragementStyle,
      memoryConsent: form.memoryConsent
    })
    if (response?.code !== 200) throw new Error(response?.message || '保存失败')
    applyContext(response.data)
    hydrateForm()
    editing.value = false
    loadRecommendations()
    ElMessage.success('学习档案已保存，小芽会从这里开始陪你学习')
  } catch (error) {
    ElMessage.error(error?.message || '保存失败，请稍后再试')
  } finally {
    saving.value = false
  }
}

const startEditing = () => {
  hydrateForm()
  editing.value = true
}

const cancelEditing = () => {
  hydrateForm()
  editing.value = false
}

const openChat = (question = '') => {
  router.push({ path: '/student/ai-qa', query: { mode: 'agent', ...(question ? { question } : {}) } })
}

const openRecommendedContent = (item) => {
  if (!item?.url) return
  if (item.external) {
    window.open(item.url, '_blank', 'noopener,noreferrer')
  } else {
    router.push(item.url)
  }
}

const askAboutContent = (item) => {
  if (!item?.title) return
  openChat(`我准备阅读“${item.title}”。请不要假装已经看过原文，先根据标题给我一个英文阅读策略。`)
}

const createMemory = async () => {
  const content = newMemory.value.trim()
  if (!content || memorySaving.value) return
  memorySaving.value = true
  try {
    const response = await addLearningAgentMemory({ memoryType: 'PREFERENCE', content, sensitive: false })
    if (response?.code !== 200) throw new Error(response?.message || '保存失败')
    applyContext(response.data)
    newMemory.value = ''
  } catch (error) {
    ElMessage.error(error?.message || '保存记忆失败')
  } finally {
    memorySaving.value = false
  }
}

const removeMemory = async (memoryId) => {
  try {
    await ElMessageBox.confirm('删除后，小芽将不再使用这条平台内记忆。', '删除记忆', {
      confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning'
    })
    const response = await deleteLearningAgentMemory(memoryId)
    if (response?.code !== 200) throw new Error(response?.message || '删除失败')
    applyContext(response.data)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error(error?.message || '删除记忆失败')
  }
}

const memoryTypeLabel = (type) => ({
  PROFILE: '我的情况', PREFERENCE: '学习偏好', HABIT: '学习习惯', GOAL: '学习目标', DIFFICULTY: '薄弱点'
}[type] || '学习记忆')

loadContext()
</script>

<style scoped>
.agent-page {
  min-height: 100%;
  background: linear-gradient(180deg, #ecfbf5 0, #f6faf8 360px, var(--bg-secondary) 100%);
  color: var(--text-primary);
  padding-bottom: 36px;
}

.agent-hero {
  position: relative;
  max-width: 1080px;
  margin: 0 auto;
  padding: 34px 24px 26px;
}

.back-button, .profile-button {
  border: 1px solid rgba(31, 122, 92, 0.18);
  background: rgba(255, 255, 255, 0.72);
  color: #276b55;
  border-radius: 999px;
  padding: 8px 14px;
  cursor: pointer;
}

.profile-button { position: absolute; right: 24px; bottom: 32px; }
.hero-content { display: flex; align-items: center; gap: 24px; margin-top: 24px; }
.hero-copy { min-width: 0; }
.eyebrow, .panel-kicker { color: #268564; font-size: 12px; font-weight: 800; letter-spacing: 0.13em; }
.hero-copy h1 { margin: 5px 0 8px; font-size: clamp(30px, 5vw, 46px); letter-spacing: -0.04em; }
.hero-copy p { max-width: 680px; margin: 0; color: #4d6b61; font-size: 16px; line-height: 1.7; }

.agent-main { max-width: 1080px; margin: 0 auto; padding: 0 24px; }
.state-card, .onboarding-card, .context-card, .memory-card, .content-recommendation-card {
  background: rgba(255, 255, 255, 0.92);
  border: 1px solid rgba(102, 168, 143, 0.16);
  border-radius: 24px;
  box-shadow: 0 16px 44px rgba(30, 93, 70, 0.07);
}
.state-card { padding: 28px; }
.error-state { text-align: center; }
.error-state h2 { margin: 0 0 8px; }
.error-state p { color: var(--text-secondary); }
.onboarding-card { padding: 30px; }
.section-heading { display: flex; align-items: flex-start; gap: 14px; margin-bottom: 28px; }
.step-mark { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 14px; background: #dff7ec; color: #187453; font-weight: 800; }
.section-heading h2 { margin: 0 0 5px; font-size: 25px; }
.section-heading p, .card-heading p { margin: 0; color: var(--text-secondary); }
.form-section { margin-top: 26px; }
.form-section.compact { margin-top: 0; }
.field-label { display: block; margin-bottom: 12px; font-size: 15px; font-weight: 700; }
.choice-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.choice-card { min-height: 116px; padding: 16px; border: 1px solid #dce9e3; border-radius: 17px; background: #fbfefc; text-align: left; color: #274c3f; cursor: pointer; transition: .2s ease; }
.choice-card:hover, .choice-card.selected { transform: translateY(-2px); border-color: #41ad83; box-shadow: 0 9px 22px rgba(45, 151, 112, .12); }
.choice-card.selected { background: #eaf9f2; }
.choice-icon { display: grid; place-items: center; width: 30px; height: 30px; margin-bottom: 10px; border-radius: 10px; background: #dff7ec; color: #187453; font-weight: 800; }
.choice-card > span:nth-child(2) { display: block; font-weight: 750; }
.choice-card small { display: block; margin-top: 5px; color: #71877f; line-height: 1.4; }
.selected-goals { display: grid; gap: 10px; margin-top: 14px; }
.selected-goal-row { display: grid; grid-template-columns: minmax(160px, 1fr) auto minmax(180px, 220px); align-items: center; gap: 14px; padding: 12px 14px; border-radius: 14px; background: #f7fbf9; }
.selected-goal-row > span { font-weight: 650; }
.selected-goal-row label { display: flex; align-items: center; gap: 8px; color: var(--text-secondary); font-size: 13px; }
.two-column-form { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 24px; margin-top: 26px; }
.minutes-input { display: flex; align-items: center; gap: 18px; }
.minutes-input :deep(.el-slider) { flex: 1; }
.minutes-input strong { min-width: 76px; color: #187453; }
.chip-list { display: flex; flex-wrap: wrap; gap: 9px; }
.select-chip { border: 1px solid #d8e6df; border-radius: 999px; padding: 8px 14px; background: white; color: #526b62; cursor: pointer; }
.select-chip.selected { border-color: #41ad83; background: #e8f8f0; color: #187453; font-weight: 700; }
.custom-interest { display: flex; gap: 10px; max-width: 480px; margin-top: 12px; }
.format-list { display: flex; flex-wrap: wrap; gap: 10px; }
.format-list :deep(.el-checkbox) { margin-right: 0; }
.memory-consent { padding: 16px; border-radius: 16px; background: #f7faf9; }
.memory-consent p { margin: 7px 0 0 24px; color: #7b8f87; font-size: 12px; line-height: 1.5; }
.form-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 30px; }

.companion-panel { display: flex; justify-content: space-between; gap: 30px; padding: 30px; border-radius: 26px; background: linear-gradient(135deg, #176e51 0%, #269970 62%, #50bd8f 100%); color: white; box-shadow: 0 18px 45px rgba(21, 112, 79, .2); }
.panel-copy h2 { margin: 6px 0 8px; font-size: 28px; }
.panel-copy > p { margin: 0; color: rgba(255,255,255,.8); }
.panel-kicker { color: #c9ffe9; }
.quick-question-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 20px; }
.quick-question-list button { border: 1px solid rgba(255,255,255,.25); border-radius: 999px; padding: 9px 13px; background: rgba(255,255,255,.1); color: white; cursor: pointer; }
.quick-question-list button:hover { background: rgba(255,255,255,.2); }
.chat-cta { align-self: center; min-width: 178px; padding: 20px; border: none; border-radius: 20px; background: #fff; color: #176e51; box-shadow: 0 12px 26px rgba(17, 72, 53, .18); cursor: pointer; text-align: left; }
.chat-cta span, .chat-cta small { display: block; }
.chat-cta span { font-size: 18px; font-weight: 800; }
.chat-cta small { margin-top: 5px; color: #6b827a; }
.context-grid { display: grid; grid-template-columns: 1.35fr 1fr; gap: 18px; margin-top: 18px; }
.context-card, .memory-card { padding: 22px; }
.card-heading { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; }
.card-heading h3 { margin: 0 0 4px; font-size: 18px; }
.card-heading > span { border-radius: 999px; padding: 5px 10px; background: #edf8f3; color: #27795c; font-size: 12px; font-weight: 700; }
.goal-list { display: grid; gap: 13px; margin-top: 18px; }
.goal-item { display: flex; align-items: flex-start; gap: 11px; }
.goal-dot { width: 9px; height: 9px; margin-top: 6px; border-radius: 50%; background: #39a97e; box-shadow: 0 0 0 5px #e5f7ef; }
.goal-item strong, .goal-item small { display: block; }
.goal-item small { margin-top: 4px; color: #7a8d85; }
.profile-chips { display: flex; flex-wrap: wrap; gap: 7px; margin-top: 18px; }
.profile-chips span { padding: 5px 9px; border-radius: 999px; background: #f3f6f5; color: #5d736a; font-size: 12px; }
.schedule-summary { display: flex; align-items: baseline; gap: 9px; margin-top: 20px; }
.schedule-summary strong { color: #208262; font-size: 44px; line-height: 1; }
.schedule-summary span, .context-card > p { color: #71847d; }
.memory-card { margin-top: 18px; }
.content-recommendation-card { margin-top: 18px; padding: 22px; }
.content-kicker { display: inline-block; margin-bottom: 5px; color: #268564; font-size: 11px; font-weight: 800; letter-spacing: .12em; }
.recommendation-loading { margin-top: 18px; }
.recommendation-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 13px; margin-top: 18px; }
.recommendation-item { display: flex; flex-direction: column; min-width: 0; padding: 17px; border: 1px solid #e1ebe6; border-radius: 17px; background: linear-gradient(145deg, #fbfefc, #f5faf7); }
.recommendation-meta { display: flex; flex-wrap: wrap; gap: 6px; }
.recommendation-meta span { padding: 4px 8px; border-radius: 999px; background: #e5f6ee; color: #27795c; font-size: 10px; font-weight: 750; }
.recommendation-item h4 { margin: 11px 0 7px; color: #244d3e; font-size: 16px; line-height: 1.45; }
.recommendation-summary { display: -webkit-box; overflow: hidden; margin: 0; color: #667c73; font-size: 13px; line-height: 1.65; -webkit-box-orient: vertical; -webkit-line-clamp: 3; }
.recommendation-reason { margin: 12px 0 0; color: #25805f; font-size: 12px; font-weight: 650; }
.recommendation-actions { display: flex; align-items: center; gap: 12px; margin-top: auto; padding-top: 15px; }
.recommendation-actions button { border: none; padding: 0; background: transparent; color: #187453; font-weight: 750; cursor: pointer; }
.recommendation-actions button.quiet { color: #75877f; font-weight: 600; }
.recommendation-empty { margin-top: 18px; padding: 20px; border-radius: 15px; background: #f7faf8; color: #71847d; text-align: center; }
.content-privacy-note { margin-top: 14px; color: #82928c; font-size: 11px; line-height: 1.55; }
.memory-list { display: grid; gap: 9px; margin-top: 18px; }
.memory-item { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 12px; padding: 11px 13px; border-radius: 13px; background: #f8fbf9; }
.memory-item p { margin: 0; color: #41594f; }
.memory-item button { border: none; background: transparent; color: #a45a5a; cursor: pointer; }
.memory-type { padding: 4px 8px; border-radius: 8px; background: #e2f5ec; color: #27795c; font-size: 11px; font-weight: 750; }
.add-memory-row { display: flex; gap: 10px; margin-top: 16px; }
.privacy-note { margin-top: 14px; padding: 10px 12px; border-radius: 12px; background: #f2f5f4; color: #6f817a; font-size: 12px; line-height: 1.5; }

@media (max-width: 760px) {
  .agent-hero, .agent-main { padding-left: 16px; padding-right: 16px; }
  .hero-content { align-items: flex-start; gap: 14px; }
  .profile-button { position: static; margin-top: 18px; }
  .choice-grid, .two-column-form, .context-grid, .recommendation-grid { grid-template-columns: 1fr; }
  .selected-goal-row { grid-template-columns: 1fr; }
  .companion-panel { flex-direction: column; padding: 23px; }
  .chat-cta { align-self: stretch; }
  .onboarding-card { padding: 20px; }
  .memory-item { grid-template-columns: auto 1fr; }
  .memory-item button { grid-column: 2; justify-self: start; }
  .add-memory-row { flex-direction: column; }
}
</style>
