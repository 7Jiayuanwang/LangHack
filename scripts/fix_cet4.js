/**
 * Fix CET-4 CSV comprehensively:
 * 1. Exact match with authoritative mahavivo CET-4 list
 * 2. Cross-reference with CET-6 and Gaokao files
 * 3. Derivation/word-root analysis
 * 4. MyMemory API as final fallback
 */
const fs = require('fs');
const https = require('https');

const CET4_CSV = 'frontend/public/data/四级核心词汇.csv';
const CET6_CSV = 'frontend/public/data/六级核心词汇.csv';
const GAOKAO_CSV = 'frontend/public/data/高考英语必背词汇.csv';
const AUTH_TXT = 'scripts/CET4_edited.txt';
const PROGRESS_FILE = 'scripts/cet4_fix_progress.json';
const CONCURRENCY = 8;

const BAD_PATTERNS = [
  'CET4词汇', '常用词汇',
  'n. 行为；状态；结果', 'n. 性质；状态；行为', 'n. 性质；状态',
  'adj. 有……性质的；倾向于……的', 'adj. 有……性质的；充满……的',
  'adj. 能……的；可……的', 'adj. ……的；有关……的',
  'adv. 以……方式；……地',
  'n. ……的人；……剂', 'n. ……的人', 'n. ……者；……物', 'n. ……者；……家',
  'v. 使……；做……', 'n./v. ……的行为；进行中', 'n./v. 复数；第三人称单数',
];

function isBad(m) {
  for (const p of BAD_PATTERNS) if (m.includes(p)) return true;
  return /^[a-z]+\.\s*(……|行为|性质|状态|复数|第三人称)/.test(m);
}

// --- Loaders ---

function loadAuthTxt(filepath) {
  const text = fs.readFileSync(filepath, 'utf-8');
  const dict = {};
  for (const rawLine of text.split('\n')) {
    const line = rawLine.trim();
    if (!line || /^[A-Z]$/.test(line)) continue;
    if (line.includes('大学英语') || (line.startsWith('(共') && line.includes('词'))) continue;
    const m = line.match(/^(\S+)\s+(?:\[.*?\]\s+)?(.+)$/);
    if (m && m[1].length < 50) dict[m[1].toLowerCase()] = m[2];
  }
  return dict;
}

function loadCSVDict(filepath) {
  const text = fs.readFileSync(filepath, 'utf-8').replace(/\r/g, '');
  const dict = {};
  for (const line of text.trim().split('\n').slice(1)) {
    const m = line.trim().match(/^"([^"]+)","([^"]+)"$/);
    if (m) dict[m[1].toLowerCase()] = m[2];
  }
  return dict;
}

function loadCSVEntries(filepath) {
  const text = fs.readFileSync(filepath, 'utf-8').replace(/\r/g, '');
  const entries = [];
  for (const line of text.trim().split('\n').slice(1)) {
    const m = line.trim().match(/^"([^"]+)","([^"]+)"$/);
    if (m) entries.push({ word: m[1], meaning: m[2] });
  }
  return entries;
}

// --- Derivation ---

function deriveBaseWords(word) {
  const lower = word.toLowerCase();
  const rules = [
    // -tion/-sion -> verb
    ['ication', 'y'], ['cation', 'y'], ['ation', 'e'], ['ition', 'e'],
    ['ution', 'e'], ['otion', 'e'], ['ction', 't'], ['ption', 'be'],
    ['ssion', 'ss'], ['sion', 'd'], ['tion', 't'], ['tion', 'te'],
    // -ment
    ['ment', ''], ['ment', 'e'],
    // -ness
    ['ness', ''], ['ness', 'e'],
    // -ity -> adj
    ['ility', 'le'], ['ality', 'al'], ['icity', 'ic'], ['osity', 'ous'],
    ['ivity', 'ive'], ['arity', 'ar'], ['bility', 'ble'],
    ['ity', ''], ['ity', 'e'],
    // -ence/-ance
    ['ence', 'ent'], ['ance', 'ant'], ['ency', 'ent'], ['ancy', 'ant'],
    // -er/-or/-ist
    ['ator', 'ate'], ['ctor', 'ct'], ['ssor', 'ss'], ['tter', 't'],
    ['nner', 'n'], ['pper', 'p'], ['ier', 'y'],
    ['er', ''], ['er', 'e'], ['or', ''], ['or', 'e'],
    ['ist', ''], ['ist', 'e'], ['ism', ''], ['ism', 'e'],
    // -ive/-ative
    ['ative', 'ate'], ['itive', ''], ['tive', 't'], ['sive', 'd'],
    ['ive', 'e'], ['ive', ''],
    // -able/-ible
    ['able', ''], ['able', 'e'], ['ible', ''], ['ible', 'e'],
    // -ly adverbs
    ['ically', 'ic'], ['ably', 'able'], ['ibly', 'ible'],
    ['ally', 'al'], ['ily', 'y'],
    ['ly', ''], ['ly', 'e'],
    // -ing/-ed
    ['ying', 'y'], ['nning', 'n'], ['tting', 't'], ['pping', 'p'],
    ['gging', 'g'], ['mming', 'm'], ['lling', 'll'],
    ['king', 'ke'], ['ving', 've'], ['cing', 'ce'], ['ding', 'de'],
    ['ing', 'e'], ['ing', ''],
    ['ied', 'y'], ['cked', 'ck'], ['ssed', 'ss'], ['lled', 'll'],
    ['ed', ''], ['ed', 'e'],
    // -al/-ial/-ical
    ['ical', 'ic'], ['tial', 't'], ['cial', 'ce'], ['gical', 'gy'],
    ['rial', 're'], ['ial', 'e'], ['ual', 'ue'],
    ['al', ''], ['al', 'e'],
    // -ful/-less/-ous/-ish
    ['fully', 'ful'], ['fulness', 'ful'], ['less', ''],
    ['eous', ''], ['ious', 'y'], ['ious', 'e'], ['uous', 'ue'],
    ['ous', ''], ['ful', ''], ['ish', ''],
    // -ize/-ise
    ['ization', 'ize'], ['isation', 'ise'],
    ['ize', ''], ['ise', ''],
    // -ure
    ['ature', 'ate'], ['sure', 'se'], ['ture', 't'], ['ure', 'e'],
    // -ate/-ite verbs
    ['ify', ''], ['ate', 'e'], ['ate', ''],
    // -y
    ['ary', ''], ['ory', ''], ['ary', 'e'], ['ory', 'e'],
    // -ship/-hood/-dom
    ['ship', ''], ['hood', ''], ['dom', ''],
    // -en
    ['en', ''], ['en', 'e'],
    // -th/-t
    ['rth', ''], ['gth', 'g'], ['dth', 'd'], ['th', ''],
    // -ic
    ['etic', 'ete'], ['atic', 'ate'], ['ic', ''],
    // re- prefix (uncommon in derivation but try)
  ];

  // Sort by suffix length (try longer/more specific first, but we'll try all)
  rules.sort((a, b) => b[0].length - a[0].length);

  const candidates = [];
  for (const [suffix, replacement] of rules) {
    if (lower.endsWith(suffix) && lower.length > suffix.length + 1) {
      const base = lower.slice(0, -suffix.length) + replacement;
      if (base.length >= 2) candidates.push(base);
    }
  }
  // Deduplicate preserving order
  return [...new Set(candidates)];
}

function findDerivedMeaning(word, dicts) {
  const { auth, cet6, gaokao } = dicts;
  const bases = deriveBaseWords(word);
  for (const base of bases) {
    // Check all dictionaries
    for (const dict of [auth, cet6, gaokao]) {
      if (dict[base] && !isBad(dict[base])) {
        const pos = guessPOS(word);
        const meaningPart = extractShortMeaning(dict[base]);
        return `${pos} ${meaningPart}（← ${base}）`;
      }
    }
  }
  return null;
}

function guessPOS(word) {
  const lower = word.toLowerCase();
  if (/^(re|un|in|im|dis|mis|over|under|out)/.test(lower) && lower.length > 6) return 'v.';
  if (/(tion|sion|ment|ness|ity|ance|ence|ship|hood|dom|th|ism|ist|er|or|ing)$/.test(lower)) return 'n.';
  if (/(able|ible|ful|less|ous|ive|al|ial|ic|ish|ed|ing|y|ary|ory)$/.test(lower)) return 'a.';
  if (/(ly)$/.test(lower)) return 'ad.';
  if (/(ize|ise|ify|ate|en)$/.test(lower)) return 'v.';
  return 'n.';
}

function extractShortMeaning(meaning) {
  const parts = meaning.split(/[；;,，]/);
  const first = parts[0].trim();
  // Remove POS prefix if present
  const cleaned = first.replace(/^[a-z.]+\s*/, '');
  return cleaned || first;
}

// --- MyMemory API ---

function translateWord(word) {
  return new Promise((resolve) => {
    const url = `https://api.mymemory.translated.net/get?q=${encodeURIComponent(word)}&langpair=en%7Czh-CN`;
    const req = https.get(url, { timeout: 10000 }, (res) => {
      let data = '';
      res.on('data', c => data += c);
      res.on('end', () => {
        try {
          const json = JSON.parse(data);
          const text = json.responseData?.translatedText;
          if (text && text !== word && text.length < 100) {
            resolve(text);
          } else {
            resolve(null);
          }
        } catch (e) { resolve(null); }
      });
    });
    req.on('error', () => resolve(null));
    req.setTimeout(10000, () => { req.destroy(); resolve(null); });
  });
}

async function processAPIBatch(words, startIdx, total, progress, results) {
  const batch = words.slice(startIdx, startIdx + CONCURRENCY);
  const promises = batch.map(async (word, i) => {
    const idx = startIdx + i;
    const lower = word.toLowerCase();
    if (progress[lower]?.done) {
      results[lower] = progress[lower].meaning;
      return;
    }
    const translation = await translateWord(word);
    if (translation) {
      results[lower] = translation;
      progress[lower] = { done: true, meaning: translation, source: 'api' };
      console.log(`  [${idx + 1}/${total}] ${word} -> ${translation.substring(0, 50)}`);
    } else {
      console.log(`  [${idx + 1}/${total}] ${word} -> FAILED`);
    }
  });
  await Promise.all(promises);
}

// --- Main ---

async function main() {
  console.log('=== CET-4 Comprehensive Fix ===\n');

  // Backup
  const bakFile = CET4_CSV + '.bak';
  if (!fs.existsSync(bakFile)) {
    fs.copyFileSync(CET4_CSV, bakFile);
    console.log('Backup: ' + bakFile + '\n');
  }

  // Load dictionaries
  console.log('Loading authoritative list...');
  const authDict = loadAuthTxt(AUTH_TXT);
  console.log(`  ${Object.keys(authDict).length} words`);

  console.log('Loading CET-6...');
  const cet6Dict = loadCSVDict(CET6_CSV);
  console.log(`  ${Object.keys(cet6Dict).length} words`);

  console.log('Loading Gaokao...');
  const gaokaoDict = loadCSVDict(GAOKAO_CSV);
  console.log(`  ${Object.keys(gaokaoDict).length} words`);

  const dicts = { auth: authDict, cet6: cet6Dict, gaokao: gaokaoDict };

  // Load CET-4 entries
  const entries = loadCSVEntries(CET4_CSV);
  console.log(`CET-4 entries: ${entries.length}\n`);

  // Fix step by step
  let alreadyGood = 0, directFix = 0, crossFix = 0, derivedFix = 0;

  for (const entry of entries) {
    if (!isBad(entry.meaning)) { alreadyGood++; continue; }
    const lower = entry.word.toLowerCase();

    // 1. Exact match in authoritative list
    if (authDict[lower]) {
      entry.meaning = authDict[lower]; directFix++; continue;
    }

    // 2. Cross-reference with CET-6
    if (cet6Dict[lower] && !isBad(cet6Dict[lower])) {
      entry.meaning = cet6Dict[lower]; crossFix++; continue;
    }

    // 3. Cross-reference with Gaokao
    if (gaokaoDict[lower] && !isBad(gaokaoDict[lower])
        && !gaokaoDict[lower].includes('[需要补充定义]')) {
      entry.meaning = gaokaoDict[lower]; crossFix++; continue;
    }

    // 4. Derivation
    const derived = findDerivedMeaning(lower, dicts);
    if (derived) {
      entry.meaning = derived; derivedFix++; continue;
    }
  }

  console.log(`Already correct: ${alreadyGood}`);
  console.log(`Fixed (exact auth): ${directFix}`);
  console.log(`Fixed (cross-ref): ${crossFix}`);
  console.log(`Fixed (derived): ${derivedFix}`);

  // Remaining bad entries need API
  const needAPI = entries.filter(e => isBad(e.meaning));
  console.log(`\nNeed API fallback: ${needAPI.length}\n`);

  if (needAPI.length > 0) {
    // Load progress
    let progress = {};
    if (fs.existsSync(PROGRESS_FILE)) {
      progress = JSON.parse(fs.readFileSync(PROGRESS_FILE, 'utf-8'));
      const done = Object.values(progress).filter(v => v.done).length;
      console.log(`Previous API progress: ${done} fixed`);
    }

    console.log('Starting API translation (MyMemory)...');
    const results = {};
    const total = needAPI.length;

    for (let i = 0; i < total; i += CONCURRENCY) {
      await processAPIBatch(needAPI.map(e => e.word), i, total, progress, results);
      fs.writeFileSync(PROGRESS_FILE, JSON.stringify(progress, null, 2));
      if (i + CONCURRENCY < total) {
        await new Promise(r => setTimeout(r, 600)); // Rate limit between batches
      }
    }

    // Apply API results
    let apiFixed = 0, apiFailed = 0;
    for (const entry of needAPI) {
      const lower = entry.word.toLowerCase();
      if (results[lower]) {
        entry.meaning = results[lower];
        apiFixed++;
      } else {
        apiFailed++;
      }
    }
    console.log(`\nAPI fixed: ${apiFixed}, API failed: ${apiFailed}`);
  }

  // Write output
  console.log('\nWriting fixed CSV...');
  const lines = ['"Word","Meaning"'];
  for (const entry of entries) {
    const safeMeaning = entry.meaning.replace(/"/g, '""');
    lines.push(`"${entry.word}","${safeMeaning}"`);
  }
  fs.writeFileSync(CET4_CSV, lines.join('\n'), 'utf-8');

  // Final stats
  const finalGood = entries.filter(e => !isBad(e.meaning)).length;
  const finalBad = entries.length - finalGood;
  console.log(`\n=== Final Results ===`);
  console.log(`Total: ${entries.length}`);
  console.log(`Good: ${finalGood} (${(finalGood*100/entries.length).toFixed(1)}%)`);
  console.log(`Still bad: ${finalBad}`);
  console.log(`Output: ${CET4_CSV}`);
}

main().catch(console.error);
