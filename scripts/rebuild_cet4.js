/**
 * Rebuild CET-4 CSV using ONLY authoritative sources:
 * 1. mahavivo authoritative CET-4 list (4615 words, hand-verified)
 * 2. CET-6 cross-reference (hand-verified)
 * 3. Gaokao cross-reference (hand-verified)
 * 4. Smart derivation from base words in authoritative lists
 * 5. Manual translations for remaining words (by AI, to be reviewed)
 *
 * NO MyMemory API - all translations from reliable sources.
 */
const fs = require('fs');

const CET4_CSV = 'frontend/public/data/四级核心词汇.csv';
const CET6_CSV = 'frontend/public/data/六级核心词汇.csv';
const GAOKAO_CSV = 'frontend/public/data/高考英语必背词汇.csv';
const AUTH_TXT = 'scripts/CET4_edited.txt';

// === Loaders ===

function loadAuth() {
  const text = fs.readFileSync(AUTH_TXT, 'utf-8');
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

// === Derivation ===

function extractCore(meaning) {
  const parts = meaning.split(/[；;,，]/);
  const first = parts[0].trim();
  return first.replace(/^[a-z.]+\s*/, '');
}

function derive(word, allDicts) {
  const lower = word.toLowerCase();
  const all = { ...allDicts.auth, ...allDicts.cet6, ...allDicts.gaokao };

  const rules = [
    // -tion nouns from verbs
    { pat: /^(.+)ication$/, bases: [m => m[1]+'y'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ation$/, bases: [m => m[1]+'ate', m => m[1]+'e'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ition$/, bases: [m => m[1]+'ite', m => m[1]+'e'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ution$/, bases: [m => m[1]+'ute', m => m[1]+'e'], pos: 'n.', suffix: '' },
    { pat: /^(.+)otion$/, bases: [m => m[1]+'ote', m => m[1]+'e'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ction$/, bases: [m => m[1]+'ct'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ption$/, bases: [m => m[1]+'be'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ssion$/, bases: [m => m[1]+'ss'], pos: 'n.', suffix: '' },
    { pat: /^(.+)sion$/, bases: [m => m[1]+'de', m => m[1]+'se'], pos: 'n.', suffix: '' },
    { pat: /^(.+)tion$/, bases: [m => m[1]+'te', m => m[1]+'t', m => m[1]+'e'], pos: 'n.', suffix: '' },
    // -ment nouns
    { pat: /^(.+)ment$/, bases: [m => m[1], m => m[1]+'e'], pos: 'n.', suffix: '' },
    // -ness nouns
    { pat: /^(.+)iness$/, bases: [m => m[1]+'y'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ness$/, bases: [m => m[1], m => m[1]+'e'], pos: 'n.', suffix: '' },
    // -ity nouns
    { pat: /^(.+)ility$/, bases: [m => m[1]+'le'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ality$/, bases: [m => m[1]+'al'], pos: 'n.', suffix: '' },
    { pat: /^(.+)icity$/, bases: [m => m[1]+'ic'], pos: 'n.', suffix: '' },
    { pat: /^(.+)osity$/, bases: [m => m[1]+'ous'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ivity$/, bases: [m => m[1]+'ive'], pos: 'n.', suffix: '' },
    { pat: /^(.+)bility$/, bases: [m => m[1]+'ble'], pos: 'n.', suffix: '' },
    { pat: /^(.+)arity$/, bases: [m => m[1]+'ar'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ity$/, bases: [m => m[1]+'e', m => m[1]], pos: 'n.', suffix: '' },
    // -ance/-ence/-ancy/-ency
    { pat: /^(.+)ance$/, bases: [m => m[1]+'ant', m => m[1]+'e'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ence$/, bases: [m => m[1]+'ent', m => m[1]+'e'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ancy$/, bases: [m => m[1]+'ant'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ency$/, bases: [m => m[1]+'ent'], pos: 'n.', suffix: '' },
    // -er/-or/-ar agents
    { pat: /^(.+)ator$/, bases: [m => m[1]+'ate'], pos: 'n.', suffix: '的人/物' },
    { pat: /^(.+)cter$/, bases: [m => m[1]+'ct'], pos: 'n.', suffix: '的人/物' },
    { pat: /^(.+)ssor$/, bases: [m => m[1]+'ss'], pos: 'n.', suffix: '的人/物' },
    { pat: /^(.+)ier$/, bases: [m => m[1]+'y'], pos: 'n.', suffix: '的人/物' },
    { pat: /^(.+)er$/, bases: [m => m[1], m => m[1]+'e'], pos: 'n.', suffix: '的人/物' },
    { pat: /^(.+)or$/, bases: [m => m[1], m => m[1]+'e'], pos: 'n.', suffix: '的人/物' },
    { pat: /^(.+)ar$/, bases: [m => m[1], m => m[1]+'e'], pos: 'n.', suffix: '的人/物' },
    // -ist/-ism
    { pat: /^(.+)ist$/, bases: [m => m[1], m => m[1]+'e'], pos: 'n.', suffix: '者/家' },
    { pat: /^(.+)ism$/, bases: [m => m[1], m => m[1]+'e'], pos: 'n.', suffix: '主义/学说' },
    // -ive adjectives
    { pat: /^(.+)ative$/, bases: [m => m[1]+'ate', m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)tive$/, bases: [m => m[1]+'t'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)sive$/, bases: [m => m[1]+'d', m => m[1]+'de'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ive$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
    // -able/-ible
    { pat: /^(.+)able$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ible$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
    // -ly adverbs
    { pat: /^(.+)ically$/, bases: [m => m[1]+'ic'], pos: 'ad.', suffix: '地' },
    { pat: /^(.+)ably$/, bases: [m => m[1]+'able'], pos: 'ad.', suffix: '地' },
    { pat: /^(.+)ibly$/, bases: [m => m[1]+'ible'], pos: 'ad.', suffix: '地' },
    { pat: /^(.+)ally$/, bases: [m => m[1]+'al'], pos: 'ad.', suffix: '地' },
    { pat: /^(.+)ily$/, bases: [m => m[1]+'y'], pos: 'ad.', suffix: '地' },
    { pat: /^(.+)ly$/, bases: [m => m[1], m => m[1]+'e'], pos: 'ad.', suffix: '地' },
    // -al adjectives
    { pat: /^(.+)ical$/, bases: [m => m[1]+'ic', m => m[1]+'ics'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)tial$/, bases: [m => m[1]+'t', m => m[1]+'ce'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)cial$/, bases: [m => m[1]+'ce'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)gical$/, bases: [m => m[1]+'gy'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)rial$/, bases: [m => m[1]+'re', m => m[1]+'ry'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ial$/, bases: [m => m[1]+'e', m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ual$/, bases: [m => m[1]+'ue', m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)al$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
    // -ful/-less/-ous/-ish/-ic
    { pat: /^(.+)ful$/, bases: [m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)less$/, bases: [m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)eous$/, bases: [m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ious$/, bases: [m => m[1]+'y', m => m[1]+'e', m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)uous$/, bases: [m => m[1]+'ue', m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ous$/, bases: [m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ish$/, bases: [m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)etic$/, bases: [m => m[1]+'ete', m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)atic$/, bases: [m => m[1]+'ate', m => m[1]], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ic$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
    // -ing
    { pat: /^(.+)ying$/, bases: [m => m[1]+'y'], pos: 'n.', suffix: '' },
    { pat: /^(.+)king$/, bases: [m => m[1]+'ke'], pos: 'n.', suffix: '' },
    { pat: /^(.+)nning$/, bases: [m => m[1]+'n'], pos: 'n.', suffix: '' },
    { pat: /^(.+)tting$/, bases: [m => m[1]+'t'], pos: 'n.', suffix: '' },
    { pat: /^(.+)pping$/, bases: [m => m[1]+'p'], pos: 'n.', suffix: '' },
    { pat: /^(.+)gging$/, bases: [m => m[1]+'g'], pos: 'n.', suffix: '' },
    { pat: /^(.+)mming$/, bases: [m => m[1]+'m'], pos: 'n.', suffix: '' },
    { pat: /^(.+)lling$/, bases: [m => m[1]+'ll'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ving$/, bases: [m => m[1]+'ve'], pos: 'n.', suffix: '' },
    { pat: /^(.+)cing$/, bases: [m => m[1]+'ce'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ing$/, bases: [m => m[1]+'e', m => m[1]], pos: 'n.', suffix: '' },
    // -ed
    { pat: /^(.+)ied$/, bases: [m => m[1]+'y'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)cked$/, bases: [m => m[1]+'ck'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ssed$/, bases: [m => m[1]+'ss'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)lled$/, bases: [m => m[1]+'ll'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ed$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
    // -ize/-ise
    { pat: /^(.+)isation$/, bases: [m => m[1]+'ise'], pos: 'n.', suffix: '化' },
    { pat: /^(.+)ization$/, bases: [m => m[1]+'ize'], pos: 'n.', suffix: '化' },
    { pat: /^(.+)ize$/, bases: [m => m[1], m => m[1]+'e'], pos: 'v.', suffix: '化' },
    { pat: /^(.+)ise$/, bases: [m => m[1], m => m[1]+'e'], pos: 'v.', suffix: '化' },
    // -ure
    { pat: /^(.+)ature$/, bases: [m => m[1]+'ate'], pos: 'n.', suffix: '' },
    { pat: /^(.+)sure$/, bases: [m => m[1]+'se'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ture$/, bases: [m => m[1]+'t'], pos: 'n.', suffix: '' },
    { pat: /^(.+)ure$/, bases: [m => m[1]+'e', m => m[1]], pos: 'n.', suffix: '' },
    // -ship/-hood/-dom
    { pat: /^(.+)ship$/, bases: [m => m[1]], pos: 'n.', suffix: '身份/关系' },
    { pat: /^(.+)hood$/, bases: [m => m[1]], pos: 'n.', suffix: '时期/状态' },
    { pat: /^(.+)dom$/, bases: [m => m[1]], pos: 'n.', suffix: '领域/状态' },
    // -en verbs
    { pat: /^(.+)en$/, bases: [m => m[1], m => m[1]+'e'], pos: 'v.', suffix: '' },
    // -th
    { pat: /^(.+)rth$/, bases: [m => m[1]], pos: 'n.', suffix: '' },
    { pat: /^(.+)gth$/, bases: [m => m[1]+'g'], pos: 'n.', suffix: '' },
    { pat: /^(.+)dth$/, bases: [m => m[1]+'d'], pos: 'n.', suffix: '' },
    { pat: /^(.+)th$/, bases: [m => m[1]], pos: 'n.', suffix: '' },
    // -y adjectives from nouns
    { pat: /^(.+)ary$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
    { pat: /^(.+)ory$/, bases: [m => m[1], m => m[1]+'e'], pos: 'a.', suffix: '的' },
  ];

  for (const rule of rules) {
    const match = lower.match(rule.pat);
    if (!match) continue;
    for (const baseFn of rule.bases) {
      const base = baseFn(match);
      if (base.length >= 2 && all[base]) {
        const core = extractCore(all[base]);
        if (core.length >= 2) {
          return rule.pos + ' ' + core + rule.suffix + '（← ' + base + '）';
        }
      }
    }
  }

  // Special: try dropping re-/un-/in-/im-/dis- prefixes
  const prefixes = ['re', 'un', 'in', 'im', 'dis', 'mis', 'over', 'under', 'out', 'up'];
  for (const pref of prefixes) {
    if (lower.startsWith(pref) && lower.length > pref.length + 2) {
      const base = lower.slice(pref.length);
      if (all[base]) {
        const core = extractCore(all[base]);
        return 'v. ' + core + '（← ' + base + '）';
      }
      if (all[base + 'e']) {
        const core = extractCore(all[base + 'e']);
        return 'v. ' + core + '（← ' + base + 'e）';
      }
    }
  }

  return null;
}

// === Manual translations from JSON dictionary files ===
function loadManualDicts() {
  const dict = {};
  const dir = 'scripts/';
  let files;
  try {
    files = fs.readdirSync(dir).filter(f => f.startsWith('manual_dict_') && f.endsWith('.json'));
  } catch(e) {
    console.log('No manual_dict JSON files found');
    return dict;
  }
  let total = 0;
  for (const f of files.sort()) {
    try {
      const data = JSON.parse(fs.readFileSync(dir + f, 'utf-8'));
      for (const [k, v] of Object.entries(data)) {
        dict[k.toLowerCase()] = v;
        total++;
      }
    } catch(e) {
      console.log('Warning: failed to parse ' + f + ': ' + e.message);
    }
  }
  console.log('Loaded ' + total + ' manual translations from ' + files.length + ' JSON files');
  return dict;
}

// === Hardcoded manual fixes for words not in any reliable source ===
const MANUAL_FIXES = {
  // A
  "abbreviation": "n. 缩写；简称（← abbreviate）",
  "abide": "v. 忍受；遵守；坚持",
  "abolition": "n. 废除；废止（← abolish）",
  "abort": "v. 中止；流产；放弃",
  "abreast": "ad. 并排；并肩",
  "absorption": "n. 吸收；专注（← absorb）",
  "absurdity": "n. 荒谬；荒唐（← absurd）",
  "abundance": "n. 丰富；充足（← abundant）",
  "abusive": "a. 辱骂的；虐待的；滥用的（← abuse）",
  "academician": "n. 院士；学会会员（← academy）",
  "acclaim": "v./n. 称赞；喝彩；欢呼",
  "accomplishment": "n. 成就；完成；技艺（← accomplish）",
  "according to": "prep. 根据；按照",
  "accountability": "n. 责任；问责（← accountable）",
  "accountable": "a. 有责任的；可解释的",
  "accountancy": "n. 会计工作；会计学（← accountant）",
  "accumulation": "n. 积累；堆积（← accumulate）",
  "accumulative": "a. 累积的（← accumulate）",
  "accusation": "n. 控告；指控；谴责（← accuse）",
  "ace": "n. 高手；王牌 a. 一流的",
  "acidity": "n. 酸性；酸度（← acid）",
  "acknowledgement": "n. 承认；感谢（← acknowledge）",
  "acquaint": "v. 使熟悉；使了解",
  "acting": "n. 表演；代理 a. 代理的",
  "activate": "v. 激活；启动（← active）",
  "activist": "n. 积极分子；活动家（← active）",
  "acupuncture": "n. 针灸；针刺疗法",
  "adaptive": "a. 适应性的；适应能力强的",
  "addict": "n. 成瘾者 v. 使上瘾",
  "addiction": "n. 上瘾；沉迷（← addict）",
  "addictive": "a. 使人上瘾的",
  "adequacy": "n. 足够；充分；胜任",
  "adhere": "v. 遵守；坚持；粘附",
  "adherence": "n. 遵守；坚持；粘附",
  "adjacent": "a. 邻近的；毗邻的",
  "administer": "v. 管理；执行；给予（药）",
  "administrate": "v. 管理；执行（同administer）",
  "admiration": "n. 钦佩；赞赏（← admire）",
  "admittance": "n. 进入权；准入",
  "admittedly": "ad. 公认地；诚然",
  "adoption": "n. 采用；收养（← adopt）",
  "adorable": "a. 可爱的；讨人喜欢的",
  "adulthood": "n. 成年期；成年",
  "advancement": "n. 进步；提升；促进",
  "advantageous": "a. 有利的；有优势的",
  "advent": "n. 到来；出现；降临",
  "adventurer": "n. 冒险家（← adventure）",
  "adventurous": "a. 有冒险精神的；爱冒险的",
  "adversary": "n. 对手；敌手",
  "adverse": "a. 不利的；相反的；逆的",
  "adversity": "n. 逆境；不幸；灾难",
  "advertising": "n. 广告业；广告",
  "advisable": "a. 明智的；可取的；适当的",
  "advocacy": "n. 拥护；支持；辩护",
  "aerial": "a. 航空的；空中的 n. 天线",
  "aeroplane": "n. 飞机（英式）",
  "aesthetic": "a. 美学的；审美的；美的",
  "aesthetics": "n. 美学",
  "affiliate": "v. 使附属；加入 n. 附属机构",
  "affiliation": "n. 加入；附属；联系",
  "affirm": "v. 确认；断言；肯定",
  "affirmation": "n. 确认；肯定；证实",
  "affirmative": "a. 肯定的；同意的",
  "affix": "v. 贴上；附加 n. 词缀",
  "affixation": "n. 附加；词缀法",
  "affluence": "n. 富裕；丰富",
  "affluent": "a. 富裕的；丰富的",
  "affordability": "n. 可负担性；支付能力",
  "afloat": "a./ad. 漂浮的；在海上；无债的",
  "aftermath": "n. 后果；余波；后效",
  "aggravate": "v. 加重；使恶化；激怒",
  "aggravation": "n. 加重；恶化",
  "aggregate": "v./n. 聚集；总计；合计",
  "agitate": "v. 煽动；鼓动；搅动",
  "agitation": "n. 激动；煽动；搅动",
  "aide": "n. 助手；副官；幕僚",
  "ail": "v. 使苦恼；困扰；生病",
  "ailment": "n. 疾病；小病",
  "airborne": "a. 空气传播的；空中的",
  "air-conditioner": "n. 空调",
  "air-conditioning": "n. 空调系统",
  "aisle": "n. 过道；走廊；通道",
  "alien": "a. 外国的；陌生的 n. 外星人；外国人",
  "alienate": "v. 使疏远；离间；转让",
  "align": "v. 使对齐；使一致；结盟",
  "alignment": "n. 对齐；排列；结盟",
  "allurement": "n. 诱惑；吸引力",
  "alternate": "v. 交替；轮流 a. 交替的；间隔的",
  "amass": "v. 积累；积聚；收集",
  "amend": "v. 修改；修订；改善",
  "analogy": "n. 类比；类推；相似",
  "anonymous": "a. 匿名的；无名的",
  "appal": "v. 使惊骇；使震惊",
  "appall": "v. 使惊骇；使震惊（美式）",
  "apparatus": "n. 仪器；设备；装置",
  "appease": "v. 安抚；平息；满足",
  "appraisal": "n. 评估；评价；鉴定",
  "apprentice": "n. 学徒；徒弟",
  "arbitrate": "v. 仲裁；公断",
  "ascendance": "n. 优势；支配地位",
  "ashore": "ad. 在岸上；上岸",
  "aspire": "v. 渴望；追求；有志于",
  "assert": "v. 断言；主张；坚持",
  "auditing": "n. 审计；查账",
  "authorisation": "n. 授权；批准",
  "autobiography": "n. 自传",
  "automobile": "n. 汽车",
  "autonomy": "n. 自治；自主权",
};

// Continue with more manual fixes in batches
function main() {
  console.log('=== Rebuild CET-4 with Authoritative Sources ===\n');

  // Backup
  const bak = CET4_CSV + '.bak3';
  fs.copyFileSync(CET4_CSV, bak);

  // Load sources
  const auth = loadAuth();
  const cet6 = loadCSVDict(CET6_CSV);
  const gaokao = loadCSVDict(GAOKAO_CSV);
  const allDicts = { auth, cet6, gaokao };

  console.log('Auth: ' + Object.keys(auth).length + ' words');
  console.log('CET-6: ' + Object.keys(cet6).length + ' words');
  console.log('Gaokao: ' + Object.keys(gaokao).length + ' words');

  // Load manual JSON dictionaries
  const manualDict = loadManualDicts();

  const entries = loadCSVEntries(CET4_CSV);
  console.log('CET-4 CSV: ' + entries.length + ' entries\n');

  // Fix each entry
  let authFix=0, cet6Fix=0, gkFix=0, deriveFix=0, manualFix=0, jsonDictFix=0, needMore=0;
  const needMoreList = [];

  for (const entry of entries) {
    const lower = entry.word.toLowerCase();

    // 1. Authoritative match
    if (auth[lower]) {
      entry.meaning = auth[lower];
      authFix++; continue;
    }

    // 2. CET-6 cross-ref
    if (cet6[lower]) {
      entry.meaning = cet6[lower];
      cet6Fix++; continue;
    }

    // 3. Gaokao cross-ref
    if (gaokao[lower] && !gaokao[lower].includes('[需要补充定义]')) {
      entry.meaning = gaokao[lower];
      gkFix++; continue;
    }

    // 4. Derivation
    const derived = derive(lower, allDicts);
    if (derived) {
      entry.meaning = derived;
      deriveFix++; continue;
    }

    // 5. JSON manual dictionaries
    if (manualDict[lower]) {
      entry.meaning = manualDict[lower];
      jsonDictFix++; continue;
    }

    // 6. Hardcoded manual fixes (legacy)
    if (MANUAL_FIXES[lower]) {
      entry.meaning = MANUAL_FIXES[lower];
      manualFix++; continue;
    }

    // 7. Still need more manual work
    needMore++;
    if (needMoreList.length < 200) needMoreList.push(entry.word);
  }

  // Save
  const lines = ['"Word","Meaning"'];
  for (const entry of entries) {
    const safe = entry.meaning.replace(/"/g, '""');
    lines.push(`"${entry.word}","${safe}"`);
  }
  fs.writeFileSync(CET4_CSV, lines.join('\n'), 'utf-8');

  console.log('=== Results ===');
  console.log('Auth match:  ' + authFix);
  console.log('CET-6 match: ' + cet6Fix);
  console.log('Gaokao match:' + gkFix);
  console.log('Derived:     ' + deriveFix);
  console.log('JSON dict:   ' + jsonDictFix);
  console.log('Manual:      ' + manualFix);
  console.log('Need more:   ' + needMore);
  console.log('Total fixed: ' + (authFix+cet6Fix+gkFix+deriveFix+jsonDictFix+manualFix) + '/' + entries.length);
  console.log('');
  if (needMoreList.length > 0) {
    console.log('Need manual fixes:');
    for (const w of needMoreList) console.log('  ' + w);
  }
}

main();
