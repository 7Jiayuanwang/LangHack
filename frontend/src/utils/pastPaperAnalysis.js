/**
 * 真题解析切分工具。
 *
 * 导入的真题数据中，每道题的 `analysis` 字段常常是**一整块文档正文**：
 * 听力原文 / 全文翻译，后面再接上从该题起的多题解析。直接整块渲染会把
 * 所有题目的解析堆在一起（用户反馈的问题）。
 *
 * 本模块按以下优先级，从大块里抽出「当前题目自己的解析」：
 *   A. `解析答案为X)。句[N]…`            —— 块内自带题号（听力；注意原文存在
 *                                           `句[11提到` 这种漏掉右括号的写法）
 *   B. `定位根据题干中的关键词语 … 可定位至第N段` —— 块前定位句与题干关键词重合（阅读）
 *   C. 整块里只有一个解析块，且它没有归属题号或正好是本题
 *   D. 没有 `解析答案为` 块时，按 `[N]` 标记切段（听力 18-20 这类）
 * 完形 / 匹配按 `section` 的题号区间裁剪（原文里两个板块的翻译混在同一块）；
 * 翻译 / 写作整篇就是本题材料，整体清洗后展示。
 *
 * 另外单独返回「共享材料」（听力原文 / 全文翻译），由界面折叠展示，
 * 避免正文把题目解析淹没。
 */

/** 题干里出现频率极高、无区分度的词 */
const STOP_WORDS = new Set([
  'about', 'according', 'answer', 'answers', 'author', 'because', 'been', 'best',
  'choose', 'could', 'directions', 'does', 'each', 'essay', 'following', 'from',
  'have', 'least', 'less', 'line', 'mainly', 'mean', 'means', 'mentioned', 'more',
  'most', 'other', 'passage', 'people', 'question', 'questions', 'read', 'said',
  'says', 'section', 'should', 'some', 'statement', 'statements', 'term', 'than',
  'that', 'their', 'there', 'these', 'they', 'this', 'those', 'what', 'when',
  'where', 'which', 'while', 'whom', 'with', 'word', 'words', 'would', 'write',
  'your', 'yours'
])

const BLOCK_RE = /解析答案为\s*([A-D])\s*[)）]\s*。?/g
/** 严格标记：[12] / [26] / [46-1] / 「44] */
const MARKER_RE = /[\[「]\s*(\d{1,3})(?:\s*-\s*\d{1,3})?\s*[\]」]/g

const SHARED_LABEL = {
  listening: '听力原文',
  single_choice: '全文翻译',
  cloze: '全文翻译（含答案）',
  matching: '全文翻译（含答案）',
  translation: '翻译点拨',
  writing: '审题思路与范文'
}

/** 从题干里提取有区分度的关键词 */
export function stemKeywords(stem) {
  const words = new Set()
  String(stem || '')
    .toLowerCase()
    .replace(/[^a-z\s]/g, ' ')
    .split(/\s+/)
    .forEach((w) => {
      if (w.length >= 5 && !STOP_WORDS.has(w)) words.add(w)
    })
  return words
}

/** 统计关键词在文本里的命中数 */
export function keywordHits(words, text) {
  if (!words || !words.size || !text) return 0
  const low = String(text).toLowerCase()
  let hits = 0
  words.forEach((w) => {
    if (low.includes(w)) hits += 1
  })
  return hits
}

/** 拉丁字母占比，用于判断一段文本是英文原文还是中文解析 */
function latinRatio(text) {
  const s = String(text || '').replace(/\s/g, '')
  if (!s) return 0
  return (s.match(/[A-Za-z]/g) || []).length / s.length
}

/** 清洗原文里的乱码占位符、页眉页脚、重复页码 */
export function cleanAnalysisText(text) {
  if (!text) return ''
  let t = String(text).replace(/\r\n?/g, '\n')
  // 「m…」「m•」「●m」这类导入时被破坏的符号占位（如「m…●审题思路●m」）
  t = t.replace(/m\s*[…·•⋯●]+\s*●?/g, ' ')
  t = t.replace(/●\s*m\b/g, ' ')
  t = t.replace(/[…·•⋯●]{3,}/g, ' ')
  t = t.replace(/●\s*/g, ' ')
  t = t.replace(/[「]/g, '[')
  // 套卷页眉：「2026年6月大学英语六级考试真题答案详解(共3套)·第1套」
  t = t.replace(/20\d{2}\s*年\s*\d{1,2}\s*月\s*大学英语[四六]级考试真题答案详解[^\n]{0,24}/g, ' ')
  t = t.replace(/[（(]?\s*共\s*3\s*套\s*[）)]?\s*[·.。]?\s*第?\s*\d?\s*套?/g, ' ')
  t = t.replace(/[（(]\s*续\s*[表页]\s*[）)]/g, ' ')
  t = t.replace(/\b第\s*\d\s*套\s*\d{1,3}\b/g, ' ')
  t = t.replace(/[^\S\n]{2,}/g, ' ')
  t = t.replace(/[ \t]+([，。；：、）】」])/g, '$1')
  t = t.replace(/\n{3,}/g, '\n\n')
  return t.trim()
}

/** 把大块按「解析答案为X)。」切成解析块 */
export function splitAnalysisBlocks(raw) {
  if (!raw) return []
  const matches = [...String(raw).matchAll(BLOCK_RE)]
  return matches.map((m, i) => {
    const start = m.index
    const end = i + 1 < matches.length ? matches[i + 1].index : raw.length
    const beforeStart = i === 0 ? 0 : matches[i - 1].index + matches[i - 1][0].length
    return {
      letter: m[1],
      start,
      end,
      before: raw.slice(beforeStart, start),
      main: raw.slice(start + m[0].length, end).trim()
    }
  })
}

/** 从解析块开头识别它属于哪一题（注意 `句[11提到` 这种漏右括号的写法） */
function ownerQuestionNumber(block) {
  const head = String(block.main || '').slice(0, 80)
  let m = head.match(/句\s*[\[「]?\s*(\d{1,3})(?:\s*-\s*\d{1,3})?\s*[\]」]?/)
  if (m) return Number(m[1])
  m = head.match(/[\[「]\s*(\d{1,3})(?:\s*-\s*\d{1,3})?\s*[\]」]?\s*(?:提到|中指|指|说|中|[:：])/)
  if (m) return Number(m[1])
  m = head.match(/第\s*(\d{1,3})\s*题/)
  if (m) return Number(m[1])
  return null
}

/** 列出文本里的 [N] / [N-M] 标记 */
function findMarkers(raw) {
  if (!raw) return []
  return [...String(raw).matchAll(MARKER_RE)].map((m) => ({
    num: Number(m[1]),
    index: m.index,
    raw: m[0]
  }))
}

/** 解析 section 里的题号区间，如「选词填空（26-35）」→ [26, 35] */
function sectionRange(section) {
  const m = String(section || '').match(/[（(]\s*(\d{1,3})\s*[-–~至]\s*(\d{1,3})\s*[）)]/)
  if (!m) return null
  return [Number(m[1]), Number(m[2])]
}

/** 按题号区间裁剪大块：去掉属于前后板块的部分 */
function sliceBySection(raw, range) {
  if (!range) return raw
  const [lo, hi] = range
  const marks = findMarkers(raw)
  const before = marks.filter((m) => m.num < lo)
  const after = marks.filter((m) => m.num > hi)
  const start = before.length ? before[before.length - 1].index : 0
  const end = after.length ? after[0].index : raw.length
  return end > start ? raw.slice(start, end) : raw
}

/** 用 `[N]` 标记为某题切段（没有「解析答案为」块时） */
function sliceByOwnMarker(raw, number, maxNumber) {
  const marks = findMarkers(raw).filter((m) => m.num >= 1 && m.num <= maxNumber)
  const at = marks.findIndex((m) => m.num === number)
  if (at < 0) return ''
  const start = marks[at].index
  let end = raw.length
  for (let i = at + 1; i < marks.length; i += 1) {
    if (marks[i].num !== number) {
      end = marks[i].index
      break
    }
  }
  return raw.slice(start, end)
}

/** 切掉解析尾部混入的英文录音原文 */
function trimEnglishTail(text) {
  const win = 200
  for (let i = 0; i + win <= text.length; i += 40) {
    if (latinRatio(text.slice(i, i + win)) > 0.85) return text.slice(0, i)
  }
  return text
}

/** 中文汉字数量，用于区分中文解析与英文录音原文 */
function cjkCount(text) {
  return (String(text || '').match(/[\u4e00-\u9fa5]/g) || []).length
}

/**
 * 为一套试卷建立解析索引。
 * @returns {{ analysisFor: Function, sharedFor: Function }}
 */
export function buildAnalysisIndex(paper) {
  const questions = (paper && paper.questions) || []
  const maxNumber = questions.reduce((m, q) => Math.max(m, Number(q.number) || 0), 0)
  const byNumber = new Map()   // 题号 -> { main, letter, score }

  // ---- Pass A：解析块内自带题号（听力 `句[N]`）----
  questions.forEach((q) => {
    splitAnalysisBlocks(q.analysis).forEach((block) => {
      const owner = ownerQuestionNumber(block)
      if (!owner || owner < 1 || owner > maxNumber + 5) return
      const existing = byNumber.get(owner)
      if (existing && existing.score >= 2) return
      byNumber.set(owner, { main: block.main, letter: block.letter, score: 2 })
    })
  })

  // ---- Pass B：用「定位关键词 vs 题干」为阅读题选块 ----
  questions.forEach((q) => {
    if (byNumber.has(q.number)) return
    const raw = q.analysis || ''
    if (!BLOCK_RE.test(raw)) return
    BLOCK_RE.lastIndex = 0
    const words = stemKeywords(q.stem)
    if (words.size < 2) return
    let best = null
    splitAnalysisBlocks(raw).forEach((block) => {
      const owner = ownerQuestionNumber(block)
      // 已明确属于别的题的块，不参与本题竞争
      if (owner && owner !== q.number) return
      const score = keywordHits(words, block.before) * 2 + keywordHits(words, block.main.slice(0, 140))
      if (!best || score > best.score) best = { score, block }
    })
    if (best && best.score >= 2) {
      byNumber.set(q.number, { main: best.block.main, letter: best.block.letter, score: best.score })
    }
  })

  // ---- Pass C：整块只有一个解析块，且未归属别人 ----
  questions.forEach((q) => {
    if (byNumber.has(q.number)) return
    const blocks = splitAnalysisBlocks(q.analysis)
    if (blocks.length !== 1) return
    const owner = ownerQuestionNumber(blocks[0])
    if (owner && owner !== q.number) return
    byNumber.set(q.number, { main: blocks[0].main, letter: blocks[0].letter, score: 1 })
  })

  // ---- Pass D：没有「解析答案为」块时，按 `[N]` 标记切段 ----
  questions.forEach((q) => {
    if (byNumber.has(q.number)) return
    const raw = q.analysis || ''
    if (!raw || BLOCK_RE.test(raw)) {
      BLOCK_RE.lastIndex = 0
      return
    }
    const piece = sliceByOwnMarker(raw, q.number, maxNumber)
    if (!piece) return
    // 开头就是英文 ⇒ 这是录音原文而不是解析（如听力第 16 题）
    if (q.type === 'listening' && cjkCount(piece.slice(0, 80)) < 8) return
    const main = q.type === 'listening' ? trimEnglishTail(piece) : piece
    if (cleanAnalysisText(main).length < 30) return
    byNumber.set(q.number, { main, letter: '', score: 1 })
  })

  // ---- 共享材料 ----
  // 听力原文：取本题所在块的块前正文（块首若是中文解析则跳过，如第 13 题）
  const listeningHead = new Map()
  questions.forEach((q) => {
    if (q.type !== 'listening') return
    const raw = q.analysis || ''
    if (!raw) return
    const first = splitAnalysisBlocks(raw)[0]
    const head = first ? raw.slice(0, first.start) : raw
    if (latinRatio(head) < 0.35) return
    const cleaned = cleanAnalysisText(head)
    if (cleaned.length >= 60) listeningHead.set(q.number, cleaned)
  })

  // 全文翻译：阅读是「一篇 5 题共享一份译文」，因此按篇分组，取同篇里最完整的那份
  const readingNumbers = questions
    .filter((x) => x.type === 'single_choice')
    .map((x) => Number(x.number))
    .filter((n) => Number.isFinite(n))
    .sort((a, b) => a - b)
  const passageGroupOf = (num) => {
    const at = readingNumbers.indexOf(num)
    if (at < 0) return null
    const base = Math.floor(at / 5) * 5
    return { lo: readingNumbers[base], hi: readingNumbers[Math.min(base + 4, readingNumbers.length - 1)] }
  }
  const headsByGroup = new Map()  // 'lo-hi' -> { text, size }
  questions.forEach((q) => {
    if (q.type !== 'single_choice') return
    const raw = q.analysis || ''
    if (!raw) return
    const first = splitAnalysisBlocks(raw)[0]
    const head = first ? raw.slice(0, first.start) : raw
    const marks = findMarkers(head)
    if (!marks.length) return
    const group = passageGroupOf(Number(q.number))
    if (!group) return
    // 只有块前正文的题号落在本篇范围内，才算同篇译文
    if (!marks.some((m) => m.num >= group.lo && m.num <= group.hi)) return
    const key = `${group.lo}-${group.hi}`
    const cleaned = cleanAnalysisText(head)
    const prev = headsByGroup.get(key)
    if (cleaned.length >= 60 && (!prev || cleaned.length > prev.length)) {
      headsByGroup.set(key, cleaned)
    }
  })
  const translationByNumber = new Map()
  readingNumbers.forEach((num) => {
    const group = passageGroupOf(num)
    if (!group) return
    const text = headsByGroup.get(`${group.lo}-${group.hi}`)
    if (text) translationByNumber.set(num, text)
  })

  function analysisFor(question) {
    if (!question) return ''
    const raw = question.analysis || ''
    const type = question.type
    // 整篇材料题：完形 / 匹配按板块裁剪，翻译 / 写作整篇即本题材料
    if (type === 'cloze' || type === 'matching') {
      return cleanAnalysisText(sliceBySection(raw, sectionRange(question.section)))
    }
    if (type === 'translation' || type === 'writing') {
      return cleanAnalysisText(raw)
    }
    const hit = byNumber.get(question.number)
    if (hit && hit.main) {
      // 去掉与面板「正确答案」重复的答案声明前缀，避免同一题出现两种字母说法
      const text = cleanAnalysisText(hit.main).replace(/^解析答案为\s*[A-D]\s*[)）]\s*。?\s*/, '')
      if (text) return text
    }
    // 回退：整块就是本题材料。听力若整块是英文录音原文，则不算解析
    if (type === 'listening' && cjkCount(raw.slice(0, 80)) < 8) return ''
    const fallback = cleanAnalysisText(type === 'listening' ? trimEnglishTail(raw) : raw)
    return fallback.length < 40 ? '' : fallback
  }

  function sharedFor(question) {
    if (!question) return null
    if (question.type === 'listening') {
      const text = listeningHead.get(question.number)
      return text ? { label: SHARED_LABEL.listening, text } : null
    }
    if (question.type === 'single_choice') {
      const text = translationByNumber.get(Number(question.number))
      return text ? { label: SHARED_LABEL.single_choice, text } : null
    }
    return null
  }

  return { analysisFor, sharedFor, byNumber, listeningHead, translationByNumber }
}
