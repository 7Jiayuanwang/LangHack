import fs from 'node:fs'
import path from 'node:path'

const [paper2Path, paper3Path] = process.argv.slice(2)

if (!paper2Path || !paper3Path) {
  throw new Error('Usage: node scripts/import-mineru-cet6.mjs <paper-2.md> <paper-3.md>')
}

const normalize = (value) => value
  .replace(/!\[[^\]]*]\([^)]*\)/g, '')
  .replace(/<[^>]+>/g, ' ')
  .replace(/&quot;/g, '"')
  .replace(/&#x27;/g, "'")
  .replace(/#{1,6}\s*/g, '')
  .replace(/答案详解/g, '')
  .replace(/\s+/g, ' ')
  .trim()

function getQuestionBlocks(section) {
  const matches = [...section.matchAll(/^(4[6-9]|5[0-5])\.\s+/gm)]
  return matches.map((match, index) => ({
    number: Number(match[1]),
    start: match.index,
    text: section.slice(match.index, matches[index + 1]?.index ?? section.length)
  }))
}

function parseOptions(block) {
  const optionMatches = [...block.matchAll(/(?:^|\s)([A-D])\)\s*/gm)]
  if (optionMatches.length !== 4) {
    throw new Error(`Question has ${optionMatches.length} options: ${block.slice(0, 80)}`)
  }

  const stem = normalize(block.slice(block.indexOf('.') + 1, optionMatches[0].index))
  const options = optionMatches.map((match, index) => normalize(
    block.slice(match.index + match[0].length, optionMatches[index + 1]?.index ?? block.length)
  ))
  return { stem, options }
}

function extractPassage(section, questionNumber, firstQuestionStart) {
  const marker = questionNumber < 51 ? /Questions 46 to 50[^\n]*\n/i : /Questions 51 to 55[^\n]*\n/i
  const markerMatch = marker.exec(section)
  if (!markerMatch) throw new Error(`Passage marker missing for question ${questionNumber}`)

  const end = questionNumber < 51
    ? firstQuestionStart
    : section.search(/^51\.\s+/m)
  return normalize(section.slice(markerMatch.index + markerMatch[0].length, end))
}

function parseAnalysis(answerSection, questionNumber) {
  const startPattern = new RegExp(`^${questionNumber}\\.【定位】`, 'm')
  const startMatch = startPattern.exec(answerSection)
  if (!startMatch) throw new Error(`Analysis missing for question ${questionNumber}`)

  const rest = answerSection.slice(startMatch.index)
  const boundaries = [
    new RegExp(`^#{0,2}\\s*${questionNumber + 1}\\.【定位】`, 'm'),
    /^#{1,3}\s*参考译文/m,
    /^#{1,3}\s*Passage (One|Two)/m,
    /^#{1,3}\s*Part (IV|V|Ⅳ|Ⅴ)/m
  ]
    .map(pattern => pattern.exec(rest)?.index)
    .filter(index => Number.isInteger(index) && index > 0)
  const block = rest.slice(0, boundaries.length ? Math.min(...boundaries) : rest.length)
  const answerMatch = /(?:^|\n)\s*([A-D])[)）]【精析】/.exec(block)
  if (!answerMatch) throw new Error(`Answer missing for question ${questionNumber}`)

  return {
    answer: answerMatch[1].charCodeAt(0) - 65,
    analysis: normalize(block.slice(answerMatch.index + answerMatch[0].length))
  }
}

function parsePaper(markdownPath, paperId) {
  const markdown = fs.readFileSync(markdownPath, 'utf8')
  const answerTitleIndex = markdown.search(/^#{1,2} .*答案与详解/m)
  if (answerTitleIndex < 0) throw new Error(`Answer section missing in ${markdownPath}`)

  const testPart = markdown.slice(0, answerTitleIndex)
  const answerPart = markdown.slice(answerTitleIndex)
  const testSectionStart = testPart.search(/^##?\s+Section C\s*$/m)
  const translationStart = testPart.search(/^##?\s+Part\s*(IV|Ⅳ)\s*Translation/m)
  const testSectionEnd = translationStart < 0 ? testPart.length : translationStart
  const answerReadingStart = answerPart.search(/^##?\s+Part\s*Ⅲ\s*Reading Comprehension/m)
  const answerReading = answerPart.slice(Math.max(0, answerReadingStart))
  const answerSectionStart = answerReading.search(/^##?\s+Section C\s*$/m)

  if (testSectionStart < 0 || testSectionEnd < 0 || answerSectionStart < 0) {
    throw new Error(`Unable to locate reading sections in ${markdownPath}`)
  }

  const testSection = testPart.slice(testSectionStart, testSectionEnd)
  const answerSection = answerReading.slice(answerSectionStart)
  const blocks = getQuestionBlocks(testSection)
  if (blocks.length !== 10) throw new Error(`Expected 10 questions, found ${blocks.length}`)

  const passages = {
    first: extractPassage(testSection, 46, blocks[0].start),
    second: extractPassage(testSection, 51, blocks[0].start)
  }

  return blocks.map(({ number, text }) => {
    const { stem, options } = parseOptions(text)
    const { answer, analysis } = parseAnalysis(answerSection, number)
    return {
      id: `${paperId}-reading-${number}`,
      number,
      section: number < 51 ? '仔细阅读 · Passage One' : '仔细阅读 · Passage Two',
      passage: number < 51 ? passages.first : passages.second,
      stem,
      options,
      answer,
      analysis
    }
  })
}

const papers = {
  cet6Paper2Questions: parsePaper(paper2Path, 'cet6-2024-12-2'),
  cet6Paper3Questions: parsePaper(paper3Path, 'cet6-2024-12-3')
}

for (const [name, questions] of Object.entries(papers)) {
  if (questions.some(question => question.options.length !== 4 || question.answer < 0 || question.answer > 3)) {
    throw new Error(`${name} failed validation`)
  }
}

const output = `// Generated from verified MinerU Markdown. Re-run scripts/import-mineru-cet6.mjs to update.\n\n${Object.entries(papers)
  .map(([name, questions]) => `export const ${name} = ${JSON.stringify(questions, null, 2)}`)
  .join('\n\n')}\n`

const outputPath = path.resolve('src/data/cet6December2024.js')
fs.writeFileSync(outputPath, output, 'utf8')
console.log(`Generated ${outputPath}: ${papers.cet6Paper2Questions.length + papers.cet6Paper3Questions.length} questions`)
