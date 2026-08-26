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

  it('removes OCR instructions appended to listening options', () => {
    const paper = parsePaperJson(JSON.stringify({
      title: '听力边界清洗',
      audioUrl: '/audio/test.mp3',
      questions: [
        {
          type: 'listening',
          stem: 'Question 3?',
          options: ['A3', 'B3', 'C3', 'D3. ## Questions 3 and 4 are based on the news report you have just heard.'],
          answer: 'D',
          analysis: 'Reason 3.'
        },
        {
          type: 'listening',
          stem: 'Question 5?',
          options: ['A5', 'B5', 'C5', 'D5. 公众号：真题PDF电子版'],
          answer: 'D',
          analysis: 'Reason 5.'
        },
        {
          type: 'listening',
          stem: 'Question 26?',
          options: ['A26', 'B26', 'C26', 'D26. Section B Directions: In this section, you will hear two conversations.'],
          answer: 'D',
          analysis: 'Reason 26.'
        }
      ]
    }), { category: 'cet4' })

    expect(paper.questions.map(question => question.options[3])).toEqual(['D3', 'D5', 'D26'])
  })

  it('allows listening JSON to be parsed before its audio is uploaded', () => {
    const paper = parsePaperJson(JSON.stringify({
      title: '待绑定音频的听力试卷',
      questions: [{
        type: 'listening',
        section: '听力理解',
        stem: 'Question?',
        options: ['A1', 'B1', 'C1', 'D1'],
        answer: 'A',
        analysis: 'Reason.'
      }]
    }), { category: 'cet4' })

    expect(paper.audioUrl).toBe('')
    expect(paper.questions[0].type).toBe('listening')
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

  it('supports extended word-bank and paragraph answer letters', () => {
    const paper = parsePaperJson(JSON.stringify({
      title: 'Extended answer letters',
      questions: [
        { type: 'cloze', stem: 'One ____.', options: Array.from({ length: 15 }, (_, index) => `word-${index}`), answers: ['N'], analysis: 'Cloze.' },
        { type: 'matching', stem: 'Match.', options: Array.from({ length: 12 }, (_, index) => `paragraph-${index}`), items: [{ id: '36', prompt: 'Statement', answer: 'L' }], analysis: 'Match.' }
      ]
    }), { category: 'cet4' })
    expect(paper.questions[0].answers).toEqual([13])
    expect(paper.questions[1].items[0].answer).toBe(11)
  })

  it('does not treat ordinary "part of" prose as a section heading', () => {
    const paper = parsePaperJson(JSON.stringify({
      title: 'Cloze section-heading regression',
      questions: [{
        type: 'cloze',
        stem: 'The part of the brain uses ____ signals and ____ patterns.',
        options: ['first', 'second'],
        answers: ['A', 'B'],
        analysis: 'Cloze analysis.'
      }]
    }), { category: 'cet4' })

    expect(paper.questions[0].stem).toContain('part of the brain')
    expect(paper.questions[0].answers).toEqual([0, 1])
  })
})
