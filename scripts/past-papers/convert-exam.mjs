#!/usr/bin/env node
/**
 * 四六级真题 → LangHack 导入 JSON 转换器
 *
 * 多源合并：
 *   1. 题目卷 PDF 文本层（pdftotext -layout）—— 最干净，且卷末常自带 KEYS 答案表
 *   2. MinerU v2 / v1 JSON —— 扫描件唯一来源
 *   3. 答案卷（解析扫描件的 MinerU JSON）—— 解析、范文、参考译文、听力题干
 * 任一来源出现缺口时按上面的优先级自动回填，并在质量报告里记录回填来源。
 *
 * 用法：
 *   node convert-exam.mjs --paper cet6_2024_06_1 --out <目录>
 *   node convert-exam.mjs --mineru <目录> --out <目录>        # 批量转换目录下所有套次
 *   node convert-exam.mjs <题目源> <答案源> [输出.json]        # 兼容旧版位置参数
 *
 * 选项：
 *   --pdf-root <目录>     原始 PDF 根目录（默认 E:/英语真题/真题集/真题）
 *   --mineru <目录>       MinerU JSON 目录（递归扫描）
 *   --out <目录>          输出目录（默认 MinerU 目录下的 converted/）
 *   --paper <id>          只转指定套次，如 cet6_2024_06_1
 *   --category cet4|cet6|postgraduate
 *   --strict              存在任何缺口即报错退出（默认尽力转换 + 出报告）
 *   --no-pdf              禁用 PDF 源，仅用 MinerU
 *   --report <文件>       质量报告输出路径（默认 <out>/_转换报告.md）
 */

import fs from 'node:fs'
import path from 'node:path'
import { execFileSync } from 'node:child_process'

// ─────────────────────────── 通用工具 ───────────────────────────

const clean = (value = '') => String(value)
  .replace(/<[^>]+>/g, ' ')
  .replace(/&nbsp;/gi, ' ')
  .replace(/&quot;/gi, '"')
  .replace(/&#x27;|&#39;/gi, "'")
  .replace(/&amp;/gi, '&')
  .replace(/\s+/g, ' ')
  .trim()

const LETTERS = 'ABCDEFGHIJKLMNO'
const letterOf = index => LETTERS[index]
const indexOfLetter = letter => LETTERS.indexOf(String(letter || '').toUpperCase())

/** OCR 常见混淆：把行首/词首被误识别的 l、1 还原成 I，0 还原成 O。 */
const fixOcrLetter = value => String(value || '').toUpperCase().replace(/^[0]$/, 'O').replace(/^[L1|]$/, 'I')

// ─────────────────────────── 命令行 ───────────────────────────

function parseArgv(argv) {
  const options = { positional: [] }
  for (let index = 0; index < argv.length; index += 1) {
    const token = argv[index]
    if (!token.startsWith('--')) { options.positional.push(token); continue }
    const key = token.slice(2)
    if (key === 'strict' || key === 'no-pdf') { options[key] = true; continue }
    options[key] = argv[++index]
  }
  return options
}

// ─────────────────────────── 数据源加载 ───────────────────────────

let pdftotextAvailable = null
function hasPdftotext() {
  if (pdftotextAvailable !== null) return pdftotextAvailable
  // poppler 的 `pdftotext -v` 返回 0，Xpdf 版返回 99，两者都把版本号打到 stderr。
  // 只要能拿到版本字样就认为可用。
  try {
    const probe = execFileSync('pdftotext', ['-v'], { stdio: 'pipe' })
    pdftotextAvailable = /pdftotext/i.test(String(probe))
  } catch (error) {
    pdftotextAvailable = /pdftotext/i.test(String(error.stderr || '') + String(error.stdout || ''))
  }
  return pdftotextAvailable
}

/** 抽取 PDF 文本层；纯扫描件返回 ''。结果缓存到 <pdf 同级>/.pdftext-cache/。 */
function loadPdfText(pdfPath) {
  if (!pdfPath || !fs.existsSync(pdfPath)) return ''
  const cacheDir = path.join(path.dirname(pdfPath), '.pdftext-cache')
  const cacheFile = path.join(cacheDir, `${path.basename(pdfPath, '.pdf')}.txt`)
  try {
    if (fs.existsSync(cacheFile) && fs.statSync(cacheFile).mtimeMs >= fs.statSync(pdfPath).mtimeMs) {
      return fs.readFileSync(cacheFile, 'utf8')
    }
  } catch { /* 缓存不可用时直接重新抽取 */ }
  if (!hasPdftotext()) return ''
  let text = ''
  try { text = execFileSync('pdftotext', ['-layout', '-enc', 'UTF-8', pdfPath, '-'], { maxBuffer: 64 * 1024 * 1024 }).toString('utf8') }
  catch { return '' }
  if (text.replace(/\s/g, '').length < 2000) return ''   // 扫描件 / 只有水印
  try { fs.mkdirSync(cacheDir, { recursive: true }); fs.writeFileSync(cacheFile, text, 'utf8') } catch { /* 缓存写失败不影响主流程 */ }
  return text
}

function collectText(value, out = []) {
  if (typeof value === 'string') out.push(value)
  else if (Array.isArray(value)) value.forEach(item => collectText(item, out))
  else if (value && typeof value === 'object') {
    const ignored = new Set(['bbox', 'image_source', 'page_number', 'page_footer', 'page_header', 'type', 'level', 'table_type', 'table_nest_level', 'list_type', 'attribute', 'item_type', 'img_path'])
    for (const [key, item] of Object.entries(value)) if (!ignored.has(key)) collectText(item, out)
  }
  return out
}

const SKIP_BLOCK_TYPES = new Set(['image', 'page_number', 'page_footer', 'page_header'])

/** MinerU JSON → 统一的 block 列表，同时兼容 v2（按页嵌套）与 v1（扁平 + page_idx）。 */
function loadMineruBlocks(jsonPath) {
  if (!jsonPath || !fs.existsSync(jsonPath)) return []
  let document
  try { document = JSON.parse(fs.readFileSync(jsonPath, 'utf8')) } catch { return [] }
  if (!Array.isArray(document) || !document.length) return []

  if (Array.isArray(document[0])) {                       // v2：[[block, ...], ...]
    return document.flatMap((page, pageIndex) => page
      .filter(block => !SKIP_BLOCK_TYPES.has(block.type))
      .map(block => ({
        page: pageIndex + 1,
        type: block.type,
        text: clean(collectText(block.content).join(' ')),
        html: block.type === 'table' ? String(block.content?.html || '') : ''
      })))
  }
  return document                                          // v1：[block, ...]
    .filter(block => !SKIP_BLOCK_TYPES.has(block.type))
    .map(block => ({
      page: (block.page_idx ?? 0) + 1,
      type: block.type === 'table' ? 'table' : (block.text_level ? 'title' : 'paragraph'),
      text: clean(block.text || collectText(block.table_caption || []).join(' ')),
      html: block.type === 'table' ? String(block.table_body || '') : ''
    }))
}

function tableRows(html) {
  return [...String(html).matchAll(/<tr[^>]*>([\s\S]*?)<\/tr>/gi)]
    .map(row => [...row[1].matchAll(/<t[dh][^>]*>([\s\S]*?)<\/t[dh]>/gi)].map(cell => clean(cell[1])))
}

// ─────────────────────── 文本规范化（PDF 版式拆栏） ───────────────────────

const NOISE_LINE = /^(?:\s*\d{1,3}\s*|.*?第\s*\d+\s*页.*|.*?(?:公众号|微信公众号|扫码|关注).*|机密★启用前|COLLEGE ENGLISH TEST|—\s*Band\s+(?:Four|Six)\s*—|试卷[一二]?)$/i

/**
 * pdftotext -layout 会把同一题的多个选项排在一行，两种常见形态：
 *   "A) Slack supervision style        C) Unforeseeable risk."      —— 行首即选项
 *   "4. A) Money.   B) Time.   C) Determination.   D) Optimism."    —— 行首是题号
 * 这里按选项字母切开让每个选项独占一行（题号行保留题号本身），
 * 后续 parseOptionBlock 才能按字母归位，左右分栏顺序（A C / B D）也不会错乱。
 */
function splitOptionColumns(line) {
  const leading = line.match(/^\s*(\d{1,2}\s*[.、]\s*)?/)[0]
  const body = line.slice(leading.length)
  if (!/^[A-O0-9]\s*[)．.]/.test(body)) return [line]

  // 分栏之间的空白宽度不可靠（有的只隔一个空格），所以改用字母序列来判定：
  // 收集所有候选标记，只保留字母严格递增的那条链。注意左右分栏是 A C / B D，
  // 字母未必连续，所以只要求递增，不要求相邻。
  const candidates = [...body.matchAll(/(?:^|\s)([A-O])\s*[)．.](?=\s|[A-Z“"'])/g)]
  const marks = []
  for (const candidate of candidates) {
    const previous = marks.length ? LETTERS.indexOf(marks[marks.length - 1][1]) : -1
    if (LETTERS.indexOf(candidate[1]) > previous) marks.push(candidate)
  }
  if (marks.length < 2) return [line]

  const parts = marks.map((mark, index) => {
    const start = mark.index + (mark[0].length - mark[0].trimStart().length)
    return body.slice(start, marks[index + 1]?.index ?? body.length).trim()
  }).filter(Boolean)

  // 题号必须留在自己的行上，否则 splitByNumber 会丢掉这一题
  return leading.trim() ? [leading.trim(), ...parts] : parts
}

/** PDF 文本 → 逐行规范化文本（拆栏、去页眉页脚水印）。 */
function normalizePdfText(raw) {
  const lines = []
  for (const rawLine of raw.split(/\r?\n/)) {
    const line = rawLine.replace(/\f/g, '')
    if (!line.trim()) { lines.push(''); continue }
    if (NOISE_LINE.test(line.trim())) continue
    lines.push(...splitOptionColumns(line))
  }
  return lines.join('\n')
}

/** MinerU blocks → 与 PDF 同构的纯文本，便于共用同一套解析器。 */
function normalizeMineruText(blocks) {
  return blocks.map(block => block.text).filter(Boolean).join('\n')
}

// ─────────────────────────── 试卷标识 ───────────────────────────

const CN_DIGIT = { 一: 1, 二: 2, 三: 3, 四: 4, 五: 5, 六: 6, 七: 7, 八: 8, 九: 9, 十: 10 }
const SET_CN = ['', '一', '二', '三', '四', '五']

/** 从文件名或标题解析出 {examType, year, month, set}。 */
function parsePaperId(source) {
  const name = String(source)
  let examType = ''
  if (/cet-?6|cet6|六级|Band\s*Six/i.test(name)) examType = 'cet6'
  else if (/cet-?4|cet4|四级|Band\s*Four/i.test(name)) examType = 'cet4'
  else if (/考研|postgraduate|英语[一二]/.test(name)) examType = 'postgraduate'

  // cet6_2024_06_1  /  cet6_2024_06_2-3
  const snake = name.match(/cet([46])_(\d{4})_(\d{2})_(\d)(?:-\d)?/i)
  if (snake) return { examType: `cet${snake[1]}`, year: +snake[2], month: +snake[3], set: +snake[4] }

  // 2025.06六级真题第1套 / 2025.06英语四级解析第1套
  const dotted = name.match(/(\d{4})[.\-年](\d{1,2})[月]?.*?第\s*([1-5一二三四五])\s*套/)
  if (dotted) return { examType, year: +dotted[1], month: +dotted[2], set: CN_DIGIT[dotted[3]] || +dotted[3] }

  // 大学英语六级考试 2024 年 6 月真题(第一套)
  const titled = name.match(/(20\d{2})\s*年\s*(\d{1,2})\s*月/)
  const setMatch = name.match(/第\s*([1-5一二三四五])\s*套|[(（]\s*([1-5一二三四五])\s*[)）]/)
  if (titled) {
    const setToken = setMatch?.[1] || setMatch?.[2] || ''
    return { examType, year: +titled[1], month: +titled[2], set: CN_DIGIT[setToken] || +setToken || 0 }
  }
  return { examType, year: 0, month: 0, set: 0 }
}

const paperKey = id => id.examType && id.year
  ? `${id.examType}_${id.year}_${String(id.month).padStart(2, '0')}_${id.set || 1}`
  : ''

const EXAM_LABEL = { cet4: '四级', cet6: '六级', postgraduate: '考研英语' }
const EXAM_DURATION = { cet4: 125, cet6: 130, postgraduate: 180 }
// 听力时段（分钟）：官方总时长 = 写作30 + 听力? + 阅读40 + 翻译30。
// 资料里没有听力题时（第三套通常与第一套共用听力），必须把听力时段从考试时长里扣掉，
// 否则学生面对 14 题的卷子却有 125/130 分钟。考研英语本就不考听力，不受影响。
const LISTENING_MINUTES = { cet4: 25, cet6: 30 }
/** 本套实际考试时长：有听力=整套时长；缺听力=整套时长 - 听力时段。 */
function examDuration(id, hasListening) {
  const full = EXAM_DURATION[id.examType] || 130
  if (hasListening) return full
  const listening = LISTENING_MINUTES[id.examType] || 0
  return full - listening
}

function paperTitle(id) {
  const label = EXAM_LABEL[id.examType] || ''
  const set = id.set ? `（第${SET_CN[id.set] || id.set}套）` : ''
  return `${id.year}年${id.month}月大学英语${label}真题${set}`
}

// ─────────────────────── 源文件定位（供“从原始 PDF 修复”） ───────────────────────

function walk(dir, out = []) {
  if (!dir || !fs.existsSync(dir)) return out
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name)
    if (entry.isDirectory()) { if (entry.name !== '.pdftext-cache') walk(full, out) }
    else out.push(full)
  }
  return out
}

/** 扫描 PDF 根目录，建立 paperKey → {question, answer} 的索引。 */
function indexPdfRoot(pdfRoot) {
  const index = new Map()
  for (const file of walk(pdfRoot).filter(item => item.toLowerCase().endsWith('.pdf'))) {
    const base = path.basename(file, path.extname(file))
    const relative = path.relative(pdfRoot, file)
    const isAnswer = /_ans$|解析|答案/i.test(base)
    const id = parsePaperId(`${relative} ${base}`)
    const key = paperKey(id)
    if (!key) continue
    const entry = index.get(key) || {}
    // 同一套可能存在多个文件，优先保留有文本层的
    entry[isAnswer ? 'answerPdf' : 'questionPdf'] ??= file
    index.set(key, entry)
  }
  return index
}

/** 扫描 MinerU 目录，建立 paperKey → {questionV2, questionV1, answerV2, answerV1}。 */
function indexMineruRoot(mineruRoot) {
  const index = new Map()
  for (const file of walk(mineruRoot).filter(item => item.toLowerCase().endsWith('.json'))) {
    const base = path.basename(file, '.json')
    if (!/_content_list(_v2)?$/i.test(base)) continue
    const isV2 = /_v2$/i.test(base)
    const stem = base.replace(/_content_list(_v2)?$/i, '')
    const isAnswer = /_ans$/i.test(stem)
    const key = paperKey(parsePaperId(stem))
    if (!key) continue
    const entry = index.get(key) || {}
    const field = `${isAnswer ? 'answer' : 'question'}${isV2 ? 'V2' : 'V1'}`
    entry[field] ??= file
    index.set(key, entry)
  }
  return index
}

// ─────────────────────────── 分节 ───────────────────────────

/**
 * 用小节名而不是 "Part N" 做锚点：2025 年的 PDF 里 "Part III" 被 OCR 成 "Part UI"，
 * 而 "Reading Comprehension" 这类标题始终稳定。
 */
const LANDMARKS = [
  ['writing', /^\s*(?:Part\s*[I1l|]\b.*)?Writing\b|^Directions:\s*(?:Suppose|For this part, you are allowed \d+ minutes to write)/im],
  ['listening', /Listening\s+Comprehension/i],
  // Some CET-4 question sheets omit the Reading Comprehension heading from
  // MinerU output.  Section A's fixed directions are a reliable fallback;
  // keeping both alternatives lets match() pick the earliest real boundary
  // instead of a duplicate heading in the answer key at the end of the sheet.
  ['reading', /Reading\s+Comprehension|^\s*Section\s+A\s*(?:\r?\n|\s)+\s*Directions:\s*In this section,\s*there is a passage with ten blanks\b/im],
  ['translation', /^\s*Part\s*[I1l|]V\b.*Translation|^\s*Translation\s*(?:\(|$)|translate a passage from Chinese into English/im]
]

function sectionBounds(text) {
  const bounds = {}
  for (const [name, pattern] of LANDMARKS) {
    const match = text.match(new RegExp(pattern.source, pattern.flags.includes('m') ? 'im' : 'i'))
    bounds[name] = match ? match.index : -1
  }
  const keysIndex = text.search(/^\s*KEYS\s*$|答案速查/m)
  bounds.keys = keysIndex
  bounds.end = keysIndex >= 0 ? keysIndex : text.length
  return bounds
}

const between = (text, from, to) => (from < 0 ? '' : text.slice(from, to > from ? to : text.length))

// ─────────────────────────── 答案表解析 ───────────────────────────

/** 解析题目卷末尾的 KEYS 表：一行题号、下一行答案。 */
function answersFromKeys(keysText) {
  const map = new Map()
  const lines = keysText.split(/\r?\n/).map(line => line.trim()).filter(Boolean)
  for (let index = 0; index < lines.length - 1; index += 1) {
    const numbers = lines[index].split(/\s+/)
    if (!numbers.length || !numbers.every(token => /^\d{1,2}$/.test(token))) continue
    const letters = lines[index + 1].split(/\s+/).map(fixOcrLetter)
    if (letters.length !== numbers.length || !letters.every(token => /^[A-O]$/.test(token))) continue
    numbers.forEach((number, position) => map.set(+number, letters[position]))
  }
  return map
}

/** 解析扫描件（答案卷）里的答案表格。 */
function answersFromTables(blocks) {
  const map = new Map()
  for (const block of blocks.filter(item => item.type === 'table' && item.html)) {
    const rows = tableRows(block.html)
    for (let row = 0; row < rows.length - 1; row += 1) {
      const numbers = rows[row].filter(cell => /^\d{1,2}$/.test(cell)).map(Number)
      const letters = rows[row + 1]
        .map(cell => cell.match(/(?:^|\.)\s*([A-O0L1])\s*$/i)?.[1])
        .filter(Boolean)
        .map(fixOcrLetter)
      if (numbers.length && numbers.length === letters.length) {
        numbers.forEach((number, position) => map.set(number, letters[position]))
      }
    }
    for (const cell of rows.flat()) {
      // MinerU may preserve two or more answer pairs in one cell, e.g.
      // "1.A 11.D".  Parse every pair rather than requiring a whole-cell
      // match; this also covers compact cells such as "52. B" and OCR "26.0".
      for (const match of cell.matchAll(/(\d{1,2})(?!\d)\s*[.、:：]?\s*([A-O0L1])(?=\s|$)/gi)) {
        const number = +match[1]
        if (number >= 1 && number <= 56) map.set(number, fixOcrLetter(match[2]))
      }
    }
  }
  return map
}

/** 兜底：答案卷正文里的 "27. I) loose" / "46. B" 之类行内答案。 */
function answersFromInline(text) {
  const map = new Map()
  for (const match of text.matchAll(/(?:^|\n)\s*(\d{1,2})\s*[.、]\s*\[?([A-O])\]?[)）]?\s*(?=[\s\u4e00-\u9fff]|$)/g)) {
    const number = +match[1]
    if (number >= 1 && number <= 56 && !map.has(number)) map.set(number, match[2].toUpperCase())
  }
  return map
}

// ─────────────────────────── 题块切分 ───────────────────────────

/**
 * 在文本里按题号切块，返回 [{number, text}]，题号必须落在 [from, to] 且严格递增。
 * 题号后的点常被 OCR 丢掉（"43 It is of great benefit..."），所以分隔符允许是
 * 点顿号，也允许是空格 —— 后者要求紧跟大写字母或 "A)" 之类的选项标记，避免把
 * 正文里的普通数字（年份、金额）误当成题号。
 */
function splitByNumber(text, from, to) {
  const marks = []
  // MinerU v2 can put two numbered statements in one list block, for
  // example "37. ...、38. ...".  Accept sentence punctuation as a boundary
  // in addition to a physical newline, while retaining the uppercase/marker
  // guard so years and ordinary numbers in the passage are not treated as
  // question numbers.
  for (const match of text.matchAll(/(?:^|\n|[。.!?！？；;、])[ \t]*(\d{1,2})(?!\d)(?:\s*[.、]|\s+(?=[A-Z]))/g)) {
    const number = +match[1]
    if (number < from || number > to) continue
    if (marks.length && number <= marks[marks.length - 1].number) continue   // 拒绝倒退的误匹配
    marks.push({ number, index: match.index + match[0].length, start: match.index })
  }
  return marks.map((mark, position) => ({
    number: mark.number,
    text: text.slice(mark.index, marks[position + 1]?.start ?? text.length)
  }))
}

// 选项行。字母放宽到 A-D 之外，因为 OCR 常把 C 认成 O/G/0、把 B 认成 8、把 D 认成 O，
// 具体归位交给 parseOptionBlock 里的上下文纠错。
const OPTION_LINE = /^\s*([A-DOG08])\s*[)．.]\s*(.*)$/
const STOP_LINE = /^\s*(?:Part\s|Section\s+[A-C]\s*$|Passage\s+(?:One|Two|Three)\s*$|Questions?\s+\d+\s+(?:to|and)\s+\d+|KEYS\s*$|Directions:)/i

/**
 * 从题块里抽 4 个选项。按字母归位，因此左右分栏（A C B D）也能正确还原。
 * 返回 {stem, options: {A,B,C,D}}，缺的字母留空由后续修复阶段补。
 */
function parseOptionBlock(blockText) {
  const lines = blockText.split(/\r?\n/).flatMap(line => {
    // MinerU CET-4 blocks commonly keep A) C) B) D) on one physical line.
    // Split every marker first; do not require alphabetical order because two-column
    // layouts naturally arrive as A/C/B/D.
    const marks = [...line.matchAll(/(?:^|\s)([A-DOG08])\s*[)．.](?=\s|[A-Z\u4e00-\u9fff]|$)/g)]
    if (marks.length < 2) return [line]
    const prefix = line.slice(0, marks[0].index).trim()
    const parts = marks.map((mark, index) => {
      const start = mark.index + (mark[0].length - mark[0].trimStart().length)
      return line.slice(start, marks[index + 1]?.index ?? line.length).trim()
    }).filter(Boolean)
    return prefix ? [prefix, ...parts] : parts
  })
  const options = new Map()
  const stemLines = []
  let current = null
  for (const line of lines) {
    if (STOP_LINE.test(line)) break
    const match = line.match(OPTION_LINE)
    if (match) {
      current = match[1].toUpperCase()
      if (!options.has(current)) options.set(current, [match[2]])
      continue
    }
    if (current) options.get(current).push(line)
    else stemLines.push(line)
  }

  // 上下文纠错：把落在 A-D 之外的 OCR 误识字母安置到唯一空缺的位置上。
  // 例如原卷 "C) It relies on..." 被识别成 "O)"，此时 A/B/D 已齐，C 空缺，即可确定归属。
  const strays = [...options.keys()].filter(letter => !'ABCD'.includes(letter))
  for (const stray of strays) {
    const vacancies = 'ABCD'.split('').filter(letter => !options.has(letter))
    if (vacancies.length !== 1) continue          // 有歧义时宁可不猜，留给后续回填
    options.set(vacancies[0], options.get(stray))
    options.delete(stray)
  }

  const values = {}
  for (const letter of 'ABCD') {
    const parts = options.get(letter)
    if (parts) values[letter] = clean(parts.join(' '))
  }
  return { stem: clean(stemLines.join(' ')), options: values }
}

// ─────────────────────── 答案卷挖掘（解析 / 范文 / 译文 / 听力题干） ───────────────────────

/** 第 number 题的解析：取答案卷里最后一次出现的 "N." 到下一题号之间的文字。 */
function analysisFor(answerText, number, fallbackAnswer) {
  const marks = [...answerText.matchAll(new RegExp(`(?:^|\\n|\\s)${number}\\s*[.、]\\s*`, 'g'))]
  if (!marks.length) return fallbackAnswer ? `正确答案：${fallbackAnswer}` : ''
  const mark = marks[marks.length - 1]
  const tail = answerText.slice(mark.index + mark[0].length)
  const stops = [
    tail.search(new RegExp(`(?:^|\\n|\\s)${number + 1}\\s*[.、]\\s*`)),
    tail.search(/\n\s*(?:Section\s+[A-C]|Part\s+[IVXⅠ-Ⅴ]+|答案速查|参考范文|参考译文)\b/i)
  ].filter(index => index > 0)
  const cut = stops.length ? Math.min(...stops) : -1
  return clean(tail.slice(0, cut < 0 ? undefined : cut)).slice(0, 5000)
}

/** 听力题干：题目卷不印题干（靠听），只能从答案卷抓。中英文都接受。 */
function stripListeningOptions(value) {
  const text = clean(value)
  // 六月答案卷把题干和 A-D 选项放在同一个 block，且 OCR 偶尔把 C 识成 O/G/0/8。
  // 选项标记通常紧跟在中英文问号后，因此截断到第一个候选标记即可保留完整题干。
  const marker = text.match(/(?:^|[\s?？。！!])([A-DOG08])\s*[)．.](?=\s|[A-Z\u4e00-\u9fff]|$)/i)
  if (!marker) return text
  const markerAt = marker.index + marker[0].indexOf(marker[1])
  return clean(text.slice(0, markerAt))
}

/** 某些答案卷会丢掉题目标题，只留下带 [N] 标记的听力原文。 */
function contextualListeningStems(answerBlocks) {
  const text = answerBlocks.map(block => block.text).join('\n')
  const ageShift = text.match(/\[(\d{1,2})\][\s\S]{0,600}?(?:positive consequence of (?:this )?age shift|这种生育年龄后移的一个积极影响)[\s\S]{0,600}?(?:economically prepared|经济上准备好)/i)
  if (!ageShift || +ageShift[1] < 1 || +ageShift[1] > 25) return new Map()
  return new Map([[+ageShift[1], 'What is said to be a positive consequence of the age shift among American mothers?']])
}

function listeningStems(answerBlocks) {
  const map = new Map()
  for (let index = 0; index < answerBlocks.length; index += 1) {
    const head = answerBlocks[index].text.match(/^(\d{1,2})(?:\s*[.、]|\s+(?=[A-Z]))\s*(.*)$/)
    if (!head) continue
    const number = +head[1]
    if (number < 1 || number > 25) continue
    let stem = stripListeningOptions(head[2])
    let cursor = index
    while (!/[?？]\s*$/.test(stem) && cursor + 1 < answerBlocks.length && cursor < index + 2) {
      stem = stripListeningOptions(`${stem} ${answerBlocks[++cursor].text}`)
    }
    if (/[?？]\s*$/.test(stem) && !map.has(number)) map.set(number, clean(stem))
  }
  for (const [number, stem] of contextualListeningStems(answerBlocks)) if (!map.has(number)) map.set(number, stem)
  return map
}

/** 从答案卷抽 "参考范文" / "参考译文" 之后的正文。 */
function textAfterMarker(blocks, marker, stopPattern) {
  const markerIndex = blocks.findIndex(block => marker.test(block.text))
  if (markerIndex < 0) return ''
  const inline = blocks[markerIndex].text.replace(new RegExp(`^.*?${marker.source}[：:·\\s]*`, 'i'), '')
  const values = inline && inline !== blocks[markerIndex].text ? [inline] : []
  for (let index = markerIndex + 1; index < blocks.length; index += 1) {
    if (stopPattern.test(blocks[index].text)) break
    if (blocks[index].text) values.push(blocks[index].text)
  }
  return clean(values.join(' '))
}

/** 参考译文（英文）：在答案卷后半段找 "参考译文" 附近最长的英文块。 */
function referenceTranslation(blocks) {
  const candidates = []
  for (let index = Math.floor(blocks.length / 3); index < blocks.length; index += 1) {
    if (!/参考译文|参考答案/.test(blocks[index].text)) continue
    for (let cursor = index; cursor < Math.min(index + 14, blocks.length); cursor += 1) {
      const text = blocks[cursor].text.replace(/^.*?参考译文[：:·\s]*/, '')
      if ((text.match(/[A-Za-z]+/g) || []).length >= 20) candidates.push({ marker: index, text })
    }
  }
  candidates.sort((a, b) => b.text.length - a.text.length)
  return { text: candidates[0]?.text || '', marker: candidates[0]?.marker ?? -1 }
}

// ─────────────────────────── 主转换流程 ───────────────────────────

function convertPaper({ key, sources, category, strict }) {
  const issues = []
  const note = (level, message) => issues.push({ level, message })

  // ── 1. 准备文本 ──
  const pdfText = sources.questionPdf ? normalizePdfText(loadPdfText(sources.questionPdf)) : ''
  const mineruV2 = loadMineruBlocks(sources.questionV2)
  const mineruV1 = loadMineruBlocks(sources.questionV1)
  const mineruText = normalizeMineruText(mineruV2.length ? mineruV2 : mineruV1)

  const primary = pdfText || mineruText
  if (!primary) throw new Error('题目卷没有任何可用来源（PDF 无文本层且缺 MinerU JSON）')
  const primarySource = pdfText ? 'PDF 文本层' : 'MinerU'
  if (!pdfText && sources.questionPdf) note('info', '题目卷 PDF 无文本层，已回退到 MinerU')

  // 备用来源，按优先级排列，用于逐题修复
  const fallbackTexts = [
    pdfText ? { name: 'MinerU v2', text: normalizeMineruText(mineruV2) } : null,
    pdfText ? { name: 'MinerU v1', text: normalizeMineruText(mineruV1) } : null,
    !pdfText && mineruV2.length && mineruV1.length ? { name: 'MinerU v1', text: normalizeMineruText(mineruV1) } : null
  ].filter(item => item && item.text)

  const answerBlocks = loadMineruBlocks(sources.answerV2).length
    ? loadMineruBlocks(sources.answerV2)
    : loadMineruBlocks(sources.answerV1)
  // CET-4 answer tables are sometimes appended to the question-sheet JSON
  // rather than emitted in the separate answer-sheet JSON.  Keep the answer
  // sheet first so it remains the preferred source, then use both MinerU
  // versions of the question sheet as a fallback.
  const answerTableBlocks = [...answerBlocks, ...mineruV2, ...mineruV1]
  const answerPdfText = sources.answerPdf ? normalizePdfText(loadPdfText(sources.answerPdf)) : ''
  const answerText = [answerPdfText, answerBlocks.map(block => block.text).join('\n')].filter(Boolean).join('\n')
  if (!answerText) note('warn', '缺少答案卷，解析与范文将使用占位文本')

  // ── 2. 试卷元信息 ──
  const titleLine = primary.split(/\r?\n/).slice(0, 12).find(line => /大学英语[四六]级考试|College English Test/i.test(line)) || ''
  const id = parsePaperId(key)
  const fromTitle = parsePaperId(titleLine)
  if (!id.year && fromTitle.year) Object.assign(id, fromTitle)
  if (!id.examType && fromTitle.examType) id.examType = fromTitle.examType
  if (category) id.examType = category
  if (!id.examType) throw new Error('无法判断考试类型（cet4 / cet6），请用 --category 指定')

  // ── 3. 分节 ──
  const bounds = sectionBounds(primary)
  const writingText = between(primary, bounds.writing, bounds.listening)
  const listeningText = between(primary, bounds.listening, bounds.reading)
  const readingText = between(primary, bounds.reading, bounds.translation)
  const translationText = between(primary, bounds.translation, bounds.end)
  const keysText = bounds.keys >= 0 ? primary.slice(bounds.keys) : ''

  // ── 4. 答案表（KEYS 优先，其次答案卷表格，最后行内） ──
  const answers = new Map()
  const answerSourceOf = new Map()
  const merge = (map, label) => { for (const [number, letter] of map) if (!answers.has(number)) { answers.set(number, letter); answerSourceOf.set(number, label) } }
  merge(answersFromKeys(keysText), '题目卷 KEYS')
  merge(answersFromTables(answerTableBlocks), '答案表格（答案卷/题目卷）')
  merge(answersFromInline(answerText), '答案卷行内')

  // ── 5. 阅读 Section 边界（Part III 内部） ──
  const readingLines = readingText
  const sectionBAt = readingLines.search(/^\s*Section\s+B\s*$/im)
  const sectionCAt = readingLines.search(/^\s*Section\s+C\s*$/im)
  const clozeText = readingLines.slice(0, sectionBAt > 0 ? sectionBAt : readingLines.length)
  const matchingText = between(readingLines, sectionBAt, sectionCAt)
  const passageText = sectionCAt >= 0 ? readingLines.slice(sectionCAt) : ''

  const questions = []

  // ── 6. 写作 ──
  const writingDirections = clean(writingText.replace(/^[\s\S]*?(Directions:)/i, '$1'))
  const opening = writingDirections.match(/begins?\s+with\s+(?:the\s+)?sentence\s*[“"]([\s\S]+?)[”"]/i)?.[1]
  const writingAnswer = textAfterMarker(answerBlocks, /参考范文/, /范文译文|精彩点评|亮点词汇|写作句型|Part\s*II|Listening Comprehension|答案速查/i)
  const writingAnalysis = (() => {
    const at = answerBlocks.findIndex(block => /审题|写作思路|精彩点评|范文点评/.test(block.text))
    return at >= 0 ? clean(answerBlocks.slice(at, at + 5).map(block => block.text).join(' ')) : ''
  })()
  if (!writingDirections) note('warn', '未识别到写作题目要求')
  if (!writingAnswer) note('warn', '未识别到写作参考范文')
  questions.push({
    type: 'writing', number: 0, section: '写作',
    stem: opening ? `作文题目：请以“${opening}”开头写一篇文章。完整要求：${writingDirections}` : (writingDirections || '请根据题目要求完成写作。'),
    referenceAnswer: writingAnswer || '⚠ 原始资料未提供参考范文，请人工补充。',
    analysis: writingAnalysis || '请围绕题目要求完成写作，注意结构、论证与语言准确性。'
  })

  // ── 7. 听力 1-25 ──
  const contextualStems = contextualListeningStems(answerBlocks)
  const stems = listeningStems(answerBlocks)
  for (const number of contextualStems.keys()) note('info', `第 ${number} 题听力题干由听力原文上下文恢复`)
  const listeningBlocks = splitByNumber(listeningText, 1, 25)
  const listening = []
  for (const block of listeningBlocks) {
    const parsed = parseOptionBlock(block.text)
    listening.push({
      type: 'single_choice', number: block.number, section: '听力理解',
      stem: stems.get(block.number) || parsed.stem || `听力第 ${block.number} 题`,
      options: parsed.options,
      answer: answers.get(block.number),
      analysis: analysisFor(answerText, block.number, answers.get(block.number))
    })
  }
  const hasListening = listening.length > 0
  if (!hasListening) note('info', '本套资料未包含听力题（第三套通常与第一套共用听力）')
  else if (listening.length < 25) note('warn', `听力仅识别到 ${listening.length}/25 题`)

  // ── 8. 选词填空 26-35 ──
  const wordBank = []
  const bankFromText = [...clozeText.matchAll(/(?:^|\n)\s*([A-O])\s*\)\s*([A-Za-z][A-Za-z-]*)\s*$/gm)]
  for (const match of bankFromText) {
    const at = indexOfLetter(match[1])
    if (at >= 0 && at < 15 && !wordBank[at]) wordBank[at] = match[2]
  }
  if (wordBank.filter(Boolean).length < 15) {                       // 回退：MinerU 的词库表格
    const bankBlock = (mineruV2.length ? mineruV2 : mineruV1)
      .find(block => block.type === 'table' && tableRows(block.html).flat().filter(cell => /^[A-O]\s*\)/i.test(cell)).length >= 12)
    if (bankBlock) {
      for (const [, letter, word] of tableRows(bankBlock.html).flat().flatMap(cell => [...cell.matchAll(/\b([A-O])\s*\)\s*([A-Za-z][A-Za-z-]*)/g)])) {
        const at = indexOfLetter(letter)
        if (at >= 0 && at < 15 && !wordBank[at]) wordBank[at] = word
      }
    }
  }

  let clozePassage = clozeText
    .replace(/^[\s\S]*?Directions:[^\n]*(?:\n(?![\s\S]*?Directions:)[^\n]*)*?\n/i, '')   // 去掉 Directions 段
    // OCR 可能把 Directions: 识成 Directions;，用词库说明的固定结尾再兜底截断。
    .replace(/^[\s\S]*?You may not use any of the words in the bank more than once\.?\s*/i, '')
    .replace(/(?:^|\n)\s*[A-O]\s*\)\s*[A-Za-z][A-Za-z-]*\s*(?=\n|$)/g, '\n')             // 去掉词库行
  clozePassage = clean(clozePassage.replace(/^\s*Section\s+A\s*/i, ''))
  for (let number = 26; number <= 35; number += 1) {
    // Do not treat the "30" prefix in a number such as "300 students" as
    // question 30.  OCR markers are surrounded by underscores, but the
    // digit-boundary guard also handles clean PDF text safely.
    clozePassage = clozePassage.replace(new RegExp(`_{0,6}\\s*(?<!\\d)${number}(?!\\d)\\s*_{0,6}`, 'g'), ' ____ ')
  }
  const clozeBlanks = (clozePassage.match(/_{2,}/g) || []).length
  const clozeAnswers = Array.from({ length: 10 }, (_, offset) => answers.get(26 + offset))
  if (wordBank.filter(Boolean).length !== 15) note('error', `选词填空词库不完整：${wordBank.filter(Boolean).length}/15`)
  if (clozeBlanks !== 10) note('error', `选词填空空格数异常：${clozeBlanks}/10（导入端要求与答案数一致）`)
  if (clozeAnswers.some(value => !value)) note('error', `选词填空缺答案：${clozeAnswers.map((v, i) => v ? null : 26 + i).filter(Boolean).join(',')}`)
  questions.push({
    type: 'cloze', number: 26, section: '选词填空（26-35）',
    stem: clozePassage,
    options: wordBank.map((word, at) => word || `⚠ 词库 ${letterOf(at)} 缺失`),
    answers: clozeAnswers.map((letter, offset) => letter || (note('error', `第 ${26 + offset} 题缺答案`), 'A')),
    analysis: Array.from({ length: 10 }, (_, offset) => {
      const number = 26 + offset
      return `${number}. ${analysisFor(answerText, number, answers.get(number)) || `正确答案：${answers.get(number) || '未知'}`}`
    }).join('\n')
  })

  // ── 9. 段落匹配 36-45 ──
  const statementsAt = matchingText.search(/(?:^|\n)\s*36\s*[.、]/)
  const paragraphPart = statementsAt > 0 ? matchingText.slice(0, statementsAt) : matchingText
  // 段落标记后不一定有空格（PDF 里常见 "A)According to..."），只要求后面跟着正文字符
  const paragraphMarks = [...paragraphPart.matchAll(/(?:^|\n)\s*([A-O])\s*\)\s*(?=[A-Za-z“"'(])/g)]
  const paragraphs = paragraphMarks.map((mark, position) => clean(
    paragraphPart.slice(mark.index + mark[0].length, paragraphMarks[position + 1]?.index ?? paragraphPart.length)
  ))
  const matchingItems = splitByNumber(matchingText.slice(statementsAt > 0 ? statementsAt : 0), 36, 45)
    .map(item => ({ id: String(item.number), prompt: clean(item.text.split(/\n\s*(?:Section|Part)\s/i)[0]), answer: answers.get(item.number) }))
  const matchingTitle = clean(paragraphPart.split(/\r?\n/).find(line => line.trim() && !/Directions:|Section\s+B/i.test(line) && !/^\s*[A-O]\s*\)/.test(line)) || '段落匹配')
  if (paragraphs.length < 10) note('error', `段落匹配段落数异常：${paragraphs.length}`)
  if (matchingItems.length !== 10) note('error', `段落匹配陈述数异常：${matchingItems.length}/10`)
  for (const item of matchingItems) {
    if (!item.answer) { note('error', `第 ${item.id} 题缺答案`); item.answer = 'A' }
    if (indexOfLetter(item.answer) >= paragraphs.length) note('error', `第 ${item.id} 题答案 ${item.answer} 超出段落范围（共 ${paragraphs.length} 段）`)
  }
  questions.push({
    type: 'matching', number: 36, section: '段落匹配（36-45）',
    passage: matchingTitle, stem: '将每条陈述与其信息来源的段落匹配。',
    options: paragraphs, items: matchingItems,
    analysis: matchingItems.map(item => `${item.id}. ${analysisFor(answerText, +item.id, item.answer) || `正确答案：${item.answer}`}`).join('\n')
  })

  // ── 10. 仔细阅读 46-55 ──
  const passageOneAt = passageText.search(/Questions\s+46\s+to\s+50/i)
  const passageTwoAt = passageText.search(/Questions\s+51\s+to\s+55/i)
  const q46At = passageText.search(/(?:^|\n)\s*46\s*[.、]/)
  const q51At = passageText.search(/(?:^|\n)\s*51\s*[.、]/)
  const passageOne = clean(passageText.slice(passageOneAt, q46At > passageOneAt ? q46At : undefined).replace(/^Questions\s+46\s+to\s+50[^.]*\./i, ''))
  const passageTwo = clean(passageText.slice(passageTwoAt, q51At > passageTwoAt ? q51At : undefined).replace(/^Questions\s+51\s+to\s+55[^.]*\./i, ''))
  const reading = splitByNumber(passageText, 46, 55).map(block => {
    const parsed = parseOptionBlock(block.text)
    return {
      type: 'single_choice', number: block.number,
      section: block.number <= 50 ? '仔细阅读 · Passage One' : '仔细阅读 · Passage Two',
      passage: block.number <= 50 ? passageOne : passageTwo,
      stem: parsed.stem, options: parsed.options,
      answer: answers.get(block.number),
      analysis: analysisFor(answerText, block.number, answers.get(block.number))
    }
  })
  if (reading.length !== 10) note('error', `仔细阅读题数异常：${reading.length}/10`)

  // ── 11. 逐题修复选择题（从备用来源 / 答案卷回填缺失选项与题干） ──
  const repairs = []
  for (const question of [...listening, ...reading]) {
    const missing = 'ABCD'.split('').filter(letter => !question.options[letter])
    if (missing.length) {
      for (const fallback of fallbackTexts) {
        const block = splitByNumber(fallback.text, question.number, question.number)[0]
        if (!block) continue
        const parsed = parseOptionBlock(block.text)
        let filled = 0
        for (const letter of missing) if (parsed.options[letter]) { question.options[letter] = parsed.options[letter]; filled += 1 }
        if (filled) repairs.push(`第 ${question.number} 题：从 ${fallback.name} 补回 ${filled} 个选项`)
        if ('ABCD'.split('').every(letter => question.options[letter])) break
      }
    }
    const stillMissing = 'ABCD'.split('').filter(letter => !question.options[letter])
    if (stillMissing.length) {
      const recovered = optionsFromAnswerPaper(answerText, question.number)
      let filled = 0
      for (const letter of stillMissing) if (recovered[letter]) { question.options[letter] = `⚠[答案卷回填] ${recovered[letter]}`; filled += 1 }
      if (filled) repairs.push(`第 ${question.number} 题：从答案卷补回 ${filled} 个选项（可能为中文译文，请复核）`)
    }
    for (const letter of 'ABCD') {
      if (!question.options[letter]) {
        question.options[letter] = `⚠ 原卷该选项缺失，请人工补充`
        note('error', `第 ${question.number} 题选项 ${letter} 无法从任何来源恢复`)
      }
    }
    if (!question.stem) {
      question.stem = `第 ${question.number} 题`
      note('warn', `第 ${question.number} 题题干缺失`)
    }
    if (!question.answer) { note('error', `第 ${question.number} 题缺答案`); question.answer = 'A' }
    if (!question.analysis) question.analysis = `正确答案：${question.answer}`
  }

  // ── 12. 翻译 56 ──
  const translationSource = clean(
    translationText.split(/\r?\n/).filter(line => /[\u4e00-\u9fff]/.test(line) && !/Directions:|Answer Sheet|注意|Part\s/i.test(line)).join(' ')
  )
  const reference = referenceTranslation(answerBlocks)
  const translationAnalysis = reference.marker >= 0
    ? clean(answerBlocks.slice(reference.marker + 1).filter(block => !/答案速查/.test(block.text)).map(block => block.text).join(' ')).slice(0, 8000)
    : ''
  if (!translationSource) note('error', '未识别到翻译原文')
  if (!reference.text) note('warn', '未识别到参考译文')
  questions.push({
    type: 'translation', number: 56, section: '翻译',
    stem: '请将下面的中文段落翻译成英文。',
    passage: translationSource || '⚠ 原卷翻译原文缺失，请人工补充。',
    referenceAnswer: reference.text || '⚠ 原始资料未提供参考译文，请人工补充。',
    analysis: translationAnalysis || '请注意关键词、句式衔接与专有表达。'
  })

  // ── 13. 组装 ──
  const ordered = [
    questions[0],                                  // 写作
    ...listening,
    questions[1],                                  // 选词填空
    questions[2],                                  // 段落匹配
    ...reading,
    questions[3]                                   // 翻译
  ]
  for (const question of ordered) {
    if (question.options && !Array.isArray(question.options)) {
      question.options = ['A', 'B', 'C', 'D'].map(letter => question.options[letter])
    }
  }

  const paper = {
    title: paperTitle(id),
    category: id.examType,
    date: `${id.year}-${String(id.month).padStart(2, '0')}`,
    duration: examDuration(id, hasListening),
    scope: hasListening ? '整套试卷' : '整套试卷（本套资料未提供听力题）',
    questions: ordered
  }

  const errors = issues.filter(issue => issue.level === 'error')
  if (strict && errors.length) throw new Error(`存在 ${errors.length} 处缺口：\n  - ${errors.map(issue => issue.message).join('\n  - ')}`)

  return { paper, issues, repairs, primarySource, answerSourceOf }
}

/** 从答案卷正文里抓某题的 A-D 选项（可能是中文译文）。 */
function optionsFromAnswerPaper(answerText, number) {
  const marks = [...answerText.matchAll(new RegExp(`(?:^|\\n|\\s)${number}\\s*[.、]`, 'g'))]
  const result = {}
  for (const mark of marks) {
    const tail = answerText.slice(mark.index, mark.index + 2000)
    const found = {}
    for (const match of tail.matchAll(/(?:^|\n|\s)([A-D])\s*[)．.]\s*([^\n]{2,300}?)(?=(?:\s|^|\n)[A-D]\s*[)．.]|\n|$)/g)) {
      if (!found[match[1]]) found[match[1]] = clean(match[2])
    }
    if (Object.keys(found).length >= Object.keys(result).length) Object.assign(result, found)
    if (Object.keys(result).length === 4) break
  }
  return result
}

// ─────────────────────────── 校验（对齐前端 pastPaperImporter.js） ───────────────────────────

function validateAgainstImporter(paper) {
  const problems = []
  paper.questions.forEach((question, position) => {
    const label = `第 ${position + 1} 题(${question.section || question.type})`
    if (!clean(question.stem)) problems.push(`${label} 缺题干`)
    if (!clean(question.analysis)) problems.push(`${label} 缺解析`)
    if (question.type === 'single_choice') {
      if (question.options?.length !== 4 || question.options.some(option => !clean(option))) problems.push(`${label} 选项不是完整 4 个`)
      const at = indexOfLetter(question.answer)
      if (at < 0 || at > 3) problems.push(`${label} 答案无效（${question.answer}）`)
    } else if (question.type === 'cloze') {
      const blanks = (clean(question.stem).match(/_{2,}/g) || []).length
      if (blanks !== question.answers?.length) problems.push(`${label} 空格数 ${blanks} ≠ 答案数 ${question.answers?.length}`)
      if (question.answers?.some(letter => indexOfLetter(letter) < 0 || indexOfLetter(letter) >= question.options.length)) problems.push(`${label} 存在越界答案`)
    } else if (question.type === 'matching') {
      if (!question.options?.length || !question.items?.length) problems.push(`${label} 匹配内容不完整`)
      if (question.items?.some(item => !clean(item.prompt) || indexOfLetter(item.answer) < 0 || indexOfLetter(item.answer) >= question.options.length)) problems.push(`${label} 存在无效匹配项`)
    } else if (!clean(question.referenceAnswer)) {
      problems.push(`${label} 缺参考答案`)
    }
  })
  return problems
}

// ─────────────────────────── 入口 ───────────────────────────

function main() {
  const options = parseArgv(process.argv.slice(2))
  const pdfRoot = options['no-pdf'] ? '' : (options['pdf-root'] || 'E:/英语真题/真题集/真题')
  const mineruRoot = options.mineru || (options.positional.length ? path.dirname(options.positional[0]) : 'E:/英语真题/MinerU输出')

  const pdfIndex = indexPdfRoot(pdfRoot)
  const mineruIndex = indexMineruRoot(mineruRoot)

  // 位置参数模式：显式指定题目源 / 答案源
  let targets
  if (options.positional.length >= 2) {
    const key = paperKey(parsePaperId(path.basename(options.positional[0])))
    targets = [[key || 'manual', {
      ...(pdfIndex.get(key) || {}),
      questionV2: options.positional[0],
      answerV2: options.positional[1]
    }]]
  } else {
    const keys = options.paper ? [options.paper] : [...new Set([...mineruIndex.keys(), ...(options.mineru ? [] : [])])]
    targets = keys.map(key => [key, { ...(pdfIndex.get(key) || {}), ...(mineruIndex.get(key) || {}) }])
  }
  if (!targets.length) { console.error('未找到可转换的试卷，请检查 --mineru / --paper 参数'); process.exit(1) }

  const outputDir = options.out || (options.positional[2] ? path.dirname(options.positional[2]) : path.join(mineruRoot, 'converted'))
  fs.mkdirSync(outputDir, { recursive: true })

  const report = []
  let ok = 0
  for (const [key, sources] of targets.sort((a, b) => a[0].localeCompare(b[0]))) {
    try {
      const result = convertPaper({ key, sources, category: options.category, strict: options.strict })
      const problems = validateAgainstImporter(result.paper)
      const target = options.positional[2] && targets.length === 1
        ? options.positional[2]
        : path.join(outputDir, `${key}_导入.json`)
      fs.writeFileSync(target, `${JSON.stringify(result.paper, null, 2)}\n`, 'utf8')
      ok += 1
      const errors = result.issues.filter(issue => issue.level === 'error')
      console.log(`✓ ${key} → ${path.basename(target)}  ${result.paper.questions.length} 题  来源:${result.primarySource}` +
        `${errors.length ? `  ⚠ ${errors.length} 处缺口` : ''}${problems.length ? `  ✗ ${problems.length} 处校验不通过` : ''}`)
      report.push({ key, target, result, problems })
    } catch (error) {
      console.error(`✗ ${key} 转换失败：${error.message}`)
      report.push({ key, error: error.message })
    }
  }

  const reportPath = options.report || path.join(outputDir, '_转换报告.md')
  fs.writeFileSync(reportPath, buildReport(report), 'utf8')
  console.log(`\n完成 ${ok}/${targets.length} 套，报告：${reportPath}`)
  if (options.strict && ok !== targets.length) process.exit(1)
}

function buildReport(report) {
  const lines = ['# 真题转换质量报告', '']
  lines.push('| 套次 | 题数 | 主来源 | 缺口 | 导入校验 |', '|---|---|---|---|---|')
  for (const row of report) {
    if (row.error) { lines.push(`| ${row.key} | — | — | 转换失败 | ${row.error} |`); continue }
    const errors = row.result.issues.filter(issue => issue.level === 'error').length
    lines.push(`| ${row.key} | ${row.result.paper.questions.length} | ${row.result.primarySource} | ${errors || '无'} | ${row.problems.length ? `${row.problems.length} 项不通过` : '通过'} |`)
  }
  for (const row of report) {
    if (row.error) continue
    const { issues, repairs } = row.result
    if (!issues.length && !repairs.length && !row.problems.length) continue
    lines.push('', `## ${row.key}`, '')
    if (repairs.length) { lines.push('**自动回填：**'); repairs.forEach(item => lines.push(`- ${item}`)); lines.push('') }
    for (const level of ['error', 'warn', 'info']) {
      const picked = issues.filter(issue => issue.level === level)
      if (!picked.length) continue
      lines.push(`**${{ error: '缺口', warn: '警告', info: '提示' }[level]}：**`)
      picked.forEach(issue => lines.push(`- ${issue.message}`))
      lines.push('')
    }
    if (row.problems.length) { lines.push('**导入端校验不通过：**'); row.problems.forEach(item => lines.push(`- ${item}`)); lines.push('') }
  }
  return `${lines.join('\n')}\n`
}

main()
