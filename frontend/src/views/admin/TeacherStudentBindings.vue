<template>
  <div class="teacher-student-bindings">
    <div class="page-header">
      <h2>师生绑定关系管理</h2>
      <p class="page-desc">管理按学院链式拉取产生的师生绑定关系</p>
    </div>

    <!-- 筛选器 -->
    <div class="filters">
      <el-select v-model="filters.college" placeholder="选择学院" clearable style="width: 200px" @change="loadBindings">
        <el-option label="全部学院" value="" />
        <el-option v-for="c in colleges" :key="c" :label="c" :value="c" />
      </el-select>
      <el-input v-model="filters.teacherId" placeholder="教师工号" clearable style="width: 150px" @change="loadBindings" />
      <el-input v-model="filters.studentId" placeholder="学号" clearable style="width: 150px" @change="loadBindings" />
      <el-button type="primary" @click="loadBindings">查询</el-button>
      <el-button :disabled="selectedIds.length === 0" @click="handleBatchDelete">
        批量删除 ({{ selectedIds.length }})
      </el-button>
    </div>

    <!-- 数据表格 -->
    <el-table
      v-loading="loading"
      :data="bindings"
      style="width: 100%"
      @selection-change="handleSelectionChange"
    >
      <el-table-column type="selection" width="55" />
      <el-table-column prop="collegeName" label="学院" width="150" />
      <el-table-column prop="teacherId" label="教师工号" width="120" />
      <el-table-column label="教师姓名" width="100">
        <template #default="{ row }">
          <span v-if="row.teacherName">{{ row.teacherName }}</span>
          <el-tooltip v-else content="CDSP 端未提供教师姓名，显示工号" placement="top">
            <span class="text-muted">{{ row.teacherId }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="studentId" label="学号" width="140" />
      <el-table-column label="学生姓名" width="100">
        <template #default="{ row }">
          <span v-if="row.studentName">{{ row.studentName }}</span>
          <el-tooltip v-else content="CDSP 端未提供学生姓名，显示学号" placement="top">
            <span class="text-muted">{{ row.studentId }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="courseCode" label="课程代码" width="120" />
      <el-table-column prop="courseName" label="课程名称" min-width="150" />
      <el-table-column prop="academicYear" label="学年" width="80" />
      <el-table-column prop="semester" label="学期" width="80">
        <template #default="{ row }">
          {{ row.semester === 1 ? '秋季' : row.semester === 2 ? '春季' : row.semester }}
        </template>
      </el-table-column>
      <el-table-column prop="sourceType" label="来源" width="100" />
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button type="danger" size="small" link @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.size"
        :total="pagination.total"
        :page-sizes="[20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        @current-change="loadBindings"
        @size-change="loadBindings"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const loading = ref(false)
const bindings = ref([])
const colleges = ref([])
const selectedIds = ref([])

const filters = ref({
  college: '',
  teacherId: '',
  studentId: ''
})

const pagination = ref({
  page: 1,
  size: 20,
  total: 0
})

// 加载绑定关系列表
async function loadBindings() {
  loading.value = true
  try {
    const res = await request({
      url: '/api/admin/teacher-student-bindings',
      method: 'get',
      params: {
        page: pagination.value.page,
        size: pagination.value.size,
        college: filters.value.college,
        teacherId: filters.value.teacherId,
        studentId: filters.value.studentId
      }
    })
    bindings.value = res.data?.records || []
    pagination.value.total = res.data?.total || 0
  } catch (e) {
    console.error('加载师生绑定关系失败:', e)
    ElMessage.error('加载数据失败')
  } finally {
    loading.value = false
  }
}

// 加载学院列表
async function loadColleges() {
  try {
    const res = await request({
      url: '/api/admin/teacher-student-bindings/colleges',
      method: 'get'
    })
    colleges.value = res.data || []
  } catch (e) {
    console.error('加载学院列表失败:', e)
  }
}

// 选择变化
function handleSelectionChange(selection) {
  selectedIds.value = selection.map(item => item.id)
}

// 删除单条
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确定删除 ${row.teacherName} - ${row.studentName} 的绑定关系吗？`,
      '确认删除',
      { type: 'warning' }
    )
    await request({
      url: `/api/admin/teacher-student-bindings/${row.id}`,
      method: 'delete'
    })
    ElMessage.success('删除成功')
    loadBindings()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('删除失败:', e)
      ElMessage.error('删除失败')
    }
  }
}

// 批量删除
async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedIds.value.length} 条绑定关系吗？`,
      '确认批量删除',
      { type: 'warning' }
    )
    await request({
      url: '/api/admin/teacher-student-bindings/batch-delete',
      method: 'post',
      data: { ids: selectedIds.value }
    })
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadBindings()
  } catch (e) {
    if (e !== 'cancel') {
      console.error('批量删除失败:', e)
      ElMessage.error('批量删除失败')
    }
  }
}

onMounted(() => {
  loadColleges()
  loadBindings()
})
</script>

<style scoped>
.teacher-student-bindings {
  padding: 20px;
}

.text-muted {
  color: #94a3b8;
  font-style: italic;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 600;
}

.page-desc {
  margin: 0;
  font-size: 14px;
  color: var(--text-tertiary);
}

.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .teacher-student-bindings {
    padding: 12px;
  }

  .filters {
    flex-direction: column;
  }

  .filters > * {
    width: 100% !important;
  }
}
</style>
