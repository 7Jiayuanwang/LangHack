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
      <label class="file-field">选择文件
        <input type="file" accept=".md,.json,text/markdown,application/json" multiple @change="handleFiles">
      </label>
      <button class="template-button" type="button" @click="downloadTemplate">下载 Markdown 模板</button>
    </div>

    <div v-if="previews.length" class="preview-list" aria-live="polite">
      <div v-for="preview in previews" :key="preview.name" :class="['preview-item', { error: preview.error }]">
        <strong>{{ preview.name }}</strong>
        <span>{{ preview.error || `${preview.paper.title} · ${preview.paper.questions.length} 题` }}</span>
      </div>
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
import { parsePaperFile } from '@/utils/pastPaperImporter'
import { createSharedPastPaper } from '@/api/pastPapers'

const visible = defineModel({ type: Boolean, default: false })
const emit = defineEmits(['imported'])
const form = reactive({ category: 'cet4', title: '', duration: 30 })
const previews = ref([])
const importing = ref(false)
const validPapers = computed(() => previews.value.filter(item => item.paper).map(item => item.paper))

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

async function confirmImport() {
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
  const url = URL.createObjectURL(new Blob([template], { type: 'text/markdown;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = '真题导入模板.md'
  link.click()
  URL.revokeObjectURL(url)
}
</script>

<style scoped>
.import-form { display: grid; gap: 14px; }
.import-form label { display: grid; gap: 6px; color: #334155; font-size: 13px; font-weight: 600; }
.import-form input, .import-form select { width: 100%; min-height: 42px; box-sizing: border-box; border: 1px solid #cbd5e1; border-radius: 6px; padding: 8px 10px; background: #fff; color: #1f2937; }
.file-field input { padding: 7px; }
.template-button { justify-self: start; border: 0; background: transparent; color: #1d4ed8; padding: 0; font-weight: 600; cursor: pointer; }
.preview-list { margin-top: 16px; display: grid; gap: 8px; max-height: 220px; overflow-y: auto; }
.preview-item { padding: 10px 12px; background: #f0fdf4; border-left: 3px solid #16a34a; display: grid; gap: 3px; }
.preview-item.error { background: #fef2f2; border-color: #dc2626; }
.preview-item span { color: #64748b; font-size: 12px; }
.secondary, .primary { min-height: 40px; padding: 0 16px; border-radius: 6px; font-weight: 600; cursor: pointer; }
.secondary { border: 1px solid #cbd5e1; background: #fff; color: #334155; margin-right: 8px; }
.primary { border: 1px solid #1d4ed8; background: #1d4ed8; color: #fff; }
.primary:disabled { opacity: .45; cursor: not-allowed; }
</style>
