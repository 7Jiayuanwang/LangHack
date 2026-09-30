// Audit remaining legacy blue styles; --write migrates only exact, known brand colors
// inside Vue <style> blocks. Template/script strings and semantic info colors stay intact.
import { readFile, readdir, writeFile } from 'node:fs/promises'
import { join, relative } from 'node:path'

const root = new URL('../src/', import.meta.url).pathname
const scanRoots = [join(root, 'views'), join(root, 'components')]
const write = process.argv.includes('--write')
const legacyColors = new Map([
  ['#409eff', 'var(--xy-brand-600)'],
  ['#0066cc', 'var(--xy-brand-600)'],
  ['#137fec', 'var(--xy-brand-600)'],
  ['#1d4ed8', 'var(--xy-brand-700)'],
  ['#1890ff', 'var(--xy-brand-600)'],
  ['#1976d2', 'var(--xy-brand-600)'],
  ['#2563eb', 'var(--xy-brand-600)'],
  ['#ecf5ff', 'var(--xy-brand-50)'],
  ['#f5f9ff', 'var(--xy-brand-50)'],
  ['#eff6ff', 'var(--xy-brand-50)'],
  ['#e6f3ff', 'var(--xy-brand-50)'],
])
const legacyRgb = [
  [/rgba\(64,\s*158,\s*255,/gi, 'rgba(23, 110, 81,'],
  [/rgba\(0,\s*102,\s*204,/gi, 'rgba(23, 110, 81,'],
  [/rgba\(29,\s*78,\s*216,/gi, 'rgba(23, 110, 81,'],
]

async function* vueFiles(directory) {
  for (const entry of await readdir(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) yield* vueFiles(path)
    else if (entry.name.endsWith('.vue')) yield path
  }
}

let files = 0
let replacements = 0
for (const scanRoot of scanRoots) {
  for await (const path of vueFiles(scanRoot)) {
    const source = await readFile(path, 'utf8')
    let count = 0
    const migrated = source.replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, (style) => {
      let next = style.replace(/#[0-9a-f]{6}\b/gi, (color) => {
        const replacement = legacyColors.get(color.toLowerCase())
        if (replacement) count++
        return replacement || color
      })
      for (const [pattern, replacement] of legacyRgb) {
        next = next.replace(pattern, () => {
          count++
          return replacement
        })
      }
      return next
    })
    if (!count) continue
    files++
    replacements += count
    process.stdout.write(`${relative(root, path)}: ${count}\n`)
    if (write) await writeFile(path, migrated)
  }
}
process.stdout.write(`${write ? 'Updated' : 'Found'} ${replacements} legacy brand colors in ${files} Vue files.\n`)
