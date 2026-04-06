<template>
  <div class="feature-toggles-page">
    <div class="page-header">
      <h2>功能开关管理</h2>
      <p class="subtitle">三层优先级：用户级 &gt; 标签级 &gt; 全局默认</p>
    </div>

    <el-tabs v-model="activeTab" class="main-tabs">

      <!-- ── Tab 1：全局开关 ──────────────────────────────────────── -->
      <el-tab-pane label="全局开关" name="global">
        <div v-if="loadingGlobal" class="loading-state">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
        <div v-else class="toggles-list">
          <div v-for="toggle in toggles" :key="toggle.configKey" class="toggle-card">
            <div class="toggle-info">
              <h3 class="toggle-name">{{ toggle.description }}</h3>
              <span class="toggle-key">{{ toggle.configKey }}</span>
            </div>
            <el-switch
              v-model="toggle.enabled"
              @change="(val) => handleToggle(toggle, val)"
              :loading="toggle.saving"
              active-text="启用"
              inactive-text="关闭"
            />
          </div>
        </div>
        <div class="save-hint" v-if="!loadingGlobal">
          <el-icon><InfoFilled /></el-icon>
          <span>全局开关即时生效，学生刷新后可见变化</span>
        </div>
      </el-tab-pane>

      <!-- ── Tab 2：标签管理 ──────────────────────────────────────── -->
      <el-tab-pane label="标签管理" name="tags">

        <el-divider>标签覆盖规则</el-divider>

        <!-- 两栏：标签列表 + 标签成员 -->
        <div class="tags-two-col">
          <!-- 左栏：标签列表 -->
          <div class="tag-list-panel">
            <div class="tag-list-header">
              <span class="tag-list-title">所有标签</span>
              <el-button
                link
                size="small"
                :loading="loadingAllTags"
                @click="loadAllTags"
              >
                <el-icon><Refresh /></el-icon>
              </el-button>
            </div>
            <div v-if="loadingAllTags" class="loading-state">
              <el-icon class="is-loading" :size="24"><Loading /></el-icon>
            </div>
            <div v-else-if="allTags.length === 0" class="empty-tags">暂无标签</div>
            <div v-else class="tag-chip-list">
              <div
                v-for="tag in allTags"
                :key="tag"
                class="tag-chip"
                :class="{ 'tag-chip--active': selectedTag === tag }"
                @click="selectTag(tag)"
              >{{ tag }}</div>
            </div>
          </div>

          <!-- 右栏：标签成员 -->
          <div class="tag-members-panel">
            <div v-if="!selectedTag" class="members-placeholder">
              请选择左侧标签查看成员
            </div>
            <template v-else>
              <div class="members-header">
                <span class="members-title">标签「{{ selectedTag }}」的成员</span>
                <el-button
                  link
                  size="small"
                  :loading="loadingTagMembers"
                  @click="loadTagMembers(selectedTag)"
                >
                  <el-icon><Refresh /></el-icon> 刷新
                </el-button>
              </div>
              <div v-if="loadingTagMembers" class="loading-state">
                <el-icon class="is-loading" :size="28"><Loading /></el-icon>
                <p>加载中...</p>
              </div>
              <el-table v-else :data="tagMembers" border stripe size="small">
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="username" label="用户名" />
                <el-table-column label="昵称">
                  <template #default="{ row }">
                    {{ row.nickname || row.realName || '-' }}
                  </template>
                </el-table-column>
                <el-table-column label="角色" width="100">
                  <template #default="{ row }">
                    <el-tag :type="roleTagType(row.role)" size="small">{{ row.role }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="80" fixed="right">
                  <template #default="{ row }">
                    <el-button link type="danger" size="small" @click="removeUserFromTag(row)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div v-if="!loadingTagMembers && tagMembers.length === 0" class="members-empty">该标签下暂无用户</div>
            </template>
          </div>
        </div>

        <div class="section-toolbar">
          <el-button type="primary" size="small" @click="openTagOverrideDialog(null)">
            <el-icon><Plus /></el-icon> 新建标签覆盖
          </el-button>
        </div>

        <div v-if="loadingTags" class="loading-state">
          <el-icon class="is-loading" :size="28"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
        <el-table v-else :data="tagOverrides" border stripe>
          <el-table-column prop="scopeValue" label="标签名" width="140" />
          <el-table-column prop="featureKey" label="功能键" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
                {{ row.enabled === 1 ? '启用' : '关闭' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="备注" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openTagOverrideDialog(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeOverride('tag', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 设置用户标签 -->
        <el-divider>用户标签分配</el-divider>
        <div class="user-tag-section">
          <div class="user-search-row">
            <el-input
              v-model="tagUserSearch"
              placeholder="搜索用户名或 ID"
              style="width:200px"
              clearable
              @keyup.enter="searchTagUsers"
            />
            <el-button @click="searchTagUsers" :loading="searchingUsers">搜索</el-button>
          </div>

          <!-- 搜索结果列表 -->
          <el-table
            v-if="tagUserResults.length > 0"
            :data="tagUserResults"
            size="small"
            style="margin-top:10px"
            highlight-current-row
            @current-change="selectTagUser"
          >
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button link type="primary" size="small" @click="selectTagUser(row)">选择</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 已选用户的标签编辑 -->
          <div v-if="tagUserId" class="selected-user-tags">
            <div class="selected-user-info">
              <el-icon><User /></el-icon>
              <span>用户 <strong>{{ tagUserName || tagUserId }}</strong> 的标签：</span>
            </div>
            <div class="user-tag-row">
              <el-tag
                v-for="t in currentUserTags"
                :key="t"
                closable
                @close="removeUserTag(t)"
                class="user-tag-chip"
              >{{ t }}</el-tag>
              <el-input
                v-if="showTagInput"
                ref="tagInputRef"
                v-model="newTagName"
                placeholder="新标签"
                style="width:120px"
                size="small"
                @keyup.enter="confirmAddUserTag"
                @blur="confirmAddUserTag"
              />
              <el-button v-else size="small" @click="showTagInput = true">+ 添加标签</el-button>
              <el-button type="primary" size="small" @click="saveUserTags">保存标签</el-button>
            </div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ── Tab 3：用户覆盖 ──────────────────────────────────────── -->
      <el-tab-pane label="用户覆盖" name="users">
        <div class="section-toolbar">
          <el-input
            v-model="overrideFilterUserId"
            placeholder="按用户 ID 筛选"
            style="width:180px"
            clearable
            @change="loadUserOverrides"
          />
          <el-button type="primary" size="small" @click="openUserOverrideDialog(null)">
            <el-icon><Plus /></el-icon> 新建用户覆盖
          </el-button>
        </div>

        <div v-if="loadingUserOverrides" class="loading-state">
          <el-icon class="is-loading" :size="28"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
        <el-table v-else :data="userOverrides" border stripe>
          <el-table-column prop="scopeValue" label="用户 ID" width="120" />
          <el-table-column prop="featureKey" label="功能键" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="row.enabled === 1 ? 'success' : 'danger'" size="small">
                {{ row.enabled === 1 ? '启用' : '关闭' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="备注" />
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" size="small" @click="openUserOverrideDialog(row)">编辑</el-button>
              <el-button link type="danger" size="small" @click="removeOverride('user', row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

    </el-tabs>

    <!-- ── 覆盖规则编辑对话框 ──────────────────────────────────────── -->
    <el-dialog v-model="overrideDialogVisible" :title="overrideForm.isNew ? '新建覆盖规则' : '编辑覆盖规则'" width="420px">
      <el-form :model="overrideForm" label-width="90px">
        <el-form-item label="作用域" v-if="overrideForm.isNew">
          <el-select v-model="overrideForm.scope" style="width:100%">
            <el-option label="标签 (tag)" value="tag" />
            <el-option label="用户 (user)" value="user" />
          </el-select>
        </el-form-item>
        <el-form-item :label="overrideForm.scope === 'tag' ? '标签名' : '用户 ID'">
          <el-input v-model="overrideForm.scopeValue" :disabled="!overrideForm.isNew" />
        </el-form-item>
        <el-form-item label="功能键">
          <el-select v-model="overrideForm.featureKey" style="width:100%" :disabled="!overrideForm.isNew" allow-create filterable>
            <el-option v-for="t in toggles" :key="t.configKey" :label="t.configKey" :value="t.configKey" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="overrideForm.enabled">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">关闭</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="overrideForm.description" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="overrideDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingOverride" @click="saveOverride">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, InfoFilled, Plus, User, Refresh } from '@element-plus/icons-vue'
import {
  getSystemConfig, updateSystemConfig,
  listAllTags, getUsersByTag, deleteTag, getUserTags, setUserTags,
  listFeatureOverrides, upsertFeatureOverride, deleteFeatureOverride,
  searchUsers
} from '@/api/systemConfig'

const activeTab = ref('global')

watch(activeTab, (val) => {
  if (val === 'tags') loadAllTags()
})

// ── Tab1：全局开关 ──────────────────────────────────────────────────
const loadingGlobal = ref(true)
const toggles = ref([])

const loadToggles = async () => {
  loadingGlobal.value = true
  try {
    const res = await getSystemConfig('feature_toggle')
    if (res?.code === 200 && Array.isArray(res.data)) {
      toggles.value = res.data.map(item => ({
        ...item,
        enabled: item.configValue === 'true',
        saving: false
      }))
    }
  } catch (e) {
    ElMessage.error('加载功能开关失败')
  } finally {
    loadingGlobal.value = false
  }
}

const handleToggle = async (toggle, enabled) => {
  toggle.saving = true
  try {
    await updateSystemConfig([{ ...toggle, configValue: enabled ? 'true' : 'false' }])
    ElMessage.success(`${toggle.description} 已${enabled ? '启用' : '关闭'}`)
  } catch (e) {
    ElMessage.error('更新失败')
    toggle.enabled = !enabled
  } finally {
    toggle.saving = false
  }
}

// ── Tab2：标签管理 ──────────────────────────────────────────────────
const loadingTags = ref(false)
const tagOverrides = ref([])

// 标签列表（左栏）
const allTags = ref([])
const loadingAllTags = ref(false)
const selectedTag = ref(null)
const tagMembers = ref([])
const loadingTagMembers = ref(false)

const loadAllTags = async () => {
  loadingAllTags.value = true
  try {
    const res = await listAllTags()
    if (res?.code === 200) allTags.value = res.data || []
  } catch (e) {
    ElMessage.error('加载标签列表失败')
  } finally {
    loadingAllTags.value = false
  }
}

const selectTag = async (tag) => {
  selectedTag.value = tag
  await loadTagMembers(tag)
}

const loadTagMembers = async (tag) => {
  if (!tag) return
  loadingTagMembers.value = true
  try {
    const res = await getUsersByTag(tag)
    if (res?.code === 200) tagMembers.value = res.data || []
  } catch (e) {
    ElMessage.error('加载标签成员失败')
  } finally {
    loadingTagMembers.value = false
  }
}

const removeUserFromTag = async (user) => {
  try {
    await ElMessageBox.confirm(
      `确认将用户 ${user.username} 从标签「${selectedTag.value}」移除？`,
      '提示',
      { type: 'warning' }
    )
    const tagsRes = await getUserTags(user.id)
    const currentTags = tagsRes?.code === 200 ? (tagsRes.data || []) : []
    const newTags = currentTags.filter(t => t !== selectedTag.value)
    await setUserTags(user.id, newTags)
    ElMessage.success('已移除')
    await loadTagMembers(selectedTag.value)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

const roleTagType = (role) => {
  if (role === 'STUDENT') return 'info'
  if (role === 'TEACHER') return 'warning'
  if (role === 'ADMIN') return 'danger'
  return ''
}

const loadTagOverrides = async () => {
  loadingTags.value = true
  try {
    const res = await listFeatureOverrides('tag', null)
    if (res?.code === 200) tagOverrides.value = res.data || []
  } catch (e) {
    ElMessage.error('加载标签覆盖失败')
  } finally {
    loadingTags.value = false
  }
}

// 用户标签分配
const tagUserId = ref('')
const tagUserName = ref('')
const tagUserSearch = ref('')
const tagUserResults = ref([])
const searchingUsers = ref(false)
const currentUserTags = ref([])
const showTagInput = ref(false)
const newTagName = ref('')
const tagInputRef = ref(null)

const searchTagUsers = async () => {
  if (!tagUserSearch.value.trim()) return
  searchingUsers.value = true
  try {
    const res = await searchUsers({ keyword: tagUserSearch.value, page: 1, size: 10 })
    if (res?.code === 200) {
      tagUserResults.value = res.data?.records || res.data || []
    }
  } catch (e) {
    ElMessage.error('搜索用户失败')
  } finally {
    searchingUsers.value = false
  }
}

const selectTagUser = async (row) => {
  if (!row) return
  tagUserId.value = String(row.id)
  tagUserName.value = row.username || row.nickname || String(row.id)
  tagUserResults.value = []
  tagUserSearch.value = ''
  await loadUserTags()
}

const loadUserTags = async () => {
  if (!tagUserId.value) return
  try {
    const res = await getUserTags(tagUserId.value)
    currentUserTags.value = res?.code === 200 ? (res.data || []) : []
  } catch (e) {
    ElMessage.error('查询用户标签失败')
  }
}

const confirmAddUserTag = () => {
  const t = newTagName.value.trim()
  if (t && !currentUserTags.value.includes(t)) {
    currentUserTags.value.push(t)
  }
  newTagName.value = ''
  showTagInput.value = false
}

const removeUserTag = (tag) => {
  currentUserTags.value = currentUserTags.value.filter(t => t !== tag)
}

const saveUserTags = async () => {
  if (!tagUserId.value) return
  try {
    await setUserTags(tagUserId.value, currentUserTags.value)
    ElMessage.success('标签已保存')
  } catch (e) {
    ElMessage.error('保存标签失败')
  }
}

// ── Tab3：用户覆盖 ──────────────────────────────────────────────────
const loadingUserOverrides = ref(false)
const userOverrides = ref([])
const overrideFilterUserId = ref('')

const loadUserOverrides = async () => {
  loadingUserOverrides.value = true
  try {
    const res = await listFeatureOverrides('user', overrideFilterUserId.value || null)
    if (res?.code === 200) userOverrides.value = res.data || []
  } catch (e) {
    ElMessage.error('加载用户覆盖失败')
  } finally {
    loadingUserOverrides.value = false
  }
}

// ── 通用覆盖规则对话框 ────────────────────────────────────────────
const overrideDialogVisible = ref(false)
const savingOverride = ref(false)
const overrideForm = ref({
  isNew: true,
  scope: 'tag',
  scopeValue: '',
  featureKey: '',
  enabled: 1,
  description: ''
})

const openTagOverrideDialog = (row) => {
  overrideForm.value = row
    ? { isNew: false, scope: 'tag', scopeValue: row.scopeValue, featureKey: row.featureKey, enabled: row.enabled, description: row.description || '' }
    : { isNew: true, scope: 'tag', scopeValue: '', featureKey: '', enabled: 1, description: '' }
  overrideDialogVisible.value = true
}

const openUserOverrideDialog = (row) => {
  overrideForm.value = row
    ? { isNew: false, scope: 'user', scopeValue: row.scopeValue, featureKey: row.featureKey, enabled: row.enabled, description: row.description || '' }
    : { isNew: true, scope: 'user', scopeValue: '', featureKey: '', enabled: 1, description: '' }
  overrideDialogVisible.value = true
}

const saveOverride = async () => {
  const f = overrideForm.value
  if (!f.scopeValue || !f.featureKey) {
    ElMessage.warning('请填写完整信息')
    return
  }
  savingOverride.value = true
  try {
    await upsertFeatureOverride({
      scope: f.scope,
      scopeValue: f.scopeValue,
      featureKey: f.featureKey,
      enabled: f.enabled,
      description: f.description
    })
    ElMessage.success('保存成功')
    overrideDialogVisible.value = false
    if (f.scope === 'tag') loadTagOverrides()
    else loadUserOverrides()
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    savingOverride.value = false
  }
}

const removeOverride = async (scope, row) => {
  try {
    await ElMessageBox.confirm(`确认删除该覆盖规则？`, '提示', { type: 'warning' })
    await deleteFeatureOverride(scope, row.scopeValue, row.featureKey)
    ElMessage.success('已删除')
    if (scope === 'tag') loadTagOverrides()
    else loadUserOverrides()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

onMounted(() => {
  loadToggles()
  loadAllTags()
  loadTagOverrides()
  loadUserOverrides()
})
</script>

<style scoped>
.feature-toggles-page {
  max-width: 900px;
  margin: 0 auto;
  padding: 24px;
}

.page-header {
  margin-bottom: 20px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 20px;
  font-weight: 700;
}

.subtitle {
  margin: 0;
  font-size: 13px;
  color: #64748b;
}

.main-tabs {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e2e8f0;
}

.toggles-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-top: 8px;
}

.toggle-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px 18px;
}

.toggle-info { flex: 1; }

.toggle-name {
  margin: 0 0 3px;
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}

.toggle-key {
  font-size: 12px;
  color: #94a3b8;
  font-family: monospace;
}

.save-hint {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 16px;
  padding: 10px 14px;
  background: #f0f9ff;
  border-radius: 8px;
  font-size: 13px;
  color: #0369a1;
}

.section-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.user-tag-section {
  margin-top: 8px;
}

.user-search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.selected-user-tags {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.selected-user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
  font-size: 13px;
  color: #475569;
}

.user-tag-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 8px;
}

.user-tag-chip {
  cursor: default;
}

.loading-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 32px;
  color: #94a3b8;
}

/* ── 标签管理两栏布局 ───────────────────────────────────────────── */
.tags-two-col {
  display: flex;
  gap: 16px;
  margin-bottom: 8px;
  min-height: 200px;
}

.tag-list-panel {
  width: 200px;
  flex-shrink: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  background: #f8fafc;
  display: flex;
  flex-direction: column;
}

.tag-list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.tag-list-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.empty-tags {
  font-size: 12px;
  color: #94a3b8;
  text-align: center;
  padding: 20px 0;
}

.tag-chip-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  overflow-y: auto;
}

.tag-chip {
  padding: 6px 12px;
  border-radius: 20px;
  background: #e2e8f0;
  color: #475569;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.tag-chip:hover {
  background: #bfdbfe;
  color: #1d4ed8;
}

.tag-chip--active {
  background: #3b82f6;
  color: #fff;
}

.tag-members-panel {
  flex: 1;
  min-width: 0;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  background: #fff;
}

.members-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 160px;
  color: #94a3b8;
  font-size: 14px;
}

.members-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}

.members-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.members-empty {
  text-align: center;
  color: #94a3b8;
  font-size: 13px;
  padding: 16px 0;
}
</style>
