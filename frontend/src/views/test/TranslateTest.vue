<template>
  <div class="translate-test">
    <h1>翻译功能测试</h1>
    
    <!-- 翻译按钮（浮动显示） -->
    <transition name="fade">
      <div
        v-if="showTranslateButton"
        class="translate-button"
        :style="{ top: translateButtonPosition.y + 'px', left: translateButtonPosition.x + 'px' }"
        @click="translateSelectedText"
      >
        <el-button type="primary" size="small">
          翻译
        </el-button>
      </div>
    </transition>
    
    <el-card>
      <div 
        class="test-content"
        @mouseup="handleTextSelection"
        @touchend="handleTextSelection"
      >
        <p>This is a test paragraph. Please select some text here to test the translation feature.</p>
        <p>You can select multiple words and the translate button should appear.</p>
        <p>这是一个测试段落。请选择一些文本来测试翻译功能。</p>
      </div>
    </el-card>

    <el-card style="margin-top: 20px;">
      <h3>调试信息</h3>
      <div>showTranslateButton: {{ showTranslateButton }}</div>
      <div>selectedText: {{ selectedText }}</div>
      <div>translateButtonPosition: {{ translateButtonPosition }}</div>
    </el-card>

    <!-- 翻译结果弹窗 -->
    <el-dialog
      v-model="showTranslateDialog"
      title="翻译结果"
      width="500px"
    >
      <div class="translate-content" v-if="!translating">
        <div class="original-text">
          <h4>原文：</h4>
          <p>{{ selectedText }}</p>
        </div>
        <el-divider />
        <div class="translated-text">
          <h4>译文：</h4>
          <p>{{ translatedText }}</p>
        </div>
      </div>
      <div class="loading-content" v-else>
        <el-icon class="is-loading" :size="40"><Loading /></el-icon>
        <p style="margin-top: 10px; color: #666;">翻译中...</p>
      </div>
      <template #footer>
        <el-button @click="showTranslateDialog = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading } from '@element-plus/icons-vue'

const showTranslateButton = ref(false)
const showTranslateDialog = ref(false)
const selectedText = ref('')
const translatedText = ref('')
const translating = ref(false)
const translateButtonPosition = reactive({ x: 0, y: 0 })

const handleTextSelection = (event) => {
  const selection = window.getSelection()
  const text = selection.toString().trim()

  if (text && text.includes(' ')) {
    selectedText.value = text
    showTranslateButton.value = true

    const range = selection.getRangeAt(0)
    const rect = range.getBoundingClientRect()

    translateButtonPosition.x = rect.left + rect.width / 2 - 40
    translateButtonPosition.y = rect.bottom + 5

  } else {
    showTranslateButton.value = false
  }
}

const translateSelectedText = async () => {
  if (!selectedText.value) return

  showTranslateButton.value = false
  showTranslateDialog.value = true
  translating.value = true
  translatedText.value = ''

  try {
    const response = await fetch('/api/translate/auto', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token') || ''}`
      },
      body: JSON.stringify({
        text: selectedText.value
      })
    })

    if (response.ok) {
      const result = await response.json()
      if (result.code === 200) {
        translatedText.value = result.data
      } else {
        ElMessage.error(result.message || '翻译失败')
        translatedText.value = '翻译失败：' + (result.message || '请稍后重试')
      }
    } else {
      throw new Error('网络请求失败')
    }
  } catch (error) {
    console.error('翻译失败:', error)
    ElMessage.error('翻译失败')
    translatedText.value = '翻译服务暂时不可用'
  } finally {
    translating.value = false
  }
}
</script>

<style scoped>
.translate-test {
  padding: 20px;
  max-width: 800px;
  margin: 0 auto;
}

.test-content {
  padding: 20px;
  line-height: 2;
  user-select: text;
}

.test-content p {
  margin-bottom: 16px;
}

.translate-button {
  position: fixed;
  z-index: 1000;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.15);
  border-radius: 4px;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.3s;
}

.fade-enter-from, .fade-leave-to {
  opacity: 0;
}

.translate-content {
  padding: 16px 0;
}

.original-text, .translated-text {
  margin-bottom: 12px;
}

.original-text h4, .translated-text h4 {
  margin: 0 0 8px 0;
  color: #666;
  font-size: 14px;
  font-weight: 600;
}

.original-text p, .translated-text p {
  margin: 0;
  padding: 12px;
  background: #f8f9fa;
  border-radius: 6px;
  line-height: 1.6;
  color: #333;
}

.translated-text p {
  background: #e6f7ff;
  border-left: 3px solid #1890ff;
}

.loading-content {
  text-align: center;
  padding: 40px 0;
}
</style>
