<template>
  <div class="word-recite-container">
    <!-- 词库选择界面 -->
    <div class="vocabulary-selection" v-if="!selectedVocabulary">
      <div class="page-header">
        <h2 class="page-title">选择词库</h2>
        <p class="page-subtitle">请选择要背诵的单词库</p>
      </div>

      <div class="vocabularies-grid">
        <div 
          v-for="vocab in vocabularies" 
          :key="vocab.id"
          class="vocabulary-card"
          @click="selectVocabulary(vocab)"
        >
          <h3 class="vocab-name">{{ vocab.name }}</h3>
          <p class="vocab-desc">{{ vocab.description }}</p>
          <p class="vocab-count">{{ vocab.wordCount }} 个单词</p>
        </div>
      </div>
    </div>

    <!-- 每日复习数量选择界面 -->
    <div class="daily-count-selection" v-else-if="!dailyCountSelected">
      <div class="page-header">
        <h2 class="page-title">设置每日复习量</h2>
        <p class="page-subtitle">选择每天要复习的单词数量</p>
      </div>

      <div class="progress-info">
        <span>剩余单词：{{ remainingWords }} 个</span>
      </div>

      <div class="count-options">
        <div 
          v-for="count in dailyCountOptions" 
          :key="count"
          class="count-card"
          @click="selectDailyCount(count)"
        >
          <span class="count-number">{{ count }}</span>
          <span class="count-label">个/天</span>
          <span class="count-days">{{ calculateDays(count) }}天完成</span>
        </div>
      </div>

      <!-- 底部返回按钮 -->
      <div class="daily-footer">
        <button @click="backToSelection" class="text-btn">返回</button>
      </div>
    </div>

    <!-- 背诵界面 -->
    <div class="recitation-view" v-else>
      <!-- 顶部概览 -->
      <div class="page-header">
        <h2 class="page-title">{{ currentVocabularyName }}</h2>
        <p class="page-subtitle">每日复习 {{ dailyCount }} 个单词</p>
      </div>

      <!-- 统计卡片 -->
      <div class="stats-cards">
        <div class="stat-card">
          <span class="stat-label">待复习</span>
          <span class="stat-value">{{ pendingWords.length }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">已掌握</span>
          <span class="stat-value">{{ masteredWords }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">今日复习</span>
          <span class="stat-value">{{ todayReviewed }}</span>
        </div>
      </div>

      <!-- 单词卡片 -->
      <div class="word-card" v-if="currentWord">
        <div class="word-content">
          <div class="word-header">
            <h3 class="word-text">{{ currentWord.word }}</h3>
            <button @click="speakWord" class="speak-btn" title="发音">
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5"/>
                <path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07"/>
              </svg>
            </button>
          </div>
          <div class="word-meaning" v-if="showMeaning">
            <p>{{ currentWord.meaning }}</p>
            <!-- 单词详细信息 -->
            <div class="word-details" v-if="wordDetails">
              <div class="detail-section" v-if="wordDetails.phonetic">
                <span class="detail-label">音标：</span>
                <span class="detail-value">{{ wordDetails.phonetic }}</span>
              </div>
              <div class="detail-section" v-if="wordDetails.origin">
                <span class="detail-label">词源：</span>
                <span class="detail-value">{{ wordDetails.origin }}</span>
              </div>
              <div class="meanings-section" v-for="(meaning, index) in wordDetails.meanings" :key="index">
                <div class="part-of-speech">{{ meaning.partOfSpeech }}</div>
                <div class="definitions">
                  <div v-for="(def, idx) in meaning.definitions.slice(0, 2)" :key="idx" class="definition-item">
                    <p class="definition">{{ def.definition }}</p>
                    <p class="example" v-if="def.example">例句：{{ def.example }}</p>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 直接选择题 -->
        <div class="action-buttons">
          <div class="quiz-error shake" v-if="quizError">
            答错了，请重新选择！
          </div>
          <div class="quiz-options">
            <button
              v-for="(option, index) in quizOptions"
              :key="index"
              @click="selectQuizOption(option)"
              class="btn quiz-btn"
            >
              {{ option }}
            </button>
          </div>
        </div>
      </div>

      <!-- 无单词提示 -->
      <div class="empty-state" v-else>
        <p>🎉 恭喜！今日复习任务已完成</p>
        <p class="empty-subtitle">今日复习：{{ todayReviewed }} 个单词</p>
        <div class="empty-actions">
          <button @click="loadMoreWords" class="btn btn-primary">再来一组</button>
          <button @click="startDeepRecite" class="btn btn-warning">深度背诵</button>
          <button @click="takeBreak" class="btn btn-info">休息一会</button>
          <button @click="backToSettings" class="btn btn-secondary">返回设置</button>
        </div>
      </div>
    </div>

    <!-- 深度背诵界面 -->
    <div class="deep-recite-view" v-if="showDeepRecite">
      <div class="page-header">
        <h2 class="page-title">深度背诵</h2>
        <p class="page-subtitle">根据释义输入正确的单词</p>
      </div>

      <!-- 深度背诵卡片 -->
      <div class="word-card" v-if="currentDeepWord">
        <div class="word-content">
          <div class="word-meaning">
            <p>{{ currentDeepWord.meaning }}</p>
          </div>
          <input 
            v-model="userInput" 
            @keyup.enter="checkDeepAnswer" 
            placeholder="请输入单词"
            class="word-input"
            :class="{ 'correct': answerStatus === 'correct', 'wrong': answerStatus === 'wrong' }"
            ref="inputRef"
          />
          <div class="answer-feedback" v-if="answerStatus">
            <span v-if="answerStatus === 'correct'" class="correct-text">✓ 正确！</span>
            <span v-if="answerStatus === 'wrong'" class="wrong-text">✗ 错误！</span>
          </div>
          <div class="deep-options" v-if="showDeepOptions">
            <p class="options-title">请选择正确答案：</p>
            <div class="options-grid">
              <button 
                v-for="(option, index) in deepOptions" 
                :key="index"
                @click="selectDeepOption(option)"
                class="btn option-btn"
              >
                {{ option }}
              </button>
            </div>
            <button @click="retryDeepInput" class="btn btn-secondary retry-btn">再来一遍</button>
          </div>
          <button @click="checkDeepAnswer" class="btn btn-primary submit-btn" v-if="!showDeepOptions">提交</button>
        </div>
      </div>

      <!-- 深度背诵完成 -->
      <div class="empty-state" v-else>
        <p>🎉 深度背诵完成！</p>
        <p class="empty-subtitle">正确率：{{ deepReciteScore }}%</p>
        <button @click="exitDeepRecite" class="btn btn-primary">返回</button>
      </div>

      <!-- 底部退出按钮 -->
      <div class="deep-footer">
        <button @click="exitDeepRecite" class="text-btn">退出</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { calculateEbbinghaus } from './ebbinghaus'
import { updateMasteryLevel, addWordToVocabularyByWord } from '@/api/student'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()

// 获取当前用户 ID
const currentUserId = computed(() => userStore.userInfo?.id || 'anonymous')

// 词库配置
const vocabularies = ref([
  {
    id: 'cet6',
    name: '六级核心词汇',
    description: '大学英语六级核心词汇',
    wordCount: 1500,
    file: '/data/六级核心词汇.csv'
  },
  {
    id: 'cet4',
    name: '四级核心词汇',
    description: '大学英语四级核心词汇',
    wordCount: 5038,
    file: '/data/四级核心词汇.csv'
  },
  {
    id: 'gaokao',
    name: '高考英语必背词汇',
    description: '高考英语核心词汇',
    wordCount: 3603,
    file: '/data/高考英语必背词汇.csv'
  }
])

const selectedVocabulary = ref(null)
const showMeaning = ref(false)
const currentWordIndex = ref(0)
const pendingWords = ref([])
const masteredWords = ref(0)
const todayReviewed = ref(0)
const allWords = ref([])
const wordImageUrl = ref('')
const quizOptions = ref([])
const showQuiz = ref(false)
const wordDetails = ref(null)
const quizError = ref(false)
const dailyCount = ref(20)
const dailyCountSelected = ref(false)
const dailyCountOptions = ref([10, 20, 30, 50, 100])
// 深度背诵状态
const showDeepRecite = ref(false)
const deepReciteWords = ref([])
const currentDeepWordIndex = ref(0)
const userInput = ref('')
const answerStatus = ref(null)
const deepReciteScore = ref(0)
const deepReciteCorrect = ref(0)
const deepReciteTotal = ref(0)
const inputRef = ref(null)
const showDeepOptions = ref(false)
const deepOptions = ref([])

const currentVocabularyName = computed(() => {
  const vocab = vocabularies.value.find(v => v.id === selectedVocabulary.value)
  return vocab ? vocab.name : ''
})

const currentWord = computed(() => pendingWords.value[currentWordIndex.value] || null)

const currentDeepWord = computed(() => deepReciteWords.value[currentDeepWordIndex.value] || null)

const remainingWords = computed(() => {
  return allWords.value.filter(w => !w.mastered).length
})

const calculateDays = (dailyCount) => {
  const remaining = remainingWords.value
  if (remaining === 0) return 0
  return Math.ceil(remaining / dailyCount)
}

// 加载单词图片
const loadWordImage = () => {
  // 使用 Picsum Photos 生成随机图片作为占位符
  wordImageUrl.value = `https://picsum.photos/400/300?random=${Math.random()}`
}

// 图片加载失败处理
const imageLoadError = () => {
  wordImageUrl.value = ''
}

// 单词发音
const speakWord = () => {
  if (!currentWord.value) return
  
  // 使用浏览器 Web Speech API
  const utterance = new SpeechSynthesisUtterance(currentWord.value.word)
  utterance.lang = 'en-US'
  utterance.rate = 0.8
  utterance.pitch = 1
  speechSynthesis.speak(utterance)
}

// 生成测验选项
const generateQuizOptions = () => {
  if (!currentWord.value) return
  
  const correctMeaning = currentWord.value.meaning
  const otherWords = allWords.value.filter(w => w.word !== currentWord.value.word && !w.mastered)
  
  // 随机选择3个错误选项
  const shuffled = otherWords.sort(() => Math.random() - 0.5)
  const wrongOptions = shuffled.slice(0, 3).map(w => w.meaning)
  
  // 组合所有选项并打乱顺序
  const allOptions = [correctMeaning, ...wrongOptions]
  quizOptions.value = allOptions.sort(() => Math.random() - 0.5)
  showQuiz.value = true
}

// 处理选择题选项
const selectQuizOption = (selectedMeaning) => {
  if (!currentWord.value || quizError.value) return

  if (selectedMeaning === currentWord.value.meaning) {
    // 选对：标记为掌握，进入下一个单词
    rateWord(5)
    showQuiz.value = false
    quizError.value = false
  } else {
    // 选错：增加不认识计数，显示错误提示，重新选择
    currentWord.value.unknownCount = (currentWord.value.unknownCount || 0) + 1
    currentWord.value.lastReviewTime = new Date().toISOString()
    saveProgress()
    quizError.value = true
    todayReviewed.value++
    const today = new Date().toDateString()
    localStorage.setItem(`todayReviewed_${currentUserId.value}`, todayReviewed.value.toString())
    localStorage.setItem(`lastReviewDate_${currentUserId.value}`, today)
    setTimeout(() => {
      quizError.value = false
    }, 1500)
  }
}

// 获取单词详细信息
const fetchWordDetails = async (word) => {
  try {
    const response = await fetch(`https://api.dictionaryapi.dev/api/v2/entries/en/${word}`)
    if (!response.ok) throw new Error('API请求失败')
    const data = await response.json()
    if (data && data.length > 0) {
      const entry = data[0]
      wordDetails.value = {
        phonetic: entry.phonetic || entry.phonetics?.[0]?.text || '',
        meanings: entry.meanings || [],
        origin: entry.origin || ''
      }
    } else {
      wordDetails.value = null
    }
  } catch (error) {
    console.error('获取单词详情失败:', error)
    wordDetails.value = null
  }
}

// 深度背诵相关函数
const startDeepRecite = () => {
  if (showDeepRecite.value) exitDeepRecite()
  // 获取今天背诵过的单词（从 allWords 中筛选已复习的）
  const todayWords = allWords.value.filter(w => w.lastReviewTime && new Date(w.lastReviewTime).toDateString() === new Date().toDateString())
  if (todayWords.length === 0) {
    ElMessage.warning('今天还没有背诵过单词')
    return
  }
  // 随机打乱
  deepReciteWords.value = todayWords.sort(() => Math.random() - 0.5)
  currentDeepWordIndex.value = 0
  userInput.value = ''
  answerStatus.value = null
  deepReciteCorrect.value = 0
  deepReciteTotal.value = 0
  deepReciteScore.value = 0
  showDeepOptions.value = false
  deepOptions.value = []
  showDeepRecite.value = true
  // 自动聚焦输入框
  setTimeout(() => {
    if (inputRef.value) inputRef.value.focus()
  }, 100)
}

const checkDeepAnswer = () => {
  if (!currentDeepWord.value || !userInput.value.trim()) return
  
  const isCorrect = userInput.value.trim().toLowerCase() === currentDeepWord.value.word.toLowerCase()
  deepReciteTotal.value++
  
  if (isCorrect) {
    answerStatus.value = 'correct'
    deepReciteCorrect.value++
    deepReciteScore.value = Math.round((deepReciteCorrect.value / deepReciteTotal.value) * 100)
    
    // 延迟后进入下一个单词
    setTimeout(() => {
      userInput.value = ''
      answerStatus.value = null
      showDeepOptions.value = false
      if (currentDeepWordIndex.value < deepReciteWords.value.length - 1) {
        currentDeepWordIndex.value++
        if (inputRef.value) inputRef.value.focus()
      } else {
        // 完成
      }
    }, 1500)
  } else {
    answerStatus.value = 'wrong'
    // 增加单词出现频率
    currentDeepWord.value.unknownCount = (currentDeepWord.value.unknownCount || 0) + 1
    saveProgress()
    // 生成选项提示
    generateDeepOptions()
    showDeepOptions.value = true
  }
}

const generateDeepOptions = () => {
  if (!currentDeepWord.value) return
  
  const correctWord = currentDeepWord.value.word
  const otherWords = allWords.value.filter(w => w.word !== correctWord)
  const shuffled = otherWords.sort(() => Math.random() - 0.5)
  const wrongOptions = shuffled.slice(0, 3).map(w => w.word)
  const allOptions = [correctWord, ...wrongOptions]
  deepOptions.value = allOptions.sort(() => Math.random() - 0.5)
}

const selectDeepOption = (option) => {
  if (option === currentDeepWord.value.word) {
    answerStatus.value = 'correct'
    deepReciteCorrect.value++
    deepReciteScore.value = Math.round((deepReciteCorrect.value / deepReciteTotal.value) * 100)
    
    setTimeout(() => {
      userInput.value = ''
      answerStatus.value = null
      showDeepOptions.value = false
      deepOptions.value = []
      if (currentDeepWordIndex.value < deepReciteWords.value.length - 1) {
        currentDeepWordIndex.value++
        if (inputRef.value) inputRef.value.focus()
      }
    }, 1000)
  } else {
    ElMessage.error('选择错误，请重新输入')
    showDeepOptions.value = false
    deepOptions.value = []
    userInput.value = ''
    if (inputRef.value) inputRef.value.focus()
  }
}

const retryDeepInput = () => {
  showDeepOptions.value = false
  deepOptions.value = []
  answerStatus.value = null
  userInput.value = ''
  if (inputRef.value) inputRef.value.focus()
}

const exitDeepRecite = () => {
  showDeepRecite.value = false
  deepReciteWords.value = []
  currentDeepWordIndex.value = 0
  userInput.value = ''
  answerStatus.value = null
  deepReciteCorrect.value = 0
  deepReciteTotal.value = 0
  deepReciteScore.value = 0
  showDeepOptions.value = false
  deepOptions.value = []
}

// 监听当前单词变化，自动加载图片、发音、详细信息，并生成选项
watch(currentWord, (newWord) => {
  if (newWord) {
    loadWordImage(newWord.word)
    fetchWordDetails(newWord.word)
    showMeaning.value = false
    showQuiz.value = false
    quizError.value = false
    generateQuizOptions()
  }
})

// 选择词库
const selectVocabulary = async (vocab) => {
  try {
    selectedVocabulary.value = vocab.id
    localStorage.setItem(`selectedVocabulary_${currentUserId.value}`, vocab.id)
    await loadVocabularyData(vocab.file)
    // 恢复之前设置的每日复习量（作为默认值），但不直接进入背诵界面
    const savedDailyCount = localStorage.getItem(`daily_count_${currentUserId.value}_${selectedVocabulary.value}`)
    if (savedDailyCount) {
      dailyCount.value = parseInt(savedDailyCount)
    }
    // 始终显示每日复习量选择界面，让用户可以重新选择
    dailyCountSelected.value = false
  } catch (error) {
    ElMessage.error('加载词库失败：' + error.message)
  }
}

// 选择每日复习数量
const selectDailyCount = (count) => {
  dailyCount.value = count
  dailyCountSelected.value = true
  localStorage.setItem(`daily_count_${currentUserId.value}_${selectedVocabulary.value}`, count.toString())
  loadWords()
}

// 返回词库选择
const backToSelection = () => {
  selectedVocabulary.value = null
  localStorage.removeItem(`selectedVocabulary_${currentUserId.value}`)
  pendingWords.value = []
  allWords.value = []
  dailyCountSelected.value = false
}

// 返回设置
const backToSettings = () => {
  if (showDeepRecite.value) exitDeepRecite()
  dailyCountSelected.value = false
  pendingWords.value = []
}

// 解析 CSV 文件
const parseCSV = (csvText) => {
  const lines = csvText.split('\n')
  const words = []

  // 跳过表头
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim()
    if (!line) continue

    // 简单的 CSV 解析（处理带引号的字段）
    const match = line.match(/^"([^"]+)","([^"]+)"$/)
    if (match) {
      words.push({
        id: i,
        word: match[1],
        meaning: match[2],
        reviewCount: 0,
        interval: 0,
        easinessFactor: 2.5,
        lastReviewTime: null,
        mastered: false,
        unknownCount: 0,
        synced: false
      })
    }
  }

  return words
}

// 加载词库数据
const loadVocabularyData = async (file) => {
  try {
    const response = await fetch(file)
    if (!response.ok) throw new Error('文件加载失败')
    const text = await response.text()
    allWords.value = parseCSV(text)

    // 从 localStorage 加载复习进度（自动检测词库更新）
    const progressKey = `vocab_progress_${currentUserId.value}_${selectedVocabulary.value}`
    const savedCountKey = `vocab_count_${currentUserId.value}_${selectedVocabulary.value}`
    const savedCount = localStorage.getItem(savedCountKey)
    const currentCount = allWords.value.length
    // 如果词库词数变了（比如从418更新到1500），自动清旧进度
    if (savedCount && parseInt(savedCount) !== currentCount) {
      localStorage.removeItem(progressKey)
      localStorage.removeItem(savedCountKey)
      localStorage.removeItem(`vocab_index_${currentUserId.value}_${selectedVocabulary.value}`)
      console.log('词库已更新，旧进度已清除')
    } else {
      const savedProgress = localStorage.getItem(progressKey)
      if (savedProgress) {
        const progress = JSON.parse(savedProgress)
        allWords.value = allWords.value.map(word => {
          const savedWord = progress.find(p => p.word === word.word)
          return savedWord ? { ...word, ...savedWord } : word
        })
      }
    }
    // 保存当前词数，用于下次检测
    localStorage.setItem(savedCountKey, currentCount.toString())

    loadWords()
    // 登录后台补偿同步：将本地已评分但未成功写入 vocabulary_books 的单词重推一次
    // 保证"背过的单词"即使当时网络失败也会最终出现在单词页统计中
    resyncPendingWords()
  } catch (error) {
    ElMessage.error('加载词库数据失败：' + error.message)
  }
}

// 补偿扫描：查找 localStorage 中已评分但未同步到后端的单词，串行补发一次
// 串行控制并发避免同时发起大量请求；每条请求失败不阻断后续
const resyncPendingWords = async () => {
  if (!userStore.token) return
  const pending = allWords.value.filter(w => w.lastReviewTime && w.synced !== true)
  if (pending.length === 0) return

  for (const word of pending) {
    const level = word.mastered ? 'MASTERED' : 'LEARNING'
    // eslint-disable-next-line no-await-in-loop
    await syncWordToDatabase(word, level)
  }
}

const loadWords = () => {
  // 过滤掉已掌握的单词，只显示未掌握的
  let words = allWords.value.filter(word => !word.mastered)
  // 优先选今天没复习过的，再按不认识次数降序，同组内随机打乱
  const today = new Date().toDateString()
  words.sort((a, b) => {
    const aToday = a.lastReviewTime && new Date(a.lastReviewTime).toDateString() === today ? 1 : 0
    const bToday = b.lastReviewTime && new Date(b.lastReviewTime).toDateString() === today ? 1 : 0
    if (aToday !== bToday) return aToday - bToday
    return (b.unknownCount || 0) - (a.unknownCount || 0)
  })
  // 在同 unknownCount 组内随机打乱
  const grouped = {}
  for (const w of words) {
    const key = w.unknownCount || 0
    if (!grouped[key]) grouped[key] = []
    grouped[key].push(w)
  }
  const shuffled = []
  const keys = Object.keys(grouped).sort((a, b) => Number(b) - Number(a))
  for (const key of keys) {
    const group = grouped[key]
    for (let i = group.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [group[i], group[j]] = [group[j], group[i]]
    }
    shuffled.push(...group)
  }
  // 限制每日复习数量
  pendingWords.value = shuffled.slice(0, dailyCount.value)
  
  // 从 localStorage 恢复背诵进度
  const savedIndex = localStorage.getItem(`vocab_index_${currentUserId.value}_${selectedVocabulary.value}`)
  if (savedIndex) {
    currentWordIndex.value = parseInt(savedIndex)
    // 确保索引在有效范围内
    if (currentWordIndex.value >= pendingWords.value.length) {
      currentWordIndex.value = 0
    }
  } else {
    currentWordIndex.value = 0
  }
  
  masteredWords.value = allWords.value.filter(w => w.mastered).length
}

const loadMoreWords = () => {
  if (showDeepRecite.value) exitDeepRecite()
  // 过滤掉已掌握的单词，只显示未掌握的
  let words = allWords.value.filter(word => !word.mastered)
  // 优先选今天没复习过的，再按不认识次数降序，同组内随机打乱
  const today = new Date().toDateString()
  words.sort((a, b) => {
    const aToday = a.lastReviewTime && new Date(a.lastReviewTime).toDateString() === today ? 1 : 0
    const bToday = b.lastReviewTime && new Date(b.lastReviewTime).toDateString() === today ? 1 : 0
    if (aToday !== bToday) return aToday - bToday
    return (b.unknownCount || 0) - (a.unknownCount || 0)
  })
  // 在同 unknownCount 组内随机打乱
  const grouped = {}
  for (const w of words) {
    const key = w.unknownCount || 0
    if (!grouped[key]) grouped[key] = []
    grouped[key].push(w)
  }
  const shuffled = []
  const keys = Object.keys(grouped).sort((a, b) => Number(b) - Number(a))
  for (const key of keys) {
    const group = grouped[key]
    for (let i = group.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [group[i], group[j]] = [group[j], group[i]]
    }
    shuffled.push(...group)
  }
  // 加载 dailyCount 个新单词
  pendingWords.value = shuffled.slice(0, dailyCount.value)
  currentWordIndex.value = 0
  // 清除保存的进度
  localStorage.removeItem(`vocab_index_${currentUserId.value}_${selectedVocabulary.value}`)
  masteredWords.value = allWords.value.filter(w => w.mastered).length
  if (pendingWords.value.length === 0) {
    ElMessage.warning('所有单词都已掌握！')
  } else {
    ElMessage.success(`已加载 ${pendingWords.value.length} 个新单词`)
  }
}

const takeBreak = () => {
  if (showDeepRecite.value) exitDeepRecite()
  ElMessage.success('休息一下，记得回来继续学习哦！')
  setTimeout(() => {
    router.push('/student')
  }, 1000)
}

const toBackendMasteryLevel = (quality) => {
  if (quality === 5) return 'MASTERED'
  if (quality === 0) return 'LEARNING'
  return 'LEARNING'
}

const rateWord = (quality) => {
  if (!currentWord.value) return

  const word = currentWord.value
  // 本次评分产生新状态，标记为未同步；syncWordToDatabase 成功后会置回 true
  word.synced = false

  if (quality === 5) {
    // 认识：标记为已掌握，不再出现
    word.mastered = true
    word.lastReviewTime = new Date().toISOString()
    // 尝试同步到后端数据库
    syncWordToDatabase(word, toBackendMasteryLevel(quality))
  } else if (quality === 0) {
    // 不认识：增加出现频率（增加不认识计数）
    word.unknownCount = (word.unknownCount || 0) + 1
    word.lastReviewTime = new Date().toISOString()
    // 尝试同步到后端数据库
    syncWordToDatabase(word, toBackendMasteryLevel(quality))
  } else if (quality === 3) {
    // 一般：按艾宾浩斯算法继续复习
    const result = calculateEbbinghaus(quality, {
      reviewCount: word.reviewCount,
      interval: word.interval,
      easinessFactor: word.easinessFactor
    })
    word.reviewCount = result.repetition
    word.interval = result.interval
    word.easinessFactor = result.easinessFactor
    word.lastReviewTime = new Date().toISOString()
    // 尝试同步到后端数据库
    syncWordToDatabase(word, toBackendMasteryLevel(quality))
  }

  // 保存进度到 localStorage（作为备份）
  saveProgress()
  
  // 保存当前单词索引
  localStorage.setItem(`vocab_index_${currentUserId.value}_${selectedVocabulary.value}`, currentWordIndex.value.toString())

  todayReviewed.value++
  
  // 保存每日背诵进度到 localStorage，供 Dashboard 读取
  const today = new Date().toDateString()
  localStorage.setItem(`todayReviewed_${currentUserId.value}`, todayReviewed.value.toString())
  localStorage.setItem(`lastReviewDate_${currentUserId.value}`, today)

  if (quality === 5) {
    masteredWords.value++
  }

  showMeaning.value = false

  // 移动到下一个单词
  if (currentWordIndex.value < pendingWords.value.length - 1) {
    currentWordIndex.value++
    // 保存新的索引
    localStorage.setItem(`vocab_index_${currentUserId.value}_${selectedVocabulary.value}`, currentWordIndex.value.toString())
  } else {
    pendingWords.value = []
    // 完成背诵，清除保存的索引
    localStorage.removeItem(`vocab_index_${currentUserId.value}_${selectedVocabulary.value}`)
    ElMessage.success('复习完成！')
  }
}

const saveProgress = () => {
  const progress = allWords.value.map(word => ({
    word: word.word,
    mastered: word.mastered,
    unknownCount: word.unknownCount,
    reviewCount: word.reviewCount,
    interval: word.interval,
    easinessFactor: word.easinessFactor,
    lastReviewTime: word.lastReviewTime,
    synced: word.synced
  }))
  localStorage.setItem(`vocab_progress_${currentUserId.value}_${selectedVocabulary.value}`, JSON.stringify(progress))
}

// 将单词同步到后端数据库
// 成功时将 word.synced 置 true 并持久化，供 onMounted 时的补偿扫描跳过已同步记录
const syncWordToDatabase = async (word, masteryLevel) => {
  try {
    // 第一步：通过单词字符串添加到生词本（如果不存在会自动创建）
    const addResponse = await addWordToVocabularyByWord({ word: word.word })

    if (addResponse?.code === 200 && addResponse.data && addResponse.data.wordId) {
      const wordId = addResponse.data.wordId

      // 第二步：更新掌握程度（无论单词是新建还是已存在，都刷新本次评分对应的状态）
      const updateResponse = await updateMasteryLevel(wordId, {
        masteryLevel,
        isCorrect: masteryLevel === 'MASTERED'
      })

      if (updateResponse?.code === 200) {
        word.synced = true
        saveProgress()
        return true
      }
    }
    console.warn(`添加单词 ${word.word} 到数据库失败，保留本地状态等待下次补偿同步`)
    return false
  } catch (error) {
    console.error('同步单词到数据库失败:', error)
    // 同步失败不影响 localStorage 保存，仅记录错误
    return false
  }
}

onMounted(() => {
  // 每次进入都显示词库选择页面，不自动恢复
  // 背诵数据会在选择词库后从 localStorage 恢复
  
  // 加载今日背诵进度
  const today = new Date().toDateString()
  const savedDate = localStorage.getItem(`lastReviewDate_${currentUserId.value}`)
  if (savedDate === today) {
    const savedCount = localStorage.getItem(`todayReviewed_${currentUserId.value}`)
    if (savedCount) {
      todayReviewed.value = parseInt(savedCount)
    }
  }
})
</script>

<style scoped>
.word-recite-container {
  max-width: 100%;
  margin: 0 auto;
  padding: 16px;
  min-height: 100vh;
  background: var(--bg-secondary);
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.page-header {
  text-align: center;
  margin-bottom: 24px;
  padding: 20px 0;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.page-subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 0;
}

/* 每日复习数量选择界面 */
.daily-count-selection {
  max-width: 600px;
  margin: 0 auto;
}

.count-options {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.count-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 32px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid var(--border-light);
  text-align: center;
  position: relative;
  overflow: hidden;
}

.count-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.count-card:hover {
  border-color: var(--primary-color);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.count-card:hover::before {
  transform: scaleX(1);
}

.count-number {
  font-size: 32px;
  font-weight: 700;
  color: var(--primary-color);
  display: block;
  margin-bottom: 8px;
  letter-spacing: -1px;
}

.count-label {
  font-size: 14px;
  color: var(--text-secondary);
  font-weight: 500;
}

.count-days {
  font-size: 12px;
  color: var(--text-tertiary);
  display: block;
  margin-top: 4px;
}

.progress-info {
  text-align: center;
  margin-bottom: 20px;
  padding: 16px;
  background: var(--bg-primary);
  border-radius: 12px;
  font-size: 14px;
  color: var(--text-secondary);
  border: 1px solid var(--border-light);
  box-shadow: var(--shadow-sm);
}

/* 词库选择界面 */
.vocabularies-grid {
  display: grid;
  gap: 16px;
}

.vocabulary-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 24px;
  box-shadow: var(--shadow-sm);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  border: 2px solid var(--border-light);
  position: relative;
  overflow: hidden;
}

.vocabulary-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
  transform: scaleX(0);
  transition: transform 0.3s ease;
}

.vocabulary-card:hover {
  border-color: var(--primary-color);
  transform: translateY(-4px);
  box-shadow: var(--shadow-lg);
}

.vocabulary-card:hover::before {
  transform: scaleX(1);
}

.vocab-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 8px 0;
  letter-spacing: -0.5px;
}

.vocab-desc {
  font-size: 14px;
  color: var(--text-secondary);
  margin: 0 0 12px 0;
  line-height: 1.5;
}

.vocab-count {
  font-size: 13px;
  color: var(--text-tertiary);
  margin: 0;
  font-weight: 500;
  background: var(--bg-tertiary);
  padding: 4px 12px;
  border-radius: 12px;
  display: inline-block;
}

/* 统计卡片 */
.stats-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--bg-primary);
  border-radius: 16px;
  padding: 20px;
  text-align: center;
  box-shadow: var(--shadow-sm);
  border: 1px solid var(--border-light);
  transition: all 0.3s ease;
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-md);
}

.stat-label {
  font-size: 12px;
  color: var(--text-secondary);
  margin-bottom: 8px;
  font-weight: 500;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--primary-color);
  letter-spacing: -0.5px;
}

/* 单词卡片 */
.word-card {
  background: var(--bg-primary);
  border-radius: 20px;
  padding: 32px;
  box-shadow: var(--shadow-md);
  text-align: center;
  border: 1px solid var(--border-light);
  position: relative;
  overflow: hidden;
}

.word-card::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: var(--gradient-brand);
}

/* 单词图片 */
.word-image-container {
  margin-bottom: 20px;
  border-radius: 8px;
  overflow: hidden;
  background: #f5f5f5;
  min-height: 200px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.word-image {
  width: 100%;
  height: 200px;
  object-fit: cover;
}

.word-header {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-bottom: 24px;
}

.word-text {
  font-size: 32px;
  font-weight: 600;
  margin: 0;
}

.speak-btn {
  background: #409eff;
  border: none;
  border-radius: 50%;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: all 0.2s;
  color: white;
  flex-shrink: 0;
}

.speak-btn:hover {
  background: #66b1ff;
  transform: scale(1.1);
}

.speak-btn:active {
  transform: scale(0.95);
}

.word-content {
  padding: 20px;
  border-radius: 16px;
  margin-bottom: 24px;
}

.word-meaning p {
  font-size: 18px;
  color: var(--text-primary);
  margin: 0 0 16px 0;
  font-weight: 500;
  line-height: 1.6;
}

/* 单词详细信息 */
.word-details {
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--border-light);
  text-align: left;
}

.detail-section {
  margin-bottom: 8px;
  font-size: 14px;
}

.detail-label {
  font-weight: 600;
  color: var(--text-secondary);
}

.detail-value {
  color: var(--text-primary);
}

.meanings-section {
  margin-top: 12px;
}

.part-of-speech {
  font-weight: 600;
  color: var(--primary-color);
  margin-bottom: 8px;
  font-size: 15px;
}

.definitions {
  margin-left: 8px;
}

.definition-item {
  margin-bottom: 12px;
}

.definition {
  font-size: 14px;
  color: var(--text-primary);
  margin: 0 0 4px 0;
}

.example {
  font-size: 13px;
  color: var(--text-secondary);
  margin: 0;
  font-style: italic;
}

.action-buttons {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

.quiz-options {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  width: 100%;
  margin-bottom: 12px;
}

@keyframes shake {
  0%, 100% { transform: translateX(0); }
  10%, 50%, 90% { transform: translateX(-6px); }
  30%, 70% { transform: translateX(6px); }
}

.quiz-error {
  color: var(--error-color);
  font-weight: 600;
  margin-bottom: 12px;
  text-align: center;
  background: rgba(245, 108, 108, 0.1);
  padding: 8px 16px;
  border-radius: 8px;
}

.quiz-error.shake {
  animation: shake 0.5s ease-in-out;
}

.quiz-btn {
  padding: 12px 8px;
  font-size: 14px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.cancel-btn {
  width: 100%;
}

.btn {
  padding: 12px 24px;
  border: none;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  letter-spacing: -0.3px;
}

.btn-primary {
  background: var(--primary-color);
  color: white;
  box-shadow: 0 4px 12px rgba(0, 102, 204, 0.3);
}

.btn-primary:hover {
  background: var(--primary-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 102, 204, 0.4);
}

.btn-success {
  background: var(--success-color);
  color: white;
  box-shadow: 0 4px 12px rgba(103, 194, 58, 0.3);
}

.btn-success:hover {
  background: var(--success-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(103, 194, 58, 0.4);
}

.btn-info {
  background: var(--info-color);
  color: white;
  box-shadow: 0 4px 12px rgba(144, 147, 153, 0.3);
}

.btn-info:hover {
  background: var(--info-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(144, 147, 153, 0.4);
}

.btn-danger {
  background: var(--error-color);
  color: white;
  box-shadow: 0 4px 12px rgba(245, 108, 108, 0.3);
}

.btn-danger:hover {
  background: var(--error-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(245, 108, 108, 0.4);
}

.btn-secondary {
  background: var(--bg-tertiary);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
}

.btn-secondary:hover {
  background: var(--bg-secondary);
  border-color: var(--border-color);
  transform: translateY(-2px);
}

.btn-warning {
  background: var(--warning-color);
  color: white;
  box-shadow: 0 4px 12px rgba(230, 162, 60, 0.3);
}

.btn-warning:hover {
  background: var(--warning-dark);
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(230, 162, 60, 0.4);
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: var(--text-secondary);
}

.empty-subtitle {
  font-size: 14px;
  color: var(--text-tertiary);
  margin: 8px 0 24px 0;
}

.empty-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
  flex-wrap: wrap;
}

/* 深度背诵界面 */
.deep-recite-view {
  max-width: 600px;
  margin: 0 auto;
}

.word-input {
  width: 100%;
  padding: 16px;
  font-size: 18px;
  border: 2px solid var(--border-color);
  border-radius: 12px;
  margin: 20px 0;
  text-align: center;
  transition: all 0.3s;
  background: var(--bg-primary);
  color: var(--text-primary);
  font-weight: 500;
}

.word-input:focus {
  outline: none;
  border-color: var(--primary-color);
  box-shadow: 0 0 0 3px rgba(0, 102, 204, 0.1);
}

.word-input.correct {
  border-color: var(--success-color);
  background: rgba(103, 194, 58, 0.1);
}

.word-input.wrong {
  border-color: var(--error-color);
  background: rgba(245, 108, 108, 0.1);
}

.answer-feedback {
  margin-bottom: 20px;
  text-align: center;
  font-size: 16px;
  font-weight: 600;
}

.correct-text {
  color: var(--success-color);
}

.wrong-text {
  color: var(--error-color);
}

.submit-btn {
  width: 100%;
}

.deep-options {
  margin-top: 20px;
}

.options-title {
  text-align: center;
  font-weight: 600;
  margin-bottom: 12px;
  color: var(--text-primary);
}

.options-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}

.option-btn {
  padding: 12px;
  font-size: 14px;
  background: var(--bg-tertiary);
  border: 1px solid var(--border-light);
  transition: all 0.2s ease;
}

.option-btn:hover {
  background: var(--bg-secondary);
  border-color: var(--border-color);
  transform: translateY(-1px);
}

.retry-btn {
  width: 100%;
}

/* 深度背诵底部 */
.deep-footer {
  margin-top: 24px;
  text-align: center;
  padding: 16px;
}

/* 每日复习量底部 */
.daily-footer {
  margin-top: 24px;
  text-align: center;
  padding: 16px;
}

.text-btn {
  background: transparent;
  border: none;
  color: var(--text-tertiary);
  font-size: 14px;
  font-weight: 500;
  padding: 8px 16px;
  cursor: pointer;
  transition: color 0.2s;
}

.text-btn:hover {
  color: var(--text-primary);
}

.text-btn:active {
  color: var(--primary-color);
}

/* 响应式布局 */
@media (max-width: 768px) {
  .word-recite-container {
    padding: 12px;
  }

  .page-header {
    margin-bottom: 16px;
    padding: 16px 0;
  }

  .page-title {
    font-size: 20px;
  }

  .page-subtitle {
    font-size: 13px;
  }

  /* 词库选择卡片 */
  .vocabularies-grid {
    gap: 12px;
  }

  .vocabulary-card {
    padding: 20px;
  }

  .vocab-name {
    font-size: 16px;
  }

  .vocab-desc {
    font-size: 13px;
  }

  /* 每日复习量选择 */
  .daily-count-selection {
    max-width: 100%;
  }

  .count-options {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .count-card {
    padding: 24px 16px;
  }

  .count-number {
    font-size: 28px;
  }

  /* 统计卡片 */
  .stats-cards {
    grid-template-columns: repeat(3, 1fr);
    gap: 8px;
  }

  .stat-card {
    padding: 12px 8px;
  }

  .stat-label {
    font-size: 11px;
  }

  .stat-value {
    font-size: 18px;
  }

  /* 单词卡片 */
  .word-card {
    padding: 20px 16px;
    border-radius: 16px;
  }

  .word-content {
    padding: 16px;
    margin-bottom: 16px;
  }

  .word-header {
    margin-bottom: 16px;
  }

  .word-text {
    font-size: 24px;
  }

  .speak-btn {
    width: 36px;
    height: 36px;
  }

  .word-meaning p {
    font-size: 16px;
  }

  /* 按钮样式 */
  .action-buttons {
    gap: 8px;
  }

  .btn {
    padding: 10px 16px;
    font-size: 14px;
  }

  .quiz-options {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .quiz-btn {
    padding: 14px 12px;
    font-size: 13px;
  }

  /* 深度背诵 */
  .deep-recite-view {
    max-width: 100%;
  }

  .word-input {
    font-size: 16px;
    padding: 14px;
  }

  .options-grid {
    grid-template-columns: 1fr;
  }

  /* 空状态 */
  .empty-state {
    padding: 32px 16px;
  }

  .empty-actions {
    flex-direction: column;
  }

  .empty-actions .btn {
    width: 100%;
  }

  /* 返回按钮 */
  .back-btn {
    padding: 8px 16px;
    font-size: 14px;
  }
}

@media (max-width: 480px) {
  .count-options {
    grid-template-columns: 1fr;
  }

  .stats-cards {
    grid-template-columns: 1fr;
    gap: 8px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 20px;
  }

  .word-text {
    font-size: 20px;
  }

  .action-buttons {
    flex-direction: column;
  }

  .action-buttons .btn {
    width: 100%;
  }

  .quiz-options {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 769px) and (max-width: 1024px) {
  .count-options {
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
  }

  .word-card {
    padding: 28px;
  }

  .word-text {
    font-size: 28px;
  }
}
</style>
