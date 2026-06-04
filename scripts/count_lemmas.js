const fs = require('fs');

// Load all base words from auth + cet6 + gaokao
const allBases = {};
for (const f of ['scripts/CET4_edited.txt']) {
  const t = fs.readFileSync(f, 'utf-8');
  for (const rawLine of t.split('\n')) {
    const line = rawLine.trim();
    if (!line || /^[A-Z]$/.test(line)) continue;
    if (line.includes('大学英语') || (line.startsWith('(共') && line.includes('词'))) continue;
    const m = line.match(/^(\S+)\s+/);
    if (m && m[1].length < 50) allBases[m[1].toLowerCase()] = true;
  }
}
for (const f of ['frontend/public/data/六级核心词汇.csv', 'frontend/public/data/高考英语必背词汇.csv']) {
  const t = fs.readFileSync(f, 'utf-8').replace(/\r/g, '');
  for (const line of t.trim().split('\n').slice(1)) {
    const m = line.match(/^"([^"]+)"/);
    if (m) allBases[m[1].toLowerCase()] = true;
  }
}

// Derivation rules
function findBase(word) {
  const lower = word.toLowerCase();
  if (allBases[lower]) return lower;
  const rules = [
    { pat: /^(.+)ication$/, bases: [m => m[1]+'y'] },
    { pat: /^(.+)ation$/, bases: [m => m[1]+'ate', m => m[1]+'e'] },
    { pat: /^(.+)ition$/, bases: [m => m[1]+'ite', m => m[1]+'e'] },
    { pat: /^(.+)ution$/, bases: [m => m[1]+'ute', m => m[1]+'e'] },
    { pat: /^(.+)otion$/, bases: [m => m[1]+'ote', m => m[1]+'e'] },
    { pat: /^(.+)ction$/, bases: [m => m[1]+'ct'] },
    { pat: /^(.+)ption$/, bases: [m => m[1]+'be'] },
    { pat: /^(.+)ssion$/, bases: [m => m[1]+'ss'] },
    { pat: /^(.+)sion$/, bases: [m => m[1]+'de', m => m[1]+'se'] },
    { pat: /^(.+)tion$/, bases: [m => m[1]+'te', m => m[1]+'t', m => m[1]+'e'] },
    { pat: /^(.+)isation$/, bases: [m => m[1]+'ise'] },
    { pat: /^(.+)ization$/, bases: [m => m[1]+'ize'] },
    { pat: /^(.+)ment$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)iness$/, bases: [m => m[1]+'y'] },
    { pat: /^(.+)ness$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ility$/, bases: [m => m[1]+'le'] },
    { pat: /^(.+)ality$/, bases: [m => m[1]+'al'] },
    { pat: /^(.+)icity$/, bases: [m => m[1]+'ic'] },
    { pat: /^(.+)osity$/, bases: [m => m[1]+'ous'] },
    { pat: /^(.+)ivity$/, bases: [m => m[1]+'ive'] },
    { pat: /^(.+)bility$/, bases: [m => m[1]+'ble'] },
    { pat: /^(.+)arity$/, bases: [m => m[1]+'ar'] },
    { pat: /^(.+)ity$/, bases: [m => m[1]+'e', m => m[1]] },
    { pat: /^(.+)ance$/, bases: [m => m[1]+'ant', m => m[1]+'e'] },
    { pat: /^(.+)ence$/, bases: [m => m[1]+'ent', m => m[1]+'e'] },
    { pat: /^(.+)ancy$/, bases: [m => m[1]+'ant'] },
    { pat: /^(.+)ency$/, bases: [m => m[1]+'ent'] },
    { pat: /^(.+)ator$/, bases: [m => m[1]+'ate'] },
    { pat: /^(.+)er$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)or$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ar$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ist$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ism$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ative$/, bases: [m => m[1]+'ate', m => m[1]] },
    { pat: /^(.+)tive$/, bases: [m => m[1]+'t'] },
    { pat: /^(.+)sive$/, bases: [m => m[1]+'d', m => m[1]+'de'] },
    { pat: /^(.+)ive$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)able$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ible$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ically$/, bases: [m => m[1]+'ic'] },
    { pat: /^(.+)ably$/, bases: [m => m[1]+'able'] },
    { pat: /^(.+)ibly$/, bases: [m => m[1]+'ible'] },
    { pat: /^(.+)ally$/, bases: [m => m[1]+'al'] },
    { pat: /^(.+)ily$/, bases: [m => m[1]+'y'] },
    { pat: /^(.+)ly$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ical$/, bases: [m => m[1]+'ic', m => m[1]+'ics'] },
    { pat: /^(.+)tial$/, bases: [m => m[1]+'t', m => m[1]+'ce'] },
    { pat: /^(.+)cial$/, bases: [m => m[1]+'ce'] },
    { pat: /^(.+)gical$/, bases: [m => m[1]+'gy'] },
    { pat: /^(.+)rial$/, bases: [m => m[1]+'re', m => m[1]+'ry'] },
    { pat: /^(.+)ial$/, bases: [m => m[1]+'e', m => m[1]] },
    { pat: /^(.+)ual$/, bases: [m => m[1]+'ue', m => m[1]] },
    { pat: /^(.+)al$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ful$/, bases: [m => m[1]] },
    { pat: /^(.+)less$/, bases: [m => m[1]] },
    { pat: /^(.+)eous$/, bases: [m => m[1]] },
    { pat: /^(.+)ious$/, bases: [m => m[1]+'y', m => m[1]+'e', m => m[1]] },
    { pat: /^(.+)uous$/, bases: [m => m[1]+'ue', m => m[1]] },
    { pat: /^(.+)ous$/, bases: [m => m[1]] },
    { pat: /^(.+)ish$/, bases: [m => m[1]] },
    { pat: /^(.+)ic$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ying$/, bases: [m => m[1]+'y'] },
    { pat: /^(.+)king$/, bases: [m => m[1]+'ke'] },
    { pat: /^(.+)nning$/, bases: [m => m[1]+'n'] },
    { pat: /^(.+)tting$/, bases: [m => m[1]+'t'] },
    { pat: /^(.+)pping$/, bases: [m => m[1]+'p'] },
    { pat: /^(.+)gging$/, bases: [m => m[1]+'g'] },
    { pat: /^(.+)mming$/, bases: [m => m[1]+'m'] },
    { pat: /^(.+)lling$/, bases: [m => m[1]+'ll'] },
    { pat: /^(.+)ving$/, bases: [m => m[1]+'ve'] },
    { pat: /^(.+)cing$/, bases: [m => m[1]+'ce'] },
    { pat: /^(.+)ing$/, bases: [m => m[1]+'e', m => m[1]] },
    { pat: /^(.+)ied$/, bases: [m => m[1]+'y'] },
    { pat: /^(.+)ed$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ize$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ise$/, bases: [m => m[1], m => m[1]+'e'] },
    { pat: /^(.+)ship$/, bases: [m => m[1]] },
    { pat: /^(.+)hood$/, bases: [m => m[1]] },
    { pat: /^(.+)dom$/, bases: [m => m[1]] },
    { pat: /^(.+)en$/, bases: [m => m[1], m => m[1]+'e'] },
  ];
  for (const rule of rules) {
    const match = lower.match(rule.pat);
    if (!match) continue;
    for (const baseFn of rule.bases) {
      const base = baseFn(match);
      if (base.length >= 2 && allBases[base]) return base;
    }
  }
  const prefixes = ['re','un','in','im','dis','mis','over','under','out','up'];
  for (const pref of prefixes) {
    if (lower.startsWith(pref) && lower.length > pref.length + 2) {
      const base = lower.slice(pref.length);
      if (allBases[base]) return base;
      if (allBases[base + 'e']) return base + 'e';
    }
  }
  return null;
}

const csvText = fs.readFileSync('frontend/public/data/四级核心词汇.csv', 'utf-8').replace(/\r/g, '');
const csvWords = [];
for (const line of csvText.trim().split('\n').slice(1)) {
  const m = line.match(/^"([^"]+)"/);
  if (m) csvWords.push(m[1].toLowerCase());
}

const groups = {};
const standalones = [];
for (const w of csvWords) {
  const base = findBase(w);
  if (base) {
    if (!groups[base]) groups[base] = [];
    groups[base].push(w);
  } else {
    standalones.push(w);
  }
}

const derivedCount = Object.values(groups).reduce((s, arr) => s + arr.length, 0);
const baseGroupCount = Object.keys(groups).length;
const totalLemmas = baseGroupCount + standalones.length;

console.log('总条目: ' + csvWords.length);
console.log('衍生词: ' + derivedCount + ' → 归入 ' + baseGroupCount + ' 个词根');
console.log('独立词: ' + standalones.length);
console.log('合并后 ≈ ' + totalLemmas + ' 个词');
