<template>
  <div class="student-markdown-reader" :class="{ 'with-background': showBackground }">
    <div class="reader-body">
      <div 
        class="markdown-content" 
        v-html="htmlContent"
        @click="handleClick"
      ></div>
    </div>

    <!-- 图片预览对话框 -->
    <el-dialog v-model="imagePreviewVisible" title="图片预览" width="auto" class="image-preview-dialog">
      <div class="image-preview-wrap">
        <img :src="previewImageSrc" alt="图片预览" class="image-preview-full" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  // 可以传入原始 markdown 文本或者已经渲染好的 html
  markdown: {
    type: String,
    default: ''
  },
  html: {
    type: String,
    default: ''
  },
  // 是否显示灰色背景（模拟学生端阅读页）
  showBackground: {
    type: Boolean,
    default: true
  },
  // 是否启用图片点击预览
  clickableImages: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['image-click', 'word-click'])

const imagePreviewVisible = ref(false)
const previewImageSrc = ref('')

// 计算渲染后的 HTML
const htmlContent = computed(() => {
  // 如果直接传入了 html，优先使用
  if (props.html) return props.html

  // 否则用 markdown 渲染
  if (!props.markdown) return '<p style="color:#c0c4cc;font-style:italic;">暂无内容</p>'
  
  try {
    const rawHtml = marked(props.markdown, { breaks: true })
    const config = {
      ALLOWED_TAGS: ['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'li', 'a', 'strong', 'em', 'code', 'pre', 'blockquote', 'img', 'br', 'hr', 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'video', 'source', 'del', 's'],
      ALLOWED_ATTR: ['href', 'src', 'alt', 'title', 'width', 'height', 'target', 'controls', 'type', 'class'],
      KEEP_CONTENT: true
    }
    return DOMPurify.sanitize(rawHtml, config)
  } catch (e) {
    console.error('Markdown 渲染错误:', e)
    return '<p style="color:#f56c6c;">Markdown 渲染失败</p>'
  }
})

const handleClick = (event) => {
  if (event.target.tagName === 'IMG' && props.clickableImages) {
    previewImageSrc.value = event.target.src
    imagePreviewVisible.value = true
    emit('image-click', event.target.src)
  }
}
</script>

<style scoped>
/* ── 学生端阅读界面 ── */
.student-markdown-reader {
  width: 100%;
  height: 100%;
  overflow: auto;
  font-family: 'Inter', -apple-system, sans-serif;
}

.student-markdown-reader.with-background {
  background-color: #f6f7f8; /* 与学生端 --bg-soft 一致 */
}

.reader-body {
  max-width: 800px;
  margin: 0 auto;
  padding: 32px 24px;
}

.markdown-content {
  font-size: 18px;
  line-height: 1.8;
  color: #1e293b;
  word-break: break-word;
  overflow-wrap: break-word;
}

/* 使用 :deep() 穿透 scoped，与学生端 InteractiveText.vue 排版一致 */
.markdown-content :deep(h1) {
  font-size: 32px;
  font-weight: 800;
  line-height: 1.2;
  letter-spacing: -0.02em;
  margin: 32px 0 16px 0;
  color: #0f172a;
}

.markdown-content :deep(h2) {
  font-size: 24px;
  font-weight: 700;
  margin: 48px 0 16px 0;
  color: #1e293b;
}

.markdown-content :deep(h3) {
  font-size: 20px;
  font-weight: 600;
  margin: 32px 0 12px 0;
  color: #1e293b;
}

.markdown-content :deep(p) {
  margin-bottom: 24px;
}

.markdown-content :deep(strong) {
  color: #1e293b;
  font-weight: 600;
}

.markdown-content :deep(img) {
  max-height: 100px !important;
  width: auto !important;
  max-width: 100%;
  object-fit: contain;
  border-radius: 8px;
  margin: 16px 0;
  display: block;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid transparent;
}

.markdown-content :deep(img:hover) {
  opacity: 0.85;
  border-color: #197fe6;
  box-shadow: 0 2px 8px rgba(25, 127, 230, 0.15);
}

.markdown-content :deep(video) {
  max-height: 200px !important;
  width: auto !important;
  max-width: 100%;
  border-radius: 8px;
  margin: 16px 0;
  display: block;
}

.markdown-content :deep(ul),
.markdown-content :deep(ol) {
  margin: 0 0 24px 0;
  padding-left: 24px;
}

.markdown-content :deep(li) {
  margin-bottom: 8px;
}

.markdown-content :deep(code) {
  background-color: rgba(226, 232, 240, 0.5);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 15px;
}

.markdown-content :deep(pre) {
  background-color: #f1f5f9;
  padding: 16px;
  border-radius: 8px;
  overflow-x: auto;
  margin: 8px 0 24px 0;
}

.markdown-content :deep(blockquote) {
  border-left: 4px solid #197fe6;
  padding: 12px 16px;
  margin: 24px 0;
  background: #f8fafc;
  border-radius: 4px;
  color: #475569;
}

.markdown-content :deep(table) {
  border-collapse: collapse;
  width: 100%;
  margin: 8px 0 24px 0;
}

.markdown-content :deep(th),
.markdown-content :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 12px 8px;
  text-align: left;
}

.markdown-content :deep(th) {
  background-color: #f8fafc;
  font-weight: bold;
}

.markdown-content :deep(hr) {
  border: none;
  height: 1px;
  background: #e2e8f0;
  margin: 32px 0;
}

/* 图片预览对话框 */
.image-preview-wrap {
  display: flex;
  justify-content: center;
  align-items: center;
  max-height: 75vh;
  overflow: auto;
}

.image-preview-full {
  max-width: 100%;
  max-height: 75vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.1);
}
</style>
