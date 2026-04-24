/**
 * Pure helpers extracted from UserTexts.vue for testability.
 */

/**
 * Build the API request payload from form state.
 * Requirements 5.2, 5.3
 */
export function buildTextPayload(form, markdownContent) {
  return {
    title: form.title,
    content: form.contentType === 'TEXT' ? form.content : '',
    markdownContent: form.contentType === 'MARKDOWN' ? markdownContent : '',
    contentType: form.contentType,
    difficulty: form.difficulty,
    tags: form.tags,
    coverUrl: form.coverUrl,
    audioUrl: form.audioUrl,
    isPublic: form.isPublic
  }
}

/**
 * Returns true if MARKDOWN mode content is empty/whitespace-only.
 * Requirement 5.4
 */
export function isMarkdownContentEmpty(contentType, markdownContent) {
  return contentType === 'MARKDOWN' && (!markdownContent || !markdownContent.trim())
}

/**
 * Populate an edit form from an article object.
 * Returns { formFields, markdownContent } — mirrors editText() in UserTexts.vue.
 * Requirement 6.2
 */
export function populateEditForm(text) {
  return {
    formFields: {
      id: text.id,
      title: text.title,
      content: text.content || '',
      contentType: text.contentType || 'TEXT',
      difficulty: text.difficulty,
      tags: text.tags || '',
      coverUrl: text.coverUrl || '',
      audioUrl: text.audioUrl || '',
      isPublic: text.isPublic != null ? text.isPublic : true
    },
    markdownContent: text.markdownContent || ''
  }
}

/**
 * Default values for the upload form after reset.
 * Requirement 6.1
 */
export const UPLOAD_FORM_DEFAULTS = {
  title: '',
  content: '',
  contentType: 'TEXT',
  difficulty: 'MEDIUM',
  tags: '',
  coverUrl: '',
  audioUrl: '',
  isPublic: true
}
