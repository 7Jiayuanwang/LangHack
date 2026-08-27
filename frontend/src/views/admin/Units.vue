<template>
  <div class="units-management">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>
            单元管理
            <el-tag v-if="courseName" type="info" style="margin-left: 10px">{{ courseName }}</el-tag>
          </span>
          <div>
            <el-button v-if="courseId" @click="backToCourses">返回课程列表</el-button>
            <el-button type="primary" @click="showAddDialog">添加单元</el-button>
          </div>
        </div>
      </template>

      <!-- 课程选择 -->
      <div class="course-selector" v-if="!courseId">
        <el-select v-model="selectedCourseId" placeholder="请选择课程" @change="loadUnits" style="width: 300px">
          <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
        </el-select>
      </div>

      <!-- 单元列表 -->
      <div class="table-wrapper">
      <el-table :data="units" stripe>
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="单元名称" />
        <el-table-column prop="description" label="单元描述" show-overflow-tooltip />
        <el-table-column prop="orderNum" label="排序" width="80" />
        <el-table-column label="内容统计" width="200">
          <template #default="scope">
            <el-tag type="info">单词: {{ scope.row.wordCount || 0 }}</el-tag>
            <el-tag type="success" style="margin-left: 5px">课文: {{ scope.row.textCount || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="scope">
            {{ formatTime(scope.row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="scope">
            <div class="action-buttons">
              <el-button 
                size="small" 
                type="primary" 
                :icon="Collection" 
                @click="manageWords(scope.row)"
                title="管理单词"
              >
                单词
              </el-button>
              <el-button 
                size="small" 
                type="success" 
                :icon="Document" 
                @click="showAddTextDialog(scope.row)"
                title="添加课文"
              >
                添加课文
              </el-button>
              <el-button 
                size="small" 
                type="primary" 
                :icon="Document" 
                @click="manageTexts(scope.row)"
                title="管理课文"
              >
                课文
              </el-button>
              <el-dropdown trigger="click" @command="(cmd) => handleDropdownCommand(cmd, scope.row)">
                <el-button size="small" :icon="MoreFilled" circle />
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item :icon="Edit" command="edit">编辑单元</el-dropdown-item>
                    <el-dropdown-item :icon="Delete" command="delete" divided>删除单元</el-dropdown-item>
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
          @size-change="loadUnits"
          @current-change="loadUnits"
        />
      </div>
    </el-card>

    <!-- 添加/编辑单元对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑单元' : '添加单元'" width="min(500px, 90%)" @close="resetUnitForm">
      <el-form ref="unitFormRef" :model="unitForm" :rules="unitRules" label-width="80px">
        <el-form-item label="单元名称" prop="name">
          <el-input v-model="unitForm.name" placeholder="请输入单元名称" />
        </el-form-item>
        <el-form-item label="课程" prop="courseId" v-if="!courseId">
          <el-select v-model="unitForm.courseId" placeholder="请选择课程" style="width: 100%">
            <el-option v-for="course in courses" :key="course.id" :label="course.name" :value="course.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="单元描述" prop="description">
          <el-input v-model="unitForm.description" type="textarea" :rows="3" placeholder="请输入单元描述" />
        </el-form-item>
        <el-form-item label="排序" prop="orderNum">
          <el-input-number v-model="unitForm.orderNum" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveUnit" :loading="saving">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 添加/编辑课文对话框 -->
    <TextEditDialog
      v-model="textDialogVisible"
      :text-data="selectedText"
      :unit-id="currentUnitId"
      @success="loadUnits"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Collection, Document, MoreFilled, Edit, Delete } from '@element-plus/icons-vue'
import request from '@/utils/request'
import TextEditDialog from '@/components/TextEditDialog.vue'

// 响应式数据
const units = ref([])
const courses = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const saving = ref(false)
const unitFormRef = ref()
const router = useRouter()
const route = useRoute()
const textDialogVisible = ref(false)
const selectedText = ref(null)
const currentUnitId = ref(null)

// 从路由参数获取课程信息
const courseId = ref(route.query.courseId ? Number(route.query.courseId) : null)
const courseName = ref(route.query.courseName || '')

// 课程选择
const selectedCourseId = ref(null)

// 分页
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0
})

// 单元表单
const unitForm = reactive({
  id: null,
  name: '',
  courseId: courseId.value || null,
  description: '',
  orderNum: 0
})

// 表单验证规则
const unitRules = {
  name: [
    { required: true, message: '请输入单元名称', trigger: 'blur' },
    { min: 2, max: 100, message: '单元名称长度在 2 到 100 个字符', trigger: 'blur' }
  ],
  courseId: [
    { required: true, message: '请选择课程', trigger: 'change' }
  ]
}

// 方法
const loadCourses = async () => {
  try {
    const response = await request({
      url: '/api/admin/courses',
      method: 'get'
    })
    if (response.code === 200) {
      courses.value = Array.isArray(response.data?.records)
        ? response.data.records
        : (Array.isArray(response.data) ? response.data : [])
    }
  } catch (error) {
    ElMessage.error('加载课程列表失败')
  }
}

const loadUnits = async () => {
  try {
    const params = {
      page: pagination.current,
      size: pagination.size
    }

    // 如果有选择的课程ID或者URL中的课程ID
    const currentCourseId = selectedCourseId.value || courseId.value
    if (currentCourseId) {
      params.courseId = currentCourseId
    }

    const response = await request({
      url: '/api/admin/units',
      method: 'get',
      params
    })

    if (response.code === 200) {
      units.value = Array.isArray(response.data?.records) ? response.data.records : []
      pagination.total = response.data?.total || 0
    }
  } catch (error) {
    ElMessage.error('加载单元列表失败')
  }
}

const backToCourses = () => {
  router.push('/admin/courses')
}

const showAddDialog = () => {
  isEdit.value = false
  dialogVisible.value = true
  resetUnitForm()
}

const editUnit = (unit) => {
  isEdit.value = true
  unitFormRef.value?.clearValidate()
  dialogVisible.value = true
  Object.assign(unitForm, {
    id: unit.id,
    name: unit.name,
    courseId: unit.courseId,
    description: unit.description,
    orderNum: unit.orderNum
  })
}

const resetUnitForm = () => {
  Object.assign(unitForm, {
    id: null,
    name: '',
    courseId: courseId.value || null,
    description: '',
    orderNum: 0
  })
}

const saveUnit = async () => {
  if (!unitFormRef.value) return

  await unitFormRef.value.validate(async (valid) => {
    if (valid) {
      saving.value = true
      try {
        let response
        if (isEdit.value) {
          response = await request({
            url: `/api/admin/units/${unitForm.id}`,
            method: 'put',
            data: unitForm
          })
        } else {
          response = await request({
            url: '/api/admin/units',
            method: 'post',
            data: unitForm
          })
        }

        if (response.code === 200) {
          ElMessage.success(isEdit.value ? '单元更新成功' : '单元创建成功')
          dialogVisible.value = false
          loadUnits()
        } else {
          ElMessage.error(response.message || '操作失败')
        }
      } catch (error) {
        ElMessage.error('操作失败')
      } finally {
        saving.value = false
      }
    }
  })
}

// 处理下拉菜单命令
const handleDropdownCommand = (command, row) => {
  switch (command) {
    case 'edit':
      editUnit(row)
      break
    case 'delete':
      deleteUnit(row)
      break
  }
}

const deleteUnit = async (unit) => {
  try {
    await ElMessageBox.confirm(`确认删除单元 "${unit.name}" 吗？`, '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })

    const response = await request({
      url: `/api/admin/units/${unit.id}`,
      method: 'delete'
    })

    if (response.code === 200) {
      ElMessage.success('单元删除成功')
      loadUnits()
    } else {
      ElMessage.error(response.message || '删除失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

const manageWords = (unit) => {
  router.push({
    path: `/admin/units/${unit.id}/words`,
    query: {
      unitName: unit.name,
      courseId: unit.courseId,
      courseName: courseName.value
    }
  })
}

const manageTexts = (unit) => {
  router.push({
    path: `/admin/units/${unit.id}/texts`,
    query: {
      unitName: unit.name,
      courseId: unit.courseId,
      courseName: courseName.value
    }
  })
}

const showAddTextDialog = (unit) => {
  currentUnitId.value = unit.id
  selectedText.value = null
  textDialogVisible.value = true
}

const formatTime = (time) => {
  if (!time) return ''
  return new Date(time).toLocaleString()
}

// 生命周期
onMounted(() => {
  loadCourses()
  loadUnits()
})
</script>

<style scoped>
.units-management {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .units-management {
    padding: 30px;
  }
  
  :deep(.el-card__header) {
    padding: 20px 24px;
    font-size: 18px;
    font-weight: 600;
  }

  :deep(.el-table) {
    font-size: 15px;
  }

  :deep(.el-table .cell) {
    line-height: 1.6;
    padding: 12px 0;
  }

  :deep(.el-button--small) {
    font-size: 13px;
    padding: 8px 15px;
  }

  .course-selector {
    margin-bottom: 25px;
    padding: 20px;
    background: #f8fafc;
    border-radius: 12px;
  }
}

.action-buttons {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: flex-start;
}

.action-buttons .el-button {
  margin: 0;
  transition: all 0.2s ease;
}

.action-buttons .el-button:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.table-wrapper {
  overflow-x: auto;
  -webkit-overflow-scrolling: touch;
  border-radius: 8px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.05);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-selector {
  margin-bottom: 20px;
}

.pagination {
  margin-top: 24px;
  display: flex;
  justify-content: center;
}

@media (max-width: 768px) {
  .units-management {
    padding: 12px;
  }

  .card-header {
    flex-direction: row;
    align-items: center;
  }

  .course-selector .el-select {
    width: 100% !important;
  }

  .action-buttons {
    flex-wrap: wrap;
    gap: 6px;
  }
}
</style>
