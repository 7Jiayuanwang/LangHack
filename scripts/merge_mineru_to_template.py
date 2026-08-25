#!/usr/bin/env python3
"""Merge MinerU exam/answer output and convert it to the LangHack import template."""
import argparse, html, json, re, zipfile
from pathlib import Path

def read_source(path):
    p = Path(path)
    if p.is_dir():
        for name in ("full.md", "content_list.json", "layout.json"):
            q = p / name
            if q.exists(): return read_source(q)
        return "\n".join(read_source(x) for x in sorted(p.rglob("*.json")))
    if p.suffix.lower() == ".zip":
        with zipfile.ZipFile(p) as z:
            names = z.namelist()
            name = next((n for n in names if n.endswith("full.md")), next((n for n in names if n.endswith("content_list.json")), None))
            if not name: raise ValueError("ZIP 中没有 full.md 或 content_list.json")
            raw = z.read(name).decode("utf-8", "ignore")
            return raw if name.endswith(".md") else json.loads(raw)
    if p.suffix.lower() in (".md", ".txt"): return p.read_text(encoding="utf-8", errors="ignore")
    return json.loads(p.read_text(encoding="utf-8"))

def flatten(value):
    out=[]
    if isinstance(value, str): return [html.unescape(value)]
    if isinstance(value, list):
        for x in value: out += flatten(x)
    elif isinstance(value, dict):
        for k in ("text", "table_body", "content", "spans", "blocks", "pages"):
            if k in value: out += flatten(value[k])
    return out

def text_of(data): return "\n".join(x.strip() for x in flatten(data) if x and x.strip())

def answers(text):
    found={}
    for n,a in re.findall(r"(?<!\d)(\d{1,3})\s*[\.、:：]?\s*([A-D])(?=\s|[\.、,，;；]|$)", text, re.I): found[int(n)] = a.upper()
    return found

def options(stem):
    marks=list(re.finditer(r"(?:^|\s)([A-D])\s*[\)．.、:：]", stem, re.I))
    if len(marks)<2: return [], stem.strip()
    first=marks[0].start(); clean=stem[:first].strip()
    vals=[]
    for i,m in enumerate(marks):
        end=marks[i+1].start() if i+1<len(marks) else len(stem)
        vals.append(re.sub(r"^\s*[A-D]\s*[\)．.、:：]\s*", "", stem[m.start():end], flags=re.I).strip(" ."))
    return vals, clean

def build(data, ans, title, date, duration):
    raw=text_of(data); qs=[]
    # numbered multiple choice blocks (MinerU often puts all options in one block)
    for m in re.finditer(r"(?ms)(?<!\d)(\d{1,3})[\.、]\s*(.*?)(?=\n\s*\d{1,3}[\.、]\s|\Z)", raw):
        num=int(m.group(1)); body=re.sub(r"\s+", " ", m.group(2)).strip()
        if "Directions:" in body or "敬告考生" in body: continue
        opts,stem=options(body)
        if len(opts)>=2:
            stem=re.sub(r"\s*(?:Questions?\s+\d+.*)$", "", stem, flags=re.I).strip()
            opts=[re.sub(r"\s*(?:Questions?\s+\d+.*)$", "", x, flags=re.I).strip() for x in opts]
            qs.append({"number":num,"type":"single_choice","section":"听力理解" if num <= 25 else "阅读理解","stem":stem,"options":opts,"answer":ans.get(num,""),"analysis":"答案来自 MinerU 答案文件；原始资料未提供逐题解析。"})
    # Preserve non-numbered sections as useful template items.
    low=raw.lower()
    trans=re.search(r"翻译[：:]?\s*(.*?)(?=作文|写作|\Z)", raw, re.S)
    if trans: qs.append({"type":"translation","section":"翻译","stem":"请翻译以下内容","passage":trans.group(1).strip(),"referenceAnswer":"","analysis":""})
    writing=re.search(r"(?:作文|写作)[：:]?\s*(.*)$", raw, re.S)
    if writing: qs.append({"type":"writing","section":"写作","stem":writing.group(1).strip(),"referenceAnswer":"","analysis":""})
    if not qs: raise ValueError("未识别到带 A-D 选项的题目，请检查 MinerU 输出或 OCR 质量")
    return {"title":title,"date":date,"duration":duration,"questions":qs}

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument("--exam", required=True); ap.add_argument("--answer")
    ap.add_argument("--output", required=True); ap.add_argument("--title", default="四六级真题")
    ap.add_argument("--date", default=""); ap.add_argument("--duration", type=int, default=120)
    ap.add_argument("--allow-missing", action="store_true")
    a=ap.parse_args(); exam=text_of(read_source(a.exam)); answer=text_of(read_source(a.answer)) if a.answer else exam
    amap=answers(answer)
    result=build(exam, amap, a.title, a.date, a.duration)
    missing=[i+1 for i,q in enumerate(result["questions"]) if q["type"]=="single_choice" and not q["answer"]]
    if missing and not a.allow_missing: raise SystemExit(f"缺少答案题号（示例）：{missing[:20]}；使用 --allow-missing 可生成待校对文件")
    Path(a.output).parent.mkdir(parents=True, exist_ok=True); Path(a.output).write_text(json.dumps(result,ensure_ascii=False,indent=2),encoding="utf-8")
    print(f"已生成 {a.output}，共 {len(result['questions'])} 题，匹配答案 {len(amap)} 个")
if __name__ == "__main__": main()
