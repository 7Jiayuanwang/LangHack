/**
 * PDF 文本版式还原（纯函数，无第三方依赖，便于单测）
 *
 * pdf.js 的 getTextContent() 返回的是碎片的文本片段（items），每片带自己的坐标。
 * 这里负责把碎片还原成人能读的段落：
 *   1. 按纵向坐标把一个页面还原成「行」
 *   2. 把「行」按标点/大小写规则重组回「段落」
 */

/** 句末标点：出现在行尾说明这一行是段落自然结束，不应与下一行拼接 */
export const SENTENCE_END_RE = /[.!?:;。！？：；”’"')\]）\]]$/
/** 下一行以小写字母或英文标点开头 → 判定为被 PDF 自动换行拆开的同一段 */
export const LOWER_START_RE = /^[a-z,;:)\]}"'’]/
/** 中日韩文字（含全角标点） */
export const CJK_RE = /[\u3000-\u303f\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\uff00-\uffef]/

/**
 * 把 pdf.js 的 textContent.items 按纵向位置还原成「行」。
 *
 * @param {Array<{str?:string, transform?:number[], height?:number, hasEOL?:boolean}>} items
 * @returns {string[]} 行文本（已 trim，空行已丢弃）
 */
export function extractPageLines(items) {
  const lines = []
  let current = ''
  let prevY = null
  let prevH = 10

  const flush = () => {
    const text = current.replace(/\s+$/, '').trim()
    if (text) lines.push(text)
    current = ''
  }

  for (const item of items || []) {
    const tf = item.transform || []
    const y = typeof tf[5] === 'number' ? tf[5] : null
    const h = Math.abs(item.height || tf[3] || 0) || 10

    // 纵向位置明显跳变 → 换行
    if (prevY !== null && y !== null && Math.abs(y - prevY) > Math.max(2, prevH * 0.4)) {
      flush()
    }

    current += typeof item.str === 'string' ? item.str : ''

    if (item.hasEOL) {
      flush()
      prevY = null
    } else if (y !== null) {
      prevY = y
    }
    prevH = h
  }
  flush()
  return lines
}

/**
 * 把行列表重组为段落文本。
 *
 * - 英文连字符断行还原：`inter-` + `national` → `international`
 * - 自动换行拼接：行尾无句末标点 且 下一行小写开头 → 视为同一段，用空格连接
 * - 中文换行拼接：两侧都是中文时直接连接，不加空格
 *
 * @param {string[]} lines
 * @returns {string} 段落之间以空行分隔
 */
export function linesToParagraphs(lines) {
  const out = []

  for (let i = 0; i < lines.length; i++) {
    let cur = lines[i]

    // 统一合并循环：每接一行都重新判断是「连字符断词」还是「自动换行」
    while (i + 1 < lines.length) {
      const next = lines[i + 1]

      // 1) 英文软连字符断行： inter- + national → international
      if (/-$/.test(cur) && /^[a-z]/.test(next)) {
        cur = cur.slice(0, -1) + next
        i++
        continue
      }

      // 已经以句末标点收尾 → 这一行是段落结尾，不再并入下一行
      if (SENTENCE_END_RE.test(cur)) break

      // 2) 自动换行拼接（英文）：行尾无句末标点 + 下一行小写开头
      if (LOWER_START_RE.test(next)) {
        cur = `${cur} ${next}`
        i++
        continue
      }

      // 3) 自动换行拼接（中文）：两侧都是中文，直接相连、不加空格
      if (CJK_RE.test(cur.slice(-1)) && CJK_RE.test(next.slice(0, 1))) {
        cur = cur + next
        i++
        continue
      }

      break
    }

    out.push(cur)
  }

  return out.join('\n\n')
}

/** 单页 items → 段落文本 */
export function pageItemsToText(items) {
  return linesToParagraphs(extractPageLines(items))
}

export default { extractPageLines, linesToParagraphs, pageItemsToText }
