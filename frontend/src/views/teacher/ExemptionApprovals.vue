<template>
  <div class="exemption-page">
    <!-- 独立工作台式页头：标题与筛选计数器一体 -->
    <section class="hero">
      <div class="hero-text">
        <div class="hero-eyebrow">EXEMPTION APPROVAL</div>
        <h1 class="hero-title">免修审批</h1>
        <div class="hero-desc">大学英语课程免修 / 免考申请审批 · 内农大教字〔2025〕18 号</div>
      </div>
      <div class="hero-stats">
        <div
          v-for="tab in statusTabs"
          :key="tab.key"
          class="hero-stat"
          :class="[`hs-${tab.key.toLowerCase() || 'all'}`, { active: filterStatus === tab.key }]"
          @click="switchTab(tab.key)"
        >
          <div class="hs-num">{{ tab.count }}</div>
          <div class="hs-label">{{ tab.label }}</div>
        </div>
      </div>
    </section>

    <el-card class="table-card" shadow="never">
      <el-table :data="rows" v-loading="loading" stripe style="width: 100%">
        <el-table-column label="学生" min-width="180">
          <template #default="{ row }">
            <div class="stu-cell">
              <div class="stu-avatar">{{ initials(row.studentName) }}</div>
              <div class="stu-meta">
                <div class="stu-name">{{ row.studentName || '未知学生' }}</div>
                <div class="stu-sub">{{ row.studentNo }}</div>
                <div class="stu-sub class-line">{{ row.classNames || '未分班' }}</div>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="申请内容" min-width="210">
          <template #default="{ row }">
            <div class="apply-cell">
              <div class="course">{{ row.courseName }}</div>
              <div class="tags">
                <el-tag :type="row.applyType === 'EXEMPT_FULL' ? 'warning' : 'primary'" size="small" effect="light">
                  {{ applyTypeLabel(row.applyType) }}
                </el-tag>
                <el-tag size="small" effect="plain" type="info">{{ examLabel(row.examType) }}</el-tag>
              </div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="成绩与折算" min-width="180">
          <template #default="{ row }">
            <div class="score-cell">
              <span class="raw-score">{{ row.examScore }}</span>
              <span class="arrow">→</span>
              <span class="converted">{{ row.convertedScore ?? '—' }} 分</span>
              <div class="score-sub">{{ examDateText(row.examDate) }}</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="材料核验" min-width="150">
          <template #default="{ row }">
            <el-tooltip v-if="row.check && !row.check.eligible" :content="row.check.problem" placement="top">
              <el-tag type="danger" size="small" effect="light">不符合条件</el-tag>
            </el-tooltip>
            <el-tag v-else type="success" size="small" effect="light">符合申请条件</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" size="default" effect="light">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="申请时间" width="160">
          <template #default="{ row }">
            <span class="time-text">{{ formatTime(row.createdTime) }}</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <template v-if="row.status === 'PENDING'">
              <el-button type="primary" size="small" @click="openReview(row, true)">通 过</el-button>
              <el-button type="danger" plain size="small" @click="openReview(row, false)">驳 回</el-button>
            </template>
            <el-button size="small" @click="openDetail(row)">
              <el-icon style="margin-right: 4px"><View /></el-icon>资 料
            </el-button>
            <el-button
              v-if="row.status !== 'PENDING'"
              size="small"
              text
              type="info"
              @click="openReview(row, row.status === 'APPROVED')"
            >
              意见
            </el-button>
          </template>
        </el-table-column>

        <template #empty>
          <el-empty description="暂无申请记录" :image-size="90" />
        </template>
      </el-table>

      <div class="pager" v-if="total > pageSize">
        <el-pagination
          layout="prev, pager, next, total"
          :total="total"
          :page-size="pageSize"
          :current-page="page"
          @current-change="onPageChange"
        />
      </div>
    </el-card>

    <!-- 资料详情抽屉 -->
    <el-drawer v-model="detailVisible" title="申请资料详情" size="540px" :destroy-on-close="false">
      <div class="detail-body" v-if="detailRow">
        <!-- 学生 -->
        <div class="detail-student">
          <div class="stu-avatar big">{{ initials(detailRow.studentName) }}</div>
          <div>
            <div class="ds-name">{{ detailRow.studentName || '未知学生' }}</div>
            <div class="ds-sub">{{ detailRow.studentNo }}<template v-if="detailRow.classNames"> · {{ detailRow.classNames }}</template></div>
          </div>
          <el-tag :type="statusTagType(detailRow.status)" effect="light" style="margin-left: auto">
            {{ statusLabel(detailRow.status) }}
          </el-tag>
        </div>

        <!-- 申请信息 -->
        <div class="detail-section">
          <div class="section-title"><el-icon><Collection /></el-icon>申请信息</div>
          <div class="info-grid">
            <div class="info-item"><span>申请课程</span><b>{{ detailRow.courseName }}</b></div>
            <div class="info-item"><span>申请方式</span><b>{{ applyTypeLabel(detailRow.applyType) }}</b></div>
            <div class="info-item"><span>考试类型</span><b>{{ examLabel(detailRow.examType) }}</b></div>
            <div class="info-item"><span>成绩获得时间</span><b>{{ detailRow.examDate ? String(detailRow.examDate).slice(0, 10) : '未填写' }}</b></div>
            <div class="info-item"><span>原始成绩</span><b class="score-em">{{ detailRow.examScore }}</b></div>
            <div class="info-item">
              <span>折算成绩</span>
              <b class="score-em brand">{{ detailRow.convertedScore ?? '—' }}<small v-if="detailRow.convertedScore != null"> 分</small></b>
            </div>
          </div>
          <el-alert
            v-if="detailRow.check && detailRow.check.problem"
            type="warning" :closable="false" show-icon :title="detailRow.check.problem"
            style="margin-top: 12px"
          />
          <el-alert
            v-else-if="detailRow.check && detailRow.check.eligible"
            type="success" :closable="false" show-icon title="成绩符合细则规定的申请条件"
            style="margin-top: 12px"
          />
        </div>

        <!-- 证明材料 -->
        <div class="detail-section">
          <div class="section-title"><el-icon><Document /></el-icon>成绩证明材料</div>
          <template v-if="detailRow.certificateUrl">
            <el-image
              v-if="isImage(detailRow.certificateUrl)"
              :src="detailRow.certificateUrl"
              :preview-src-list="[detailRow.certificateUrl]"
              fit="cover"
              class="cert-image"
              preview-teleported
            />
            <el-link v-else type="primary" :href="detailRow.certificateUrl" target="_blank">
              <el-icon><Link /></el-icon>&nbsp;打开证明文件
            </el-link>
            <div v-if="!/^data:/i.test(detailRow.certificateUrl)" class="cert-url">{{ detailRow.certificateUrl }}</div>
          </template>
          <el-empty v-else description="学生暂未上传证明材料" :image-size="70" />
        </div>

        <!-- 学生说明 -->
        <div class="detail-section">
          <div class="section-title"><el-icon><ChatDotRound /></el-icon>学生申请说明</div>
          <div class="remark-box" v-if="detailRow.studentRemark">{{ detailRow.studentRemark }}</div>
          <div class="remark-empty" v-else>未填写</div>
        </div>

        <!-- 审批信息 -->
        <div class="detail-section">
          <div class="section-title"><el-icon><Stamp /></el-icon>审批信息</div>
          <div class="info-grid">
            <div class="info-item"><span>提交时间</span><b>{{ formatTime(detailRow.createdTime) || '—' }}</b></div>
            <div class="info-item"><span>审批时间</span><b>{{ formatTime(detailRow.reviewedTime) || '—' }}</b></div>
          </div>
          <div class="remark-box" v-if="detailRow.reviewComment">{{ detailRow.reviewComment }}</div>
          <div class="remark-empty" v-else-if="detailRow.status === 'PENDING'">等待审批</div>
          <div class="remark-empty" v-else>无审批意见</div>
        </div>
      </div>

      <template #footer>
        <div style="display: flex; gap: 10px; justify-content: flex-end" v-if="detailRow && detailRow.status === 'PENDING'">
          <el-button type="danger" plain @click="openReview(detailRow, false)">驳 回</el-button>
          <el-button type="primary" @click="openReview(detailRow, true)">通 过</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog
      v-model="reviewVisible"
      :title="reviewApprove ? '通过免修申请' : (reviewRow.status === 'PENDING' ? '驳回免修申请' : '审批意见')"
      width="520px"
      :close-on-click-modal="false"
    >
      <div class="review-summary" v-if="reviewRow">
        <div class="summary-row"><span>学生</span><b>{{ reviewRow.studentName }}（{{ reviewRow.studentNo }}）</b></div>
        <div class="summary-row"><span>课程</span><b>{{ reviewRow.courseName }}</b></div>
        <div class="summary-row">
          <span>方式</span>
          <b>{{ applyTypeLabel(reviewRow.applyType) }} · {{ examLabel(reviewRow.examType) }} {{ reviewRow.examScore }} 分</b>
        </div>
        <div class="summary-row">
          <span>折算成绩</span>
          <b>{{ reviewRow.convertedScore ?? '—' }} 分</b>
        </div>
        <el-alert
          v-if="reviewRow.check && reviewRow.check.problem"
          type="warning"
          :closable="false"
          show-icon
          :title="reviewRow.check.problem"
          style="margin-top: 10px"
        />
      </div>

      <el-input
        v-model="reviewComment"
        type="textarea"
        :rows="4"
        maxlength="500"
        show-word-limit
        :placeholder="reviewApprove
          ? '审批意见（选填），例如：材料核验无误，同意免修。'
          : (reviewRow.status === 'PENDING' ? '请填写驳回原因（必填）' : '教师审批意见')"
        :disabled="reviewRow.status !== 'PENDING'"
      />

      <template #footer>
        <el-button @click="reviewVisible = false">关 闭</el-button>
        <el-button
          v-if="reviewRow.status === 'PENDING'"
          :type="reviewApprove ? 'primary' : 'danger'"
          :loading="submitting"
          @click="submitReview"
        >
          {{ reviewApprove ? '确认通过' : '确认驳回' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { ChatDotRound, Collection, Document, Link, Stamp, View } from '@element-plus/icons-vue'
import {
  approveExemption,
  listExemptionApplications,
  rejectExemption
} from '@/api/exemption'

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 10
const filterStatus = ref('PENDING')

const counts = ref({ PENDING: 0, APPROVED: 0, REJECTED: 0, ALL: 0 })

const statusTabs = computed(() => [
  { key: 'PENDING', label: '待审批', count: counts.value.PENDING },
  { key: 'APPROVED', label: '已通过', count: counts.value.APPROVED },
  { key: 'REJECTED', label: '已驳回', count: counts.value.REJECTED },
  { key: '', label: '全部申请', count: counts.value.ALL }
])

const reviewVisible = ref(false)
const reviewApprove = ref(true)
const reviewComment = ref('')
const reviewRow = ref({})
const submittingId = ref(null)

const detailVisible = ref(false)
const detailRow = ref(null)

const load = async () => {
  loading.value = true
  try {
    const res = await listExemptionApplications({
      status: filterStatus.value,
      page: page.value,
      size: pageSize
    })
    if (res.code === 200 && res.data) {
      rows.value = Array.isArray(res.data.records) ? res.data.records : []
      total.value = Number(res.data.total || 0)
      if (res.data.counts) counts.value = { ...counts.value, ...res.data.counts }
      // 详情抽屉打开时刷新数据，保持内容同步
      if (detailVisible.value && detailRow.value) {
        const fresh = rows.value.find((r) => r.id === detailRow.value.id)
        if (fresh) detailRow.value = fresh
      }
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (e) {
    console.error('加载免修申请失败', e)
    ElMessage.error('加载免修申请失败')
  } finally {
    loading.value = false
  }
}

const switchTab = (key) => {
  filterStatus.value = key
  page.value = 1
  load()
}

const onPageChange = (p) => {
  page.value = p
  load()
}

const openReview = (row, approve) => {
  reviewRow.value = row
  reviewApprove.value = approve
  reviewComment.value = row.reviewComment || ''
  reviewVisible.value = true
}

const openDetail = (row) => {
  detailRow.value = row
  detailVisible.value = true
}

const submitReview = async () => {
  const row = reviewRow.value
  submitting.value = true
  submittingId.value = row.id
  try {
    const fn = reviewApprove.value ? approveExemption : rejectExemption
    const res = await fn(row.id, reviewComment.value.trim())
    if (res.code === 200) {
      ElMessage.success(res.message || '操作成功')
      reviewVisible.value = false
      load()
    } else {
      ElMessage.error(res.message || '操作失败')
    }
  } catch (e) {
    console.error('审批失败', e)
    ElMessage.error('审批操作失败')
  } finally {
    submitting.value = false
    submittingId.value = null
  }
}

const applyTypeLabel = (v) => (v === 'EXEMPT_FULL' ? '免修免考' : v === 'EXEMPT_WITH_EXAM' ? '免修不免考' : v)
const examLabel = (v) => ({ CET4: '大学英语四级', CET6: '大学英语六级', IELTS: '雅思', TOEFL: '托福' }[v] || v)
const statusLabel = (v) => ({ PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回' }[v] || v)
const statusTagType = (v) => ({ PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }[v] || 'info')
const initials = (name) => (name ? name.slice(-2) : '?')
const examDateText = (d) => (d ? `获得时间 ${String(d).slice(0, 10)}` : '未填写获得时间')
const formatTime = (t) => (t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '')
const isImage = (url) => /^data:image\//i.test(url || '') || /\.(png|jpe?g|gif|webp|bmp|svg)(\?.*)?$/i.test(url || '')

onMounted(load)
</script>

<style scoped>
/* 间距基调与教师端其它页面统一：页面内边距 24px，区块间 20px */
.exemption-page {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px;
  min-height: calc(100vh - 56px);
  background: var(--xy-gradient-page);
}

/* ── 工作台横幅：本页独立 UI ── */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 28px;
  flex-wrap: wrap;
  border-radius: 16px;
  padding: 28px 32px;
  margin-bottom: 20px;
  background: linear-gradient(120deg, #0f5f49 0%, #17836a 55%, #1f9d7d 100%);
  box-shadow: 0 10px 26px rgba(23, 110, 81, 0.28);
  position: relative;
  overflow: hidden;
}

.hero::after {
  content: '';
  position: absolute;
  right: -60px;
  top: -80px;
  width: 260px;
  height: 260px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.06);
  pointer-events: none;
}

.hero-eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 2.5px;
  color: rgba(255, 255, 255, 0.68);
}

.hero-title {
  margin: 6px 0 0;
  font-size: 30px;
  font-weight: 700;
  letter-spacing: 1px;
  color: #fff;
  line-height: 1.2;
}

.hero-desc {
  margin-top: 8px;
  font-size: 14px;
  color: rgba(255, 255, 255, 0.82);
  letter-spacing: 0.2px;
}

.hero-stats {
  display: flex;
  align-items: stretch;
  position: relative;
  z-index: 1;
}

.hero-stat {
  min-width: 108px;
  padding: 10px 22px;
  text-align: center;
  cursor: pointer;
  border-radius: 12px;
  border: 1px solid transparent;
  transition: background 0.22s ease, border-color 0.22s ease, transform 0.22s ease;
}

.hero-stat + .hero-stat {
  border-left: 1px solid rgba(255, 255, 255, 0.22);
  border-top-left-radius: 0;
  border-bottom-left-radius: 0;
}

.hero-stat:hover {
  background: rgba(255, 255, 255, 0.1);
}

.hero-stat.active {
  background: rgba(255, 255, 255, 0.16);
  border-color: rgba(255, 255, 255, 0.4);
  transform: translateY(-2px);
}

.hero-stat.active + .hero-stat {
  border-left-color: transparent;
}

.hs-num {
  font-size: 32px;
  font-weight: 700;
  line-height: 1.1;
  font-variant-numeric: tabular-nums;
  color: #fff;
}

.hs-pending .hs-num { color: #ffd54a; }
.hs-approved .hs-num { color: #b8f0d4; }
.hs-rejected .hs-num { color: #ffc2c2; }

.hs-label {
  margin-top: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.85);
}

.table-card {
  border-radius: 16px;
  border: 1px solid var(--xy-border);
}

.stu-cell { display: flex; align-items: center; gap: 12px; }
.stu-avatar {
  width: 42px; height: 42px; border-radius: 12px; flex-shrink: 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff; font-size: 14px; font-weight: 600;
  display: flex; align-items: center; justify-content: center;
}
.stu-avatar.big { width: 52px; height: 52px; font-size: 16px; border-radius: 14px; }
.stu-meta { min-width: 0; }
.stu-name { font-weight: 600; color: #303133; font-size: 15px; }
.stu-sub { font-size: 12px; color: #909399; margin-top: 2px; }
.class-line { max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.apply-cell .course { font-weight: 600; color: #303133; margin-bottom: 6px; }
.tags { display: flex; gap: 6px; flex-wrap: wrap; }

.score-cell { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.raw-score { font-weight: 700; color: #303133; }
.arrow { color: #c0c4cc; }
.converted { color: var(--xy-brand-600); font-weight: 600; }
.score-sub { width: 100%; font-size: 12px; color: #909399; }

.time-text { font-size: 13px; color: #606266; }

.pager { display: flex; justify-content: flex-end; padding: 16px 4px 4px; }

/* ── 详情抽屉 ── */
.detail-body { padding: 0 4px; }

.detail-student {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 14px;
  background: linear-gradient(120deg, rgba(15, 95, 73, 0.08), rgba(31, 157, 125, 0.1));
}
.ds-name { font-size: 16px; font-weight: 700; color: #303133; }
.ds-sub { font-size: 13px; color: #606266; margin-top: 2px; }

.detail-section { margin-top: 20px; }
.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 700;
  color: #303133;
  margin-bottom: 12px;
}
.section-title .el-icon { color: var(--xy-brand-600, #176e51); }

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}
.info-item {
  background: #f8f9fa;
  border-radius: 10px;
  padding: 10px 14px;
}
.info-item span { display: block; font-size: 12px; color: #909399; margin-bottom: 4px; }
.info-item b { font-size: 14px; color: #303133; font-weight: 600; word-break: break-all; }
.score-em { font-size: 18px !important; font-variant-numeric: tabular-nums; }
.score-em.brand { color: var(--xy-brand-600, #176e51) !important; }

.cert-image {
  width: 100%;
  max-height: 260px;
  border-radius: 12px;
  border: 1px solid var(--xy-border, #ebeef5);
}
.cert-url {
  margin-top: 6px;
  font-size: 12px;
  color: #909399;
  word-break: break-all;
}

.remark-box {
  background: #f8f9fa;
  border-left: 3px solid var(--xy-brand-600, #176e51);
  border-radius: 0 10px 10px 0;
  padding: 12px 14px;
  font-size: 14px;
  color: #303133;
  line-height: 1.7;
  white-space: pre-wrap;
}
.remark-empty { font-size: 13px; color: #c0c4cc; padding: 4px 0; }

.review-summary { margin-bottom: 14px; }
.summary-row {
  display: flex; gap: 12px; padding: 6px 0; font-size: 14px;
  border-bottom: 1px dashed #ebeef5;
}
.summary-row:last-child { border-bottom: none; }
.summary-row span { width: 72px; color: #909399; flex-shrink: 0; }
.summary-row b { color: #303133; font-weight: 600; }

@media (max-width: 768px) {
  .exemption-page { padding: 16px; }
  .hero { padding: 20px; gap: 16px; }
  .hero-title { font-size: 24px; }
  .hero-stats { width: 100%; }
  .hero-stat { flex: 1; min-width: 0; padding: 8px 10px; }
  .hs-num { font-size: 24px; }
  .info-grid { grid-template-columns: 1fr; }
}
</style>
