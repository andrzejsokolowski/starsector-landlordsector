"""Merge reader*.jsonl: coverage check, a review table, and (with --write) the new conditions.json."""
import glob, json, os, sys

S = os.path.dirname(os.path.abspath(__file__))
OUT_JSON = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "data", "config", "landlordsector", "conditions.json")
CODED = {"habitable", "mild_climate", "pollution", "pirate_activity", "pather_cells"}
overrides = {}
if os.path.isfile(os.path.join(S, "overrides.json")):
    overrides = json.load(open(os.path.join(S, "overrides.json"), encoding="utf-8"))

_idx = os.path.join(S, "cond_index.json")
index = {r["id"]: r for r in json.load(open(_idx, encoding="utf-8"))} if os.path.isfile(_idx) else {}
got = {}
bad_lines = 0
for path in sorted(glob.glob(os.path.join(S, "reader*.jsonl"))):
    for line in open(path, encoding="utf-8"):
        line = line.strip()
        if not line:
            continue
        try:
            o = json.loads(line)
            got[o["id"]] = o
        except Exception:
            bad_lines += 1
missing = sorted(set(index) - set(got))
print("readers:", len(got), "of", len(index), "| bad lines:", bad_lines, "| missing:", missing)


def clamp(v, lo, hi):
    return max(lo, min(hi, v))


def final(o):
    p = dict(o.get("proposal") or {})
    p = {"price": float(p.get("price", 1) or 1), "comfort": float(p.get("comfort", 0) or 0),
         "happiness": float(p.get("happiness", 0) or 0), "security": float(p.get("security", 0) or 0)}
    if o["id"] in overrides:
        p.update({k: v for k, v in overrides[o["id"]].items() if k in ("price", "comfort", "happiness", "security")})
    p["price"] = round(clamp(p["price"], 0.5, 1.5), 2)
    p["comfort"] = round(clamp(p["comfort"], -20, 20))
    p["happiness"] = round(clamp(p["happiness"], -20, 20))
    p["security"] = round(clamp(p["security"], -25, 25))
    return p


rows = []
for cid, o in got.items():
    if cid in CODED:
        continue
    p = final(o)
    nonzero = p["price"] != 1 or p["comfort"] or p["happiness"] or p["security"]
    rows.append((o.get("mod", ""), cid, o, p, nonzero))
rows.sort(key=lambda r: (r[0] != "vanilla", r[0].lower(), r[1]))

if "--table" in sys.argv:
    for mod, cid, o, p, nz in rows:
        if not nz and "--all" not in sys.argv:
            continue
        print("%-11.11s %-34.34s P%.2f C%+d H%+d S%+d st=%s hz=%s [%s] %s" % (
            mod, cid, p["price"], p["comfort"], p["happiness"], p["security"], o.get("stability"), o.get("hazard"),
            (o.get("confidence") or "?")[0], (o.get("effects") or "")[:150]))
    print("non-zero:", sum(1 for r in rows if r[4]), "zero:", sum(1 for r in rows if not r[4]))

if "--write" in sys.argv:
    out = ['{',
           '\t# What market conditions do to homes there, on top of the built-in rule (size, stability, hazard,',
           '\t# habitable, mild climate, pollution, pirate activity, Pather cells). Built from each condition\'s',
           '\t# real effect in the game (its code), 2026-11-12. A condition\'s own stability and hazard changes are',
           '\t# already counted through the market\'s stability and hazard, so they are not repeated here.',
           '\t# price multiplies the price (and the location grade). comfort, happiness and security are added',
           '\t# (0 to 100 scale). A condition from a mod that is not installed never appears on a market, so its',
           '\t# line does nothing. Other mods can add lines with their own conditions.',
           '\t"conditions":{']
    entries = [r for r in rows if r[4]]
    last_mod = None
    for i, (mod, cid, o, p, _) in enumerate(entries):
        if mod != last_mod:
            out.append('\t\t# ---- %s ----' % mod)
            last_mod = mod
        eff = (o.get("effects") or "").replace("\n", " ")
        if len(eff) > 150:
            eff = eff[:147].rsplit(" ", 1)[0] + "..."
        out.append('\t\t# %s: %s' % (o.get("name") or cid, eff))
        parts = []
        if p["price"] != 1:
            parts.append('"price":%s' % p["price"])
        for k in ("comfort", "happiness", "security"):
            if p[k]:
                parts.append('"%s":%d' % (k, p[k]))
        ov = overrides.get(cid, {})
        if ov.get("owners"):
            parts.append('"owners":[%s]' % ", ".join('"%s"' % f for f in ov["owners"]))
        if ov.get("ownersContain"):
            parts.append('"ownersContain":[%s]' % ", ".join('"%s"' % f for f in ov["ownersContain"]))
        if ov.get("others"):
            parts.append('"others":{%s}' % ", ".join('"%s":%s' % (k, v) for k, v in ov["others"].items()))
        out.append('\t\t"%s":{%s}%s' % (cid, ", ".join(parts), "," if i < len(entries) - 1 else ""))
    out += ['\t}', '}', '']
    text = "\n".join(out)
    text = text.encode("ascii", "replace").decode("ascii")
    open(OUT_JSON, "w", encoding="utf-8", newline="\n").write(text)
    print("wrote", len(entries), "entries to conditions.json")
