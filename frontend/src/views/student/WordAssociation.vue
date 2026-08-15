<template>
  <div class="word-association-page">
    <div class="search-header">
      <div class="search-title">
        <el-icon :size="28" color="#409eff"><Reading /></el-icon>
        <span>单词联想</span>
      </div>
      <div class="search-wrapper">
        <el-autocomplete
          v-model="searchWord"
          :fetch-suggestions="querySearch"
          placeholder="输入单词，如 international、unhappy..."
          size="large"
          clearable
          @keyup.enter="handleSearch"
          @select="handleSelect"
          class="search-input"
        >
          <template #prefix>
            <el-icon class="search-icon"><Search /></el-icon>
          </template>
          <template #default="{ item }">
            <div class="suggestion-item">
              <span class="sug-word">{{ item.word }}</span>
              <span class="sug-def" v-if="item.definition">{{ item.definition }}</span>
            </div>
          </template>
          <template #append>
            <el-button type="primary" :loading="loading" @click="handleSearch" class="search-btn">
              <el-icon><Search /></el-icon>
              查词
            </el-button>
          </template>
        </el-autocomplete>
      </div>
      <div class="hot-words" v-if="!result">
        <span class="hot-label">热门：</span>
        <el-tag v-for="w in hotWords" :key="w" class="hot-tag" effect="plain" @click="quickSearch(w)">{{ w }}</el-tag>
      </div>
    </div>

    <div v-if="loading" class="loading-area">
      <el-skeleton :rows="8" animated />
    </div>

    <div v-if="result && !loading" class="word-detail-card">
      <!-- 单词头部：词典式展示 -->
      <div class="word-hero">
        <div class="word-main">
          <div class="word-head-row">
            <h2 class="word-text">{{ result.word }}</h2>
            <el-button text size="small" class="speak-btn" @click="speakWord">
              <el-icon><VideoPlay /></el-icon>
            </el-button>
          </div>
          <div class="word-phonetic-row" v-if="result.pronunciation || result.phonetic">
            <span class="phonetic" v-if="result.pronunciation">英 /{{ result.pronunciation }}/</span>
            <span class="phonetic" v-if="result.phonetic && result.phonetic !== result.pronunciation">美 /{{ result.phonetic }}/</span>
          </div>
          <div class="word-tags" v-if="result.tag || result.collins || result.oxford">
            <el-tag v-if="result.collins" size="small" type="warning" effect="dark" class="dict-tag">柯林斯 {{ result.collins }}星</el-tag>
            <el-tag v-if="result.oxford == 1 || result.oxford == '1'" size="small" type="success" effect="dark" class="dict-tag">牛津</el-tag>
            <el-tag v-for="t in parseTags(result.tag)" :key="t" size="small" effect="plain" class="dict-tag">{{ tagLabel(t) }}</el-tag>
          </div>
        </div>
      </div>

      <!-- 中文翻译（词典式分行） -->
      <div class="dict-section" v-if="result.translation">
        <div class="dict-section-title">中文释义</div>
        <div class="dict-entries">
          <div v-for="(line, idx) in splitLines(result.translation)" :key="'t'+idx" class="dict-entry">
            <span class="entry-pos" v-if="extractPos(line)">{{ extractPos(line) }}</span>
            <span class="entry-text">{{ removePosPrefix(line) }}</span>
          </div>
        </div>
      </div>

      <!-- 英文释义（词典式分行） -->
      <div class="dict-section" v-if="result.definition">
        <div class="dict-section-title">英文释义</div>
        <div class="dict-entries">
          <div v-for="(line, idx) in splitLines(result.definition)" :key="'d'+idx" class="dict-entry en-entry">
            <span class="entry-pos" v-if="extractPos(line)">{{ extractPos(line) }}</span>
            <span class="entry-text">{{ removePosPrefix(line) }}</span>
          </div>
        </div>
      </div>

      <!-- 词根词缀 -->
      <div class="section-card" v-if="result.etymology && hasEtymology(result.etymology)">
        <div class="section-header" @click="toggleSection('etymology')">
          <div class="section-title">
            <div class="title-icon ety-icon"><el-icon><Connection /></el-icon></div>
            <span>词根词缀</span>
            <el-tag size="small" type="success" effect="light">{{ etymologyCount(result.etymology) }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.etymology }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.etymology">
          <div class="ety-group" v-if="result.etymology.prefixes && result.etymology.prefixes.length">
            <div class="ety-group-title"><span class="dot pre-dot"></span>前缀</div>
            <div class="ety-chips">
              <div v-for="(p, i) in result.etymology.prefixes" :key="'p'+i" class="ety-chip pre-chip">
                <span class="chip-affix">{{ p.prefix }}-</span>
                <span class="chip-meaning">{{ p.meaning }}</span>
                <div class="chip-examples">{{ p.examples }}</div>
              </div>
            </div>
          </div>
          <div class="ety-group" v-if="result.etymology.roots && result.etymology.roots.length">
            <div class="ety-group-title"><span class="dot root-dot"></span>词根</div>
            <div class="ety-chips">
              <div v-for="(r, i) in result.etymology.roots" :key="'r'+i" class="ety-chip root-chip">
                <span class="chip-affix">{{ r.root }}</span>
                <span class="chip-meaning">{{ r.meaning }}</span>
                <div class="chip-examples">{{ r.examples }}</div>
              </div>
            </div>
          </div>
          <div class="ety-group" v-if="result.etymology.suffixes && result.etymology.suffixes.length">
            <div class="ety-group-title"><span class="dot suf-dot"></span>后缀</div>
            <div class="ety-chips">
              <div v-for="(s, i) in result.etymology.suffixes" :key="'s'+i" class="ety-chip suf-chip">
                <span class="chip-affix">-{{ s.suffix }}</span>
                <span class="chip-meaning">{{ s.meaning }}</span>
                <el-tag size="small" type="info" effect="plain">{{ s.partOfSpeech }}</el-tag>
                <div class="chip-examples">{{ s.examples }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 联想记忆 -->
      <div class="section-card" v-if="result.etymology && result.etymology.mnemonics && result.etymology.mnemonics.length">
        <div class="section-header" @click="toggleSection('mnemonic')">
          <div class="section-title">
            <div class="title-icon mne-icon"><el-icon><MagicStick /></el-icon></div>
            <span>联想记忆</span>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.mnemonic }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.mnemonic">
          <div v-for="(m, i) in result.etymology.mnemonics" :key="'m'+i" class="mnemonic-box">
            <pre class="mnemonic-content">{{ m }}</pre>
          </div>
        </div>
      </div>

      <!-- 词形变化（真实数据） -->
      <div class="section-card" v-if="result.wordForms && Object.keys(result.wordForms).length">
        <div class="section-header" @click="toggleSection('wordForms')">
          <div class="section-title">
            <div class="title-icon wf-icon"><el-icon><Refresh /></el-icon></div>
            <span>词形变化</span>
            <el-tag size="small" type="info" effect="light">{{ Object.keys(result.wordForms).length }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.wordForms }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.wordForms">
          <div class="word-forms-grid">
            <div v-for="(val, key) in result.wordForms" :key="key" class="word-form-card" @click="quickSearch(val)">
              <div class="wf-label">{{ key }}</div>
              <div class="wf-value">{{ val }}</div>
            </div>
          </div>
        </div>
      </div>

      <!-- 同义词 -->
      <div class="section-card" v-if="result.synonyms && result.synonyms.length">
        <div class="section-header" @click="toggleSection('synonym')">
          <div class="section-title">
            <div class="title-icon syn-icon"><el-icon><Share /></el-icon></div>
            <span>同义词</span>
            <el-tag size="small" type="primary" effect="light">{{ result.synonyms.length }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.synonym }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.synonym">
          <div class="syn-chips">
            <div v-for="(item, i) in result.synonyms" :key="'syn'+i" class="syn-chip" @click="quickSearch(item.word)">
              <span class="chip-word">{{ item.word }}</span>
              <span class="chip-def" v-if="item.definition">{{ item.definition }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 衍生词 -->
      <div class="section-card" v-if="result.derivatives && result.derivatives.length">
        <div class="section-header" @click="toggleSection('derivative')">
          <div class="section-title">
            <div class="title-icon der-icon"><el-icon><Cpu /></el-icon></div>
            <span>衍生词</span>
            <el-tag size="small" type="warning" effect="light">{{ result.derivatives.length }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.derivative }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.derivative">
          <div class="der-chips">
            <div v-for="(item, i) in result.derivatives" :key="'der'+i" class="der-chip" @click="quickSearch(item.word)">
              <el-tag v-if="item.type" size="small" effect="dark" round class="der-type">{{ item.type }}</el-tag>
              <span class="chip-word">{{ item.word }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 常用搭配 -->
      <div class="section-card" v-if="result.collocations && result.collocations.length">
        <div class="section-header" @click="toggleSection('collocation')">
          <div class="section-title">
            <div class="title-icon col-icon"><el-icon><Link /></el-icon></div>
            <span>常用搭配</span>
            <el-tag size="small" type="danger" effect="light">{{ result.collocations.length }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.collocation }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.collocation">
          <div v-for="(item, i) in result.collocations" :key="'col'+i" class="collocation-item">
            <div class="col-phrase">{{ item.collocation }}</div>
            <div class="col-def" v-if="item.definition">{{ item.definition }}</div>
            <div class="col-example" v-if="item.example">
              <el-icon><ChatLineRound /></el-icon> {{ item.example }}
            </div>
          </div>
        </div>
      </div>

      <!-- 同前缀词 -->
      <div class="section-card" v-if="result.prefixWords && result.prefixWords.length">
        <div class="section-header" @click="toggleSection('prefixWords')">
          <div class="section-title">
            <div class="title-icon pw-icon"><el-icon><ArrowLeft /></el-icon></div>
            <span>同前缀词</span>
            <el-tag size="small" type="success" effect="light">{{ result.prefixWords.length }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.prefixWords }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.prefixWords">
          <div class="related-words-list">
            <div v-for="(item, i) in result.prefixWords" :key="'pw'+i" class="related-word-row" @click="quickSearch(item.word)">
              <span class="rw-word">{{ item.word }}</span>
              <span class="rw-pos" v-if="item.pos">{{ item.pos }}</span>
              <span class="rw-trans" v-if="item.translation">{{ item.translation }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 同后缀词 -->
      <div class="section-card" v-if="result.suffixWords && result.suffixWords.length">
        <div class="section-header" @click="toggleSection('suffixWords')">
          <div class="section-title">
            <div class="title-icon sw-icon"><el-icon><ArrowRight /></el-icon></div>
            <span>同后缀词</span>
            <el-tag size="small" type="warning" effect="light">{{ result.suffixWords.length }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.suffixWords }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.suffixWords">
          <div class="related-words-list">
            <div v-for="(item, i) in result.suffixWords" :key="'sw'+i" class="related-word-row" @click="quickSearch(item.word)">
              <span class="rw-word">{{ item.word }}</span>
              <span class="rw-pos" v-if="item.pos">{{ item.pos }}</span>
              <span class="rw-trans" v-if="item.translation">{{ item.translation }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 同根词 -->
      <div class="section-card" v-if="result.rootWords && result.rootWords.length">
        <div class="section-header" @click="toggleSection('rootWords')">
          <div class="section-title">
            <div class="title-icon rw-icon"><el-icon><Share /></el-icon></div>
            <span>同根词</span>
            <el-tag size="small" type="primary" effect="light">{{ result.rootWords.length }}</el-tag>
          </div>
          <el-icon class="arrow-icon" :class="{ expanded: sections.rootWords }"><ArrowDown /></el-icon>
        </div>
        <div class="section-body" v-show="sections.rootWords">
          <div class="related-words-list">
            <div v-for="(item, i) in result.rootWords" :key="'rw'+i" class="related-word-row" @click="quickSearch(item.word)">
              <span class="rw-word">{{ item.word }}</span>
              <el-tag v-if="item.root" size="small" type="primary" effect="plain" class="rw-root">{{ item.root }}</el-tag>
              <span class="rw-trans" v-if="item.translation">{{ item.translation }}</span>
            </div>
          </div>
        </div>
      </div>

      <div v-if="!hasAnyData(result)" class="empty-data">
        <el-empty description="该单词暂无联想数据">
          <template #image>
            <el-icon :size="64" color="#C0C4CC"><Document /></el-icon>
          </template>
        </el-empty>
      </div>
    </div>

    <div v-if="!result && !loading" class="init-state">
      <div class="init-content">
        <div class="init-icon">
          <el-icon :size="48" color="#409eff"><Reading /></el-icon>
        </div>
        <h3 class="init-title">单词联想分析</h3>
        <p class="init-desc">输入任意英文单词，获取词根词缀拆解、联想记忆、同义词、衍生词、词形变化、同根词等全方位联想分析</p>
        <div class="feature-list">
          <div class="feature-item"><el-icon color="#67c23a"><Connection /></el-icon><span>词根词缀</span></div>
          <div class="feature-item"><el-icon color="#e6a23c"><MagicStick /></el-icon><span>联想记忆</span></div>
          <div class="feature-item"><el-icon color="#f56c6c"><Refresh /></el-icon><span>词形变化</span></div>
          <div class="feature-item"><el-icon color="#409eff"><Share /></el-icon><span>同根词</span></div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Search, Reading, VideoPlay, Connection, MagicStick,
  Share, Cpu, Link, ChatLineRound, Document, ArrowDown,
  Refresh, ArrowLeft, ArrowRight
} from '@element-plus/icons-vue'
import { getWordAssociations, searchWordSuggestions } from '@/api/wordAssociation'

const searchWord = ref('')
const loading = ref(false)
const result = ref(null)

const sections = reactive({
  etymology: true, mnemonic: true, synonym: true,
  derivative: true, collocation: true, wordForms: true,
  prefixWords: false, suffixWords: false, rootWords: false
})

const hotWords = ['international', 'unhappy', 'transportation', 'beautiful', 'impossible', 'psychology', 'telephone', 'biology']

const splitLines = (text) => {
  if (!text) return []
  return text.split('\n').filter(l => l.trim())
}

const posMap = {
  'n.': '名词', 'v.': '动词', 'vt.': '及物动词', 'vi.': '不及物动词',
  'a.': '形容词', 'adj.': '形容词', 'ad.': '副词', 'adv.': '副词',
  'pron.': '代词', 'prep.': '介词', 'conj.': '连词', 'art.': '冠词',
  'num.': '数词', 'int.': '感叹词', 'aux.': '助动词', 's.': '',
  'p.': '', 'pl.': '复数'
}

const extractPos = (line) => {
  const m = line.match(/^([a-z]+\.)\s*/i)
  if (m) return posMap[m[1].toLowerCase()] || m[1]
  return ''
}

const removePosPrefix = (line) => {
  return line.replace(/^[a-z]+\.\s*/i, '').trim()
}

const parseTags = (tag) => {
  if (!tag) return []
  return tag.split(/\s+/).filter(t => t)
}

const tagLabel = (t) => {
  const m = { zk: '中考', gk: '高考', cet4: '四级', cet6: '六级', ielts: '雅思', toefl: '托福', gre: 'GRE', ky: '考研' }
  return m[t.toLowerCase()] || t
}

const querySearch = async (queryString, cb) => {
  if (!queryString || queryString.trim().length < 2) { cb([]); return }
  try {
    const res = await searchWordSuggestions(queryString.trim())
    if (res?.code === 200 && res.data) {
      const results = res.data
        .filter(w => w.word && w.word.toLowerCase().startsWith(queryString.toLowerCase()))
        .slice(0, 10)
        .map(w => ({ word: w.word, definition: w.definition || w.translation || '' }))
      cb(results)
    } else { cb([]) }
  } catch (e) { cb([]) }
}

const handleSelect = (item) => {
  if (item && item.word) { searchWord.value = item.word; handleSearch() }
}

const quickSearch = (word) => {
  if (!word) return; searchWord.value = word; handleSearch()
}

const handleSearch = async () => {
  const word = searchWord.value?.trim()
  if (!word) { ElMessage.warning('请输入单词'); return }
  if (!/^[a-zA-Z\s-]+$/.test(word)) { ElMessage.warning('请输入有效的英文单词'); return }
  loading.value = true; result.value = null
  try {
    const res = await getWordAssociations(word)
    if (res?.code === 200 && res.data) { result.value = res.data }
    else { ElMessage.error(res?.message || '查询失败') }
  } catch (error) {
    console.error('查询失败:', error); ElMessage.error('网络错误，请稍后重试')
  } finally { loading.value = false }
}

const speakWord = () => {
  if (result.value?.word) {
    const u = new SpeechSynthesisUtterance(result.value.word)
    u.lang = 'en-US'; window.speechSynthesis.speak(u)
  }
}

const toggleSection = (key) => { sections[key] = !sections[key] }

const hasEtymology = (ety) => ety && ((ety.prefixes && ety.prefixes.length) || (ety.roots && ety.roots.length) || (ety.suffixes && ety.suffixes.length))

const etymologyCount = (ety) => {
  if (!ety) return 0
  let c = 0
  if (ety.prefixes) c += ety.prefixes.length
  if (ety.roots) c += ety.roots.length
  if (ety.suffixes) c += ety.suffixes.length
  return c
}

const hasAnyData = (r) => {
  if (!r) return false
  return (r.synonyms && r.synonyms.length) || (r.derivatives && r.derivatives.length) ||
    (r.collocations && r.collocations.length) || (r.etymology && hasEtymology(r.etymology)) ||
    (r.wordForms && Object.keys(r.wordForms).length) || (r.prefixWords && r.prefixWords.length) ||
    (r.suffixWords && r.suffixWords.length) || (r.rootWords && r.rootWords.length)
}
</script>

<style scoped>
.word-association-page { padding: 20px; background: linear-gradient(135deg, #f0f4ff 0%, #f6f8fa 100%); min-height: 100vh; }
.search-header { max-width: 900px; margin: 0 auto 24px; text-align: center; }
.search-title { display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 20px; font-size: 24px; font-weight: 700; color: #303133; }
.search-wrapper { max-width: 600px; margin: 0 auto; }
.search-input { width: 100%; }
.search-input :deep(.el-input__wrapper) { border-radius: 24px 0 0 24px; box-shadow: 0 2px 16px rgba(64,158,255,0.15); }
.search-btn { border-radius: 0 24px 24px 0 !important; height: 100% !important; }
.search-icon { color: #909399; }
.suggestion-item { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; }
.sug-word { font-weight: 600; color: #303133; }
.sug-def { font-size: 12px; color: #909399; max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.hot-words { margin-top: 16px; display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 8px; }
.hot-label { font-size: 13px; color: #909399; }
.hot-tag { cursor: pointer; transition: all 0.2s; }
.hot-tag:hover { transform: translateY(-1px); }
.loading-area { max-width: 900px; margin: 0 auto; padding: 20px; background: #fff; border-radius: 16px; }
.word-detail-card { max-width: 900px; margin: 0 auto; background: #fff; border-radius: 16px; padding: 28px; box-shadow: 0 4px 24px rgba(0,0,0,0.06); }
.word-hero { margin-bottom: 20px; padding-bottom: 20px; border-bottom: 2px solid #ebeef5; }
.word-head-row { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.word-text { font-size: 36px; font-weight: 800; color: #303133; margin: 0; letter-spacing: -0.5px; }
.speak-btn { color: #409eff !important; padding: 4px 8px !important; }
.word-phonetic-row { display: flex; gap: 16px; margin-bottom: 10px; }
.phonetic { font-size: 16px; color: #606266; font-family: 'Courier New', monospace; }
.word-tags { display: flex; flex-wrap: wrap; gap: 6px; }
.dict-tag { font-size: 11px !important; }
.dict-section { margin-bottom: 20px; padding: 16px; background: #f5f7fa; border-radius: 12px; }
.dict-section-title { font-size: 13px; font-weight: 700; color: #409eff; margin-bottom: 10px; padding-bottom: 6px; border-bottom: 1px solid #e4e7ed; }
.dict-entries { display: flex; flex-direction: column; gap: 8px; }
.dict-entry { display: flex; align-items: flex-start; gap: 8px; font-size: 14px; line-height: 1.6; }
.entry-pos { flex-shrink: 0; min-width: 60px; font-size: 12px; font-weight: 600; color: #e6a23c; background: #fdf6ec; padding: 2px 8px; border-radius: 4px; text-align: center; }
.entry-text { color: #303133; flex: 1; }
.en-entry .entry-text { color: #606266; font-style: italic; }
.section-card { margin-bottom: 16px; border: 1px solid #ebeef5; border-radius: 12px; overflow: hidden; transition: all 0.2s; }
.section-card:hover { border-color: #dcdfe6; }
.section-card:last-child { margin-bottom: 0; }
.section-header { display: flex; justify-content: space-between; align-items: center; padding: 14px 18px; cursor: pointer; background: #fafbfc; user-select: none; }
.section-header:hover { background: #f5f7fa; }
.section-title { display: flex; align-items: center; gap: 10px; }
.title-icon { width: 32px; height: 32px; border-radius: 8px; display: flex; align-items: center; justify-content: center; color: #fff; }
.ety-icon { background: linear-gradient(135deg, #67c23a, #85ce61); }
.mne-icon { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.syn-icon { background: linear-gradient(135deg, #409eff, #66b1ff); }
.der-icon { background: linear-gradient(135deg, #909399, #a6a9ad); }
.col-icon { background: linear-gradient(135deg, #f56c6c, #f78989); }
.wf-icon { background: linear-gradient(135deg, #909399, #a6a9ad); }
.pw-icon { background: linear-gradient(135deg, #67c23a, #85ce61); }
.sw-icon { background: linear-gradient(135deg, #e6a23c, #ebb563); }
.rw-icon { background: linear-gradient(135deg, #409eff, #66b1ff); }
.arrow-icon { color: #c0c4cc; transition: transform 0.3s; }
.arrow-icon.expanded { transform: rotate(180deg); }
.section-body { padding: 16px 18px; border-top: 1px solid #ebeef5; }
.ety-group { margin-bottom: 16px; }
.ety-group:last-child { margin-bottom: 0; }
.ety-group-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 10px; display: flex; align-items: center; gap: 6px; }
.dot { width: 8px; height: 8px; border-radius: 50%; }
.pre-dot { background: #67c23a; }
.root-dot { background: #409eff; }
.suf-dot { background: #e6a23c; }
.ety-chips { display: flex; flex-wrap: wrap; gap: 10px; }
.ety-chip { padding: 10px 14px; border-radius: 10px; min-width: 180px; flex: 1; max-width: 280px; }
.pre-chip { background: #f0f9eb; border-left: 3px solid #67c23a; }
.root-chip { background: #ecf5ff; border-left: 3px solid #409eff; }
.suf-chip { background: #fdf6ec; border-left: 3px solid #e6a23c; }
.chip-affix { font-size: 18px; font-weight: 700; font-family: 'Courier New', monospace; margin-right: 8px; }
.pre-chip .chip-affix { color: #67c23a; }
.root-chip .chip-affix { color: #409eff; }
.suf-chip .chip-affix { color: #e6a23c; }
.chip-meaning { font-size: 14px; color: #303133; font-weight: 500; }
.chip-examples { font-size: 12px; color: #909399; margin-top: 6px; }
.mnemonic-box { margin-bottom: 12px; }
.mnemonic-box:last-child { margin-bottom: 0; }
.mnemonic-content { margin: 0; padding: 14px 16px; background: #fdf6ec; border-radius: 10px; font-size: 14px; line-height: 1.8; color: #303133; white-space: pre-wrap; font-family: inherit; }
.word-forms-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
.word-form-card { padding: 12px 14px; background: #f4f4f5; border-radius: 10px; cursor: pointer; transition: all 0.2s; border-left: 3px solid #909399; }
.word-form-card:hover { background: #e9e9eb; transform: translateY(-2px); }
.wf-label { font-size: 12px; color: #909399; margin-bottom: 4px; }
.wf-value { font-size: 16px; font-weight: 600; color: #303133; }
.syn-chips, .der-chips { display: flex; flex-wrap: wrap; gap: 8px; }
.syn-chip, .der-chip { display: flex; align-items: center; gap: 6px; padding: 8px 14px; background: #ecf5ff; border-radius: 20px; cursor: pointer; transition: all 0.2s; border: 1px solid transparent; }
.syn-chip:hover, .der-chip:hover { background: #d9ecff; border-color: #409eff; transform: translateY(-1px); }
.der-chip { background: #f4f4f5; }
.der-chip:hover { background: #e9e9eb; border-color: #909399; }
.chip-word { font-size: 14px; font-weight: 600; color: #303133; }
.chip-def { font-size: 12px; color: #909399; }
.der-type { font-size: 10px !important; }
.collocation-item { padding: 10px 12px; background: #fef0f0; border-radius: 8px; margin-bottom: 8px; }
.collocation-item:last-child { margin-bottom: 0; }
.col-phrase { font-size: 15px; font-weight: 600; color: #f56c6c; margin-bottom: 4px; }
.col-def { font-size: 13px; color: #606266; margin-bottom: 4px; }
.col-example { font-size: 12px; color: #909399; display: flex; align-items: center; gap: 4px; }
.related-words-list { display: flex; flex-direction: column; gap: 6px; }
.related-word-row { display: flex; align-items: center; gap: 10px; padding: 8px 12px; background: #f5f7fa; border-radius: 8px; cursor: pointer; transition: all 0.2s; }
.related-word-row:hover { background: #ecf5ff; }
.rw-word { font-size: 14px; font-weight: 600; color: #303133; min-width: 120px; }
.rw-pos { font-size: 11px; color: #e6a23c; font-style: italic; min-width: 40px; }
.rw-trans { font-size: 13px; color: #606266; flex: 1; }
.rw-root { font-size: 10px; }
.empty-data { padding: 40px 0; }
.init-state { max-width: 900px; margin: 60px auto; text-align: center; }
.init-icon { margin-bottom: 20px; }
.init-title { font-size: 28px; font-weight: 700; color: #303133; margin: 0 0 12px 0; }
.init-desc { font-size: 15px; color: #606266; line-height: 1.8; max-width: 500px; margin: 0 auto 28px; }
.feature-list { display: flex; justify-content: center; gap: 24px; flex-wrap: wrap; }
.feature-item { display: flex; align-items: center; gap: 6px; font-size: 14px; color: #606266; padding: 8px 16px; background: #fff; border-radius: 20px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
</style>
