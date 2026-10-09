/**
 * PDF → Markdown 文本抽取（纯浏览器端，不依赖后端）
 *
 * 用途：支撑「我的上传内容 → 批量导入文章」的 PDF 文章导入。
 * 把 PDF 正文抽取成 Markdown 兼容的纯文本，再交给原有导入流程
 * （云端图片搬运 / 本地图片匹配）继续处理。
 *
 * 版式还原（行列 → 段落）的逻辑在 pdfTextLayout.js，本文件只负责 pdf.js 的调用。
 *
 * 已知边界：
 * - 只支持「文本型 PDF」（文字可选中、可复制）。扫描件 / 图片型 PDF 没有文字层，抽不出内容。
 * - 不抽取 PDF 内嵌图片。需要配图时，请在同一次文件选择里一并勾选本地图片文件。
 */
import * as pdfjsLib from 'pdfjs-dist'
import pdfWorkerUrl from 'pdfjs-dist/build/pdf.worker.min.mjs?url'
import { pageItemsToText } from './pdfTextLayout'

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfWorkerUrl

/**
 * 抽取 PDF 文本。
 *
 * @param {File|Blob} file PDF 文件
 * @param {(page:number, totalPages:number)=>void} [onProgress] 每页解析完成后的回调
 * @returns {Promise<{ totalPages:number, text:string }>}
 */
export async function extractPdfToMarkdown(file, onProgress) {
  const buffer = await file.arrayBuffer()

  const doc = await pdfjsLib.getDocument({
    data: new Uint8Array(buffer),
    isEvalSupported: false,
    disableFontFace: true
  }).promise

  const totalPages = doc.numPages
  const pages = []

  try {
    for (let p = 1; p <= totalPages; p++) {
      const page = await doc.getPage(p)
      const content = await page.getTextContent()
      pages.push(pageItemsToText(content.items))
      page.cleanup()
      if (typeof onProgress === 'function') onProgress(p, totalPages)
    }
  } finally {
    await doc.destroy()
  }

  const text = pages
    .join('\n\n')
    .replace(/[ \t]+\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()

  return { totalPages, text }
}

export default extractPdfToMarkdown
