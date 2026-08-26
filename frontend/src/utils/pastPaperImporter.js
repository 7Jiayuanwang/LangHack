const CATEGORY_VALUES = new Set(['cet4', 'cet6', 'postgraduate'])
const QUESTION_TYPES = new Set(['single_choice', 'cloze', 'matching', 'translation', 'writing', 'listening'])

const clean = (value = '') => {
  const text = value
    .replace(/!\[[^\]]*]\([^)]*\)/g, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim()
  // 切掉选项里被错误拼接的"下一题引导语"，比如
  // "Minimum base salary. Questions 22 to 25 are based on the passage you have just heard."
  // 或带 markdown 分隔符 "## Questions 19 to 21 are based on ..."
  // 这些是题目之间的指令，不属于当前选项内容。
  return text
    .replace(/\.?\s*(?:##\s*)?Questions?\s+\d+\s+(?:to|and)\s+\d+\s+are\s+based\s+on.*$/is, '')
    .replace(/\.?\s*(?:##\s*)?Questions?\s+\d+\s+are\s+based\s+on.*$/is, '')
    // Only remove actual exam section headings. The previous broad pattern
    // treated ordinary prose such as "part of the brain" as "Part O..."
    // (the `i` flag made the lowercase word eligible) and truncated cloze
    // stems before their blanks.
    .replace(/\.?\s*(?:#{1,3}\s*)?(?:Part\s+(?:[IVX]+|\d+)|Section\s+[A-C])\b.*$/is, '')
    .replace(/\.?\s*={2,}\s*第?\s*\d+\s*页\s*={2,}.*$/is, '')
    .replace(/[.。]?\s*(?:公众号|微信公众(?:号|平台)?)[：:]?\s*[^\s]+\s*$/i, '')
    .replace(/\.?\s*(?:##\s*)?听(?:下)?一?段?(?:对话|独白|短文)[^。]*[。]?\s*$/i, '')
    .replace(/\.?\s*听(?:下)?一?段?(?:对话|独白|短文)[^。]*[。]?\s*$/i, '')
    .trim()
}

const answerIndex = (value) => {
  if (Number.isInteger(value) && value >= 0 && value < 26) return value
  const letter = String(value || '').trim().toUpperCase().match(/[A-Z]/)?.[0]
  return letter ? letter.charCodeAt(0) - 65 : -1
}

const answerLetter = (value) => {
  const index = answerIndex(value)
  return index >= 0 ? String.fromCharCode(65 + index) : ''
}

// Export the normalized internal model in the same shape as the full-question JSON template.
export function toTemplatePaper(paper) {
  return {
    title: paper.title,
    date: paper.date || '',
    duration: paper.duration,
    questions: (paper.questions || []).map((question) => {
      const output = {
        type: question.type || 'single_choice',
        section: question.section || '选择题',
        stem: question.stem || '',
        analysis: question.analysis || ''
      }
      if (question.passage) output.passage = question.passage
      if (output.type === 'single_choice' || output.type === 'listening') {
        output.options = (question.options || []).slice()
        output.answer = answerLetter(question.answer)
        if (output.type === 'listening' && question.audioUrl) output.audioUrl = question.audioUrl
      } else if (output.type === 'cloze') {
        output.options = (question.options || []).slice()
        output.answers = (question.answers || []).map(answerLetter)
      } else if (output.type === 'matching') {
        output.options = (question.options || []).slice()
        output.items = (question.items || []).map((item) => ({ id: item.id, prompt: item.prompt, answer: answerLetter(item.answer) }))
      } else {
        output.referenceAnswer = question.referenceAnswer || ''
      }
      return output
    })
  }
}

export function validatePaper(input, fallback = {}) {
  const paper = {
    ...input,
    category: input.category || fallback.category,
    title: clean(input.title || fallback.title),
    date: clean(input.date || fallback.date || ''),
    duration: Number(input.duration || fallback.duration || 30),
    scope: clean(input.scope || fallback.scope || ''),
    audioUrl: clean(input.audioUrl || fallback.audioUrl || ''),
    status: 'ready',
    custom: true
  }

  if (!CATEGORY_VALUES.has(paper.category)) throw new Error('请选择四级、六级或考研分类')
  if (!paper.title) throw new Error('试卷名称不能为空')
  if (!Array.isArray(input.questions) || input.questions.length === 0) throw new Error('没有识别到可导入题目')

  const prefix = `custom-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
  paper.id = input.id?.startsWith('custom-') ? input.id : prefix
  paper.questions = input.questions.map((question, index) => {
    const type = question.type || 'single_choice'
    if (!clean(question.stem)) throw new Error(`第 ${index + 1} 题缺少题干`)
    if (!QUESTION_TYPES.has(type)) throw new Error(`第 ${index + 1} 题题型无效`)
    if (!clean(question.analysis)) throw new Error(`第 ${index + 1} 题缺少解析`)

    const normalized = {
      id: `${paper.id}-q-${index + 1}`,
      number: question.number || index + 1,
      type,
      section: clean(question.section || '选择题'),
      passage: clean(question.passage),
      stem: clean(question.stem),
      analysis: clean(question.analysis),
      audioUrl: clean(question.audioUrl || paper.audioUrl)
    }

    if (type === 'single_choice') {
      normalized.options = question.options?.map(clean)
      normalized.answer = answerIndex(question.answer)
      if (normalized.options?.length !== 4 || normalized.options.some(option => !option)) throw new Error(`第 ${index + 1} 题必须有完整的 A-D 四个选项`)
      if (normalized.answer < 0 || normalized.answer > 3) throw new Error(`第 ${index + 1} 题答案必须是 A、B、C 或 D`)
    } else if (type === 'cloze') {
      normalized.options = question.options?.map(clean)
      normalized.answers = (question.answers || []).map(answerIndex)
      const blankCount = (normalized.stem.match(/_{2,}/g) || []).length
      if (!normalized.options?.length || normalized.options.some(option => !option)) throw new Error(`第 ${index + 1} 题缺少完形词库`)
      if (!blankCount || normalized.answers.length !== blankCount || normalized.answers.some(answer => answer < 0 || answer >= normalized.options.length)) throw new Error(`第 ${index + 1} 题完形答案数量必须与空格数量一致`)
    } else if (type === 'matching') {
      normalized.options = question.options?.map(clean)
      normalized.items = (question.items || []).map((item, itemIndex) => ({ id: item.id || `${itemIndex + 1}`, prompt: clean(item.prompt), answer: answerIndex(item.answer) }))
      if (!normalized.options?.length || !normalized.items.length || normalized.items.some(item => !item.prompt || item.answer < 0 || item.answer >= normalized.options.length)) throw new Error(`第 ${index + 1} 题段落匹配内容不完整`)
    } else if (type === 'listening') {
      normalized.options = question.options?.map(clean)
      normalized.answer = answerIndex(question.answer)
      if (normalized.options?.length !== 4 || normalized.options.some(option => !option)) throw new Error(`第 ${index + 1} 题必须有完整的 A-D 四个选项`)
      if (normalized.answer < 0 || normalized.answer > 3) throw new Error(`第 ${index + 1} 题答案必须是 A、B、C 或 D`)
    } else {
      normalized.referenceAnswer = clean(question.referenceAnswer)
      if (!normalized.referenceAnswer) throw new Error(`第 ${index + 1} 题缺少参考答案`)
    }
    return normalized
  })
  return paper
}

export function parsePaperJson(text, fallback) {
  let parsed
  try {
    parsed = JSON.parse(text)
  } catch {
    throw new Error('JSON 格式无效')
  }
  return validatePaper(parsed, fallback)
}

export function parsePaperMarkdown(text, fallback) {
  const title = clean(text.match(/^#\s+(.+)$/m)?.[1] || fallback.title)
  const date = clean(text.match(/\*\*日期[：:]\*\*\s*(.+)/)?.[1] || fallback.date)
  const duration = Number(text.match(/\*\*时长[：:]\*\*\s*(\d+)/)?.[1] || fallback.duration || 30)
  const headingMatches = [...text.matchAll(/^##\s+(.+)$/gm)]
  const questions = []

  for (let sectionIndex = 0; sectionIndex < headingMatches.length; sectionIndex += 1) {
    const sectionMatch = headingMatches[sectionIndex]
    const section = clean(sectionMatch[1])
    const sectionText = text.slice(sectionMatch.index + sectionMatch[0].length, headingMatches[sectionIndex + 1]?.index ?? text.length)
    const questionMatches = [...sectionText.matchAll(/^###\s+(\d+)\s*$/gm)]

    for (let index = 0; index < questionMatches.length; index += 1) {
      const match = questionMatches[index]
      const block = sectionText.slice(match.index + match[0].length, questionMatches[index + 1]?.index ?? sectionText.length)
      const answerMatch = block.match(/\*\*答案[：:]\*\*\s*([A-D])/i)
      const analysisMatch = block.match(/\*\*解析[：:]\*\*\s*([\s\S]+)$/i)
      const contentBeforeAnswer = block.slice(0, answerMatch?.index ?? block.length)
      const optionMatches = [...contentBeforeAnswer.matchAll(/^([A-D])[.、)）]\s*(.+)$/gm)]
      if (optionMatches.length !== 4) throw new Error(`${section}第 ${match[1]} 题未识别到完整 A-D 选项`)

      const firstOptionIndex = optionMatches[0].index
      const prompt = contentBeforeAnswer.slice(0, firstOptionIndex).trim()
      const passageMatch = prompt.match(/\*\*(?:文章|原文|材料)[：:]\*\*\s*([\s\S]*?)(?=\*\*(?:问题|题目)[：:]\*\*)/i)
      const stemMatch = prompt.match(/\*\*(?:问题|题目)[：:]\*\*\s*([\s\S]+)$/i)

      questions.push({
        number: Number(match[1]),
        section,
        passage: clean(passageMatch?.[1]),
        stem: clean(stemMatch?.[1] || prompt),
        options: optionMatches.sort((a, b) => a[1].localeCompare(b[1])).map(item => clean(item[2])),
        answer: answerMatch?.[1],
        analysis: analysisMatch?.[1]
      })
    }
  }

  if (questions.length) return validatePaper({ title, date, duration, questions }, fallback)
  return parseMineruMarkdown(text, { ...fallback, title, date, duration })
}

// MinerU exports exam questions as inline `1. A) ... B) ...` blocks and puts
// answers in a table near the end. Convert that common OCR shape to our schema.
function parseMineruMarkdown(text, fallback) {
  const answerText = text.slice(Math.max(text.lastIndexOf('答案速查'), text.lastIndexOf('答案')))
  const answerMap = new Map()
  for (const match of answerText.matchAll(/(?:^|[<td>\s])([0-9]{1,3})\s*[.、：:]?\s*([A-D])\b/gi)) {
    answerMap.set(Number(match[1]), match[2].toUpperCase())
  }
  const questions = []
  const optionPattern = /(?:^|\n)\s*(\d{1,3})[.、]\s*A[)）.]\s*(.*?)\s+B[)）.]\s*(.*?)\s+C[)）.]\s*(.*?)\s+D[)）.]\s*([^\n]+?)(?=\s*\n\s*\d{1,3}[.、]|\s*\n\s*##|$)/gs
  for (const match of text.slice(0, Math.max(0, text.lastIndexOf('答案速查'))).matchAll(optionPattern)) {
    const number = Number(match[1])
    const answer = answerMap.get(number)
    if (!answer) continue
    questions.push({
      number,
      section: 'MinerU 识别题目',
      stem: `第 ${number} 题`,
      options: [match[2], match[3], match[4], match[5]].map(clean),
      answer,
      analysis: '该题由 MinerU 原始资料转换，原文未提供逐题解析。'
    })
  }
  if (!questions.length) throw new Error('未识别到 MinerU 题目。请确认文件包含“题号 + A) B) C) D)”以及文末答案。')
  return validatePaper({ ...fallback, questions }, {})
}

export async function parsePaperFile(file, fallback) {
  if (file.name.toLowerCase().endsWith('.pdf')) {
    throw new Error('PDF 尚未直接解析：请先在 MinerU 中上传 PDF 并下载 ZIP，解压后选择其中的 full.md。这样才能保留题目、选项和答案结构。')
  }
  const text = await file.text()
  if (file.name.toLowerCase().endsWith('.json')) return parsePaperJson(text, fallback)
  if (file.name.toLowerCase().endsWith('.md')) return parsePaperMarkdown(text, fallback)
  throw new Error('仅支持 .md 和 .json 文件')
}
