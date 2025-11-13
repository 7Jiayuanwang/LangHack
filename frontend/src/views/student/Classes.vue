<template>
  <div class="student-classes">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>我的班级</span>
          <el-button type="primary" @click="showJoinDialog">加入班级</el-button>
        </div>
      </template>

      <!-- 班级列表 -->
      <el-table v-loading="loading" :data="classes" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="班级名称" min-width="150" />
        <el-table-column label="所属课程" min-width="150">
          <template #default="{ row }">
            {{ row.course?.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="任课教师" min-width="120">
          <template #default="{ row }">
            {{ row.teacher?.nickname || row.teacher?.realName || row.teacher?.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="班级描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="joinTime" label="加入时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.joinTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="班级创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" size="small" @click="leaveClass(row)">退出班级</el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 空状态 -->
      <el-empty v-if="classes.length === 0" description="暂未加入任何班级">
        <el-button type="primary" @click="showJoinDialog">加入班级</el-button>
      </el-empty>
    </el-card>

    <!-- 加入班级对话框 -->
    <el-dialog
      v-model="joinDialogVisible"
      title="加入班级"
      width="500px"
      @close="resetJoinForm"
    >
      <el-form ref="joinFormRef" :model="joinForm" :rules="joinFormRules" label-width="100px">
        <el-form-item label="班级ID" prop="classId">
          <el-input
            v-model="joinForm.classId"
            placeholder="请输入班级ID"
            type="number"
          />
        </el-form-item>
        <el-form-item>
          <el-alert
            title="提示"
            type="info"
            description="班级ID由任课教师提供，请联系教师获取正确的班级ID。"
            show-icon
            :closable="false"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="joinDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="joining" @click="submitJoinForm">
          加入班级
        </el-button>
      </template>
    </el-dialog>

    <!-- 可加入班级列表对话框 -->
    <el-dialog
      v-model="availableClassesVisible"
      title="可加入的班级"
      width="800px"
    >
      <el-input
        v-model="searchKeyword"
        placeholder="搜索班级名称或描述"
        style="margin-bottom: 20px"
        @keyup.enter="loadAvailableClasses"
        clearable
      >
        <template #append>
          <el-button @click="loadAvailableClasses">搜索</el-button>
        </template>
      </el-input>

      <el-table v-loading="availableLoading" :data="availableClasses" stripe>
        <el-table-column prop="id" label="班级ID" width="80" />
        <el-table-column prop="name" label="班级名称" min-width="150" />
        <el-table-column label="所属课程" min-width="150">
          <template #default="{ row }">
            {{ row.course?.name || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="任课教师" min-width="120">
          <template #default="{ row }">
            {{ row.teacher?.nickname || row.teacher?.realName || row.teacher?.username || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="description" label="班级描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatDateTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button
              type="primary"
              size="small"
              @click="quickJoinClass(row)"
              :disabled="isJoinedClass(row.id)"
            >
              {{ isJoinedClass(row.id) ? '已加入' : '加入' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="availableClasses.length === 0" description="暂无可加入的班级">
        <el-button type="primary" @click="showJoinDialog">输入班级ID加入</el-button>
      </el-empty>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

// 数据状态
const loading = ref(false)
const classes = ref([])
const availableClasses = ref([])
const availableLoading = ref(false)
const joinDialogVisible = ref(false)
const availableClassesVisible = ref(false)
const joining = ref(false)
const searchKeyword = ref('')

// 表单
const joinForm = reactive({
  classId: null
})

const joinFormRef = ref(null)

// 表单验证规则
const joinFormRules = {
  classId: [
    { required: true, message: '请输入班级ID', trigger: 'blur' },
    { type: 'number', message: '班级ID必须是数字', trigger: 'blur' }
  ]
}

// 加载学生班级列表
const loadClasses = async () => {
  loading.value = true
  try {
    const response = await fetch('/api/classes/student/me', {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        classes.value = data.data
      } else {
        ElMessage.error(data.message || '加载班级列表失败')
      }
    } else {
      ElMessage.error('加载班级列表失败')
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
    ElMessage.error('加载班级列表失败')
  } finally {
    loading.value = false
  }
}

// 加载可加入的班级列表
const loadAvailableClasses = async () => {
  availableLoading.value = true
  try {
    const params = new URLSearchParams({
      size: '100'
    })

    if (searchKeyword.value) {
      params.append('keyword', searchKeyword.value)
    }

    const response = await fetch(`/api/classes/search?${params.toString()}`, {
      headers: {
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      }
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        // 过滤掉已加入的班级
        const joinedClassIds = classes.value.map(c => c.id)
        availableClasses.value = data.data.filter(cls => !joinedClassIds.includes(cls.id))
      } else {
        ElMessage.error(data.message || '加载班级列表失败')
      }
    } else {
      ElMessage.error('加载班级列表失败')
    }
  } catch (error) {
    console.error('加载班级列表失败:', error)
    ElMessage.error('加载班级列表失败')
  } finally {
    availableLoading.value = false
  }
}

// 显示加入班级对话框
const showJoinDialog = () => {
  joinDialogVisible.value = true
}

// 显示可加入班级列表
const showAvailableClasses = () => {
  availableClassesVisible.value = true
  loadAvailableClasses()
}

// 提交加入班级表单
const submitJoinForm = async () => {
  if (!joinFormRef.value) return

  try {
    await joinFormRef.value.validate()
    joining.value = true

    const response = await fetch('/api/classes/join', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({
        classId: joinForm.classId
      })
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('成功加入班级')
        joinDialogVisible.value = false
        loadClasses()
      } else {
        ElMessage.error(data.message || '加入班级失败')
      }
    } else {
      ElMessage.error('加入班级失败')
    }
  } catch (error) {
    console.error('加入班级失败:', error)
    ElMessage.error('加入班级失败')
  } finally {
    joining.value = false
  }
}

// 快速加入班级
const quickJoinClass = async (classInfo) => {
  if (isJoinedClass(classInfo.id)) {
    return
  }

  try {
    const response = await fetch('/api/classes/join', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({
        classId: classInfo.id
      })
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('成功加入班级')
        loadClasses()
        loadAvailableClasses()
      } else {
        ElMessage.error(data.message || '加入班级失败')
      }
    } else {
      ElMessage.error('加入班级失败')
    }
  } catch (error) {
    console.error('加入班级失败:', error)
    ElMessage.error('加入班级失败')
  }
}

// 退出班级
const leaveClass = async (classInfo) => {
  try {
    await ElMessageBox.confirm(
      `确定要退出班级"${classInfo.name}"吗？退出后需要重新申请加入。`,
      '退出确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )

    const response = await fetch('/api/classes/leave', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('token')}`
      },
      body: JSON.stringify({
        classId: classInfo.id
      })
    })

    if (response.ok) {
      const data = await response.json()
      if (data.code === 200) {
        ElMessage.success('已退出班级')
        loadClasses()
      } else {
        ElMessage.error(data.message || '退出班级失败')
      }
    } else {
      ElMessage.error('退出班级失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('退出班级失败:', error)
      ElMessage.error('退出班级失败')
    }
  }
}

// 检查是否已加入班级
const isJoinedClass = (classId) => {
  return classes.value.some(c => c.id === classId)
}

// 重置加入表单
const resetJoinForm = () => {
  Object.assign(joinForm, {
    classId: null
  })
  if (joinFormRef.value) {
    joinFormRef.value.resetFields()
  }
}

// 格式化日期时间
const formatDateTime = (dateTime) => {
  if (!dateTime) return '-'
  return new Date(dateTime).toLocaleString('zh-CN')
}

// 初始化
onMounted(() => {
  loadClasses()
})
</script>

<style scoped>
.student-classes {
  padding: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>