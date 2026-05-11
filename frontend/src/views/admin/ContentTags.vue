<template>
  <div class="content-tags-management">
    <div class="header">
      <h2>标签管理</h2>
      <div class="header-actions">
        <el-button type="primary" @click="showCreateDialog">
          <el-icon><Plus /></el-icon>
          新增标签
        </el-button>
        <el-button @click="loadTags">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="tags" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="标签名称" min-width="160">
        <template #default="{ row }">
          <el-tag :color="row.color || undefined" :style="row.color ? { color: '#fff', borderColor: row.color } : {}">
            {{ row.name }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="color" label="颜色" width="120">
        <template #default="{ row }">
          <div v-if="row.color" class="color-cell">
            <span class="color-dot" :style="{ background: row.color }"></span>
            {{ row.color }}
          </div>
          <span v-else class="text-muted">默认</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">
          {{ formatTime(row.createdTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" size="small" text @click="editTag(row)">编辑</el-button>
          <el-button type="danger" size="small" text @click="deleteTag(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 创建/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingTag ? '编辑标签' : '新增标签'"
      width="min(460px, 92vw)"
      :close-on-click-modal="false"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入标签名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="标签颜色">
          <el-color-picker v-model="form.color" />
          <span v-if="form.color" class="color-preview">{{ form.color }}</span>
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
          <div class="form-tip">数值越小排越前</div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ editingTag ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { getContentTags, createContentTag, updateContentTag, deleteContentTag } from '@/api/admin'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const editingTag = ref(null)
const tags = ref([])
const formRef = ref()

const form = reactive({
  name: '',
  color: '',
  sortOrder: 0
})

const rules = {
  name: [
    { required: true, message: '请输入标签名称', trigger: 'blur' },
    { min: 1, max: 50, message: '标签名称不能超过50个字符', trigger: 'blur' }
  ]
}

const loadTags = async () => {
  loading.value = true
  try {
    const res = await getContentTags()
    if (res.code === 200) {
      tags.value = res.data || []
    }
  } catch (e) {
    ElMessage.error('加载标签失败')
  } finally {
    loading.value = false
  }
}

const showCreateDialog = () => {
  editingTag.value = null
  Object.assign(form, { name: '', color: '', sortOrder: 0 })
  dialogVisible.value = true
}

const editTag = (tag) => {
  editingTag.value = tag
  Object.assign(form, {
    name: tag.name,
    color: tag.color || '',
    sortOrder: tag.sortOrder || 0
  })
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch { return }

  submitLoading.value = true
  try {
    const data = { name: form.name, color: form.color || null, sortOrder: form.sortOrder }

    if (editingTag.value) {
      const res = await updateContentTag(editingTag.value.id, data)
      if (res.code === 200) {
        ElMessage.success('更新成功')
        dialogVisible.value = false
        loadTags()
      } else {
        ElMessage.error(res.message || '更新失败')
      }
    } else {
      const res = await createContentTag(data)
      if (res.code === 200) {
        ElMessage.success('创建成功')
        dialogVisible.value = false
        loadTags()
      } else {
        ElMessage.error(res.message || '创建失败')
      }
    }
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    submitLoading.value = false
  }
}

const deleteTag = (tag) => {
  ElMessageBox.confirm(`确定删除标签「${tag.name}」？删除后学生将无法再选择此标签。`, '删除确认', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      const res = await deleteContentTag(tag.id)
      if (res.code === 200) {
        ElMessage.success('删除成功')
        loadTags()
      } else {
        ElMessage.error(res.message || '删除失败')
      }
    } catch (e) {
      ElMessage.error('删除失败')
    }
  }).catch(() => {})
}

const resetForm = () => {
  editingTag.value = null
  formRef.value?.clearValidate()
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

onMounted(() => {
  loadTags()
})

defineExpose({ loadTags })
</script>

<style scoped>
.content-tags-management {
  padding: 20px;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header h2 {
  margin: 0;
  color: #303133;
  font-weight: 500;
  font-size: 16px;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.color-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: #606266;
}

.color-dot {
  width: 16px;
  height: 16px;
  border-radius: 50%;
  display: inline-block;
  border: 1px solid rgba(0,0,0,0.1);
}

.text-muted {
  color: #c0c4cc;
  font-size: 13px;
}

.color-preview {
  margin-left: 12px;
  font-size: 13px;
  color: #909399;
}

.form-tip {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
</style>
