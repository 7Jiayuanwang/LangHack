function getHeaderValue(headers, name) {
  if (!headers || !name) return ''
  const target = name.toLowerCase()
  for (const [key, value] of Object.entries(headers)) {
    if (String(key).toLowerCase() === target) {
      return Array.isArray(value) ? value[0] : value
    }
  }
  return ''
}

export function parseDownloadFilename(contentDisposition, fallbackName = 'download.bin') {
  if (!contentDisposition) return fallbackName

  const utf8Match = /filename\*\s*=\s*UTF-8''([^;]+)/i.exec(contentDisposition)
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim())
    } catch {
      return utf8Match[1].trim()
    }
  }

  const plainMatch = /filename\s*=\s*"?([^\";]+)"?/i.exec(contentDisposition)
  if (plainMatch?.[1]) {
    return plainMatch[1].trim()
  }

  return fallbackName
}

export async function normalizeDownloadResponse(response, fallbackName, defaultMime = 'application/octet-stream') {
  const headers = response?.headers || {}
  const contentType = getHeaderValue(headers, 'content-type') || defaultMime
  const contentDisposition = getHeaderValue(headers, 'content-disposition')
  const raw = response?.data ?? response

  let blob
  if (raw instanceof Blob) {
    blob = raw
  } else if (raw instanceof ArrayBuffer) {
    blob = new Blob([raw], { type: contentType })
  } else if (typeof raw === 'string') {
    blob = new Blob([raw], { type: contentType })
  } else if (raw == null) {
    blob = new Blob([], { type: contentType })
  } else {
    blob = new Blob([JSON.stringify(raw)], { type: contentType || 'application/json' })
  }

  return {
    blob,
    filename: parseDownloadFilename(contentDisposition, fallbackName),
    contentType
  }
}

export async function extractBlobErrorMessage(blob, fallback = '下载失败') {
  if (!(blob instanceof Blob)) return fallback

  try {
    const text = await blob.text()
    if (!text) return fallback

    if ((blob.type || '').includes('application/json')) {
      const parsed = JSON.parse(text)
      return parsed?.message || parsed?.detail || fallback
    }

    return text.trim() || fallback
  } catch {
    return fallback
  }
}

export function triggerBlobDownload(blob, filename) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
