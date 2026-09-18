<template>
  <div class="favorites-page">
    <XyPageHeader class="favorites-intro" title="我的收藏" eyebrow="SAVED FOR LATER" description="值得回看的文章与例句，都在这里。" variant="compact" />

    <div class="favorite-tabs" role="tablist" aria-label="收藏类型">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab-button"
        :class="{ active: activeTab === tab.key }"
        type="button"
        role="tab"
        :aria-selected="activeTab === tab.key"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
        <span v-if="tab.count !== undefined" class="tab-count">{{ tab.count }}</span>
      </button>
    </div>

    <section v-if="activeTab === 'articles'" class="favorite-content">
      <div v-if="articleLoading" class="state-view">
        <el-icon class="is-loading" :size="30"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="articles.length === 0" class="state-view">
        <el-icon :size="44"><Star /></el-icon>
        <h2>暂无文章收藏</h2>
        <p>阅读文章时点击收藏按钮即可添加</p>
      </div>
      <div v-else class="favorite-list">
        <article
          v-for="item in articles"
          :key="item.id"
          class="article-item"
          tabindex="0"
          @click="readArticle(item.textId)"
          @keydown.enter="readArticle(item.textId)"
        >
          <h2>{{ item.title }}</h2>
          <p>{{ item.snippet }}</p>
          <div class="item-meta">
            <span>{{ difficultyText(item.difficulty) }}</span>
            <time>{{ formatDate(item.favoritedAt) }}</time>
          </div>
        </article>
      </div>
      <PageControls
        :page="articlePage"
        :pages="articlePages"
        @previous="articlePage--"
        @next="articlePage++"
      />
    </section>

    <section v-else class="favorite-content">
      <div v-if="sentenceLoading" class="state-view">
        <el-icon class="is-loading" :size="30"><Loading /></el-icon>
        <span>加载中...</span>
      </div>
      <div v-else-if="sentences.length === 0" class="state-view">
        <el-icon :size="44"><Star /></el-icon>
        <h2>暂无例句收藏</h2>
        <p>阅读文章时选中例句，在更多操作中点击“收藏例句”</p>
      </div>
      <div v-else class="favorite-list">
        <article v-for="item in sentences" :key="item.id" class="sentence-item">
          <div class="sentence-header">
            <button type="button" class="source-link" @click="readArticle(item.textId)">
              {{ item.sourceTitle || '查看原文' }}
            </button>
            <el-button
              text
              circle
              type="danger"
              :loading="removingId === item.id"
              title="取消收藏"
              @click="removeSentence(item)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <blockquote>{{ item.sentence }}</blockquote>
          <time>{{ formatDate(item.createdTime) }}</time>
        </article>
      </div>
      <PageControls
        :page="sentencePage"
        :pages="sentencePages"
        @previous="sentencePage--"
        @next="sentencePage++"
      />
    </section>
  </div>
</template>

<script setup>
import { computed, defineComponent, h, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Loading, Star } from '@element-plus/icons-vue'
import { getFavoriteArticles } from '@/api/student'
import { deleteSentenceFavorite, getSentenceFavorites } from '@/api/sentenceFavorite'
import XyPageHeader from '@/components/ui/XyPageHeader.vue'

const PAGE_SIZE = 10
const router = useRouter()
const activeTab = ref('articles')
const articles = ref([])
const sentences = ref([])
const articleLoading = ref(false)
const sentenceLoading = ref(false)
const articlePage = ref(1)
const sentencePage = ref(1)
const articleTotal = ref(0)
const sentenceTotal = ref(0)
const removingId = ref(null)

const tabs = ref([
  { key: 'articles', label: '文章收藏', count: undefined },
  { key: 'sentences', label: '例句收藏', count: undefined }
])

const articlePages = computed(() => Math.max(1, Math.ceil(articleTotal.value / PAGE_SIZE)))
const sentencePages = computed(() => Math.max(1, Math.ceil(sentenceTotal.value / PAGE_SIZE)))

const PageControls = defineComponent({
  props: { page: Number, pages: Number },
  emits: ['previous', 'next'],
  setup(props, { emit }) {
    return () => props.pages > 1
      ? h('nav', { class: 'page-controls', 'aria-label': '分页' }, [
          h('button', { type: 'button', disabled: props.page <= 1, onClick: () => emit('previous') }, '上一页'),
          h('span', `${props.page} / ${props.pages}`),
          h('button', { type: 'button', disabled: props.page >= props.pages, onClick: () => emit('next') }, '下一页')
        ])
      : null
  }
})

const loadArticles = async () => {
  articleLoading.value = true
  try {
    const response = await getFavoriteArticles({ page: articlePage.value, size: PAGE_SIZE })
    articles.value = response?.data?.records || []
    articleTotal.value = response?.data?.total || 0
    tabs.value[0].count = articleTotal.value
  } catch (error) {
    ElMessage.error('加载文章收藏失败')
  } finally {
    articleLoading.value = false
  }
}

const loadSentences = async () => {
  sentenceLoading.value = true
  try {
    const response = await getSentenceFavorites({ page: sentencePage.value, size: PAGE_SIZE })
    sentences.value = response?.data?.records || []
    sentenceTotal.value = response?.data?.total || 0
    tabs.value[1].count = sentenceTotal.value
  } catch (error) {
    ElMessage.error('加载例句收藏失败')
  } finally {
    sentenceLoading.value = false
  }
}

const removeSentence = async (item) => {
  try {
    await ElMessageBox.confirm('确定取消收藏这条例句？', '取消收藏', { type: 'warning' })
    removingId.value = item.id
    await deleteSentenceFavorite(item.id)
    ElMessage.success('已取消收藏')
    if (sentences.value.length === 1 && sentencePage.value > 1) sentencePage.value--
    else await loadSentences()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error('取消收藏失败')
  } finally {
    removingId.value = null
  }
}

const readArticle = (textId) => router.push({ path: `/article/${textId}`, query: { from: 'favorites' } })
const formatDate = (value) => {
  if (!value) return ''
  const date = new Date(value)
  return `${date.getMonth() + 1}/${date.getDate()}`
}
const difficultyText = (value) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' })[value] || value || '未知'

watch(activeTab, (tab) => {
  if (tab === 'articles' && articles.value.length === 0) loadArticles()
  if (tab === 'sentences' && sentences.value.length === 0) loadSentences()
})
watch(articlePage, loadArticles)
watch(sentencePage, loadSentences)
onMounted(loadArticles)
</script>

<style scoped>
.favorites-page { max-width: 920px; min-height: 80vh; margin: 0 auto; padding: 18px 16px 32px; }
.favorites-intro { margin-bottom: 18px; }
.favorite-tabs { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 4px; margin-bottom: 18px; padding: 4px; border-radius: var(--xy-radius-sm); background: var(--xy-surface-tint); }
.tab-button { min-height: 44px; border: 0; border-radius: var(--xy-radius-sm); background: transparent; color: var(--xy-ink-muted); font: inherit; font-weight: 600; cursor: pointer; }
.tab-button.active { background: var(--xy-surface); color: var(--xy-brand-700); box-shadow: var(--shadow-sm); }
.tab-count { margin-left: 6px; padding: 1px 6px; border-radius: 10px; background: var(--xy-brand-100); font-size: 12px; }
.favorite-list { display: grid; gap: 10px; }
.article-item, .sentence-item { border: 1px solid var(--xy-border); border-radius: var(--xy-radius-md); background: var(--xy-surface); padding: 16px; box-shadow: var(--xy-shadow-card); }
.article-item { cursor: pointer; }
.article-item:hover, .article-item:focus-visible { border-color: var(--xy-brand-400); outline: 2px solid var(--xy-brand-400); outline-offset: 2px; }
.article-item h2 { margin: 0 0 7px; color: var(--xy-ink); font-size: 16px; letter-spacing: 0; }
.article-item p { display: -webkit-box; overflow: hidden; margin: 0; color: var(--xy-ink-muted); line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.item-meta, .sentence-header { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.item-meta { margin-top: 12px; color: var(--xy-ink-soft); font-size: 12px; }
.source-link { overflow: hidden; border: 0; padding: 0; background: transparent; color: var(--xy-brand-600); font: inherit; font-weight: 600; text-overflow: ellipsis; white-space: nowrap; cursor: pointer; }
.sentence-item blockquote { margin: 8px 0; color: var(--xy-ink); line-height: 1.65; }
.sentence-item time { color: var(--xy-ink-soft); font-size: 12px; }
.state-view { display: flex; min-height: 260px; flex-direction: column; align-items: center; justify-content: center; color: var(--xy-ink-soft); text-align: center; }
.state-view h2 { margin: 14px 0 6px; color: var(--xy-ink); font-size: 16px; letter-spacing: 0; }
.state-view p { margin: 0; font-size: 13px; }
:deep(.page-controls) { display: flex; align-items: center; justify-content: center; gap: 14px; padding: 18px 0; color: var(--xy-ink-muted); font-size: 13px; }
:deep(.page-controls button) { min-height: 36px; border: 1px solid var(--xy-border); border-radius: var(--xy-radius-sm); background: var(--xy-surface); padding: 0 14px; color: var(--xy-ink); cursor: pointer; }
:deep(.page-controls button:disabled) { opacity: .45; cursor: default; }
</style>
