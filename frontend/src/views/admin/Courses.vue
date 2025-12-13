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
      <el-table :data="courses" stripe @expand-change="handleExpandChange">
        <el-table-column type="expand">
          <template #default="scope">
            <div class="expand-content">
              <h4>课文列表</h4>
              <el-button
                size="small"
                type="primary"
                @click="goToTexts(scope.row.id)"
                style="margin-bottom: 10px"
              >
                前往课文管理
              </el-button>
              <div v-loading="loadingTexts[scope.row.id]">
                <el-table
                  v-if="courseTexts[scope.row.id] && courseTexts[scope.row.id].length > 0"
                  :data="courseTexts[scope.row.id]"
                  size="small"
                  style="width: 100%"
                >
                  <el-table-column prop="title" label="课文标题">
                    <template #default="textScope">
                      <el-link
                        type="primary"
                        @click="openTextReader(textScope.row)"
                        :underline="false"
                      >
                        {{ textScope.row.title }}
                      </el-link>
                    </template>
                  </el-table-column>
                  <el-table-column prop="contentType" label="类型" width="100">
                    <template #default="textScope">
                      <el-tag size="small" :type="getContentTypeTag(textScope.row.contentType).type">
                        {{ getContentTypeTag(textScope.row.contentType).label }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="difficulty" label="难度" width="80">
                    <template #default="textScope">
                      <el-tag size="small" type="info">{{ textScope.row.difficulty }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="orderNum" label="序号" width="80" />
                </el-table>
                <el-empty
                  v-else
                  description="暂无课文"
                  :image-size="60"
                />
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="课程名称">
          <template #default="scope">
            <el-link
              type="primary"
              @click="goToTexts(scope.row.id)"
              :underline="false"
            >
              {{ scope.row.name }}
            </el-link>
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="200">
          <template #default="scope">
            <el-button size="small" @click="editCourse(scope.row)">编辑</el-button>
            <el-button size="small" type="danger" @click="deleteCourse(scope.row)">删除</el-button>
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
          @size-change="loadCourses"
          @current-change="loadCourses"
        />
      </div>
    </el-card>

    <!-- 添加/编辑课程对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑课程' : '添加课程'"
      width="600px"
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

    <!-- 课文阅读器对话框 -->
    <el-dialog
      v-model="readerVisible"
      :title="currentText.title"
      width="80%"
      class="reader-dialog"
    >
      <div class="reader-container">
        <div class="reader-content">
          <p
            v-for="(paragraph, index) in getFormattedContent(currentText.content)"
            :key="index"
          >
            {{ paragraph }}
          </p>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { getCourseList, createCourse, updateCourse, deleteCourse as deleteCourseApi } from '@/api/admin'
import { getTextsByCourse } from '@/api/text'

const router = useRouter()

// 响应式数据
const courses = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const courseFormRef = ref()

// 课文相关
const courseTexts = ref({}) // 存储每个课程的课文列表
const loadingTexts = ref({}) // 加载状态
const readerVisible = ref(false) // 阅读器显示状态
const currentText = ref({}) // 当前查看的课文

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
    const response = await getCourseList(params)
    courses.value = response.data.records
    pagination.total = response.data.total
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
        ElMessage.error(error.message || '操作失败')
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
      ElMessage.error(error.message || '删除失败')
    }
  }
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 跳转到课文管理页面
const goToTexts = (courseId) => {
  router.push({
    path: '/admin/texts',
    query: { courseId: courseId }
  })
}

// 加载课程的课文列表（用于展开行）
const loadCourseTexts = async (courseId) => {
  if (courseTexts.value[courseId]) {
    return // 已加载过，不重复加载
  }

  loadingTexts.value[courseId] = true
  try {
    const res = await getTextsByCourse(courseId)
    courseTexts.value[courseId] = res.data.sort((a, b) => a.orderNum - b.orderNum)
  } catch (error) {
    ElMessage.error('加载课文列表失败')
    courseTexts.value[courseId] = []
  } finally {
    loadingTexts.value[courseId] = false
  }
}

// 打开课文阅读器
const openTextReader = (text) => {
  currentText.value = text
  readerVisible.value = true
}

// 格式化课文内容为段落
const getFormattedContent = (content) => {
  if (!content) return []
  return content.split('\n').filter(p => p.trim())
}

// 获取内容类型标签
const getContentTypeTag = (contentType) => {
  const types = {
    'ARTICLE': { label: '课文', type: 'primary' },
    'EXERCISE': { label: '练习题', type: 'success' },
    'VOCABULARY': { label: '词汇', type: 'warning' }
  }
  return types[contentType] || { label: '未知', type: 'info' }
}

// 处理表格展开事件
const handleExpandChange = (row, expandedRows) => {
  // 如果展开了这一行，则加载课文数据
  if (expandedRows.some(r => r.id === row.id)) {
    loadCourseTexts(row.id)
  }
}

// 生命周期
onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.courses-management {
  padding: 20px;
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

/* 展开内容样式 */
.expand-content {
  padding: 20px;
  background: #f5f7fa;
  border-radius: 4px;
}

.expand-content h4 {
  margin: 0 0 10px 0;
  color: #303133;
}

/* 阅读器样式 */
.reader-dialog :deep(.el-dialog__body) {
  padding: 20px;
}

.reader-container {
  min-height: 400px;
  background: #fff;
}

.reader-content {
  line-height: 2;
  font-size: 16px;
}

.reader-content p {
  margin-bottom: 15px;
  text-indent: 2em;
}
</style>