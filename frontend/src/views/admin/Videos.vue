<template>
  <div class="videos-management">
    <div class="header">
      <h2>视频管理</h2>
      <div class="actions">
        <el-button type="primary" @click="showAddDialog">
          <el-icon><Plus /></el-icon>
          添加视频
        </el-button>
        <el-button @click="loadVideos">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 搜索栏 -->
    <div class="search-bar">
      <el-form :inline="true" :model="searchForm" class="search-form">
        <el-form-item label="关键词">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索标题或描述"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
          <el-button @click="resetSearch">
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 统计信息 -->
    <div class="stats-cards">
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalVideos || 0 }}</div>
        <div class="stat-label">总视频数</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalFeatured || 0 }}</div>
        <div class="stat-label">推荐视频</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalPublic || 0 }}</div>
        <div class="stat-label">公开视频</div>
      </div>
      <div class="stat-card">
        <div class="stat-value">{{ stats.totalPending || 0 }}</div>
        <div class="stat-label">待审核</div>
      </div>
    </div>

    <!-- 视频列表 -->
    <div class="videos-table">
      <el-table
        v-loading="loading"
        :data="videos"
        stripe
        border
        style="width: 100%"
        empty-text="暂无视频数据"
      >
        <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
        <el-table-column prop="description" label="描述" min-width="250" show-overflow-tooltip>
          <template #default="{ row }">
            {{ getContentPreview(row.description) }}
          </template>
        </el-table-column>
        <el-table-column prop="tags" label="标签" width="150" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="isFeatured" label="推荐" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              :model-value="row.isFeatured === 1"
              @change="toggleFeatured(row, $event)"
              :loading="row._featuredLoading"
            />
          </template>
        </el-table-column>
        <el-table-column label="优先级" width="110" align="center">
          <template #default="{ row }">
            <el-input-number
              v-if="row.isFeatured === 1"
              v-model="row.featuredPriority"
              :min="0"
              :max="100"
              :step="1"
              size="small"
              controls-position="right"
              style="width: 90px"
              @change="(val) => updatePriority(row, val)"
            />
            <span v-else class="priority-na">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="viewCount" label="观看" width="80" align="center" />
        <el-table-column prop="createdTime" label="创建时间" width="180">
          <template #default="{ row }">
            {{ formatTime(row.createdTime) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="editVideo(row)">编辑</el-button>
            <el-button type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          v-model:current-page="pagination.current"
          v-model:page-size="pagination.size"
          :page-sizes="[10, 20, 50]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </div>

    <!-- 添加/编辑对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="editingVideo ? '编辑视频' : '添加视频'"
      width="min(600px, 94vw)"
      class="custom-video-dialog"
      @close="resetForm"
    >
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="80px">
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入视频标题" maxlength="200" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请输入视频描述" />
        </el-form-item>
        <el-form-item label="视频文件" prop="mediaId" v-if="!editingVideo">
          <el-upload
            class="video-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept="video/mp4,video/webm,video/ogg"
            :on-change="handleVideoFileChange"
          >
            <el-button type="primary">选择视频文件</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 MP4/WebM/OGG，最大 512MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
            class="cover-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept="image/jpeg,image/png,image/webp"
            :on-change="handleCoverFileChange"
          >
            <el-button>选择封面图</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 JPG/PNG/WebP，最大 10MB</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="英文字幕">
          <el-upload
            class="subtitle-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept=".vtt,.srt,.txt"
            :on-change="handleSubtitleFileChange"
          >
            <el-button>选择英文字幕</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 VTT/SRT/TXT 格式（可选）</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="中文字幕">
          <el-upload
            class="subtitle-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept=".vtt,.srt,.txt"
            :on-change="handleSubtitleZhFileChange"
          >
            <el-button>选择中文字幕</el-button>
            <template #tip>
              <div class="el-upload__tip">支持 VTT/SRT/TXT 格式（可选）</div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="form.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>
        <el-form-item label="公开">
          <el-switch v-model="form.isPublicBool" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
          {{ editingVideo ? '保存修改' : '确认添加' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { uploadMedia } from '@/api/media'
import * as videoApi from '@/api/video'

const videos = ref([])
const stats = ref({})
const loading = ref(false)
const dialogVisible = ref(false)
const editingVideo = ref(null)
const submitLoading = ref(false)
const formRef = ref()

const searchForm = reactive({ keyword: '' })
const pagination = reactive({ current: 1, size: 20, total: 0 })

const form = reactive({
  title: '',
  description: '',
  mediaId: null,
  coverMediaId: null,
  tags: '',
  subtitleContent: '',
  subtitleContentZh: '',
  isPublicBool: true,
  videoUrl: '',
  coverUrl: ''
})

const formRules = {
  title: [{ required: true, message: '请输入视频标题', trigger: 'blur' }]
}

// 文件暂存
let videoFile = null
let coverFile = null

const handleVideoFileChange = (file) => { videoFile = file.raw }
const handleCoverFileChange = (file) => { coverFile = file.raw }
const handleSubtitleFileChange = async (file) => {
  try {
    const text = await file.raw.text()
    form.subtitleContent = text
  } catch (e) {
    ElMessage.error('英文字幕文件读取失败')
  }
}
const handleSubtitleZhFileChange = async (file) => {
  try {
    const text = await file.raw.text()
    form.subtitleContentZh = text
  } catch (e) {
    ElMessage.error('中文字幕文件读取失败')
  }
}

const loadStats = async () => {
  try {
    const res = await videoApi.getAdminVideoStats()
    if (res.code === 200) stats.value = res.data || {}
  } catch (e) {
    console.error('Load stats error:', e)
  }
}

const loadVideos = async () => {
  loading.value = true
  try {
    const params = { page: pagination.current, size: pagination.size }
    if (searchForm.keyword) params.keyword = searchForm.keyword

    const res = await videoApi.getAdminVideos(params)
    if (res.code === 200) {
      videos.value = res.data?.records || []
      pagination.total = res.data?.total || 0
    }
  } catch (e) {
    ElMessage.error('加载视频列表失败')
  } finally {
    loading.value = false
  }
}

const handleSearch = () => { pagination.current = 1; loadVideos() }
const resetSearch = () => { searchForm.keyword = ''; pagination.current = 1; loadVideos() }
const handleSizeChange = (size) => { pagination.size = size; pagination.current = 1; loadVideos() }
const handleCurrentChange = (page) => { pagination.current = page; loadVideos() }

const showAddDialog = () => { editingVideo.value = null; resetForm(); dialogVisible.value = true }
const editVideo = (row) => {
  editingVideo.value = row
  Object.assign(form, {
    title: row.title,
    description: row.description || '',
    mediaId: row.mediaId,
    coverMediaId: row.coverMediaId,
    tags: row.tags || '',
    subtitleContent: row.subtitleContent || '',
    subtitleContentZh: row.subtitleContentZh || '',
    isPublicBool: row.isPublic === 1,
    videoUrl: row.videoUrl || '',
    coverUrl: row.coverUrl || ''
  })
  dialogVisible.value = true
}

const resetForm = () => {
  Object.assign(form, { title: '', description: '', mediaId: null, coverMediaId: null, tags: '', subtitleContent: '', subtitleContentZh: '', isPublicBool: true, videoUrl: '', coverUrl: '' })
  videoFile = null; coverFile = null
  formRef.value?.clearValidate()
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    const valid = await formRef.value.validate()
    if (!valid) return
  } catch {
    return
  }
  submitLoading.value = true
  try {
    // 上传视频文件
    if (videoFile) {
      const uploadRes = await uploadMedia(videoFile, 'VIDEO')
      if (uploadRes.code === 200 && uploadRes.data?.mediaId) {
        form.mediaId = uploadRes.data.mediaId
        form.videoUrl = uploadRes.data.url || `/api/media/${uploadRes.data.mediaId}/download`
      } else {
        ElMessage.error('视频上传失败'); return
      }
    }
    // 上传封面
    if (coverFile) {
      const coverRes = await uploadMedia(coverFile, 'IMAGE')
      if (coverRes.code === 200 && coverRes.data?.mediaId) {
        form.coverMediaId = coverRes.data.mediaId
        form.coverUrl = coverRes.data.url || `/api/media/${coverRes.data.mediaId}/download`
      }
    }

    if (!editingVideo.value && !form.mediaId) {
      ElMessage.error('请选择视频文件'); return
    }

    const data = {
      title: form.title,
      description: form.description,
      mediaId: form.mediaId,
      coverMediaId: form.coverMediaId,
      tags: form.tags,
      subtitleContent: form.subtitleContent || null,
      subtitleContentZh: form.subtitleContentZh || null,
      isPublic: form.isPublicBool ? 1 : 0,
      status: 'APPROVED',
      videoUrl: form.videoUrl,
      coverUrl: form.coverUrl
    }

    if (editingVideo.value) {
      const res = await videoApi.updateVideo(editingVideo.value.id, data)
      if (res.code === 200) { ElMessage.success('更新成功'); dialogVisible.value = false; loadVideos(); loadStats() }
      else ElMessage.error(res.message || '更新失败')
    } else {
      const res = await videoApi.createVideo(data)
      if (res.code === 200) { ElMessage.success('创建成功'); dialogVisible.value = false; loadVideos(); loadStats() }
      else ElMessage.error(res.message || '创建失败')
    }
  } catch (e) {
    console.error('Submit error:', e)
    ElMessage.error(e.message || '操作失败')
  } finally {
    submitLoading.value = false
  }
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除视频 "${row.title}" 吗？`, '删除确认', { type: 'warning' })
    .then(async () => {
      const res = await videoApi.deleteAdminVideo(row.id)
      if (res.code === 200) { ElMessage.success('删除成功'); loadVideos(); loadStats() }
      else ElMessage.error(res.message || '删除失败')
    }).catch(() => {})
}

const toggleFeatured = async (row, val) => {
  const target = val ? 1 : 0
  row._featuredLoading = true
  try {
    await videoApi.toggleVideoFeatured(row.id, target)
    row.isFeatured = target
    ElMessage.success(target === 1 ? '已设为推荐' : '已取消推荐')
    loadStats()
  } catch (e) { row.isFeatured = 1 - target }
  finally { row._featuredLoading = false }
}

const updatePriority = async (row, val) => {
  const priority = Math.max(0, Math.min(100, val || 0))
  try {
    const res = await videoApi.setVideoFeaturedPriority(row.id, priority)
    if (res.code === 200) { row.featuredPriority = priority; ElMessage.success('优先级已更新') }
  } catch (e) { ElMessage.error('更新失败') }
}

const getContentPreview = (content) => content ? (content.length > 80 ? content.substring(0, 80) + '...' : content) : ''
const getStatusType = (s) => ({ APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger' }[s] || 'info')
const getStatusText = (s) => ({ APPROVED: '已审核', PENDING: '待审核', REJECTED: '已拒绝' }[s] || s)
const formatTime = (t) => t ? new Date(t).toLocaleString('zh-CN') : ''

onMounted(() => { loadStats(); loadVideos() })
</script>

<style scoped>
.videos-management {
  padding: 20px;
  max-width: 1400px;
  margin: 0 auto;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .videos-management {
    padding: 30px;
  }
  
  .header h2 {
    font-size: 28px;
  }

  .stat-value {
    font-size: 36px;
  }

  .stat-label {
    font-size: 16px;
  }

  :deep(.el-table) {
    font-size: 15px;
  }

  :deep(.el-table .cell) {
    line-height: 1.6;
    padding: 12px 0;
  }

  .search-bar {
    margin-bottom: 25px;
    padding: 24px;
  }
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #f1f5f9;
}

.header h2 {
  margin: 0;
  color: #1e293b;
  font-weight: 700;
}

.actions {
  display: flex;
  gap: 12px;
}

.search-bar {
  margin-bottom: 24px;
  padding: 20px;
  background-color: #f8fafc;
  border-radius: 12px;
  border: 1px solid #f1f5f9;
}

.stats-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 20px;
  margin-bottom: 24px;
}

.stat-card {
  text-align: center;
  padding: 24px;
  background: #fff;
  border: none;
  border-radius: 16px;
  box-shadow: 0 4px 12px rgba(0,0,0,0.05);
  transition: all 0.3s;
  border: 1px solid #f1f5f9;
}

.stat-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 8px 24px rgba(0,0,0,0.08);
}

.stat-value {
  font-size: 32px;
  font-weight: 800;
  color: #3b82f6;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 14px;
  color: #64748b;
  font-weight: 500;
}

.videos-table {
  background: #fff;
  border-radius: 12px;
  overflow: hidden;
  box-shadow: 0 4px 20px rgba(0,0,0,0.06);
}

.pagination {
  margin-top: 30px;
  display: flex;
  justify-content: center;
}

.priority-na {
  color: #cbd5e1;
  font-size: 14px;
}

@media (max-width: 768px) {
  .videos-management {
    padding: 12px;
  }

  .header {
    flex-direction: column;
    gap: 16px;
    align-items: flex-start;
  }

  .stats-cards {
    grid-template-columns: repeat(2, 1fr);
    gap: 12px;
  }

  .stat-card {
    padding: 16px;
  }

  .stat-value {
    font-size: 24px;
  }
}
</style>

<style>
/* 修复视频弹窗内上传列表内容溢出问题并允许多行完整显示 */
.custom-video-dialog .el-form-item__content {
  min-width: 0 !important; /* 关键修复：防止 flex 子项无限自适应文本宽度撑开弹窗 */
}
.custom-video-dialog .video-uploader,
.custom-video-dialog .cover-uploader,
.custom-video-dialog .subtitle-uploader {
  width: 100%;
  max-width: 100%;
}
.custom-video-dialog .el-upload-list {
  width: 100%;
}
.custom-video-dialog .el-upload-list__item {
  height: auto !important;
  min-height: 32px;
  max-width: 100% !important;
  box-sizing: border-box;
}
.custom-video-dialog .el-upload-list__item-info {
  width: 100%;
  min-width: 0 !important;
}
.custom-video-dialog .el-upload-list__item-name {
  display: block !important;
  white-space: normal !important;
  word-wrap: break-word !important;
  word-break: break-all !important;
  line-height: 1.5 !important;
  height: auto !important;
  padding-right: 25px !important;
  margin-bottom: 2px !important;
}
.custom-video-dialog .el-upload-list__item-file-name {
  white-space: normal !important;
  word-wrap: break-word !important;
  word-break: break-all !important;
}
</style>
