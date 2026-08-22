import { describe, expect, it } from 'vitest'
import { parsePaperJson, parsePaperMarkdown } from '@/utils/pastPaperImporter'

const markdown = `# 2023 年 12 月六级真题（第一套）

**日期：** 2023-12
**时长：** 25

## 仔细阅读

### 46
**文章：** A short passage.
**问题：** What is the passage about?

A. Option one
B. Option two
C. Option three
D. Option four

**答案：** B
**解析：** The second option is supported by the passage.
`

describe('past paper importer', () => {
  it('parses the standard Markdown format', () => {
    const paper = parsePaperMarkdown(markdown, { category: 'cet6' })
    expect(paper.title).toContain('六级真题')
    expect(paper.questions).toHaveLength(1)
    expect(paper.questions[0].answer).toBe(1)
    expect(paper.questions[0].passage).toBe('A short passage.')
  })

  it('parses and validates JSON papers', () => {
    const paper = parsePaperJson(JSON.stringify({
      title: '考研英语测试卷',
      questions: [{ stem: 'Question?', options: ['A1', 'B1', 'C1', 'D1'], answer: 'D', analysis: 'Reason.' }]
    }), { category: 'postgraduate' })
    expect(paper.questions[0].answer).toBe(3)
  })

  it('rejects incomplete options', () => {
    expect(() => parsePaperMarkdown(markdown.replace('D. Option four', ''), { category: 'cet6' }))
      .toThrow('完整 A-D')
  })

  it('validates cloze, matching, translation and writing questions', () => {
    const paper = parsePaperJson(JSON.stringify({
      title: '全题型试卷',
      questions: [
        { type: 'cloze', stem: 'One ____ two ____.', options: ['one', 'two', 'three'], answers: ['A', 'C'], analysis: 'Cloze analysis.' },
        { type: 'matching', stem: 'Match them.', options: ['P1', 'P2'], items: [{ id: '1', prompt: 'Statement', answer: 'B' }], analysis: 'Matching analysis.' },
        { type: 'translation', stem: 'Translate it.', referenceAnswer: '译文', analysis: 'Translation analysis.' },
        { type: 'writing', stem: 'Write an essay.', referenceAnswer: 'Sample essay.', analysis: 'Writing rubric.' }
      ]
    }), { category: 'cet6' })
    expect(paper.questions.map(question => question.type)).toEqual(['cloze', 'matching', 'translation', 'writing'])
    expect(paper.questions[0].answers).toEqual([0, 2])
    expect(paper.questions[1].items[0].answer).toBe(1)
  })
})
