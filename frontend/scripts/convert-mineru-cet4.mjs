import fs from 'node:fs'
import path from 'node:path'

const [input, output = '2024年12月大学英语四级真题第1套.json'] = process.argv.slice(2)
if (!input) throw new Error('Usage: node scripts/convert-mineru-cet4.mjs <full.md> [output.json]')

const raw = fs.readFileSync(input, 'utf8')
const clean = (value = '') => value
  .replace(/!\[[^\]]*]\([^)]*\)/g, '')
  .replace(/<[^>]+>/g, ' ')
  .replace(/&#x27;/g, "'")
  .replace(/&quot;/g, '"')
  .replace(/\s+/g, ' ')
  .trim()

const answerPart = raw.slice(raw.indexOf('答案速查'))
const answerMap = new Map([...raw.matchAll(/(?:<td[^>]*>|\s)(\d{1,3})\s*[.、：:]\s*([A-O])(?=\s|<)/gi)]
  .map(match => [Number(match[1]), match[2].toUpperCase()]))
function parseChoiceBlock(block, number) {
  // A question block can contain the next passage heading before the next
  // numbered question. Do not let that material become option D.
  block = block.split(/\n\s*##\s+(?:Passage|Section|Part)\b/i)[0]
  const matches = [...block.matchAll(/(?:^|\s)([A-D])[)）.]\s*/g)]
  const byLetter = new Map()
  for (let i = 0; i < matches.length; i += 1) {
    const letter = matches[i][1]
    byLetter.set(letter, clean(block.slice(matches[i].index + matches[i][0].length, matches[i + 1]?.index ?? block.length)))
  }
  const options = ['A', 'B', 'C', 'D'].map(letter => byLetter.get(letter))
  if (options.some(option => !option)) throw new Error(`第 ${number} 题选项不完整`)
  return { stem: clean(block.slice(block.indexOf('.') + 1, matches[0].index)) || '请根据听力录音选择最佳答案。', options }
}

function numberedBlocks(text, from, to) {
  const matches = [...text.matchAll(/(?:^|\n)\s*(\d{1,3})[.、]\s*/g)].filter(match => Number(match[1]) >= from && Number(match[1]) <= to)
  return matches.map((match, index) => ({ number: Number(match[1]), text: text.slice(match.index, matches[index + 1]?.index ?? text.length) }))
}

const partThree = raw.indexOf('Part Ⅲ')
const listeningText = raw.slice(raw.indexOf('## Section A'), partThree)
const listening = numberedBlocks(listeningText, 1, 25).map(({ number, text }) => {
  const parsed = parseChoiceBlock(text, number)
  return { type: 'single_choice', number, section: '听力理解', ...parsed, answer: answerMap.get(number), analysis: '原始资料未提供听力原文及逐题解析，请结合听力音频复习。' }
})

const sectionAStart = raw.indexOf('## Section A', partThree)
const sectionBStart = raw.indexOf('## Section B', sectionAStart)
const clozeText = raw.slice(sectionAStart, sectionBStart)
const wordBank = new Map([...clozeText.matchAll(/<td>([A-O])\)\s*([^<]+)<\/td>/g)].map(match => [match[1], clean(match[2])]))
const clozeOptions = [...wordBank.entries()].sort(([a], [b]) => a.localeCompare(b)).map(([, value]) => value)
let clozePassage = clean(clozeText.slice(clozeText.indexOf('When Toni Morrison'), clozeText.indexOf('<table>')))
for (let number = 26; number <= 35; number += 1) clozePassage = clozePassage.replace(new RegExp(`(?<!\\d)${number}(?!\\d)`), ' ____ ')
const cloze = {
  type: 'cloze', number: 26, section: '选词填空（26-35）', stem: clozePassage,
  options: clozeOptions, answers: Array.from({ length: 10 }, (_, i) => answerMap.get(26 + i)),
  analysis: '答案依次为：26.N、27.I、28.F、29.E、30.H、31.A、32.L、33.B、34.J、35.C。原始资料未提供逐空解析。'
}

const sectionCStart = raw.indexOf('## Section C', sectionBStart)
const matchingText = raw.slice(sectionBStart, sectionCStart)
const paragraphMatches = [...matchingText.matchAll(/(?:^|\n)([A-L])\)\s*/g)]
const paragraphs = paragraphMatches.map((match, index) => clean(matchingText.slice(match.index + match[0].length, paragraphMatches[index + 1]?.index ?? matchingText.search(/\n36\./))))
const matchingItems = numberedBlocks(matchingText.slice(matchingText.indexOf('\n36.')), 36, 45).map(({ number, text }) => ({ id: String(number), prompt: clean(text.replace(/^\s*\d+[.、]\s*/, '')), answer: answerMap.get(number) }))
const matching = { type: 'matching', number: 36, section: '段落匹配（36-45）', passage: 'If we care about plastic waste, why won\'t we stop drinking bottled water?', stem: '将每条陈述与对应段落匹配。', options: paragraphs, items: matchingItems, analysis: '答案依次为：36.E、37.H、38.B、39.L、40.G、41.C、42.K、43.A、44.I、45.F。' }

const translationStart = raw.indexOf('## Part IV Translation', sectionCStart)
const readingText = raw.slice(sectionCStart, translationStart)
const passageOne = clean(readingText.slice(readingText.indexOf('Questions 46 to 50'), readingText.indexOf('\n46.')))
const passageTwo = clean(readingText.slice(readingText.indexOf('Questions 51 to 55'), readingText.indexOf('\n51.')))
const reading = numberedBlocks(readingText, 46, 55).map(({ number, text }) => {
  const parsed = parseChoiceBlock(text, number)
  return { type: 'single_choice', number, section: number < 51 ? '仔细阅读 · Passage One' : '仔细阅读 · Passage Two', passage: number < 51 ? passageOne : passageTwo, ...parsed, answer: answerMap.get(number), analysis: '原始资料未提供逐题解析。' }
})

const writingPromptMatch = raw.match(/Part I Writing \(30 minutes\)[\s\S]*?Directions:\s*([\s\S]*?)<\/td>/i)
const writingAnswer = clean(answerPart.slice(answerPart.indexOf('## Part I Writing') + 17, answerPart.indexOf('## Section C')))
const writing = { type: 'writing', number: 0, section: '写作', stem: clean(writingPromptMatch?.[1]), referenceAnswer: writingAnswer, analysis: '写作要求：120-180 词。可从行业发展、生活便利、就业机会、包装废弃物和碳排放等方面展开。' }

const translationOriginal = clean(raw.slice(raw.indexOf('近年来，中国新能源汽车', translationStart), raw.indexOf('<table>', translationStart)))
const translationAnswer = clean(answerPart.slice(answerPart.indexOf('## Part V Translation') + 21))
const translation = { type: 'translation', number: 56, section: '翻译', stem: '请将下列中文段落翻译成英文。', passage: translationOriginal, referenceAnswer: translationAnswer, analysis: '注意新能源汽车产业、市场份额、政策引导、技术创新和环境保护等表达。' }

if (listening.length !== 25 || clozeOptions.length !== 15 || matchingItems.length !== 10 || paragraphs.length !== 12 || reading.length !== 10) {
  throw new Error(`转换数量异常：听力${listening.length}，词库${clozeOptions.length}，匹配${matchingItems.length}/${paragraphs.length}，阅读${reading.length}`)
}
const questions = [writing, ...listening, cloze, matching, ...reading, translation]
const paper = { title: '2024年12月大学英语四级真题（第1套）', category: 'cet4', date: '2024-12', duration: 125, scope: '整套试卷', questions }
fs.writeFileSync(output, JSON.stringify(paper, null, 2), 'utf8')
console.log(`Generated ${path.resolve(output)}: ${questions.length} interactive questions, covering 57 numbered tasks including writing and translation.`)
