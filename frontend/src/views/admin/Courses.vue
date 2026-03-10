<template>
  <div class="courses-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课程管理</span>
          <el-button type="primary" @click="showAddDialog">添加课程</el-button>
        </div>
      </template>

      <!-- 搜索栏 -->
      <div class="search-bar">
        <el-form :inline="true" :model="searchForm" class="search-form">
          <el-form-item label="状态">
            <el-select v-model="searchForm.status" placeholder="请选择" clearable>
              <el-option label="启用" value="ACTIVE" />
              <el-option label="禁用" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="关键词">
            <el-input v-model="searchForm.keyword" placeholder="课程名称/描述" clearable />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="loadCourses">搜索</el-button>
            <el-button @click="resetSearch">重置</el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 课程列表 -->
      <div class="table-wrapper">
      <el-table :data="courses" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="课程名称" />
        <el-table-column prop="description" label="课程描述" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ACTIVE' ? 'success' : 'danger'">
              {{ scope.row.status === 'ACTIVE' ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button 
                size="small" 
                type="primary" 
                :icon="Setting" 
                @click="manageUnits(scope.row)"
                title="管理单元"
              >
                单元
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleDropdownCommand(cmd, scope.row)">
                <el-button size="small" :icon="MoreFilled" circle />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :icon="Edit" command="edit">编辑课程</el-dropdown-item>
                    <el-dropdown-item :icon="Delete" command="delete" divided>删除课程</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </template>
        </el-table-column>
      </el-table>
      </div>

      <!-- 分页 -->
      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :total="pagination.total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadCourses"
          @current-change="loadCourses"
        />
      </div>
    </el-card>

    <!-- 添加/编辑课程对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑课程' : '添加课程'"
      width="min(600px, 90%)"
      @close="resetCourseForm"
    >
      <el-form
        ref="courseFormRef"
        :model="courseForm"
        :rules="courseRules"
        label-width="80px"
      >
        <el-form-item label="课程名称" prop="name">
          <el-input v-model="courseForm.name" />
        </el-form-item>
        <el-form-item label="课程描述" prop="description">
          <el-input
            v-model="courseForm.description"
            type="textarea"
            :rows="4"
            placeholder="请输入课程描述"
          />
        </el-form-item>
        <el-form-item label="封面图片" prop="coverImage">
          <el-input v-model="courseForm.coverImage" placeholder="图片URL" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="courseForm.status" style="width: 100%">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="INACTIVE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveCourse" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Setting, MoreFilled, Edit, Delete } from '@element-plus/icons-vue'
import { getCourses, createCourse, updateCourse, deleteCourse as deleteCourseApi } from '@/api/admin'

// 响应式数据
const courses = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const courseFormRef = ref()
const router = useRouter()

// 搜索表单
const searchForm = reactive({
  status: '',
  keyword: ''
})

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 课程表单
const courseForm = reactive({
  id: null,
  name: '',
  description: '',
  coverImage: '',
  status: 'ACTIVE'
})

// 表单验证规则
const courseRules = {
  name: [
    { required: true, message: '请输入课程名称', trigger: 'blur' },
    { min: 2, max: 100, message: '课程名称长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入课程描述', trigger: 'blur' },
    { max: 500, message: '课程描述不能超过 500 个字符', trigger: 'blur' }
  ],
  status: [
    { required: true, message: '请选择状态', trigger: 'change' }
  ]
}

// 方法
const loadCourses = async () => {
  try {
    const params = {
      current: pagination.current,
      size: pagination.size,
      ...searchForm
    }
    const response = await getCourses(params)
    courses.value = response.data?.records || []
    pagination.total = response.data?.total || 0
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const resetSearch = () => {
  searchForm.status = ''
  searchForm.keyword = ''
  pagination.current = 1
  loadCourses()
}

const showAddDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetCourseForm()
}

const editCourse = (course) => {
  isEdit.value = true
  dialogVisible.value = true
  Object.assign(courseForm, {
    id: course.id,
    name: course.name,
    description: course.description,
    coverImage: course.coverImage,
    status: course.status
  })
}

const resetCourseForm = () => {
  Object.assign(courseForm, {
    id: null,
    name: '',
    description: '',
    coverImage: '',
    status: 'ACTIVE'
  })
}

const saveCourse = async () => {
  if (!courseFormRef.value) return

  await courseFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        if (isEdit.value) {
          await updateCourse(courseForm.id, courseForm)
          ElMessage.success('课程更新成功')
        } else {
          await createCourse(courseForm)
          ElMessage.success('课程创建成功')
        }
        dialogVisible.value = false
        loadCourses()
      } catch (error) {
        ElMessage.error('操作失败，请稍后重试')
      } finally {
        saving.value = false
      }
    }
  })
}

const deleteCourse = async (course) => {
  try {
    await ElMessageBox.confirm(`确认删除课程 "${course.name}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    await deleteCourseApi(course.id)
    ElMessage.success('课程删除成功')
    loadCourses()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败，请稍后重试')
    }
  }
}

// 处理下拉菜单命令
const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'edit':
      editCourse(row)
      break
    case 'delete':
      deleteCourse(row)
      break
  }
}

const manageUnits = (course) => {
  // 跳转到单元管理页面，并传递课程ID
  router.push({
    path: '/admin/units',
    query: {
      courseId: course.id,
      courseName: course.name
    }
  })
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.action-buttons {
  display: flex;
  align-items: center;
  gap: 6px;
  justify-content: flex-start;
}

.action-buttons .el-button {
  margin: 0;
  padding: 5px 12px;
  font-size: 13px;
  border-radius: 4px;
  transition: all 0.2s ease;
}

.action-buttons .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.action-buttons .el-button--circle {
  width: 28px;
  height: 28px;
  padding: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-buttons .el-dropdown {
  margin-left: 2px;
}

.courses-management {
  padding: 20px;
}

.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.search-bar {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 20px;
  text-align: right;
}

@media (max-width: 768px) {
  .courses-management {
    padding: 12px;
  }

  .search-bar .el-form {
    display: block;
  }

  .search-bar .el-form-item {
    margin-right: 0;
    margin-bottom: 12px;
  }
}
</style>