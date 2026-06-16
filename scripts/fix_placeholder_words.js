/**
 * 扫描 words 表里 definition 含占位符（"基础词汇，待完善定义" / 含"待完善"）的记录，
 * 从三本前端 CSV（高考 / 四级 / 六级）里查正确释义并更新。
 *
 * 用法：
 *   node scripts/fix_placeholder_words.js            # dry-run，只打印将要更新/缺失的词
 *   node scripts/fix_placeholder_words.js --apply    # 真的写库
 *
 * 依赖：
 *   - docker compose 服务 mysql-master 在跑
 *   - .env 里的 MYSQL_ROOT_PASSWORD（用环境变量 DB_PASSWORD 覆盖也可）
 */

const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const CSV_FILES = [
  'frontend/public/data/六级核心词汇.csv',
  'frontend/public/data/四级核心词汇.csv',
  'frontend/public/data/高考英语必背词汇.csv',
];

const APPLY = process.argv.includes('--apply');
const MYSQL_PASSWORD = process.env.DB_PASSWORD || readEnvPassword() || 'pyk112507';
const DB_NAME = 'english_learning';
const SERVICE = 'mysql-master';

function readEnvPassword() {
  try {
    const env = fs.readFileSync(path.join(process.cwd(), '.env'), 'utf-8');
    const m = env.match(/MYSQL_ROOT_PASSWORD\s*=\s*([^\r\n]+)/);
    return m ? m[1].trim() : null;
  } catch {
    return null;
  }
}

// --- CSV 解析（与 WordRecite.vue 的 parseCSV 同口径） ---
function parseCSV(csvText) {
  const out = [];
  const lines = csvText.split('\n');
  for (let i = 1; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;
    const m = line.match(/^"([^"]+)","([^"]+)"$/);
    if (m) out.push({ word: m[1], meaning: m[2] });
  }
  return out;
}

function loadCsvDict() {
  const dict = new Map();
  for (const file of CSV_FILES) {
    const full = path.join(process.cwd(), file);
    if (!fs.existsSync(full)) {
      console.warn(`[warn] 找不到 CSV: ${file}`);
      continue;
    }
    const rows = parseCSV(fs.readFileSync(full, 'utf-8'));
    for (const r of rows) {
      const key = r.word.toLowerCase().trim();
      // 第一个出现的优先（六级 > 四级 > 高考，按上面顺序）
      if (!dict.has(key)) dict.set(key, r.meaning.trim());
    }
    console.log(`[ok] 加载 ${file}: ${rows.length} 词`);
  }
  return dict;
}

// --- MySQL 调用：通过 docker compose exec 跑 ---
function runSql(sql) {
  const args = [
    'compose',
    'exec',
    '-T',
    SERVICE,
    'mysql',
    '-uroot',
    `-p${MYSQL_PASSWORD}`,
    DB_NAME,
    '--batch',
    '--raw',
    '--default-character-set=utf8mb4',
    '-e',
    sql,
  ];
  try {
    return execFileSync('docker', args, { encoding: 'utf-8', maxBuffer: 32 * 1024 * 1024 });
  } catch (e) {
    const stderr = (e.stderr || '').toString();
    if (stderr.includes('Using a password on the command line')) {
      // 仅警告，结果仍在 stdout
      return e.stdout ? e.stdout.toString() : '';
    }
    throw e;
  }
}

function fetchPlaceholderWords() {
  const sql =
    "SELECT id, word, definition FROM words " +
    "WHERE definition LIKE '%基础词汇%' OR definition LIKE '%待完善%' " +
    "ORDER BY id";
  const out = runSql(sql);
  const lines = out.split('\n').filter(Boolean);
  // 第一行为表头
  return lines.slice(1).map(line => {
    const [id, word, definition] = line.split('\t');
    return { id: parseInt(id, 10), word, definition };
  });
}

function escapeSqlString(s) {
  return s.replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function updateWord(id, newDefinition) {
  const safe = escapeSqlString(newDefinition);
  const sql = `UPDATE words SET definition = '${safe}' WHERE id = ${id}`;
  runSql(sql);
}

// --- 主流程 ---
function main() {
  console.log(`模式: ${APPLY ? 'APPLY (将写库)' : 'DRY-RUN (只打印)'}`);
  console.log('');

  const csvDict = loadCsvDict();
  console.log(`CSV 合并字典共 ${csvDict.size} 词`);
  console.log('');

  const broken = fetchPlaceholderWords();
  console.log(`words 表里占位释义共 ${broken.length} 条`);
  console.log('');

  const fixable = [];
  const missing = [];
  for (const row of broken) {
    const key = row.word.toLowerCase().trim();
    const newDef = csvDict.get(key);
    if (newDef) fixable.push({ ...row, newDefinition: newDef });
    else missing.push(row);
  }

  console.log(`可在 CSV 中查到的: ${fixable.length} 条`);
  console.log(`CSV 中缺失需人工处理的: ${missing.length} 条`);
  console.log('');

  if (fixable.length) {
    console.log('--- 将要更新的词 ---');
    for (const r of fixable) {
      console.log(`  [${r.id}] ${r.word.padEnd(20)} → ${r.newDefinition}`);
    }
    console.log('');
  }

  if (missing.length) {
    console.log('--- CSV 中缺失（需人工或调外部词典）---');
    for (const r of missing) {
      console.log(`  [${r.id}] ${r.word}`);
    }
    console.log('');
  }

  if (!APPLY) {
    console.log('提示: 加 --apply 真正写库');
    return;
  }

  if (!fixable.length) {
    console.log('无可更新内容');
    return;
  }

  console.log('--- 开始写库 ---');
  let ok = 0;
  let fail = 0;
  for (const r of fixable) {
    try {
      updateWord(r.id, r.newDefinition);
      ok++;
    } catch (e) {
      fail++;
      console.error(`  [FAIL] id=${r.id} word=${r.word}: ${e.message}`);
    }
  }
  console.log(`写库完成: 成功 ${ok}，失败 ${fail}`);
}

main();
