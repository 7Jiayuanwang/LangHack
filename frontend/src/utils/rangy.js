// Rangy library wrapper
// Re-exports rangy from node_modules for consistent importing

import 'rangy/lib/rangy-core'

const rangy = (typeof window !== 'undefined' && window.rangy) || (typeof globalThis !== 'undefined' && globalThis.rangy)

if (!rangy) {
  console.error('Rangy not loaded')
}

export default rangy
export const {
  init,
  createClassApplier,
  createHighlighter,
  serializeSelection,
  deserializeSelection,
  saveSelection,
  restoreSelection,
  getSelection,
  createRange
} = rangy || {}
