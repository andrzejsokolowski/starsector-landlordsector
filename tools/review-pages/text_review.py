"""Builds the Landlordsector text review page (text.html) from text_lines.json.

Andrzej (2026-11-16): every line Claude writes for the mod goes on a page with Line | When it is used | Accept |
Reject with comment. Add each new or reworded line to text_lines.json, run this, and republish text.html to the
same artifact URL (see the repo AGENTS.md).
"""
import json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, "..", ".."))
VERSION = json.loads(re.sub(r",(\s*[}\]])", r"\1", open(os.path.join(MOD, "mod_info.json"), encoding="utf-8").read()))["version"]


def main():
    data = json.load(open(os.path.join(HERE, "text_lines.json"), encoding="utf-8"))
    ids = [l["id"] for l in data["lines"]]
    assert len(ids) == len(set(ids)), "duplicate line id"
    page = {"version": VERSION, "lines": data["lines"]}
    tpl = open(os.path.join(HERE, "text.tpl.html"), encoding="utf-8").read()
    css = open(os.path.join(HERE, "common.css"), encoding="utf-8").read()
    js = open(os.path.join(HERE, "common.js"), encoding="utf-8").read()
    out = (tpl.replace("/*__CSS__*/", css).replace("/*__COMMON__*/", js)
           .replace("__VERSION__", VERSION)
           .replace("__DATA__", json.dumps(page, ensure_ascii=False).replace("</", "<\\/")))
    open(os.path.join(HERE, "text.html"), "w", encoding="utf-8", newline="\n").write(out)
    print(VERSION, len(ids), "lines")


if __name__ == "__main__":
    main()
