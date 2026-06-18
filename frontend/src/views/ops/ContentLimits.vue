<template>
  <div class="content-limits-page">
    <div class="page-header">
      <h2>内容大小限制</h2>
      <p class="page-desc">动态调控图片、视频和批量导入文件的大小上限；角色覆盖优先于全局默认</p>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载中...</p>
    </div>

    <template v-else>
      <!-- 图片限制 -->
      <LimitCard
        title="图片上传限制"
        type="image"
        :global="defaults.image"
        :overrides="roleOverrides.filter(r => r.type === 'image')"
        :cap-bytes="52428800"
        cap-label="50MB"
        @save="handleSave"
        @delete-override="handleDeleteOverride"
      />

      <!-- 视频限制 -->
      <LimitCard
        title="视频上传限制"
        type="video"
        :global="defaults.video"
        :overrides="roleOverrides.filter(r => r.type === 'video')"
        :cap-bytes="1073741824"
        cap-label="1GB"
        @save="handleSave"
        @delete-override="handleDeleteOverride"
      />

      <!-- Excel 导入 -->
      <el-card class="limit-card" shadow="never">
        <template #header>
          <div class="card-header">
            <el-icon><Document /></el-icon>
            <span>Excel 批量导入限制</span>
          </div>
        </template>
        <div class="row-item">
          <span class="row-label">全局默认</span>
          <div class="row-bar">
            <el-progress
              :percentage="Math.min(100, Math.round(defaults.excelImport.bytes / 104857600 * 100))"
              :stroke-width="10"
              :color="barColor(defaults.excelImport.bytes / 104857600)"
              style="flex:1"
            />
            <span class="bar-hint">/ 100MB</span>
          </div>
          <SizeInput v-model="editExcel" />
          <el-button type="primary" size="small" :loading="savingExcel" @click="saveExcel">保存</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup lang="jsx">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Document } from '@element-plus/icons-vue'
import { getContentLimits, updateContentLimits, deleteContentLimit } from '@/api/systemConfig'

// ── 子组件：尺寸输入框（数字 + 单位）─────────────────────────────────────────
const SizeInput = {
  props: { modelValue: String },
  emits: ['update:modelValue'],
  setup(props, { emit }) {
    const units = ['KB', 'MB', 'GB']
    const parse = (v) => {
      if (!v) return { num: 10, unit: 'MB' }
      const m = String(v).toUpperCase().match(/^(\d+(?:\.\d+)?)(KB|MB|GB)$/)
      return m ? { num: parseFloat(m[1]), unit: m[2] } : { num: 10, unit: 'MB' }
    }
    const { num, unit } = parse(props.modelValue)
    const numVal  = ref(num)
    const unitVal = ref(unit)
    const emit_ = () => emit('update:modelValue', `${numVal.value}${unitVal.value}`)
    return () => (
      <span class="size-input">
        <el-input-number
          modelValue={numVal.value}
          min={1} max={9999} step={1} controls-position="right"
          style="width:110px"
          onUpdate:modelValue={v => { numVal.value = v; emit_() }}
        />
        <el-select
          modelValue={unitVal.value}
          style="width:70px;margin-left:4px"
          onUpdate:modelValue={v => { unitVal.value = v; emit_() }}
        >
          {units.map(u => <el-option key={u} label={u} value={u} />)}
        </el-select>
      </span>
    )
  }
}

// ── 子组件：每种内容类型的限制卡片 ────────────────────────────────────────────
const ROLES = ['ADMIN', 'TEACHER', 'STUDENT']

const LimitCard = {
  props: {
    title: String, type: String,
    global: Object,   // { value, bytes }
    overrides: Array, // [{ key, type, role, value, bytes }]
    capBytes: Number, capLabel: String
  },
  emits: ['save', 'delete-override'],
  setup(props, { emit }) {
    const editGlobal   = ref(props.global?.value || '10MB')
    const editOverride = reactive({})   // role -> value string
    const saving       = reactive({})   // key -> bool

    ROLES.forEach(r => {
      const found = props.overrides?.find(o => o.role === r)
      editOverride[r] = found ? found.value : ''
    })

    const pct = (bytes) => Math.min(100, Math.round((bytes || 0) / props.capBytes * 100))
    const color = (bytes) => {
      const p = (bytes || 0) / props.capBytes
      return p < 0.5 ? '#67c23a' : p < 0.8 ? '#e6a23c' : '#f56c6c'
    }

    const saveGlobal = () => {
      saving['global'] = true
      emit('save', [{ key: `upload.content_size.${props.type}`, value: editGlobal.value }],
        () => { saving['global'] = false })
    }

    const saveRole = (role) => {
      if (!editOverride[role]) return
      const key = `upload.content_size.${props.type}.role.${role}`
      saving[role] = true
      emit('save', [{ key, value: editOverride[role] }], () => { saving[role] = false })
    }

    const delRole = (role) => {
      const key = `upload.content_size.${props.type}.role.${role}`
      emit('delete-override', key, () => { editOverride[role] = '' })
    }

    return () => (
      <el-card class="limit-card" shadow="never">
        <template v-slot:header>
          <div class="card-header">
            <el-icon><props.type === 'image' ? 'Picture' : 'VideoPlay' /></el-icon>
            <span>{props.title}</span>
          </div>
        </template>

        {/* 全局默认行 */}
        <div class="row-item">
          <span class="row-label">全局默认</span>
          <div class="row-bar">
            <el-progress
              percentage={pct(props.global?.bytes)}
              stroke-width={10}
              color={color(props.global?.bytes)}
              style="flex:1"
            />
            <span class="bar-hint">/ {props.capLabel}</span>
          </div>
          <SizeInput modelValue={editGlobal.value} onUpdate:modelValue={v => editGlobal.value = v} />
          <el-button type="primary" size="small" loading={saving['global']} onClick={saveGlobal}>保存</el-button>
        </div>

        <el-divider style="margin:12px 0" />

        {/* 角色覆盖行 */}
        {ROLES.map(role => {
          const ov = props.overrides?.find(o => o.role === role)
          return (
            <div class="row-item row-item--role" key={role}>
              <el-tag size="small" type={role === 'ADMIN' ? 'danger' : role === 'TEACHER' ? 'warning' : 'success'}
                style="width:72px;text-align:center">
                {role}
              </el-tag>
              <div class="row-bar">
                {ov
                  ? <el-progress percentage={pct(ov.bytes)} stroke-width={8} color={color(ov.bytes)} style="flex:1" />
                  : <span class="inherit-hint">继承全局默认（{props.global?.value}）</span>
                }
                {ov && <span class="bar-hint">/ {props.capLabel}</span>}
              </div>
              <SizeInput
                modelValue={editOverride[role] || props.global?.value || '10MB'}
                onUpdate:modelValue={v => editOverride[role] = v}
              />
              <el-button type="primary" size="small" loading={saving[role]} onClick={() => saveRole(role)}>
                {ov ? '更新' : '覆盖'}
              </el-button>
              {ov && (
                <el-button type="danger" size="small" plain onClick={() => delRole(role)}>重置</el-button>
              )}
            </div>
          )
        })}
      </el-card>
    )
  }
}

// ── 页面逻辑 ──────────────────────────────────────────────────────────────────
const loading    = ref(true)
const savingExcel = ref(false)
const defaults   = reactive({ image: { value: '10MB', bytes: 0 }, video: { value: '512MB', bytes: 0 }, excelImport: { value: '10MB', bytes: 0 } })
const roleOverrides = ref([])
const editExcel  = ref('10MB')

const barColor = (ratio) => ratio < 0.5 ? '#67c23a' : ratio < 0.8 ? '#e6a23c' : '#f56c6c'

const load = async () => {
  loading.value = true
  try {
    const res = await getContentLimits()
    if (res?.code === 200 && res.data) {
      const d = res.data.defaults || {}
      Object.assign(defaults.image,       d.image       || {})
      Object.assign(defaults.video,       d.video       || {})
      Object.assign(defaults.excelImport, d.excelImport || {})
      editExcel.value = defaults.excelImport.value || '10MB'
      roleOverrides.value = res.data.roleOverrides || []
    }
  } catch (e) {
    ElMessage.error('加载内容大小限制失败')
  } finally {
    loading.value = false
  }
}

const handleSave = async (items, done) => {
  try {
    await updateContentLimits(items)
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    done?.()
  }
}

const handleDeleteOverride = async (key, done) => {
  try {
    await ElMessageBox.confirm('重置该角色为全局默认？', '确认', { type: 'warning' })
    await deleteContentLimit(key)
    ElMessage.success('已重置')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('重置失败')
  } finally {
    done?.()
  }
}

const saveExcel = async () => {
  savingExcel.value = true
  await handleSave([{ key: 'upload.content_size.excel_import', value: editExcel.value }],
    () => { savingExcel.value = false })
}

onMounted(load)
</script>

<style scoped>
.content-limits-page { padding: 20px; max-width: 960px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0 0 6px; font-size: 20px; font-weight: 600; }
.page-desc { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.loading-state { display: flex; flex-direction: column; align-items: center; padding: 60px 0; gap: 12px; color: var(--el-text-color-secondary); }

.limit-card { margin-bottom: 20px; border-radius: 12px; }
.card-header { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; }

.row-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
}
.row-item + .row-item { border-top: 1px solid var(--el-border-color-lighter); }
.row-item--role { padding: 8px 0; }

.row-label { width: 70px; font-size: 13px; color: var(--el-text-color-secondary); flex-shrink: 0; }
.row-bar { flex: 1; display: flex; align-items: center; gap: 6px; min-width: 0; }
.bar-hint { font-size: 12px; color: var(--el-text-color-placeholder); white-space: nowrap; }
.inherit-hint { font-size: 12px; color: var(--el-text-color-placeholder); flex: 1; }

.size-input { display: inline-flex; align-items: center; }

@media (max-width: 768px) {
  .content-limits-page { padding: 12px; }
  .row-item { flex-wrap: wrap; }
  .row-bar { width: 100%; flex-basis: 100%; }
}
</style>
