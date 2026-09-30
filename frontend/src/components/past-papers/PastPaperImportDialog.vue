<template>
  <el-dialog v-model="visible" title="导入真题" width="min(620px, 94vw)" :close-on-click-modal="false" @closed="reset">
    <div class="import-form">
      <label>考试分类
        <select v-model="form.category">
          <option value="cet4">英语四级</option>
          <option value="cet6">英语六级</option>
          <option value="postgraduate">考研英语</option>
        </select>
      </label>
      <label>试卷名称（文件内没有标题时使用）
        <input v-model.trim="form.title" placeholder="例如：2023 年 12 月六级真题（第一套）">
      </label>
      <label>预计时长（分钟）
        <input v-model.number="form.duration" type="number" min="1" max="300">
      </label>
      <label class="file-field">批量选择文件
        <input type="file" accept=".pdf,.md,.json,application/pdf,text/markdown,application/json" multiple @change="handleFiles">
        <small>支持一次选择多份 PDF、MinerU 的 full.md 或 JSON。PDF 需先用 MinerU 导出 full.md。</small>
      </label>
      <label class="file-field">听力音频（含听力题时必选，MP3/WAV/M4A）
        <input type="file" accept="audio/*" @change="handleAudio">
        <span v-if="form.audioUrl" class="audio-ready">已上传，可用于听力题播放</span>
      </label>
      <div class="template-actions">
        <button class="template-button" type="button" @click="downloadTemplate">下载选择题 Markdown 模板</button>
        <button class="template-button" type="button" @click="downloadAdvancedTemplate">下载全题型 JSON 模板</button>
      </div>
    </div>

    <div v-if="previews.length" class="preview-list" aria-live="polite">
      <div v-for="preview in previews" :key="preview.name" :class="['preview-item', { error: preview.error }]">
        <strong>{{ preview.name }}</strong>
        <span>{{ preview.error || previewSummary(preview.paper) }}</span>
        <button v-if="preview.paper" class="export-button" type="button" @click="downloadConvertedPaper(preview.paper, preview.name)">下载转换 JSON</button>
      </div>
      <button v-if="validPapers.length > 1" class="batch-export" type="button" @click="downloadAllConverted">下载全部转换 JSON（{{ validPapers.length }} 份）</button>
    </div>

    <template #footer>
      <button class="secondary" type="button" @click="visible = false">取消</button>
      <button class="primary" type="button" :disabled="validPapers.length === 0 || importing" @click="confirmImport">
        {{ importing ? '正在导入' : `导入 ${validPapers.length} 套` }}
      </button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { parsePaperFile, toTemplatePaper } from '@/utils/pastPaperImporter'
import { createSharedPastPaper } from '@/api/pastPapers'
import { uploadMedia } from '@/api/media'

const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['imported'])
const form = reactive({ category: 'cet4', title: '', duration: 30, audioUrl: '' })
const previews = ref([])
const importing = ref(false)
const validPapers = computed(() => previews.value.filter(item => item.paper).map(item => item.paper))

const isListeningQuestion = (question) => question?.type === 'listening' || String(question?.section || '').includes('听力')
const paperNeedsAudio = (paper) => paper?.questions?.some(isListeningQuestion) && !paper.audioUrl

function previewSummary(paper) {
  const audioStatus = paperNeedsAudio(paper) ? ' · 请上传听力音频' : ''
  return `${paper.title} · ${paper.questions.length} 题${audioStatus}`
}

async function handleFiles(event) {
  previews.value = []
  for (const file of Array.from(event.target.files || [])) {
    try {
      const paper = await parsePaperFile(file, form)
      previews.value.push({ name: file.name, paper })
    } catch (error) {
      previews.value.push({ name: file.name, error: error.message })
    }
  }
}

async function handleAudio(event) {
  const file = event.target.files?.[0]
  if (!file) return
  try {
    const response = await uploadMedia(file, 'AUDIO')
    form.audioUrl = response?.data?.url || ''
    if (!form.audioUrl) throw new Error('服务器没有返回音频地址')
    previews.value.forEach((preview) => {
      if (!preview.paper) return
      preview.paper.audioUrl = form.audioUrl
      preview.paper.questions?.forEach((question) => {
        if (isListeningQuestion(question)) question.audioUrl = form.audioUrl
      })
    })
    ElMessage.success('听力音频上传成功')
  } catch (error) {
    ElMessage.error(error.message || '音频上传失败')
  }
}

async function confirmImport() {
  const missingAudioPaper = validPapers.value.find(paperNeedsAudio)
  if (missingAudioPaper) {
    ElMessage.error(`“${missingAudioPaper.title}”包含听力题，请先上传听力音频`)
    return
  }
  importing.value = true
  try {
    for (const paper of validPapers.value) await createSharedPastPaper(paper)
    ElMessage.success(`成功导入 ${validPapers.value.length} 套试卷`)
    emit('imported')
    visible.value = false
  } catch (error) {
    ElMessage.error(error.message || '导入失败')
  } finally {
    importing.value = false
  }
}

function reset() {
  previews.value = []
  form.title = ''
  form.audioUrl = ''
}

function downloadTemplate() {
  const template = `# 试卷名称

**日期：** 2024-12
**时长：** 30

## 阅读理解

### 1
**文章：** 在这里填写文章或材料，没有可删除本行。
**问题：** 在这里填写题干。

A. 选项 A
B. 选项 B
C. 选项 C
D. 选项 D

**答案：** B
**解析：** 在这里填写解析。
`
  downloadFile(template, '真题导入模板.md', 'text/markdown;charset=utf-8')
}

function downloadAdvancedTemplate() {
  const template = {
    title: '2025 年真题示例', date: '2025-12', duration: 120,
    questions: [
      { type: 'single_choice', section: '阅读理解', stem: '题干', options: ['选项A', '选项B', '选项C', '选项D'], answer: 'A', analysis: '解析' },
      { type: 'cloze', section: '选词填空', passage: '材料原文', stem: 'The first ____ and the second ____.', options: ['word one', 'word two', 'word three'], answers: ['A', 'C'], analysis: '逐空解析' },
      { type: 'matching', section: '段落匹配', stem: '将陈述与段落匹配', options: ['段落 A 内容', '段落 B 内容', '段落 C 内容'], items: [{ id: '1', prompt: '陈述一', answer: 'B' }, { id: '2', prompt: '陈述二', answer: 'C' }], analysis: '匹配依据' },
      { type: 'translation', section: '翻译', stem: '请翻译以下内容', passage: '待翻译原文', referenceAnswer: '参考译文', analysis: '翻译要点' },
      { type: 'writing', section: '写作', stem: '作文题目和要求', referenceAnswer: '参考范文', analysis: '评分标准与写作要点' }
    ]
  }
  downloadFile(JSON.stringify(template, null, 2), '全题型真题导入模板.json', 'application/json;charset=utf-8')
}

function downloadConvertedPaper(paper, sourceName) {
  const baseName = sourceName.replace(/\.[^.]+$/, '').replace(/[\\/:*?"<>|]+/g, '_') || '真题'
  downloadFile(JSON.stringify(toTemplatePaper(paper), null, 2), `${baseName}-转换.json`, 'application/json;charset=utf-8')
}

function downloadAllConverted() {
  // 浏览器会分别保存每个文件，避免引入 ZIP 依赖，也不会丢失文件名。
  validPapers.value.forEach((paper, index) => {
    const source = previews.value.find(item => item.paper === paper)?.name || `真题-${index + 1}`
    window.setTimeout(() => downloadConvertedPaper(paper, source), index * 150)
  })
  ElMessage.success(`已开始下载 ${validPapers.value.length} 个 JSON 文件`)
}

function downloadFile(content, name, type) {
  const url = URL.createObjectURL(new Blob([content], { type }))
  const link = document.createElement('a')
  link.href = url
  link.download = name
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  // Some browsers start the download asynchronously; revoking immediately can
  // turn a valid Blob URL into a failed download.
  window.setTimeout(() => URL.revokeObjectURL(url), 1000)
}
</script>

<style scoped>
.import-form { display: grid; gap: 14px; }
.import-form label { display: grid; gap: 6px; color: #334155; font-size: 13px; font-weight: 600; }
.import-form input, .import-form select { width: 100%; min-height: 42px; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 10px; background: #fff; color: #1f2937; }
.file-field input { padding: 7px; }
.file-field small { color: #64748b; font-size: 12px; font-weight: 400; line-height: 1.5; }
.template-button { justify-self: start; border: 0; background: transparent; color: var(--xy-brand-700); padding: 0; font-weight: 600; cursor: pointer; }
.template-actions { display: flex; flex-wrap: wrap; gap: 14px; }
.preview-list { margin-top: 16px; display: grid; gap: 8px; max-height: 220px; overflow-y: auto; }
.preview-item { padding: 10px 12px; background: #f0fdf4; border-left: 3px solid #16a34a; display: grid; gap: 3px; }
.preview-item.error { background: #fef2f2; border-color: #dc2626; }
.preview-item span { color: #64748b; font-size: 12px; }
.export-button { justify-self: start; border: 1px solid #93c5fd; border-radius: 5px; padding: 5px 9px; background: var(--xy-brand-50); color: var(--xy-brand-700); font-size: 12px; cursor: pointer; }
.batch-export { justify-self: start; border: 1px solid var(--xy-brand-600); border-radius: 5px; padding: 7px 10px; background: var(--xy-brand-600); color: #fff; font-size: 12px; cursor: pointer; }
.secondary, .primary { min-height: 40px; padding: 0 16px; border-radius: 6px; font-weight: 600; cursor: pointer; }
.secondary { border: 1px solid #cbd5e1; background: #fff; color: #334155; margin-right: 8px; }
.primary { border: 1px solid var(--xy-brand-700); background: var(--xy-brand-700); color: #fff; }
.primary:disabled { opacity: .45; cursor: not-allowed; }
</style>
