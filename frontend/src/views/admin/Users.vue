<template>
  <div class="users-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>用户管理</span>
          <div>
            <el-button type="success" @click="handleExport">
              <el-icon><Download /></el-icon>
              导出用户
            </el-button>
            <el-button type="primary" @click="showAddDialog">
              <el-icon><Plus /></el-icon>
              添加用户
            </el-button>
          </div>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="角色">
            <el-select v-model="searchForm.role" placeholder="请选择" clearable style="width: 120px">
              <el-option label="管理员" value="ADMIN" />
              <el-option label="教师" value="TEACHER" />
              <el-option label="学生" value="STUDENT" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择" clearable style="width: 120px">
              <el-option label="正常" value="0" />
              <el-option label="禁用" value="1" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="searchForm.keyword" placeholder="用户名/姓名/邮箱" clearable style="width: 200px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadUsers">
              <el-icon><Search /></el-icon>
              搜索
            </el-button>
            <el-button @click="resetSearch">
              <el-icon><Refresh /></el-icon>
              重置
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 批量操作 -->
      <div class="batch-actions" v-if="selectedUsers.length > 0">
        <el-alert
          :title="`已选择 ${selectedUsers.length} 个用户`"
          type="info"
          :closable="false"
          style="margin-bottom: 15px"
        >
          <template #default>
            <el-button size="small" type="danger" @click="batchDelete">批量删除</el-button>
            <el-button size="small" type="warning" @click="batchResetPassword">批量重置密码</el-button>
          </template>
        </el-alert>
      </div>

      <!-- 用户列表 -->
      <el-table
        :data="users"
        stripe
        v-loading="loading"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" sortable />
        <el-table-column prop="username" label="用户名" width="150" />
        <el-table-column prop="realName" label="真实姓名" width="120" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column prop="phone" label="手机号" width="130" />
        <el-table-column prop="role" label="角色" width="100">
          <template #default="scope">
            <el-tag :type="getRoleType(scope.row.role)">
              {{ getRoleText(scope.row.role) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="deleted" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.deleted === 0 ? 'success' : 'danger'">
              {{ scope.row.deleted === 0 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="scope">
            <el-button size="small" type="primary" @click="editUser(scope.row)">编辑</el-button>
            <el-button size="small" type="warning" @click="resetPassword(scope.row)">重置密码</el-button>
            <el-button
              size="small"
              :type="scope.row.deleted === 0 ? 'danger' : 'success'"
              @click="toggleUserStatus(scope.row)"
              :disabled="scope.row.id === 1"
            >
              {{ scope.row.deleted === 0 ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </el-card>

    <!-- 添加/编辑���户对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑用户' : '添加用户'"
      width="600px"
    >
      <el-form
        ref="userFormRef"
        :model="userForm"
        :rules="userRules"
        label-width="100px"
      >
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用户名" prop="username">
              <el-input v-model="userForm.username" :disabled="isEdit" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="真实姓名" prop="realName">
              <el-input v-model="userForm.realName" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="邮箱" prop="email">
              <el-input v-model="userForm.email" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="userForm.phone" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="角色" prop="role">
              <el-select v-model="userForm.role" style="width: 100%">
                <el-option label="管理员" value="ADMIN" />
                <el-option label="教师" value="TEACHER" />
                <el-option label="学生" value="STUDENT" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="deleted">
              <el-select v-model="userForm.deleted" style="width: 100%">
                <el-option label="正常" :value="0" />
                <el-option label="禁用" :value="1" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item :label="isEdit ? '新密码' : '密码'" prop="password">
          <el-input
            v-model="userForm.password"
            type="password"
            :placeholder="isEdit ? '留空则不修���密码' : '请输入密码'"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveUser" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Download, Search, Refresh } from '@element-plus/icons-vue'
import { getUsers, createUser, updateUser, deleteUser as deleteUserApi } from '@/api/admin'

// 响应式数据
const users = ref([])
const selectedUsers = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const loading = ref(false)
const userFormRef = ref()

// 搜索表单
const searchForm = reactive({
  role: '',
  status: '',
  keyword: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 用户表单
const userForm = reactive({
  id: null,
  username: '',
  password: '',
  realName: '',
  email: '',
  phone: '',
  role: 'STUDENT',
  deleted: 0
})

// 表单验证规则
const userRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 20, message: '用户名长度在 3 到 20 个字符', trigger: 'blur' }
  ],
  password: [
    { required: !isEdit.value, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 个字符', trigger: 'blur' }
  ],
  realName: [
    { required: true, message: '请输入真实姓名', trigger: 'blur' }
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: 'blur' }
  ],
  phone: [
    { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号', trigger: 'blur' }
  ],
  role: [
    { required: true, message: '请选择角色', trigger: 'change' }
  ]
}

// 方法
const loadUsers = async () => {
  loading.value = true
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      ...searchForm
    }
    const response = await getUsers(params)
    users.value = response.data.records || response.data
    pagination.total = response.data.total || users.value.length
  } catch (error) {
    ElMessage.error('加载用户列表失败')
  } finally {
    loading.value = false
  }
}

const resetSearch = () => {
  searchForm.role = ''
  searchForm.status = ''
  searchForm.keyword = ''
  pagination.current = 1
  loadUsers()
}

const handleSelectionChange = (selection) => {
  selectedUsers.value = selection
}

const showAddDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetUserForm()
}

const editUser = (user) => {
  isEdit.value = true
  dialogVisible.value = true
  Object.assign(userForm, {
    id: user.id,
    username: user.username,
    password: '',
    realName: user.realName,
    email: user.email,
    phone: user.phone,
    role: user.role,
    deleted: user.deleted
  })
}

const resetUserForm = () => {
  Object.assign(userForm, {
    id: null,
    username: '',
    password: '',
    realName: '',
    email: '',
    phone: '',
    role: 'STUDENT',
    deleted: 0
  })
}

const saveUser = async () => {
  if (!userFormRef.value) return

  await userFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        if (isEdit.value) {
          await updateUser(userForm.id, userForm)
          ElMessage.success('用户更新成功')
        } else {
          await createUser(userForm)
          ElMessage.success('用户创建成功')
        }
        dialogVisible.value = false
        loadUsers()
      } catch (error) {
        ElMessage.error(error.message || '操作失败')
      } finally {
        saving.value = false
      }
    }
  })
}

const toggleUserStatus = async (user) => {
  try {
    const action = user.deleted === 0 ? '禁用' : '启用'
    await ElMessageBox.confirm(`确认${action}用户 "${user.username}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await updateUser(user.id, { ...user, deleted: user.deleted === 0 ? 1 : 0 })
    ElMessage.success(`${action}成功`)
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error.message || '操作失败')
    }
  }
}

const resetPassword = async (user) => {
  try {
    await ElMessageBox.prompt('请输入新密码（至少6位）', '重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.{6,}/,
      inputErrorMessage: '密码至少6位'
    }).then(async ({ value }) => {
      await updateUser(user.id, { ...user, password: value })
      ElMessage.success('密码重置成功')
    })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('密码重置失败')
    }
  }
}

const batchDelete = async () => {
  try {
    await ElMessageBox.confirm(`确认删除选中的 ${selectedUsers.value.length} 个用户吗？`, '批量删除', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    for (const user of selectedUsers.value) {
      if (user.id !== 1) {
        await deleteUserApi(user.id)
      }
    }
    ElMessage.success('批量删除成功')
    loadUsers()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量删除失败')
    }
  }
}

const batchResetPassword = async () => {
  try {
    await ElMessageBox.prompt('请输入新密码（将应用到所有选中用户）', '批量重置密码', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /.{6,}/,
      inputErrorMessage: '密码至少6位'
    }).then(async ({ value }) => {
      for (const user of selectedUsers.value) {
        await updateUser(user.id, { ...user, password: value })
      }
      ElMessage.success('批量重置密码成功')
    })
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('批量重置密码失败')
    }
  }
}

const handleExport = () => {
  try {
    // 准备导出数据
    const exportData = users.value.map(user => ({
      '用户ID': user.id,
      '用户名': user.username,
      '真实姓名': user.realName,
      '邮箱': user.email,
      '手机号': user.phone,
      '角色': getRoleText(user.role),
      '状态': user.deleted === 0 ? '正常' : '禁用',
      '创建时间': formatTime(user.createdTime)
    }))

    // 转换为CSV
    const headers = Object.keys(exportData[0])
    const csvContent = [
      headers.join(','),
      ...exportData.map(row => headers.map(header => `"${row[header] || ''}"`).join(','))
    ].join('\n')

    // 创建Blob并下载
    const blob = new Blob(['\ufeff' + csvContent], { type: 'text/csv;charset=utf-8;' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = `用户列表_${new Date().getTime()}.csv`
    link.click()

    ElMessage.success('导出成功')
  } catch (error) {
    ElMessage.error('导出失败')
  }
}

const getRoleType = (role) => {
  const types = {
    'ADMIN': 'danger',
    'TEACHER': 'warning',
    'STUDENT': 'success'
  }
  return types[role] || ''
}

const getRoleText = (role) => {
  const texts = {
    'ADMIN': '管理员',
    'TEACHER': '教师',
    'STUDENT': '学生'
  }
  return texts[role] || role
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString('zh-CN')
}

// 生命周期
onMounted(() => {
  loadUsers()
})
</script>

<style scoped>
.users-management {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  margin-bottom: 20px;
}

.batch-actions {
  margin-bottom: 15px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}
</style>
