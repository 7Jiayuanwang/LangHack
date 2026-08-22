const CATEGORY_VALUES = new Set(['cet4', 'cet6', 'postgraduate'])
const QUESTION_TYPES = new Set(['single_choice', 'cloze', 'matching', 'translation', 'writing'])

const clean = (value = '') => value
  .replace(/!\[[^\]]*]\([^)]*\)/g, '')
  .replace(/<[^>]+>/g, ' ')
  .replace(/\s+/g, ' ')
  .trim()

const answerIndex = (value) => {
  if (Number.isInteger(value) && value >= 0 && value <= 3) return value
  const letter = String(value || '').trim().toUpperCase().match(/[A-D]/)?.[0]
  return letter ? letter.charCodeAt(0) - 65 : -1
}

export function validatePaper(input, fallback = {}) {
  const paper = {
    ...input,
    category: input.category || fallback.category,
    title: clean(input.title || fallback.title),
    date: clean(input.date || fallback.date || ''),
    duration: Number(input.duration || fallback.duration || 30),
    scope: clean(input.scope || fallback.scope || ''),
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
      analysis: clean(question.analysis)
    }

    if (type === 'single_choice') {
      normalized.options = question.options?.map(clean)
      normalized.answer = answerIndex(question.answer)
      if (normalized.options?.length !== 4 || normalized.options.some(option => !option)) throw new Error(`第 ${index + 1} 题必须有完整的 A-D 四个选项`)
      if (normalized.answer < 0) throw new Error(`第 ${index + 1} 题答案必须是 A、B、C 或 D`)
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

  return validatePaper({ title, date, duration, questions }, fallback)
}

export async function parsePaperFile(file, fallback) {
  const text = await file.text()
  if (file.name.toLowerCase().endsWith('.json')) return parsePaperJson(text, fallback)
  if (file.name.toLowerCase().endsWith('.md')) return parsePaperMarkdown(text, fallback)
  throw new Error('仅支持 .md 和 .json 文件')
}
