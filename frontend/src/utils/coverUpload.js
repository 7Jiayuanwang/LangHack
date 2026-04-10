/**
 * Pure helper extracted from UserTexts.vue / TextEditDialog.vue
 * so it can be unit- and property-tested without mounting a component.
 */

const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/gif', 'image/webp']
const MAX_SIZE_MB = 10

/**
 * Returns true iff the file passes both MIME-type and size checks.
 * Does NOT call ElMessage — callers are responsible for showing errors.
 */
export function isCoverFileValid(file) {
  if (!ALLOWED_TYPES.includes(file.type)) return false
  if (file.size / 1024 / 1024 > MAX_SIZE_MB) return false
  return true
}

export { ALLOWED_TYPES, MAX_SIZE_MB }
