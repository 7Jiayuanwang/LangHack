<template>
  <div class="notification-management" v-loading="loading">
    <div class="header-bar">
      <h2>通知管理</h2>
      <el-button type="primary" @click="openCreateDialog">发布通知</el-button>
    </div>

    <el-table :data="notifications" stripe size="small" class="notification-table">
      <el-table-column prop="title" label="标题" min-width="200" />
      <el-table-column prop="targetRole" label="目标" width="120">
        <template #default="{ row }">
          <el-tag :type="targetRoleType(row.targetRole)" size="small">{{ targetRoleLabel(row.targetRole) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.isActive ? 'success' : 'info'" size="small">{{ row.isActive ? '生效' : '停用' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdByName" label="创建者" width="100" />
      <el-table-column prop="createdAt" label="创建时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
          <el-button link :type="row.isActive ? 'warning' : 'success'" size="small" @click="toggleActive(row)">
            {{ row.isActive ? '停用' : '启用' }}
          </el-button>
          <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- Create/Edit Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑通知' : '发布通知'"
      width="640px"
      destroy-on-close
    >
      <el-form :model="form" label-width="100px" ref="formRef" :rules="formRules">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入通知标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="目标对象" prop="targetRole">
          <el-select v-model="form.targetRole" placeholder="选择目标对象">
            <el-option label="全部（教师+学生）" value="ALL" />
            <el-option label="仅教师" value="TEACHER" />
            <el-option label="仅学生" value="STUDENT" />
          </el-select>
        </el-form-item>
        <el-form-item label="通知内容" prop="content">
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            placeholder="请输入通知内容"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">{{ isEdit ? '保存' : '发布' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getAdminNotifications,
  createNotification,
  updateNotification,
  deleteNotification
} from '@/api/notification'

const loading = ref(false)
const submitting = ref(false)
const notifications = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const editId = ref(null)

const form = ref({
  title: '',
  content: '',
  targetRole: 'ALL'
})

const formRules = {
  title: [{ required: true, message: '请输入通知标题', trigger: 'blur' }],
  content: [{ required: true, message: '请输入通知内容', trigger: 'blur' }],
  targetRole: [{ required: true, message: '请选择目标对象', trigger: 'change' }]
}

const formRef = ref(null)

function targetRoleLabel(role) {
  const map = { ALL: '全部', TEACHER: '教师', STUDENT: '学生' }
  return map[role] || role
}

function targetRoleType(role) {
  const map = { ALL: '', TEACHER: 'warning', STUDENT: 'success' }
  return map[role] || 'info'
}

async function fetchNotifications() {
  loading.value = true
  try {
    const res = await getAdminNotifications()
    if (res.code === 200) {
      notifications.value = res.data || []
    }
  } catch (e) {
    ElMessage.error('获取通知列表失败')
  } finally {
    loading.value = false
  }
}

function openCreateDialog() {
  isEdit.value = false
  editId.value = null
  form.value = { title: '', content: '', targetRole: 'ALL' }
  dialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editId.value = row.id
  form.value = {
    title: row.title,
    content: row.content,
    targetRole: row.targetRole
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()
  submitting.value = true
  try {
    if (isEdit.value) {
      await updateNotification(editId.value, form.value)
      ElMessage.success('更新成功')
    } else {
      await createNotification(form.value)
      ElMessage.success('发布成功')
    }
    dialogVisible.value = false
    fetchNotifications()
  } catch (e) {
    ElMessage.error(isEdit.value ? '更新失败' : '发布失败')
  } finally {
    submitting.value = false
  }
}

async function toggleActive(row) {
  try {
    await updateNotification(row.id, { ...row, isActive: !row.isActive })
    ElMessage.success(row.isActive ? '已停用' : '已启用')
    fetchNotifications()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确定删除该通知？删除后不可恢复。', '确认删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteNotification(row.id)
    ElMessage.success('删除成功')
    fetchNotifications()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  fetchNotifications()
})
</script>

<style scoped>
.notification-management {
  padding: 20px;
}

.header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}

.header-bar h2 {
  margin: 0;
  font-size: 20px;
  color: #1a237e;
}

.notification-table {
  width: 100%;
}
</style>
