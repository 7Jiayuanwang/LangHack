/**
 * Extract an image file from a browser paste event.
 * Returns null for normal text-only clipboard content.
 */
export function getClipboardImageFile(event) {
  const items = event?.clipboardData?.items
  if (!items) return null

  for (const item of items) {
    if (
      item?.kind !== 'file' ||
      typeof item.type !== 'string' ||
      !item.type.startsWith('image/') ||
      typeof item.getAsFile !== 'function'
    ) continue

    const blob = item.getAsFile()
    if (!blob || typeof blob.type !== 'string' || !blob.type.startsWith('image/')) continue

    const extension = blob.type.split('/')[1]?.replace('jpeg', 'jpg') || 'png'
    return new File([blob], `pasted-cover-${Date.now()}.${extension}`, {
      type: blob.type
    })
  }

  return null
}

/**
 * Read the first image from the browser clipboard when Clipboard API access
 * is available. Returns null when there is no image or the API is unavailable.
 */
export async function readClipboardImageFile() {
  if (typeof navigator === 'undefined' || !navigator.clipboard?.read) return null

  const items = await navigator.clipboard.read()
  for (const item of items) {
    const imageType = item.types?.find(type => type.startsWith('image/'))
    if (!imageType) continue

    const blob = await item.getType(imageType)
    if (!blob) continue

    const extension = imageType.split('/')[1]?.replace('jpeg', 'jpg') || 'png'
    return new File([blob], `pasted-cover-${Date.now()}.${extension}`, {
      type: imageType
    })
  }

  return null
}
