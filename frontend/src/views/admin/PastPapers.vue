<template>
  <div class="admin-papers-page">
    <header class="page-header">
      <div>
        <h2>真题题库管理</h2>
        <p>管理员导入并发布真题后，学生端会自动显示并可以作答。</p>
      </div>
      <el-button type="primary" @click="importVisible = true">
        <el-icon><Upload /></el-icon>
        导入真题
      </el-button>
    </header>

    <section class="workflow-note">
      <strong>导入一套完整真题</strong>
      <span>选择 MinerU 导出的 <code>full.md</code> 或全题型 JSON，再上传对应听力 MP3。原始题目 PDF 和答案 PDF 不能直接作为题目内容使用。</span>
    </section>

    <div class="category-tabs" role="tablist" aria-label="考试分类">
      <button
        v-for="category in examCategories"
        :key="category.key"
        type="button"
        role="tab"
        :aria-selected="selectedCategory === category.key"
        :class="{ active: selectedCategory === category.key }"
        @click="selectedCategory = category.key"
      >
        {{ category.label }}
        <span>{{ countByCategory(category.key) }}</span>
      </button>
    </div>

    <el-table v-loading="loading" :data="filteredPapers" stripe border empty-text="暂无已发布真题">
      <el-table-column prop="title" label="试卷名称" min-width="260" show-overflow-tooltip />
      <el-table-column prop="date" label="考试日期" width="130" />
      <el-table-column label="题目数" width="100" align="center">
        <template #default="{ row }">{{ row.questions?.length || 0 }}</template>
      </el-table-column>
      <el-table-column label="听力" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="hasListening(row) ? 'success' : 'info'" size="small">
            {{ hasListening(row) ? '已配置' : '未配置' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110" align="center">
        <template #default>
          <el-tag type="success" size="small">学生可用</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right" align="center">
        <template #default="{ row }">
          <el-button type="danger" link @click="removePaper(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <PastPaperImportDialog v-model="importVisible" @imported="loadPapers" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { examCategories } from '@/data/pastPapers'
import PastPaperImportDialog from '@/components/past-papers/PastPaperImportDialog.vue'
import { deleteSharedPastPaper, getSharedPastPapers } from '@/api/pastPapers'

const loading = ref(false)
const importVisible = ref(false)
const selectedCategory = ref('cet4')
const papers = ref([])

const filteredPapers = computed(() => papers.value.filter((paper) => paper.category === selectedCategory.value))

function countByCategory(category) {
  return papers.value.filter((paper) => paper.category === category).length
}

function hasListening(paper) {
  return Boolean(paper?.audioUrl || paper?.questions?.some((question) => question.type === 'listening' && question.audioUrl))
}

async function loadPapers() {
  loading.value = true
  try {
    const response = await getSharedPastPapers()
    papers.value = Array.isArray(response?.data) ? response.data : []
  } catch (error) {
    ElMessage.error(error.message || '加载真题列表失败')
  } finally {
    loading.value = false
  }
}

async function removePaper(paper) {
  try {
    await ElMessageBox.confirm(`确定删除“${paper.title}”吗？删除后学生端将无法继续使用。`, '删除真题', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSharedPastPaper(paper.id)
    await loadPapers()
    ElMessage.success('真题已删除')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.message || '删除失败')
  }
}

onMounted(loadPapers)
</script>

<style scoped>
.admin-papers-page { max-width: 1400px; margin: 0 auto; padding: 28px 32px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 20px; margin-bottom: 20px; }
.page-header h2 { margin: 0; color: #1f2937; font-size: 24px; }
.page-header p { margin: 8px 0 0; color: #64748b; font-size: 14px; }
.workflow-note { display: flex; flex-direction: column; gap: 6px; margin-bottom: 20px; padding: 14px 16px; border-left: 4px solid #2563eb; background: #eff6ff; color: #475569; font-size: 13px; line-height: 1.6; }
.workflow-note strong { color: #1e40af; font-size: 14px; }
.workflow-note code { padding: 1px 4px; border-radius: 3px; background: #dbeafe; color: #1e3a8a; }
.category-tabs { display: flex; gap: 8px; margin-bottom: 16px; border-bottom: 1px solid #e2e8f0; }
.category-tabs button { display: inline-flex; align-items: center; gap: 7px; padding: 10px 14px; border: 0; border-bottom: 2px solid transparent; background: transparent; color: #64748b; cursor: pointer; font-weight: 600; }
.category-tabs button.active { border-bottom-color: #2563eb; color: #1d4ed8; }
.category-tabs span { min-width: 20px; padding: 1px 5px; border-radius: 10px; background: #e2e8f0; color: #475569; font-size: 11px; text-align: center; }
.category-tabs button.active span { background: #dbeafe; color: #1d4ed8; }
@media (max-width: 768px) {
  .admin-papers-page { padding: 18px 16px; }
  .page-header { flex-direction: column; }
}
</style>
