<template>
  <div class="content-limits-page">
    <div class="page-header">
      <h2>内容大小限制</h2>
      <p class="page-desc">
        动态调控图片、视频和批量导入文件的大小上限；
        <b>特权用户</b> &gt; <b>角色覆盖</b> &gt; <b>全局默认</b>（优先级依次降低）
      </p>
    </div>

    <div v-if="loading" class="loading-state">
      <el-icon class="is-loading" :size="32"><Loading /></el-icon>
      <p>加载中...</p>
    </div>

    <template v-else>
      <el-row :gutter="20">
        <el-col v-for="card in cards" :key="card.type" :xs="24" :md="12">
          <el-card class="limit-card" shadow="never">
            <template #header>
              <div class="card-header">
                <el-icon><component :is="card.icon" /></el-icon>
                <span>{{ card.title }}</span>
                <el-tag size="small" type="info" effect="plain" class="cap-tag">
                  上限 {{ card.capLabel }}
                </el-tag>
              </div>
            </template>

            <!-- 1. 全局默认 -->
            <div class="row-item">
              <div class="row-label">全局默认</div>
              <div class="row-bar">
                <el-progress
                  :percentage="pct(card.globalValue.bytes, card.capBytes)"
                  :stroke-width="10"
                  :color="barColor(card.globalValue.bytes, card.capBytes)"
                  style="flex:1"
                />
                <span class="bar-hint">/ {{ card.capLabel }}</span>
              </div>
              <SizeInput v-model="card.editGlobal" placeholder="10" />
              <el-button
                type="primary"
                size="small"
                :loading="card.savingGlobal"
                @click="saveGlobal(card)"
              >保存</el-button>
            </div>

            <el-divider style="margin:14px 0" />

            <!-- 2. 角色覆盖 -->
            <div class="row-label section-label">角色覆盖</div>
            <div
              v-for="role in ROLES"
              :key="role"
              class="row-item row-item--role"
            >
              <el-tag
                size="small"
                :type="tagType(role)"
                style="width:72px;text-align:center"
              >{{ role }}</el-tag>
              <div class="row-bar">
                <template v-if="getRoleOverride(card, role)">
                  <el-progress
                    :percentage="pct(getRoleOverride(card, role).bytes, card.capBytes)"
                    :stroke-width="8"
                    :color="barColor(getRoleOverride(card, role).bytes, card.capBytes)"
                    style="flex:1"
                  />
                  <span class="bar-hint">/ {{ card.capLabel }}</span>
                </template>
                <span v-else class="inherit-hint">
                  继承全局默认（{{ card.globalValue.value }}）
                </span>
              </div>
              <SizeInput v-model="card.editRoles[role]" placeholder="继承" />
              <el-button
                type="primary"
                size="small"
                :loading="card.savingRoles[role]"
                @click="saveRole(card, role)"
              >{{ getRoleOverride(card, role) ? '更新' : '覆盖' }}</el-button>
              <el-button
                v-if="getRoleOverride(card, role)"
                type="danger"
                size="small"
                plain
                @click="delRole(card, role)"
              >重置</el-button>
            </div>

            <el-divider style="margin:14px 0" />

            <!-- 3. 特权用户 -->
            <div class="row-label section-label flex-between">
              <span>特权用户</span>
              <span class="section-hint">优先级最高，覆盖角色限制</span>
            </div>
            <div
              v-for="u in card.userOverrides"
              :key="u.username"
              class="row-item row-item--user"
            >
              <div class="user-tag">
                <el-icon><User /></el-icon>
                <span class="user-name">{{ u.realName || u.username }}</span>
                <el-tag size="small" :type="tagType(u.role)" effect="plain">
                  {{ u.role }}
                </el-tag>
              </div>
              <div class="row-bar">
                <el-progress
                  :percentage="pct(u.bytes, card.capBytes)"
                  :stroke-width="8"
                  :color="barColor(u.bytes, card.capBytes)"
                  style="flex:1"
                />
                <span class="bar-hint">/ {{ card.capLabel }}</span>
              </div>
              <SizeInput
                v-model="card.editUsers[u.username]"
                :placeholder="card.globalValue.value"
              />
              <el-button
                type="primary"
                size="small"
                :loading="card.savingUsers[u.username]"
                @click="saveUser(card, u.username)"
              >更新</el-button>
              <el-button
                type="danger"
                size="small"
                plain
                @click="delUser(card, u.username)"
              >移除</el-button>
            </div>

            <!-- 添加特权用户 -->
            <div class="row-item row-item--add">
              <el-select
                v-model="card.addingUser"
                filterable
                remote
                :remote-method="(kw) => searchUser(card, kw)"
                :loading="card.searching"
                placeholder="输入用户名/姓名搜索"
                style="flex:1;min-width:200px"
                clearable
                value-key="username"
              >
                <el-option
                  v-for="u in card.userOptions"
                  :key="u.username"
                  :label="`${u.realName || u.username} (${u.username}) · ${u.role}`"
                  :value="u"
                >
                  <span style="float:left">{{ u.realName || u.username }}</span>
                  <span style="float:right;color:#999;font-size:12px">{{ u.role }}</span>
                </el-option>
              </el-select>
              <SizeInput v-model="card.addingSize" placeholder="留空=继承全局" />
              <el-button
                type="success"
                size="small"
                :disabled="!card.addingUser"
                :loading="card.adding"
                @click="addUser(card)"
              >设为特权</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- Excel 批量导入（独立卡片，无角色/用户覆盖） -->
      <el-card class="limit-card limit-card--full" shadow="never" style="margin-top:20px">
        <template #header>
          <div class="card-header">
            <el-icon><Document /></el-icon>
            <span>Excel 批量导入限制</span>
            <el-tag size="small" type="info" effect="plain" class="cap-tag">上限 100MB</el-tag>
          </div>
        </template>
        <div class="row-item">
          <div class="row-label">全局默认</div>
          <div class="row-bar">
            <el-progress
              :percentage="pct(excelGlobal.bytes, 104857600)"
              :stroke-width="10"
              :color="barColor(excelGlobal.bytes, 104857600)"
              style="flex:1"
            />
            <span class="bar-hint">/ 100MB</span>
          </div>
          <SizeInput v-model="editExcel" placeholder="10" />
          <el-button
            type="primary"
            size="small"
            :loading="savingExcel"
            @click="saveExcel"
          >保存</el-button>
        </div>
      </el-card>

      <!-- 备份文件导入（独立卡片，无角色/用户覆盖） -->
      <el-card class="limit-card limit-card--full" shadow="never" style="margin-top:20px">
        <template #header>
          <div class="card-header">
            <el-icon><Upload /></el-icon>
            <span>备份文件导入限制</span>
            <el-tag size="small" type="info" effect="plain" class="cap-tag">上限 10GB</el-tag>
          </div>
        </template>
        <div class="row-item">
          <div class="row-label">全局默认</div>
          <div class="row-bar">
            <el-progress
              :percentage="pct(backupImportGlobal.bytes, 10737418240)"
              :stroke-width="10"
              :color="barColor(backupImportGlobal.bytes, 10737418240)"
              style="flex:1"
            />
            <span class="bar-hint">/ 10GB</span>
          </div>
          <SizeInput v-model="editBackupImport" placeholder="2048" />
          <el-button
            type="primary"
            size="small"
            :loading="savingBackupImport"
            @click="saveBackupImport"
          >保存</el-button>
        </div>
      </el-card>
    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Document, Picture, VideoPlay, User, Upload } from '@element-plus/icons-vue'
import {
  getContentLimits, updateContentLimits, deleteContentLimit, searchUsersForLimit
} from '@/api/systemConfig'
import SizeInput from '@/components/SizeInput.vue'

const ROLES = ['ADMIN', 'TEACHER', 'STUDENT']

const loading = ref(true)
const savingExcel = ref(false)
const editExcel = ref('10MB')
const excelGlobal = reactive({ value: '10MB', bytes: 0 })

const savingBackupImport = ref(false)
const editBackupImport = ref('2048MB')
const backupImportGlobal = reactive({ value: '2048MB', bytes: 0 })

const cardDefs = [
  { type: 'image', title: '图片上传限制', icon: Picture,   capBytes: 52428800,  capLabel: '50MB' },
  { type: 'video', title: '视频上传限制', icon: VideoPlay, capBytes: 1073741824, capLabel: '1GB' }
]

const freshRoles  = () => ({ ADMIN: '', TEACHER: '', STUDENT: '' })
const freshSaving = () => ({ ADMIN: false, TEACHER: false, STUDENT: false })

const cards = ref(cardDefs.map(def => ({
  ...def,
  globalValue: { value: '10MB', bytes: 0 },
  roleOverrides: [],
  userOverrides: [],
  editGlobal: '10MB',
  editRoles: freshRoles(),
  editUsers: {},          // username -> size string
  savingGlobal: false,
  savingRoles: freshSaving(),
  userOptions: [],        // search results
  searching: false,
  addingUser: null,       // selected {id, username, realName, role}
  addingSize: '',
  adding: false,
  savingUsers: {}         // username -> bool
})))

const barColor = (bytes, cap) => {
  const p = (bytes || 0) / cap
  return p < 0.5 ? '#67c23a' : p < 0.8 ? '#e6a23c' : '#f56c6c'
}
const pct = (bytes, cap) => Math.min(100, Math.round(((bytes || 0) / cap) * 100))
const tagType = (role) =>
  role === 'ADMIN' ? 'danger' : role === 'TEACHER' ? 'warning' : 'success'

const getRoleOverride = (card, role) =>
  card.roleOverrides.find(o => o.role === role)

const load = async () => {
  loading.value = true
  try {
    const res = await getContentLimits()
    if (res?.code === 200 && res.data) {
      const d = res.data.defaults || {}
      const roleOv = res.data.roleOverrides || []
      const userOv = res.data.userOverrides || []
      cards.value.forEach(card => {
        Object.assign(card.globalValue, d[card.type] || {})
        card.editGlobal = card.globalValue.value || '10MB'
        card.roleOverrides = roleOv.filter(o => o.type === card.type)
        card.editRoles = freshRoles()
        card.roleOverrides.forEach(o => { card.editRoles[o.role] = o.value })
        card.userOverrides = userOv.filter(o => o.type === card.type)
        card.editUsers = {}
        card.userOverrides.forEach(o => { card.editUsers[o.username] = o.value })
        card.savingUsers = {}
        card.userOverrides.forEach(o => { card.savingUsers[o.username] = false })
      })
      Object.assign(excelGlobal, d.excelImport || {})
      editExcel.value = excelGlobal.value || '10MB'
      Object.assign(backupImportGlobal, d.backupImport || {})
      editBackupImport.value = backupImportGlobal.value || '2048MB'
    }
  } catch (e) {
    ElMessage.error('加载内容大小限制失败')
  } finally {
    loading.value = false
  }
}

const saveGlobal = async (card) => {
  card.savingGlobal = true
  try {
    await updateContentLimits([{ key: `upload.content_size.${card.type}`, value: card.editGlobal }])
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    card.savingGlobal = false
  }
}

const saveRole = async (card, role) => {
  if (!card.editRoles[role]) {
    ElMessage.warning('请输入覆盖值')
    return
  }
  card.savingRoles[role] = true
  try {
    const key = `upload.content_size.${card.type}.role.${role}`
    await updateContentLimits([{ key, value: card.editRoles[role] }])
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    card.savingRoles[role] = false
  }
}

const delRole = async (card, role) => {
  try {
    await ElMessageBox.confirm('重置该角色为全局默认？', '确认', { type: 'warning' })
    const key = `upload.content_size.${card.type}.role.${role}`
    await deleteContentLimit(key)
    ElMessage.success('已重置')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('重置失败')
  }
}

const saveUser = async (card, username) => {
  if (!card.editUsers[username]) {
    ElMessage.warning('请输入限制值')
    return
  }
  card.savingUsers[username] = true
  try {
    const key = `upload.content_size.${card.type}.user.${username}`
    await updateContentLimits([{ key, value: card.editUsers[username] }])
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    card.savingUsers[username] = false
  }
}

const delUser = async (card, username) => {
  try {
    await ElMessageBox.confirm(`移除 ${username} 的「${card.title}」特权？`, '确认', { type: 'warning' })
    const key = `upload.content_size.${card.type}.user.${username}`
    await deleteContentLimit(key)
    ElMessage.success('已移除')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('移除失败')
  }
}

const searchUser = async (card, keyword) => {
  card.searching = true
  try {
    const res = await searchUsersForLimit(keyword || '', '')
    if (res?.code === 200) {
      // 过滤掉已经是特权的用户，避免重复
      const taken = new Set(card.userOverrides.map(o => o.username))
      card.userOptions = (res.data || []).filter(u => !taken.has(u.username))
    }
  } catch (e) {
    // 静默失败
  } finally {
    card.searching = false
  }
}

const addUser = async (card) => {
  if (!card.addingUser) {
    ElMessage.warning('请选择用户')
    return
  }
  const username = card.addingUser.username
  const value = card.addingSize || card.globalValue.value
  card.adding = true
  try {
    const key = `upload.content_size.${card.type}.user.${username}`
    await updateContentLimits([{ key, value }])
    ElMessage.success(`已为 ${card.addingUser.realName || username} 设为特权用户`)
    card.addingUser = null
    card.addingSize = ''
    await load()
  } catch (e) {
    ElMessage.error('设置失败')
  } finally {
    card.adding = false
  }
}

const saveExcel = async () => {
  savingExcel.value = true
  try {
    await updateContentLimits([{ key: 'upload.content_size.excel_import', value: editExcel.value }])
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingExcel.value = false
  }
}

const saveBackupImport = async () => {
  savingBackupImport.value = true
  try {
    await updateContentLimits([{ key: 'upload.content_size.backup_import', value: editBackupImport.value }])
    ElMessage.success('已保存')
    await load()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingBackupImport.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.content-limits-page { padding: 20px; max-width: 1200px; }
.page-header { margin-bottom: 24px; }
.page-header h2 { margin: 0 0 6px; font-size: 20px; font-weight: 600; }
.page-desc { margin: 0; font-size: 13px; color: var(--el-text-color-secondary); }
.loading-state { display: flex; flex-direction: column; align-items: center; padding: 60px 0; gap: 12px; color: var(--el-text-color-secondary); }

.limit-card { border-radius: 12px; }
.limit-card--full { max-width: 100%; }
.card-header { display: flex; align-items: center; gap: 8px; font-size: 15px; font-weight: 600; }
.cap-tag { margin-left: auto; font-weight: normal; }

.row-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
}
.row-item + .row-item { border-top: 1px solid var(--el-border-color-lighter); }
.row-item--role,
.row-item--user,
.row-item--add { padding: 6px 0; }
.row-item--add {
  margin-top: 4px;
  border-top: 1px dashed var(--el-border-color-lighter) !important;
  padding-top: 12px;
}

.row-label {
  width: 80px;
  font-size: 13px;
  color: var(--el-text-color-secondary);
  flex-shrink: 0;
}
.section-label { font-weight: 600; color: var(--el-text-color-primary); margin-bottom: 4px; }
.flex-between { display: flex; justify-content: space-between; align-items: baseline; width: 100%; }
.section-hint { font-size: 11px; font-weight: normal; color: var(--el-text-color-placeholder); }

.row-bar { flex: 1; display: flex; align-items: center; gap: 6px; min-width: 0; }
.bar-hint { font-size: 12px; color: var(--el-text-color-placeholder); white-space: nowrap; }
.inherit-hint { font-size: 12px; color: var(--el-text-color-placeholder); flex: 1; }

.user-tag {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 130px;
  flex-shrink: 0;
  font-size: 13px;
}
.user-name {
  max-width: 70px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 768px) {
  .content-limits-page { padding: 12px; }
  .row-item { flex-wrap: wrap; }
  .row-bar { width: 100%; flex-basis: 100%; }
  .row-label { width: 100%; }
  .user-tag { width: 100%; }
}
</style>