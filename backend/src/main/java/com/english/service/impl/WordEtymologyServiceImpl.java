package com.english.service.impl;

import com.english.service.WordEtymologyService;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 单词词源/词根词缀分析服务实现
 * 内置常见前缀、后缀、词根数据库
 */
@Service
public class WordEtymologyServiceImpl implements WordEtymologyService {

    // 常见前缀：前缀 -> [含义, 示例词]
    private static final Map<String, String[]> PREFIXES = new LinkedHashMap<>();
    // 常见后缀：后缀 -> [含义, 词性, 示例词]
    private static final Map<String, String[]> SUFFIXES = new LinkedHashMap<>();
    // 常见词根：词根 -> [含义, 示例词]
    private static final Map<String, String[]> ROOTS = new LinkedHashMap<>();

    static {
        // ========== 前缀 ==========
        PREFIXES.put("un", new String[]{"不，非，相反", "unhappy, undo, unusual"});
        PREFIXES.put("re", new String[]{"再，重新，回", "return, review, rebuild"});
        PREFIXES.put("pre", new String[]{"前，预先", "preview, prepare, predict"});
        PREFIXES.put("dis", new String[]{"不，分离，相反", "disagree, disappear, discover"});
        PREFIXES.put("mis", new String[]{"错误，坏", "mistake, misunderstand, mislead"});
        PREFIXES.put("in", new String[]{"不，向内", "inactive, include, input"});
        PREFIXES.put("im", new String[]{"不，向内（b,m,p前）", "impossible, import, improve"});
        PREFIXES.put("il", new String[]{"不（l前）", "illegal, illogical"});
        PREFIXES.put("ir", new String[]{"不（r前）", "irregular, irresponsible"});
        PREFIXES.put("sub", new String[]{"下，次，副", "subway, submarine, subtitle"});
        PREFIXES.put("super", new String[]{"超，上", "superman, supermarket, supernatural"});
        PREFIXES.put("inter", new String[]{"在…之间，相互", "international, internet, interview"});
        PREFIXES.put("trans", new String[]{"跨越，转变", "transport, translate, transform"});
        PREFIXES.put("ex", new String[]{"出，外，前任", "export, exit, ex-president"});
        PREFIXES.put("anti", new String[]{"反对，抗", "antiwar, antibody, antivirus"});
        PREFIXES.put("auto", new String[]{"自己，自动", "automatic, automobile, autobiography"});
        PREFIXES.put("bi", new String[]{"二，双", "bicycle, bilingual, biweekly"});
        PREFIXES.put("co", new String[]{"共同，一起", "cooperate, coexist, coauthor"});
        PREFIXES.put("de", new String[]{"向下，去除，相反", "decrease, decode, depart"});
        PREFIXES.put("en", new String[]{"使…，放进", "enable, encourage, enlarge"});
        PREFIXES.put("em", new String[]{"使…（b,m,p前）", "embrace, employ, empower"});
        PREFIXES.put("fore", new String[]{"前，预先", "forecast, forehead, foresee"});
        PREFIXES.put("micro", new String[]{"微小", "microscope, microphone, microwave"});
        PREFIXES.put("mini", new String[]{"小", "minibus, miniskirt, minimum"});
        PREFIXES.put("mono", new String[]{"单一", "monologue, monopoly, monotone"});
        PREFIXES.put("multi", new String[]{"多", "multimedia, multiply, multinational"});
        PREFIXES.put("non", new String[]{"非，不", "nonsense, nonstop, nonprofit"});
        PREFIXES.put("out", new String[]{"超过，外", "outdoor, output, outstanding"});
        PREFIXES.put("over", new String[]{"过度，在上", "overcome, overlook, overseas"});
        PREFIXES.put("post", new String[]{"后", "postwar, postpone, postgraduate"});
        PREFIXES.put("pro", new String[]{"向前，支持，代", "progress, promote, produce"});
        PREFIXES.put("semi", new String[]{"半", "semicircle, semifinal, semiconductor"});
        PREFIXES.put("tele", new String[]{"远", "telephone, television, telescope"});
        PREFIXES.put("tri", new String[]{"三", "triangle, tricycle, triple"});
        PREFIXES.put("ultra", new String[]{"超，极端", "ultraviolet, ultrasonic, ultra-modern"});
        PREFIXES.put("under", new String[]{"下，不足", "underground, understand, underestimate"});
        PREFIXES.put("uni", new String[]{"单一", "uniform, unique, universe"});
        PREFIXES.put("counter", new String[]{"反对，对应", "counteract, counterpart, counterattack"});
        PREFIXES.put("extra", new String[]{"额外，超出", "extraordinary, extracurricular, extrasensory"});
        PREFIXES.put("neo", new String[]{"新", "neoclassical, neologism, neonatal"});
        PREFIXES.put("poly", new String[]{"多", "polygon, polyglot, polytechnic"});
        PREFIXES.put("circum", new String[]{"环绕", "circumstance, circumference, circumvent"});
        PREFIXES.put("bene", new String[]{"善，好", "benefit, benevolent, beneficial"});
        PREFIXES.put("mal", new String[]{"恶，坏", "malfunction, malicious, malnutrition"});
        PREFIXES.put("con", new String[]{"共同，一起", "connect, contain, conclude"});
        PREFIXES.put("com", new String[]{"共同（b,m,p前）", "combine, compare, compete"});
        PREFIXES.put("cor", new String[]{"共同（r前）", "correct, correspond, corrupt"});
        PREFIXES.put("col", new String[]{"共同（l前）", "collaborate, collapse, collect"});
        PREFIXES.put("syn", new String[]{"共同，同步", "syntax, synthesis, sympathy"});
        PREFIXES.put("sym", new String[]{"共同（b,m,p前）", "symbol, symmetry, sympathy"});
        PREFIXES.put("sys", new String[]{"共同（s前）", "system, systematic"});
        PREFIXES.put("a", new String[]{"不，无，在", "atypical, anonymous, aboard"});
        PREFIXES.put("ab", new String[]{"离开，相反", "abnormal, absent, abstract"});
        PREFIXES.put("ad", new String[]{"向，加强", "advance, adapt, admit"});
        PREFIXES.put("per", new String[]{"贯穿，每", "perfect, perform, permanent"});
        PREFIXES.put("para", new String[]{"旁，类似，保护", "paragraph, parallel, parachute"});
        PREFIXES.put("dia", new String[]{"通过，相对", "dialogue, diagram, diameter"});
        PREFIXES.put("hyper", new String[]{"超过，过度", "hyperactive, hyperbole, hypertension"});
        PREFIXES.put("hypo", new String[]{"下，低", "hypothesis, hypocrite, hypothermia"});

        // ========== 后缀 ==========
        SUFFIXES.put("tion", new String[]{"行为，状态，结果", "名词", "action, nation, education"});
        SUFFIXES.put("sion", new String[]{"行为，状态", "名词", "decision, vision, conclusion"});
        SUFFIXES.put("ment", new String[]{"行为，结果，状态", "名词", "development, movement, agreement"});
        SUFFIXES.put("ness", new String[]{"性质，状态", "名词", "happiness, kindness, darkness"});
        SUFFIXES.put("ity", new String[]{"性质，状态", "名词", "ability, reality, activity"});
        SUFFIXES.put("ty", new String[]{"性质，状态", "名词", "safety, loyalty, certainty"});
        SUFFIXES.put("ance", new String[]{"行为，状态", "名词", "importance, distance, performance"});
        SUFFIXES.put("ence", new String[]{"行为，状态", "名词", "difference, confidence, existence"});
        SUFFIXES.put("er", new String[]{"做…的人/物", "名词", "teacher, worker, computer"});
        SUFFIXES.put("or", new String[]{"做…的人/物", "名词", "actor, doctor, inventor"});
        SUFFIXES.put("ist", new String[]{"从事…的人", "名词", "scientist, artist, pianist"});
        SUFFIXES.put("ism", new String[]{"主义，学说，行为", "名词", "capitalism, tourism, criticism"});
        SUFFIXES.put("ship", new String[]{"身份，关系，状态", "名词", "friendship, leadership, membership"});
        SUFFIXES.put("dom", new String[]{"领域，状态", "名词", "freedom, kingdom, wisdom"});
        SUFFIXES.put("hood", new String[]{"身份，状态，时期", "名词", "childhood, neighborhood, brotherhood"});
        SUFFIXES.put("age", new String[]{"行为，结果，集合", "名词", "storage, marriage, package"});
        SUFFIXES.put("ure", new String[]{"行为，结果", "名词", "pressure, failure, structure"});
        SUFFIXES.put("al", new String[]{"行为，人", "名词", "arrival, refusal, criminal"});
        SUFFIXES.put("ful", new String[]{"充满…的", "形容词", "beautiful, helpful, careful"});
        SUFFIXES.put("less", new String[]{"没有…的", "形容词", "homeless, careless, useless"});
        SUFFIXES.put("able", new String[]{"能够…的", "形容词", "comfortable, readable, available"});
        SUFFIXES.put("ible", new String[]{"能够…的", "形容词", "possible, visible, flexible"});
        SUFFIXES.put("ous", new String[]{"具有…的", "形容词", "famous, dangerous, nervous"});
        SUFFIXES.put("ive", new String[]{"有…性质的", "形容词", "active, creative, attractive"});
        SUFFIXES.put("ic", new String[]{"…的，与…有关的", "形容词", "historic, economic, scientific"});
        SUFFIXES.put("ical", new String[]{"…的", "形容词", "historical, political, practical"});
        SUFFIXES.put("al", new String[]{"…的", "形容词", "national, natural, personal"});
        SUFFIXES.put("ary", new String[]{"与…有关的", "形容词", "necessary, ordinary, primary"});
        SUFFIXES.put("ory", new String[]{"…的，场所", "形容词/名词", "history, factory, memory"});
        SUFFIXES.put("ly", new String[]{"…地", "副词", "quickly, happily, carefully"});
        SUFFIXES.put("ize", new String[]{"使…化", "动词", "realize, organize, modernize"});
        SUFFIXES.put("ise", new String[]{"使…化（英）", "动词", "realise, organise, modernise"});
        SUFFIXES.put("ify", new String[]{"使…化", "动词", "simplify, beautify, clarify"});
        SUFFIXES.put("en", new String[]{"使…", "动词", "widen, strengthen, darken"});
        SUFFIXES.put("ate", new String[]{"使…，做", "动词", "create, operate, graduate"});
        SUFFIXES.put("ward", new String[]{"向…方向", "副词/形容词", "forward, backward, upward"});
        SUFFIXES.put("wise", new String[]{"方式，方向", "副词", "otherwise, clockwise, likewise"});
        SUFFIXES.put("fy", new String[]{"使…化", "动词", "satisfy, terrify, identify"});
        SUFFIXES.put("esque", new String[]{"…风格的", "形容词", "picturesque, grotesque, statuesque"});
        SUFFIXES.put("most", new String[]{"最…的", "形容词", "foremost, utmost, topmost"});
        SUFFIXES.put("dom", new String[]{"状态，领域", "名词", "boredom, freedom, kingdom"});
        SUFFIXES.put("th", new String[]{"状态，过程", "名词", "growth, strength, warmth"});
        SUFFIXES.put("ant", new String[]{"…的人/物", "名词/形容词", "assistant, important, pleasant"});
        SUFFIXES.put("ent", new String[]{"…的人/物", "名词/形容词", "student, different, excellent"});

        // ========== 词根 ==========
        ROOTS.put("spect", new String[]{"看", "inspect, respect, spectator, prospect"});
        ROOTS.put("dict", new String[]{"说，言", "dictionary, predict, contradict, dictate"});
        ROOTS.put("port", new String[]{"拿，运", "transport, import, export, support"});
        ROOTS.put("duct", new String[]{"引导，带", "conduct, produce, reduce, introduce"});
        ROOTS.put("struct", new String[]{"建造", "structure, construct, destroy, instruct"});
        ROOTS.put("ject", new String[]{"投，掷", "project, reject, inject, subject"});
        ROOTS.put("mit", new String[]{"送，发", "transmit, submit, admit, permit"});
        ROOTS.put("miss", new String[]{"送，发", "mission, dismiss, permission, missile"});
        ROOTS.put("ven", new String[]{"来", "prevent, event, invent, convention"});
        ROOTS.put("vent", new String[]{"来", "adventure, prevent, invent, convention"});
        ROOTS.put("vis", new String[]{"看", "visible, vision, visit, television"});
        ROOTS.put("vid", new String[]{"看", "video, evident, provide, divide"});
        ROOTS.put("aud", new String[]{"听", "audio, audience, audition, auditorium"});
        ROOTS.put("scrib", new String[]{"写", "describe, subscribe, prescribe, scribble"});
        ROOTS.put("script", new String[]{"写", "manuscript, scripture, transcript, prescription"});
        ROOTS.put("graph", new String[]{"写，画", "photograph, geography, biography, autograph"});
        ROOTS.put("gram", new String[]{"写，画", "program, telegram, diagram, grammar"});
        ROOTS.put("log", new String[]{"说，学科", "dialogue, biology, psychology, technology"});
        ROOTS.put("ology", new String[]{"…学", "biology, psychology, technology, sociology"});
        ROOTS.put("phon", new String[]{"声音", "telephone, microphone, symphony, phonetic"});
        ROOTS.put("scope", new String[]{"看，观察", "telescope, microscope, horoscope, scope"});
        ROOTS.put("meter", new String[]{"测量", "thermometer, diameter, kilometer, barometer"});
        ROOTS.put("chron", new String[]{"时间", "chronic, chronicle, synchronize, chronology"});
        ROOTS.put("bio", new String[]{"生命", "biology, biography, antibiotic, biodiversity"});
        ROOTS.put("geo", new String[]{"地球，土地", "geography, geology, geometry, geopolitics"});
        ROOTS.put("path", new String[]{"感情，疾病", "sympathy, pathology, empathy, apathy"});
        ROOTS.put("psych", new String[]{"心灵，精神", "psychology, psychiatry, psychic, psycho"});
        ROOTS.put("phil", new String[]{"爱", "philosophy, philanthropy, bibliophile, philharmonic"});
        ROOTS.put("tele", new String[]{"远", "telephone, television, telescope, telegram"});
        ROOTS.put("auto", new String[]{"自己", "automatic, automobile, autonomy, autobiography"});
        ROOTS.put("man", new String[]{"手", "manual, manage, manufacture, manuscript"});
        ROOTS.put("ped", new String[]{"脚，儿童", "pedestrian, pedal, pedestal, pediatric"});
        ROOTS.put("pod", new String[]{"脚", "tripod, podiatry, gastropod, arthropod"});
        ROOTS.put("cap", new String[]{"头，拿", "capital, captain, capture, capable"});
        ROOTS.put("cept", new String[]{"拿，取", "accept, concept, except, intercept"});
        ROOTS.put("ceive", new String[]{"拿，取", "receive, deceive, perceive, conceive"});
        ROOTS.put("cip", new String[]{"拿，取", "participate, anticipate, recipient, municipal"});
        ROOTS.put("tract", new String[]{"拉，拖", "attract, contract, extract, distract"});
        ROOTS.put("duc", new String[]{"引导", "educate, produce, reduce, introduce"});
        ROOTS.put("fer", new String[]{"带来，拿", "transfer, refer, differ, offer"});
        ROOTS.put("lat", new String[]{"带来，拿", "translate, relate, circulate, legislate"});
        ROOTS.put("pose", new String[]{"放置", "compose, expose, propose, dispose"});
        ROOTS.put("pon", new String[]{"放置", "component, opponent, postpone, exponent"});
        ROOTS.put("pos", new String[]{"放置", "position, deposit, compose, oppose"});
        ROOTS.put("sit", new String[]{"坐", "position, sit, site, situate"});
        ROOTS.put("sid", new String[]{"坐", "preside, reside, president, dissident"});
        ROOTS.put("sed", new String[]{"坐", "sedentary, sediment, supersede, sedate"});
        ROOTS.put("sta", new String[]{"站立", "station, stable, state, statue"});
        ROOTS.put("stit", new String[]{"站立，建立", "constitute, institute, substitute, restitution"});
        ROOTS.put("sist", new String[]{"站立", "resist, consist, assist, persist"});
        ROOTS.put("cur", new String[]{"跑，发生", "current, occur, recur, cursor"});
        ROOTS.put("curs", new String[]{"跑", "cursor, excursion, precursor, cursive"});
        ROOTS.put("cour", new String[]{"跑", "course, discourse, courier, recourse"});
        ROOTS.put("grad", new String[]{"步，级", "grade, graduate, gradual, degrade"});
        ROOTS.put("gress", new String[]{"走，步", "progress, congress, aggressive, digress"});
        ROOTS.put("ced", new String[]{"走，让步", "proceed, precede, recede, exceed"});
        ROOTS.put("ceed", new String[]{"走", "proceed, succeed, exceed, precede"});
        ROOTS.put("cess", new String[]{"走", "process, access, success, recess"});
        ROOTS.put("vad", new String[]{"走", "invade, evade, pervade, wade"});
        ROOTS.put("vas", new String[]{"走", "invasion, evasion, pervasive, vase"});
        ROOTS.put("mot", new String[]{"移动", "motion, motor, motivate, remote"});
        ROOTS.put("mob", new String[]{"移动", "mobile, automobile, mobilize, immobile"});
        ROOTS.put("mov", new String[]{"移动", "move, movie, remove, movement"});
        ROOTS.put("rupt", new String[]{"破裂", "interrupt, corrupt, erupt, bankrupt"});
        ROOTS.put("fract", new String[]{"破裂", "fraction, fracture, refract, infraction"});
        ROOTS.put("frag", new String[]{"破裂", "fragment, fragile, fragrance, suffrage"});
        ROOTS.put("sect", new String[]{"切割", "section, insect, dissect, bisect"});
        ROOTS.put("cis", new String[]{"切割", "decision, precision, incision, scissors"});
        ROOTS.put("tom", new String[]{"切割", "anatomy, atom, epitome, dichotomy"});
        ROOTS.put("tend", new String[]{"伸展，倾向", "extend, intend, attend, pretend"});
        ROOTS.put("tens", new String[]{"伸展", "tension, intense, extensive, tensile"});
        ROOTS.put("tent", new String[]{"伸展", "tent, extent, content, attention"});
        ROOTS.put("vers", new String[]{"转", "reverse, universe, diverse, conversation"});
        ROOTS.put("vert", new String[]{"转", "convert, invert, divert, advert"});
        ROOTS.put("volv", new String[]{"转，卷", "involve, evolve, revolve, devolve"});
        ROOTS.put("volut", new String[]{"转，卷", "evolution, revolution, convolution, volution"});
        ROOTS.put("rot", new String[]{"旋转", "rotate, rotary, rotation, rotor"});
        ROOTS.put("tour", new String[]{"转，迂回", "tour, tournament, contour, detour"});
        ROOTS.put("tort", new String[]{"扭曲", "torture, distort, extort, tortoise"});
        ROOTS.put("tors", new String[]{"扭曲", "torsion, torso, distort, extort"});
        ROOTS.put("ply", new String[]{"折叠，填满", "apply, reply, supply, multiply"});
        ROOTS.put("plic", new String[]{"折叠", "complicate, duplicate, explicit, implicit"});
        ROOTS.put("plex", new String[]{"折叠", "complex, perplex, duplex, multiplex"});
        ROOTS.put("fold", new String[]{"折叠", "unfold, manifold, twofold, hold"});
        ROOTS.put("form", new String[]{"形状，形式", "reform, inform, perform, transform"});
        ROOTS.put("figur", new String[]{"形状，形式", "figure, configure, figurative, effigy"});
        ROOTS.put("fact", new String[]{"做，制造", "factory, manufacture, fact, factor"});
        ROOTS.put("fect", new String[]{"做，制造", "effect, perfect, affect, infect"});
        ROOTS.put("fic", new String[]{"做，制造", "office, sacrifice, efficient, proficient"});
        ROOTS.put("fit", new String[]{"做，制造", "benefit, profit, outfit, fit"});
        ROOTS.put("feat", new String[]{"做，制造", "feature, defeat, feat, forfeit"});
        ROOTS.put("ag", new String[]{"做，驱动", "agent, agenda, agile, navigate"});
        ROOTS.put("act", new String[]{"做，行动", "action, active, react, interact"});
        ROOTS.put("fac", new String[]{"做，制造", "face, facility, factor, faculty"});
    }

    @Override
    public Map<String, Object> analyze(String word) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (word == null || word.trim().isEmpty()) {
            result.put("word", "");
            result.put("prefixes", new ArrayList<>());
            result.put("suffixes", new ArrayList<>());
            result.put("roots", new ArrayList<>());
            result.put("mnemonics", new ArrayList<>());
            result.put("partOfSpeechChanges", new HashMap<>());
            return result;
        }

        word = word.trim().toLowerCase();
        result.put("word", word);
        result.put("prefixes", extractPrefixes(word));
        result.put("suffixes", extractSuffixes(word));
        result.put("roots", extractRoots(word));
        result.put("mnemonics", generateMnemonics(word));
        result.put("partOfSpeechChanges", getPartOfSpeechChanges(word));
        return result;
    }

    @Override
    public List<Map<String, String>> extractPrefixes(String word) {
        List<Map<String, String>> result = new ArrayList<>();
        if (word == null || word.length() < 3) return result;

        // 按前缀长度降序匹配，优先匹配更长的前缀
        List<String> sortedPrefixes = new ArrayList<>(PREFIXES.keySet());
        sortedPrefixes.sort((a, b) -> b.length() - a.length());

        for (String prefix : sortedPrefixes) {
            if (word.startsWith(prefix) && word.length() > prefix.length() + 1) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("prefix", prefix);
                item.put("meaning", PREFIXES.get(prefix)[0]);
                item.put("examples", PREFIXES.get(prefix)[1]);
                result.add(item);
                break; // 只匹配一个前缀
            }
        }
        return result;
    }

    @Override
    public List<Map<String, String>> extractSuffixes(String word) {
        List<Map<String, String>> result = new ArrayList<>();
        if (word == null || word.length() < 4) return result;

        // 按后缀长度降序匹配
        List<String> sortedSuffixes = new ArrayList<>(SUFFIXES.keySet());
        sortedSuffixes.sort((a, b) -> b.length() - a.length());

        for (String suffix : sortedSuffixes) {
            if (word.endsWith(suffix) && word.length() > suffix.length() + 1) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("suffix", suffix);
                item.put("meaning", SUFFIXES.get(suffix)[0]);
                item.put("partOfSpeech", SUFFIXES.get(suffix)[1]);
                item.put("examples", SUFFIXES.get(suffix)[2]);
                result.add(item);
                break; // 只匹配一个后缀
            }
        }
        return result;
    }

    @Override
    public List<Map<String, String>> extractRoots(String word) {
        List<Map<String, String>> result = new ArrayList<>();
        if (word == null || word.length() < 3) return result;

        // 去掉前后缀后再匹配词根
        String core = word;
        List<Map<String, String>> prefixes = extractPrefixes(word);
        if (!prefixes.isEmpty()) {
            core = core.substring(prefixes.get(0).get("prefix").length());
        }
        List<Map<String, String>> suffixes = extractSuffixes(word);
        if (!suffixes.isEmpty()) {
            core = core.substring(0, core.length() - suffixes.get(0).get("suffix").length());
        }

        // 在原词和词根中匹配
        for (Map.Entry<String, String[]> entry : ROOTS.entrySet()) {
            String root = entry.getKey();
            if (word.contains(root) || core.contains(root)) {
                Map<String, String> item = new LinkedHashMap<>();
                item.put("root", root);
                item.put("meaning", entry.getValue()[0]);
                item.put("examples", entry.getValue()[1]);
                result.add(item);
            }
        }

        // 最多返回3个词根
        if (result.size() > 3) {
            result = result.subList(0, 3);
        }
        return result;
    }

    @Override
    public List<String> generateMnemonics(String word) {
        List<String> mnemonics = new ArrayList<>();
        if (word == null || word.trim().isEmpty()) return mnemonics;

        word = word.trim().toLowerCase();
        List<Map<String, String>> prefixes = extractPrefixes(word);
        List<Map<String, String>> suffixes = extractSuffixes(word);
        List<Map<String, String>> roots = extractRoots(word);

        StringBuilder sb = new StringBuilder();
        sb.append("【词根词缀拆解】");

        if (!prefixes.isEmpty()) {
            Map<String, String> p = prefixes.get(0);
            sb.append("\n前缀 ").append(p.get("prefix")).append("- = ").append(p.get("meaning"));
        }
        if (!roots.isEmpty()) {
            Map<String, String> r = roots.get(0);
            sb.append("\n词根 ").append(r.get("root")).append(" = ").append(r.get("meaning"));
        }
        if (!suffixes.isEmpty()) {
            Map<String, String> s = suffixes.get(0);
            sb.append("\n后缀 -").append(s.get("suffix")).append(" = ").append(s.get("meaning"))
              .append("（").append(s.get("partOfSpeech")).append("）");
        }

        if (prefixes.isEmpty() && roots.isEmpty() && suffixes.isEmpty()) {
            sb.append("\n该词为基础词，暂无明显词根词缀拆分。");
        } else {
            // 生成组合记忆
            StringBuilder meaning = new StringBuilder("\n\n【联想记忆】");
            if (!prefixes.isEmpty() && !roots.isEmpty()) {
                meaning.append("\n").append(prefixes.get(0).get("meaning"))
                       .append(" + ").append(roots.get(0).get("meaning"));
                if (!suffixes.isEmpty()) {
                    meaning.append(" + ").append(suffixes.get(0).get("meaning"));
                }
                meaning.append(" → ").append(word);
            }
            sb.append(meaning);
        }

        mnemonics.add(sb.toString());

        // 添加同根词联想
        if (!roots.isEmpty()) {
            StringBuilder related = new StringBuilder("【同根词联想】");
            related.append("\n").append(roots.get(0).get("examples"));
            mnemonics.add(related.toString());
        }

        // 添加同前缀词联想
        if (!prefixes.isEmpty()) {
            StringBuilder related = new StringBuilder("【同前缀词联想】");
            related.append("\n").append(prefixes.get(0).get("examples"));
            mnemonics.add(related.toString());
        }

        return mnemonics;
    }

    @Override
    public Map<String, String> getPartOfSpeechChanges(String word) {
        Map<String, String> changes = new LinkedHashMap<>();
        if (word == null || word.trim().isEmpty()) return changes;

        word = word.trim().toLowerCase();
        List<Map<String, String>> suffixes = extractSuffixes(word);

        if (!suffixes.isEmpty()) {
            String pos = suffixes.get(0).get("partOfSpeech");
            changes.put("current", pos);

            // 根据当前词性推测其他词性变化
            String base = word;
            if (word.endsWith("tion") || word.endsWith("sion")) {
                base = word.substring(0, word.length() - 4);
                changes.put("verb", base + "e");
                changes.put("adjective", base + "ive");
            } else if (word.endsWith("ment")) {
                base = word.substring(0, word.length() - 4);
                changes.put("verb", base);
                changes.put("adjective", base + "able");
            } else if (word.endsWith("ness")) {
                base = word.substring(0, word.length() - 4);
                changes.put("adjective", base);
                changes.put("adverb", base + "ly");
            } else if (word.endsWith("ity")) {
                base = word.substring(0, word.length() - 3);
                changes.put("adjective", base + "e");
                changes.put("adverb", base + "ely");
            } else if (word.endsWith("able") || word.endsWith("ible")) {
                changes.put("adverb", word + "ly");
                changes.put("noun", word + "ness");
            } else if (word.endsWith("ful")) {
                base = word.substring(0, word.length() - 3);
                changes.put("noun", base);
                changes.put("adverb", word + "ly");
                changes.put("antonym", base + "less");
            } else if (word.endsWith("ive")) {
                base = word.substring(0, word.length() - 3);
                changes.put("verb", base + "e");
                changes.put("noun", base + "ion");
            } else if (word.endsWith("ly")) {
                base = word.substring(0, word.length() - 2);
                changes.put("adjective", base);
            } else if (word.endsWith("er") || word.endsWith("or")) {
                base = word.substring(0, word.length() - 2);
                changes.put("verb", base);
                changes.put("noun_action", base + "ion");
            } else if (word.endsWith("ist")) {
                base = word.substring(0, word.length() - 3);
                changes.put("noun_field", base + "y");
                changes.put("adjective", base + "ic");
            }
        } else {
            // 没有明显后缀，可能是动词或基础词
            changes.put("current", "动词/基础词");
            changes.put("noun", word + "tion / " + word + "ment");
            changes.put("adjective", word + "able / " + word + "ive");
            changes.put("adverb", word + "ly");
        }

        return changes;
    }
}
