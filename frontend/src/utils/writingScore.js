/**
 * 作文判分工具。
 *
 * 1. `parseScorePayload` —— 从模型回复里稳健地提取评分 JSON（模型可能包裹
 *    代码块、前后加解释文字）。
 * 2. `localEssayScore`   —— AI 通道不可用时的本地启发式评分（明确标注为本地
 *    参考分），按四六级作文 15 分制，从内容 / 结构 / 语言三个维度给分并给出
 *    可操作的改进建议。
 */

const CONNECTIVES = [
  'however', 'moreover', 'furthermore', 'therefore', 'thus', 'hence',
  'consequently', 'nevertheless', 'nonetheless', 'meanwhile', 'besides',
  'in addition', 'for instance', 'for example', 'in conclusion', 'to sum up',
  'firstly', 'secondly', 'finally', 'in contrast', 'on the contrary',
  'as a result', 'not only', 'what is more', 'in other words', 'according to'
]
const ADVANCED_WORDS = [
  'significant', 'essential', 'crucial', 'contribute', 'cultivate', 'advocate',
  'perspective', 'phenomenon', 'substantial', 'inevitable', 'consequently',
  'moreover', 'furthermore', 'nevertheless', 'acknowledge', 'demonstrate',
  'illustrate', 'emphasize', 'beneficial', 'detrimental', 'indispensable'
]

function words(text) {
  return String(text || '')
    .toLowerCase()
    .replace(/[^a-z'\s-]/g, ' ')
    .split(/\s+/)
    .filter(Boolean)
}

function sentences(text) {
  return String(text || '')
    .split(/[.!?。！？]+/)
    .map((s) => s.trim())
    .filter((s) => words(s).length > 0)
}

/** 从题目要求里解析最低词数要求 */
function requiredWords(promptText) {
  const text = String(promptText || '')
  const range = text.match(/(\d{2,3})\s*(?:-|–|~|至|到)\s*(\d{2,3})\s*words?/i)
  if (range) return Number(range[1])
  const atLeast = text.match(/(?:at\s+least|不少于|至少|no\s+fewer\s+than)\s*(\d{2,3})/i)
  if (atLeast) return Number(atLeast[1])
  const plain = text.match(/(\d{2,3})\s*words?/i)
  if (plain) return Number(plain[1])
  return 120
}

function clampScore(value, max) {
  const n = Number(value)
  if (!Number.isFinite(n)) return null
  return Math.max(0, Math.min(max, Math.round(n * 10) / 10))
}

/** 从模型回复中提取评分 JSON */
export function parseScorePayload(reply) {
  let text = ''
  if (reply == null) return null
  if (typeof reply === 'string') text = reply
  else if (typeof reply === 'object') {
    text = reply.answer || reply.content || reply.message || reply.text || reply.reply || ''
    if (typeof text !== 'string') {
      // 有些实现直接返回结构化对象
      return normalizePayload(reply)
    }
  }
  if (!text) return null
  const fence = text.match(/```(?:json)?\s*([\s\S]*?)```/i)
  const body = fence ? fence[1] : text
  const start = body.indexOf('{')
  const end = body.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  try {
    return normalizePayload(JSON.parse(body.slice(start, end + 1)))
  } catch {
    return null
  }
}

function asList(value) {
  if (Array.isArray(value)) return value.map((v) => String(v)).filter(Boolean)
  if (typeof value === 'string') {
    return value
      .split(/\n|；|;/)
      .map((s) => s.replace(/^[-*\d.、\s]+/, '').trim())
      .filter(Boolean)
  }
  return []
}

function dimension(source, key) {
  const raw = source && source[key]
  if (raw == null) return null
  if (typeof raw === 'number' || typeof raw === 'string') {
    const score = clampScore(raw, 5)
    return score == null ? null : { score, comment: '' }
  }
  const score = clampScore(raw.score ?? raw.points ?? raw.value, 5)
  const comment = String(raw.comment || raw.feedback || raw.reason || raw.detail || '').trim()
  if (score == null && !comment) return null
  return { score, comment }
}

/** 统一模型返回的字段名，容错不同模型的输出习惯 */
export function normalizePayload(payload) {
  if (!payload || typeof payload !== 'object') return null
  const dims = payload.dimensions || payload.dimension || payload.scores || payload
  const structure = {
    total: clampScore(payload.total ?? payload.score ?? payload.overall ?? payload.totalScore, 15),
    dimensions: {
      content: dimension(dims, 'content'),
      structure: dimension(dims, 'structure'),
      language: dimension(dims, 'language')
    },
    wordCount: Number.isFinite(Number(payload.wordCount ?? payload.words)) ? Number(payload.wordCount ?? payload.words) : null,
    strengths: asList(payload.strengths || payload.advantages || payload.positives),
    improvements: asList(payload.improvements || payload.suggestions || payload.issues || payload.weaknesses),
    revisedSample: String(payload.revisedSample || payload.sample || payload.improvedSentence || '').trim()
  }
  const hasAny = structure.total != null
    || Object.values(structure.dimensions).some(Boolean)
    || structure.strengths.length || structure.improvements.length
  return hasAny ? structure : null
}

/**
 * 本地启发式评分（AI 不可用时的兜底，结果会标注为本地参考分）。
 * @returns {{ total:number, dimensions:object, wordCount:number, strengths:string[], improvements:string[], local:boolean }}
 */
export function localEssayScore(essay, promptText) {
  const text = String(essay || '').trim()
  const wordList = words(text)
  const wordCount = wordList.length
  const sentenceList = sentences(text)
  const paragraphList = text.split(/\n\s*\n|\n/).map((p) => p.trim()).filter((p) => words(p).length > 0)
  const minWords = requiredWords(promptText)

  const strengths = []
  const improvements = []

  // —— 内容（5 分）：篇幅是否达标 + 是否扣题 ——
  const ratio = minWords > 0 ? wordCount / minWords : 1
  let content = 3
  if (ratio >= 1) {
    content = 4.5
    strengths.push(`篇幅达标（${wordCount} 词，要求不少于 ${minWords} 词）`)
  } else if (ratio >= 0.8) {
    content = 3.5
    improvements.push(`篇幅略短（${wordCount} 词），建议扩充到 ${minWords} 词以上，补一个具体例子`)
  } else {
    content = 2.5
    improvements.push(`篇幅明显不足（${wordCount} 词，要求不少于 ${minWords} 词），需要补充论证与例证`)
  }
  // 扣题：题干实词在作文中的复现率
  const topicWords = [...new Set(words(promptText).filter((w) => w.length >= 5))].slice(0, 40)
  if (topicWords.length) {
    const hitCount = topicWords.filter((w) => text.toLowerCase().includes(w)).length
    const coverage = hitCount / topicWords.length
    if (coverage >= 0.25) {
      content = Math.min(5, content + 0.5)
      strengths.push('行文围绕题目关键词展开，切题度较好')
    } else if (coverage < 0.1) {
      content = Math.max(1.5, content - 0.5)
      improvements.push('与题目关键词的呼应偏少，建议在首段明确回应题目要求')
    }
  }

  // —— 结构（5 分）：段落划分 + 衔接词 ——
  const usedConnectives = CONNECTIVES.filter((c) => text.toLowerCase().includes(c))
  let structure = 3
  if (paragraphList.length >= 3) {
    structure += 0.8
    strengths.push(`段落层次清晰（${paragraphList.length} 段）`)
  } else if (paragraphList.length === 1 && wordCount > 80) {
    improvements.push('全篇只有一段，建议按“引入—论证—总结”分成 3 段')
  }
  if (usedConnectives.length >= 3) {
    structure += 0.7
    strengths.push(`衔接词使用到位（如 ${usedConnectives.slice(0, 3).join('、')}）`)
  } else if (usedConnectives.length === 0) {
    structure -= 0.5
    improvements.push('缺少 however / therefore / in addition 这类衔接词，句子之间偏平铺')
  }
  structure = Math.max(1.5, Math.min(5, structure))

  // —— 语言（5 分）：句式长度变化 + 词汇多样性 + 高级词 ——
  const lens = sentenceList.map((s) => words(s).length)
  const avgLen = lens.length ? lens.reduce((a, b) => a + b, 0) / lens.length : 0
  const variety = lens.length > 1 ? Math.max(...lens) - Math.min(...lens) : 0
  const typeToken = wordList.length ? new Set(wordList).size / wordList.length : 0
  const advanced = ADVANCED_WORDS.filter((w) => text.toLowerCase().includes(w))
  let language = 3
  if (avgLen >= 12 && variety >= 5) {
    language += 0.8
    strengths.push('句式长短结合，读起来不单调')
  } else if (avgLen > 0 && avgLen < 8) {
    language -= 0.4
    improvements.push('句子普遍偏短，可尝试用定语从句、非谓语结构把两句合并')
  }
  if (typeToken >= 0.62) {
    language += 0.5
    strengths.push('用词重复率低，词汇有一定丰富度')
  } else if (typeToken > 0 && typeToken < 0.5) {
    language -= 0.3
    improvements.push('同一批词反复出现，建议替换部分表达')
  }
  if (advanced.length >= 3) {
    language += 0.7
    strengths.push(`用到了较高级词汇（如 ${advanced.slice(0, 3).join('、')}）`)
  }
  language = Math.max(1.5, Math.min(5, language))

  const total = Math.round((content + structure + language) * 10) / 10
  // 篇幅严重不足时，结构与语言分不可能拿高分，做一次上限约束
  const capped = ratio < 0.7 ? Math.min(total, 10.5) : total
  if (!strengths.length) strengths.push('已经能完整表达观点，继续保持')
  if (!improvements.length) improvements.push('可以再打磨个别搭配，争取语言分满分')

  return {
    total: Math.min(15, capped),
    dimensions: {
      content: { score: content, comment: '基于篇幅与切题度估算' },
      structure: { score: structure, comment: '基于段落划分与衔接词估算' },
      language: { score: language, comment: '基于句式变化与词汇多样性估算' }
    },
    wordCount,
    strengths,
    improvements,
    revisedSample: '',
    local: true
  }
}
