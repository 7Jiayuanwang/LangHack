<template>
  <div class="feature-toggles-page">
    <div class="page-header">
      <div class="header-left">
        <h2>功能开关管理</h2>
        <div class="priority-badges">
          <span class="priority-label">三层优先级策略：</span>
          <el-tag size="small" effect="light" type="danger">1. 用户级</el-tag>
          <el-icon><ArrowRight /></el-icon>
          <el-tag size="small" effect="light" type="warning">2. 标签级</el-tag>
          <el-icon><ArrowRight /></el-icon>
          <el-tag size="small" effect="light" type="info">3. 全局默认</el-tag>
        </div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="main-tabs premium-tabs">

      <!-- ── Tab 1：全局开关 ──────────────────────────────────────── -->
      <el-tab-pane label="全局开关" name="global">
        <div v-if="loadingGlobal" class="loading-state">
          <el-icon class="is-loading" :size="32"><Loading /></el-icon>
          <p>加载中...</p>
        </div>
        <template v-else>
          <div class="role-filter-row">
            <span class="role-filter-label">按角色筛选：</span>
            <el-radio-group v-model="roleFilter" size="default">
              <el-radio-button value="all">
                全部 <el-tag size="small" round>{{ toggles.length }}</el-tag>
              </el-radio-button>
              <el-radio-button
                v-for="g in groupDefs"
                :key="g.key"
                :value="g.key"
              >
                {{ g.label }}
                <el-tag size="small" round :type="countTagType(g.key)">
                  {{ groupCounts[g.key] || 0 }}
                </el-tag>
              </el-radio-button>
            </el-radio-group>
          </div>

          <el-collapse v-model="activeGroupPanels" class="group-collapse">
            <el-collapse-item
              v-for="group in visibleGroupedToggles"
              :key="group.key"
              :name="group.key"
            >
              <template #title>
                <div class="group-title">
                  <el-icon class="group-icon" :class="`group-icon--${group.key}`">
                    <component :is="group.iconName" />
                  </el-icon>
                  <span class="group-label">{{ group.label }}</span>
                  <el-tag size="small" :type="countTagType(group.key)" round class="group-count">
                    {{ group.items.length }}
                  </el-tag>
                  <span class="group-desc">{{ group.desc }}</span>
                </div>
              </template>
              <div class="toggles-list">
                <div
                  v-for="toggle in group.items"
                  :key="toggle.configKey"
                  class="toggle-card"
                >
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
            </el-collapse-item>
          </el-collapse>

          <div v-if="visibleGroupedToggles.length === 0" class="empty-filter">
            当前筛选无任何开关
          </div>

          <div class="save-hint">
            <el-icon><InfoFilled /></el-icon>
            <span>全局开关即时生效；用户刷新页面后立即可见变化</span>
          </div>
        </template>
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
import { ref, onMounted, nextTick, watch, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, InfoFilled, Plus, User, Refresh, Monitor, Management, MoreFilled, Reading, ArrowRight } from '@element-plus/icons-vue'
import { useFeatureToggles, invalidateFeatureToggleCache } from '@/composables/useFeatureToggles'
import {
  getSystemConfig, updateSystemConfig,
  listAllTags, getUsersByTag, deleteTag, getUserTags, setUserTags,
  listFeatureOverrides, upsertFeatureOverride, deleteFeatureOverride,
  searchUsers
} from '@/api/systemConfig'

const activeTab = ref('global')
const { load: loadFeatureToggleRuntime } = useFeatureToggles()

watch(activeTab, (val) => {
  if (val === 'tags') loadAllTags()
})

// ── Tab1：全局开关 ──────────────────────────────────────────────────
const loadingGlobal = ref(true)
const toggles = ref([])

// 角色分组定义（按 configKey 前缀分组）
const groupDefs = [
  { key: 'student', label: '学生端', desc: '学生端页面与导航可见性', prefixes: ['page_'], excludePrefixes: ['page_teacher_', 'page_admin_', 'page_ops_'], iconName: 'User' },
  { key: 'teacher', label: '教师端', desc: '教师端页面与功能',       prefixes: ['page_teacher_'], excludePrefixes: [], iconName: 'Reading' },
  { key: 'admin',   label: '管理员端', desc: '管理员模块访问与功能',  prefixes: ['admin.', 'page_admin_'], excludePrefixes: [], iconName: 'Management' },
  { key: 'ops',     label: '运维端',  desc: '运维工具与数据集成配置',  prefixes: ['ops.', 'page_ops_'], excludePrefixes: [], iconName: 'Monitor' },
  { key: 'other',   label: '其他',    desc: '未归类的全局开关',        prefixes: [], excludePrefixes: [], iconName: 'MoreFilled' }
]

const roleFilter = ref('all')
const activeGroupPanels = ref(['student', 'teacher', 'admin', 'ops', 'other'])

const categorize = (key) => {
  if (!key) return 'other'

  // 按优先级匹配（更具体的前缀优先）
  for (const g of groupDefs) {
    if (g.key === 'other') continue

    // 检查排除前缀
    if (g.excludePrefixes.some(ex => key.startsWith(ex))) continue

    // 检查匹配前缀
    if (g.prefixes.some(p => key.startsWith(p))) return g.key
  }

  return 'other'
}

const groupedToggles = computed(() => {
  const groups = groupDefs.map(g => ({ ...g, items: [] }))
  const otherGroup = groups.find(g => g.key === 'other')
  for (const t of toggles.value) {
    const cat = categorize(t.configKey)
    const target = groups.find(g => g.key === cat) || otherGroup
    target.items.push(t)
  }
  return groups.filter(g => g.items.length > 0)
})

const visibleGroupedToggles = computed(() => {
  if (roleFilter.value === 'all') return groupedToggles.value
  return groupedToggles.value.filter(g => g.key === roleFilter.value)
})

const groupCounts = computed(() => {
  const counts = {}
  for (const g of groupedToggles.value) counts[g.key] = g.items.length
  return counts
})

const countTagType = (key) => {
  switch (key) {
    case 'student': return 'info'
    case 'teacher': return 'success'
    case 'admin':   return 'danger'
    case 'ops':     return 'warning'
    default:        return ''
  }
}

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
    await refreshFeatureToggleRuntime('global_toggle_updated')
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
    await refreshFeatureToggleRuntime('user_removed_from_tag')
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
    await refreshFeatureToggleRuntime('user_tags_updated')
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
    await refreshFeatureToggleRuntime('feature_override_upserted')
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
    await refreshFeatureToggleRuntime('feature_override_deleted')
    ElMessage.success('已删除')
    if (scope === 'tag') loadTagOverrides()
    else loadUserOverrides()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

const refreshFeatureToggleRuntime = async (reason) => {
  invalidateFeatureToggleCache(reason)
  try {
    await loadFeatureToggleRuntime({ authenticated: true, force: true })
  } catch {
    // fall back to lazy reload on next guarded navigation
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
  max-width: 1400px;
  margin: 0 auto;
  padding: 20px;
}

/* PC端大字号适配 */
@media (min-width: 1200px) {
  .feature-toggles-page {
    padding: 30px;
  }
  
  .page-header h2 {
    font-size: 28px;
  }

  :deep(.el-tabs__item) {
    font-size: 16px;
    height: 48px;
  }

  .toggle-name {
    font-size: 16px;
  }

  .group-label {
    font-size: 18px;
  }

  :deep(.el-table) {
    font-size: 14px;
  }
}

.page-header {
  margin-bottom: 32px;
}

.priority-badges {
  display: flex;
  align-items: center;
  gap: 8px;
}

.priority-label {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}

.premium-tabs {
  background: white;
  border-radius: 24px;
  padding: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 10px 30px rgba(0, 0, 0, 0.03);
}

:deep(.el-tabs__header) {
  margin-bottom: 24px;
  padding: 0 16px;
}

:deep(.el-tabs__nav-wrap::after) {
  display: none;
}

:deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 600;
  height: 44px;
}

.toggles-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: 16px;
  margin-top: 12px;
}

.toggle-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  padding: 16px 20px;
  transition: all 0.2s;
}

.toggle-card:hover {
  background: white;
  border-color: #4f46e5;
  box-shadow: 0 8px 16px rgba(79, 70, 229, 0.05);
}

.toggle-name {
  margin: 0 0 4px;
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}

.role-filter-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  padding: 12px 16px;
  background: #f1f5f9;
  border-radius: 16px;
}

.group-collapse {
  border: none;
}

:deep(.group-collapse .el-collapse-item) {
  border: 1px solid #e2e8f0;
  border-radius: 16px;
  margin-bottom: 12px;
  overflow: hidden;
}

:deep(.group-collapse .el-collapse-item__header) {
  border-bottom: none;
  background: white;
  padding: 12px 20px;
}

:deep(.group-collapse .el-collapse-item__wrap) {
  border-bottom: none;
  background: #f8fafc;
}

.tags-two-col {
  display: flex;
  gap: 20px;
  margin-bottom: 24px;
}

.tag-list-panel {
  width: 240px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 20px;
}

.tag-members-panel {
  flex: 1;
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  padding: 20px;
}

.tag-chip {
  padding: 8px 16px;
  border-radius: 12px;
  margin-bottom: 8px;
  font-weight: 600;
  transition: all 0.2s;
}

.tag-chip--active {
  background: #4f46e5;
  box-shadow: 0 4px 12px rgba(79, 70, 229, 0.2);
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
