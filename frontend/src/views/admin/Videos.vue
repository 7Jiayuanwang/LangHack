<template>
  <div class="videos-management">
    <!-- 搜索栏 + 操作按钮 -->
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
        <el-form-item label="标签">
          <el-select v-model="tagFilter" multiple collapse-tags placeholder="标签筛选" clearable style="width: 200px" @change="handleSearch">
            <el-option v-for="t in availableTags" :key="t.id" :label="t.name" :value="t.name" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </el-form-item>
      </el-form>
      <div class="search-actions">
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
              :disabled="row.status !== 'APPROVED'"
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
        <!-- 编辑时显示当前视频文件名 + 预览按钮 -->
        <el-form-item label="当前视频" v-if="editingVideo && editingVideo.mediaId">
          <div class="file-info-row">
            <span class="file-name">{{ editingVideo.title || '视频文件' }}.mp4</span>
            <el-button type="primary" text size="small" @click="previewVideo(editingVideo)">
              预览
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="封面图">
          <!-- 编辑时显示封面文件名 + 预览按钮 -->
          <div v-if="editingVideo && editingVideo.coverMediaId && !coverFile" class="file-info-row" style="margin-bottom: 8px">
            <span class="file-name">封面图片</span>
            <el-button type="primary" text size="small" @click="previewCoverVisible = true">
              预览
            </el-button>
          </div>
          <el-upload
            class="cover-uploader"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            accept="image/jpeg,image/png,image/webp"
            :on-change="handleCoverFileChange"
          >
            <el-button>{{ editingVideo ? '更换封面图' : '选择封面图' }}</el-button>
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

    <!-- 封面预览弹窗 -->
    <el-dialog v-model="previewCoverVisible" title="封面预览" width="min(500px, 90vw)">
      <img v-if="editingVideo && editingVideo.coverMediaId" :src="`/api/media/${editingVideo.coverMediaId}/download`" alt="封面" style="width: 100%; border-radius: 8px;" />
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, RefreshLeft, Refresh } from '@element-plus/icons-vue'
import { uploadMedia } from '@/api/media'
import * as videoApi from '@/api/video'
import { getContentTags } from '@/api/admin'

const router = useRouter()

const videos = ref([])
const stats = ref({})
const loading = ref(false)
const dialogVisible = ref(false)
const editingVideo = ref(null)
const submitLoading = ref(false)
const formRef = ref()
const previewCoverVisible = ref(false)

// 视频预览 — 新标签页打开完整播放器
const previewVideo = (row) => {
  if (row && row.id) {
    const routeData = router.resolve({
      name: 'StudentVideoPlayer',
      query: { id: row.id, preview: 'admin' }
    })
    window.open(routeData.href, '_blank')
  }
}

const searchForm = reactive({ keyword: '' })
const tagFilter = ref([])
const availableTags = ref([])
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
    if (tagFilter.value && tagFilter.value.length > 0) params.tag = tagFilter.value.join(',')

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
  loadStats()
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

const loadAvailableTags = async () => {
  try {
    const res = await getContentTags()
    if (res.code === 200) {
      availableTags.value = res.data || []
    }
  } catch (e) {
    // 静默失败
  }
}

onMounted(() => { loadStats(); loadVideos(); loadAvailableTags() })

defineExpose({ loadVideos })
</script>

<style scoped>
.videos-management { padding: 20px; max-width: 1400px; margin: 0 auto; }
.search-bar { display: flex; justify-content: space-between; align-items: flex-start; flex-wrap: wrap; gap: 12px; margin-bottom: 20px; padding: 20px; background-color: #f8f9fa; border-radius: 8px; }
.search-form { margin: 0; }
.search-actions { display: flex; gap: 12px; flex-shrink: 0; }
.stats-cards { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 20px; margin-bottom: 20px; }
.stat-card { text-align: center; padding: 20px; background: #fff; border: 1px solid #e4e7ed; border-radius: 8px; transition: all 0.3s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.stat-value { font-size: 32px; font-weight: bold; color: #409eff; margin-bottom: 8px; }
.stat-label { font-size: 14px; color: #909399; }
.videos-table { background: #fff; border-radius: 8px; overflow-x: auto; box-shadow: 0 2px 8px rgba(0,0,0,0.1); }
.pagination { margin-top: 20px; display: flex; justify-content: center; }
.priority-na { color: #c0c4cc; font-size: 16px; }
.file-info-row { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: #f5f7fa; border-radius: 6px; border: 1px solid #e4e7ed; }
.file-name { font-size: 13px; color: #606266; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
@media (max-width: 768px) {
  .videos-management { padding: 16px; }
  .header { flex-direction: column; gap: 16px; align-items: flex-start; }
  .stats-cards { grid-template-columns: repeat(2, 1fr); gap: 12px; }
}
</style>
