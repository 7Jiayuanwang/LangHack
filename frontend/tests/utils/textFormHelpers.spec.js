/**
 * Property-based tests for text form helpers
 * Validates: Requirements 5.2, 5.3, 5.4, 6.1
 */
import { describe, it, expect } from 'vitest'
import * as fc from 'fast-check'
import {
  buildTextPayload,
  isMarkdownContentEmpty,
  UPLOAD_FORM_DEFAULTS
} from '@/utils/textFormHelpers'

// Arbitrary: a non-empty, non-whitespace string
const nonBlankStr = fc.string({ minLength: 1 }).filter((s) => s.trim().length > 0)

// Arbitrary: whitespace-only string (empty or spaces/tabs/newlines)
const blankStr = fc.oneof(
  fc.constant(''),
  fc.array(fc.constantFrom(' ', '\t', '\n', '\r'), { minLength: 1, maxLength: 20 })
    .map((chars) => chars.join(''))
)

// Arbitrary: a form-like object
const formArb = (contentType) =>
  fc.record({
    title: fc.string(),
    content: nonBlankStr,
    contentType: fc.constant(contentType),
    difficulty: fc.constantFrom('EASY', 'MEDIUM', 'HARD'),
    tags: fc.string(),
    coverUrl: fc.string(),
    audioUrl: fc.string(),
    isPublic: fc.boolean()
  })

describe('buildTextPayload — Property 4: 提交时内容字段与格式一致', () => {
  /**
   * Property 4a: TEXT 模式 → content 非空，markdownContent 为空字符串
   * Validates: Requirements 5.2
   */
  it('Property 4a: TEXT mode sets content and clears markdownContent', () => {
    fc.assert(
      fc.property(
        formArb('TEXT'),
        nonBlankStr,
        (form, md) => {
          const payload = buildTextPayload(form, md)
          expect(payload.content).toBe(form.content)
          expect(payload.markdownContent).toBe('')
          expect(payload.contentType).toBe('TEXT')
        }
      ),
      { numRuns: 100 }
    )
  })

  /**
   * Property 4b: MARKDOWN 模式 → markdownContent 非空，content 为空字符串
   * Validates: Requirements 5.3
   */
  it('Property 4b: MARKDOWN mode sets markdownContent and clears content', () => {
    fc.assert(
      fc.property(
        formArb('MARKDOWN'),
        nonBlankStr,
        (form, md) => {
          const payload = buildTextPayload(form, md)
          expect(payload.markdownContent).toBe(md)
          expect(payload.content).toBe('')
          expect(payload.contentType).toBe('MARKDOWN')
        }
      ),
      { numRuns: 100 }
    )
  })
})

describe('isMarkdownContentEmpty — Property 5: MARKDOWN 模式空内容阻止提交', () => {
  /**
   * Property 5a: MARKDOWN + blank content → returns true (should block)
   * Validates: Requirements 5.4
   */
  it('Property 5a: returns true for MARKDOWN with blank content', () => {
    fc.assert(
      fc.property(blankStr, (md) => {
        expect(isMarkdownContentEmpty('MARKDOWN', md)).toBe(true)
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 5b: MARKDOWN + non-blank content → returns false (should not block)
   * Validates: Requirements 5.4
   */
  it('Property 5b: returns false for MARKDOWN with non-blank content', () => {
    fc.assert(
      fc.property(nonBlankStr, (md) => {
        expect(isMarkdownContentEmpty('MARKDOWN', md)).toBe(false)
      }),
      { numRuns: 100 }
    )
  })

  /**
   * Property 5c: TEXT mode → never blocks regardless of content
   * Validates: Requirements 5.4
   */
  it('Property 5c: TEXT mode never blocks submission', () => {
    fc.assert(
      fc.property(fc.string(), (md) => {
        expect(isMarkdownContentEmpty('TEXT', md)).toBe(false)
      }),
      { numRuns: 100 }
    )
  })
})

describe('UPLOAD_FORM_DEFAULTS — Property 6: 表单重置后所有新增字段恢复默认值', () => {
  /**
   * Property 6: After reset, all new fields have correct defaults
   * Validates: Requirements 6.1
   */
  it('Property 6: defaults have correct values for all new fields', () => {
    expect(UPLOAD_FORM_DEFAULTS.coverUrl).toBe('')
    expect(UPLOAD_FORM_DEFAULTS.contentType).toBe('TEXT')
    expect(UPLOAD_FORM_DEFAULTS.audioUrl).toBe('')
    expect(UPLOAD_FORM_DEFAULTS.isPublic).toBe(true)
  })

  it('Property 6 (PBT): any arbitrary form state reset to defaults satisfies invariants', () => {
    // Simulate: take any form state, apply defaults, verify
    const arbitraryFormState = fc.record({
      title: fc.string(),
      content: fc.string(),
      contentType: fc.constantFrom('TEXT', 'MARKDOWN'),
      difficulty: fc.constantFrom('EASY', 'MEDIUM', 'HARD'),
      tags: fc.string(),
      coverUrl: fc.string(),
      audioUrl: fc.string(),
      isPublic: fc.boolean()
    })

    fc.assert(
      fc.property(arbitraryFormState, (state) => {
        // Simulate resetUploadForm: Object.assign(form, UPLOAD_FORM_DEFAULTS)
        const form = { ...state }
        Object.assign(form, UPLOAD_FORM_DEFAULTS)

        expect(form.coverUrl).toBe('')
        expect(form.contentType).toBe('TEXT')
        expect(form.audioUrl).toBe('')
        expect(form.isPublic).toBe(true)
      }),
      { numRuns: 100 }
    )
  })
})

import { populateEditForm } from '@/utils/textFormHelpers'

describe('populateEditForm — Property 7: 编辑表单回填包含所有新增字段', () => {
  /**
   * Property 7: For any article object, populateEditForm populates all new fields correctly.
   * Validates: Requirements 6.2
   */
  it('Property 7: edit form is populated with all new fields from article', () => {
    const articleArb = fc.record({
      id: fc.integer({ min: 1 }),
      title: fc.string(),
      content: fc.string(),
      contentType: fc.constantFrom('TEXT', 'MARKDOWN'),
      difficulty: fc.constantFrom('EASY', 'MEDIUM', 'HARD'),
      tags: fc.string(),
      coverUrl: fc.string(),
      audioUrl: fc.string(),
      isPublic: fc.boolean(),
      markdownContent: fc.string()
    })

    fc.assert(
      fc.property(articleArb, (article) => {
        const { formFields, markdownContent } = populateEditForm(article)

        expect(formFields.id).toBe(article.id)
        expect(formFields.title).toBe(article.title)
        expect(formFields.contentType).toBe(article.contentType)
        expect(formFields.coverUrl).toBe(article.coverUrl)
        expect(formFields.audioUrl).toBe(article.audioUrl)
        expect(formFields.isPublic).toBe(article.isPublic)
        expect(markdownContent).toBe(article.markdownContent)
      }),
      { numRuns: 100 }
    )
  })

  it('Property 7b: missing optional fields fall back to defaults', () => {
    // Article with no coverUrl, audioUrl, isPublic, markdownContent, tags, content
    const minimalArticle = {
      id: 1,
      title: 'Test',
      difficulty: 'EASY'
    }
    const { formFields, markdownContent } = populateEditForm(minimalArticle)

    expect(formFields.coverUrl).toBe('')
    expect(formFields.audioUrl).toBe('')
    expect(formFields.isPublic).toBe(true)
    expect(formFields.contentType).toBe('TEXT')
    expect(formFields.tags).toBe('')
    expect(formFields.content).toBe('')
    expect(markdownContent).toBe('')
  })
})
