"""Builds the two Landlordsector review pages (traits, events) from the mod's current data files."""
import csv, json, os, re

MOD = r"C:\Programming\PersonalProjects\Starsector mods\Landlordsector"
CFG = os.path.join(MOD, "data", "config", "landlordsector")
CORE = r"D:\Games\StarSector\starsector-core\data\campaign\market_conditions.csv"
HERE = os.path.dirname(os.path.abspath(__file__))
VERSION = json.loads(re.sub(r",(\s*[}\]])", r"\1", open(os.path.join(MOD, "mod_info.json"), encoding="utf-8").read()))["version"]


def raw(name):
    return open(os.path.join(CFG, name), encoding="utf-8").read()


def load(name):
    text = "\n".join(l for l in raw(name).splitlines() if not l.strip().startswith("#"))
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", text))


tenant = load("tenant_traits.json")["traits"]
house = load("house_traits.json")["traits"]
ev_file = load("tenant_events.json")
events = ev_file["events"]
upgrades = load("upgrades.json")["upgrades"]
levels = {l["id"]: l for l in load("levels.json")["levels"]}
env = {s["id"]: s for s in load("environment.json")["scores"]}
worlds = load("worlds.json")["worlds"]
tiers = load("tiers.json")["tiers"]
TIER_NAMES = [t["name"].lower() for t in tiers]
TIER_BY_ID = {t["id"]: t["name"] for t in tiers}

cond_names = {}
with open(CORE, encoding="utf-8", errors="replace") as f:
    for row in csv.DictReader(f):
        if row.get("id"):
            cond_names[row["id"]] = row["name"]
pending = None
for line in raw("conditions.json").splitlines():
    m = re.match(r'^\s*#\s*([^:#]{2,40}):', line)
    if m:
        pending = m.group(1).strip()
        continue
    m = re.match(r'^\s*"([^"]+)":\{', line)
    if m and pending and m.group(1) not in cond_names:
        cond_names[m.group(1)] = pending
    pending = None


cond_names.update({"bbplus_sigmairradiated": "Sigma radiation", "jdp_volturnianLobsterPens": "Volturnian Lobster Pens", "rat_bionic_plantlife": "Bionic plantlife"})


def cname(c):
    return cond_names.get(c, c.replace("_", " "))


FACTIONS = {"pirates": "Pirates", "tritachyon": "Tri-Tachyon", "hegemony": "Hegemony", "luddic_path": "Luddic Path",
            "luddic_church": "Luddic Church", "independent": "Independent", "sindrian_diktat": "Sindrian Diktat",
            "persean": "Persean League", "player": "Your colonies"}
COLONY_TAGS = {"police": "Police or military buildings on the colony", "spying": "Spy buildings on the colony"}
LIKE, DISLIKE = 8, 15


def signed(v):
    r = round(v, 2)
    r = int(r) if r == int(r) else r
    return ("+" if r > 0 else "") + str(r)


def pct(v):
    return str(round(abs(v - 1) * 100))


def num(v):
    return int(v) if v == int(v) else v


tag_house = {}
for h in house:
    for t in h.get("tags", []):
        tag_house.setdefault(t, []).append(h)
tag_upg = {}
for u in upgrades:
    for t in u.get("tags") or []:
        tag_upg.setdefault(t, []).append(u)


def tag_things(tags):
    out = []
    for tag in tags:
        for h in tag_house.get(tag, []):
            item = [h["name"], h.get("good", True)]
            if item not in out:
                out.append(item)
        for u in tag_upg.get(tag, []):
            item = [u["name"] + " (renovation or furniture)", None]
            if item not in out:
                out.append(item)
        if tag in COLONY_TAGS:
            out.append([COLONY_TAGS[tag], None])
    return out


def level_line(k, m):
    """A tenant's multiplier on a home level or environment score."""
    if k == "commute":
        if m == 0:
            return ["Works from home: no commute", True]
        return ["A long commute hurts x" + str(num(m)), m < 1]
    name = (levels.get(k) or env.get(k) or {}).get("name", k).lower()
    if k == "air":
        name = "bad air"
    if m == 0:
        return ["Doesn't mind " + name, True]
    if m == 0.5:
        return [name.capitalize() + " hurts half as much", True]
    return [name.capitalize() + " hurts x" + str(num(m)), m < 1]


# ---------------- world data (grouped by the faction headers in worlds.json) ----------------
WORLD_NAMES = {"kantas_den": "Kanta's Den", "station_kapteyn": "Kapteyn Station", "eochu_bres": "Eochu Bres"}
world_faction = {}
group = "Other"
for line in raw("worlds.json").splitlines():
    m = re.match(r'^\s*#\s*-+\s*(.+?)\s*-+\s*$', line)
    if m:
        group = m.group(1)
        continue
    m = re.match(r'^\t\t"([a-z_]+)":\{', line)
    if m:
        world_faction[m.group(1)] = group


def wname(wid):
    return WORLD_NAMES.get(wid, wid.replace("_", " ").title())


house_world = {}  # house trait id -> [(world, mult)]
for wid, w in worlds.items():
    for tid, m in (w.get("traits") or {}).items():
        house_world.setdefault(tid, []).append((wname(wid), m))

# ---------------- tenant traits ----------------
ev_by_id = {e["id"]: e for e in events}
EV_NAMES = {}
out_t = []
for t in tenant:
    fx = []
    if t.get("happiness"):
        fx.append(["Happiness " + signed(t["happiness"]), t["happiness"] > 0])
    if t.get("health"):
        fx.append(["Health " + signed(t["health"]), t["health"] > 0])
    if t.get("healthCost"):
        c = t["healthCost"]
        fx.append(["Takes " + (pct(c) + "% less" if c < 1 else pct(c) + "% more") + " health damage", c < 1])
    if t.get("wealth"):
        fx.append(["Wealth " + signed(t["wealth"]) + " letter", t["wealth"] > 0])
    if t.get("wear", 1) != 1:
        fx.append(["Wears a home " + pct(t["wear"]) + "% " + ("slower" if t["wear"] < 1 else "faster"), t["wear"] < 1])
    for k, m in (t.get("levels") or {}).items():
        fx.append(level_line(k, m))
    if t.get("kids"):
        fx.append(["Has kids: kids' furniture counts for them", None])
    if t.get("loyal"):
        fx.append(["Never moves out on their own", True])
    if t.get("homeShift", 0) > 0:
        fx.append(["Wants a home one size bigger than they can comfortably pay for", False])
    if t.get("homeShift", 0) < 0:
        fx.append(["Happy with a home one size smaller than they can pay for", True])
    if t.get("criminal"):
        fx.append(["Criminal", False])
    ev = []
    for eid, m in (t.get("events") or {}).items():
        e = ev_by_id.get(eid)
        nm = e_name = (e or {}).get("_name") or eid
        ev.append([eid, m, (e or {}).get("good", True)])
    where = []
    for f, m in sorted((t.get("factions") or {}).items(), key=lambda kv: -kv[1]):
        where.append(FACTIONS.get(f, f.replace("_", " ").title()) + " x" + str(num(m)))
    for c, m in sorted((t.get("conditions") or {}).items(), key=lambda kv: -kv[1]):
        where.append(cname(c) + " x" + str(num(m)))
    conflicts = [next((x["name"] for x in tenant if x["id"] == c), c) for c in t.get("conflicts", [])]
    out_t.append({"id": "t_" + t["id"], "name": t["name"], "good": t.get("good"), "weight": t.get("weight", 1),
                  "fx": fx, "likes": tag_things(t.get("likes", [])), "hates": tag_things(t.get("dislikes", [])),
                  "events": ev, "where": where, "conflicts": conflicts})

# ---------------- house traits ----------------
out_h = []
for h in house:
    fx = []
    for k, label in (("comfort", "Comfort"), ("decor", "Decor"), ("happiness", "Tenant happiness"),
                     ("health", "Tenant health"), ("security", "Security")):
        if h.get(k):
            fx.append([label + " " + signed(h[k]), h[k] > 0])
    if h.get("price", 1) != 1:
        fx.append(["Price x" + str(h["price"]), h["price"] > 1])
    if h.get("wear", 1) != 1:
        fx.append(["Wears out " + pct(h["wear"]) + "% " + ("slower" if h["wear"] < 1 else "faster"), h["wear"] < 1])
    for k, v in (h.get("env") or {}).items():
        s = env.get(k, {})
        fx.append([(s.get("better") if v > 0 else s.get("worse")) + " (" + signed(v) + ")", v > 0])
    for k, v in (h.get("levels") or {}).items():
        n = levels.get(k, {}).get("name", k)
        steps = {1: "one level", 2: "two levels"}.get(abs(v), str(abs(v)) + " levels")
        fx.append([n + " " + steps + (" higher" if v > 0 else " lower"), v < 0])
    for c, hz in (h.get("cancels") or {}).items():
        fx.append(["No " + cname(c).lower() + " in this home (hazard -" + str(round(hz * 100)) + "%)", True])
    rolls = []
    if h.get("weight", 1) == 0:
        rolls.append("Never on a new home")
    if h.get("mark"):
        rolls.append("Left on a home by tenants or events; a repair cleans it off")
    if h.get("anyCondition"):
        rolls.append("Markets with " + " or ".join(cname(c) for c in h["anyCondition"]))
    if h.get("climates"):
        rolls.append("Climates: " + ", ".join(h["climates"]))
    if "station" in h:
        rolls.append("Stations only" if h["station"] else "Planets only")
    lo, hi = h.get("minTier", 0), min(h.get("maxTier", 5), 5)
    if lo > 0 or hi < 5:
        rolls.append("Homes from " + TIER_NAMES[lo] + " to " + TIER_NAMES[hi])
    if not rolls:
        rolls.append("Anywhere")
    liked, hated = [], []
    for t in tenant:
        if set(t.get("likes", [])) & set(h.get("tags", [])):
            liked.append([t["name"], t.get("good")])
        if set(t.get("dislikes", [])) & set(h.get("tags", [])):
            hated.append([t["name"], t.get("good")])
    ws = sorted(house_world.get(h["id"], []), key=lambda x: -x[1])
    every = [w + " x" + str(num(m)) for w, m in ws if m >= 10]
    more = [w + " x" + str(num(m)) for w, m in ws if 1 < m < 10]
    fewer = [w + " x" + str(num(m)) for w, m in ws if 0 < m < 1]
    never = [w for w, m in ws if m == 0]
    conflicts = [next((x["name"] for x in house if x["id"] == c), c) for c in h.get("conflicts", [])]
    out_h.append({"id": "h_" + h["id"], "name": h["name"], "good": h.get("good", True), "weight": h.get("weight", 1),
                  "fx": fx, "rolls": rolls, "liked": liked, "hated": hated, "conflicts": conflicts,
                  "every": every, "more": more, "fewer": fewer, "never": never})

# ---------------- worlds ----------------
hname = {h["id"]: [h["name"], h.get("good", True)] for h in house}
out_w = []
for wid, w in worlds.items():
    fx = []
    if w.get("homes", 1) != 1:
        fx.append(["Room for " + str(round(w["homes"] * 100)) + "% of the usual homes", w["homes"] > 1])
    if w.get("price", 1) != 1:
        fx.append(["Price x" + str(w["price"]), w["price"] > 1])
    for k, label in (("comfort", "Comfort"), ("happiness", "Happiness"), ("security", "Security")):
        if w.get(k):
            fx.append([label + " " + signed(w[k]), w[k] > 0])
    sizes = []
    for tid, m in (w.get("tiers") or {}).items():
        sizes.append([TIER_BY_ID.get(tid, tid) + (" never" if m == 0 else " x" + str(num(m))), None if m else False])
    tr = sorted((w.get("traits") or {}).items(), key=lambda kv: -kv[1])
    every = [[hname[k][0], hname[k][1]] for k, m in tr if m >= 10 and k in hname]
    more = [[hname[k][0] + " x" + str(num(m)), hname[k][1]] for k, m in tr if 1 <= m < 10 and k in hname]
    fewer = [[hname[k][0] + " x" + str(num(m)), hname[k][1]] for k, m in tr if 0 < m < 1 and k in hname]
    never = [[hname[k][0], hname[k][1]] for k, m in tr if m == 0 and k in hname]
    out_w.append({"id": "w_" + wid, "name": wname(wid), "title": w.get("name", ""), "line": w.get("line") or "",
                  "faction": world_faction.get(wid, "Other"), "fx": fx, "sizes": sizes,
                  "every": every, "more": more, "fewer": fewer, "never": never})

# ---------------- events ----------------
tname = {t["id"]: t["name"] for t in tenant}
NICE = {"housewarming": "Party", "recommended": "Good word", "garden": "Planted a garden", "mess": "Filthy mess",
        "trashed": "Trashed home", "tip": "Tip", "pests": "Pests", "late_rent": "Late rent",
        "skipped_rent": "Skipped rent", "fixed_something": "Fixed something", "cleaned_up": "Cleaned up",
        "died_ill": "Died of illness", "got_fit": "Got fit", "partner_moves_in": "Partner moves in",
        "pet_acquired": "Adopted a pet", "child_born": "Baby born", "fell_sick": "Fell sick",
        "hospital": "Hospital stay", "lost_job": "Lost job", "chased_burglars": "Chased off burglars"}


def ev_name(e):
    return NICE.get(e["id"], e["id"].replace("_", " ").capitalize())


for e in events:
    e["_name"] = ev_name(e)
for t in out_t:
    t["events"] = [[ev_by_id[eid]["_name"] if eid in ev_by_id else eid, m, g] for eid, m, g in t["events"]]


def plain_share(e):
    return not e.get("requires") and not e.get("sickOnly") and not e.get("maxHealth")


totals = {}
for e in events:
    if plain_share(e):
        k = (e.get("type", "house"), e["good"])
        totals[k] = totals.get(k, 0) + e["weight"]

msgs = ev_file.get("messages", {})
kinds = ev_file["kinds"]
out_e = []
for e in events:
    fx = []
    if e.get("condition"):
        fx.append(["Condition " + signed(e["condition"] * 100) + "%", e["condition"] > 0])
    if e.get("mood"):
        fx.append(["Tenant mood " + signed(e["mood"]), e["mood"] > 0])
    if "rentMult" in e:
        fx.append(["No rent this month" if e["rentMult"] == 0 else "Rent this month x" + str(e["rentMult"]), False])
    if e.get("tip"):
        fx.append(["Tip: " + str(round(e["tip"] * 100)) + "% of a month's rent", True])
    if e.get("decor"):
        fx.append(["Decor " + signed(e["decor"]), e["decor"] > 0])
    if e.get("rep"):
        fx.append(["Reputation " + signed(e["rep"]), e["rep"] > 0])
    if e.get("wealth"):
        fx.append(["Wealth " + signed(e["wealth"]) + " letter", e["wealth"] > 0])
    if e.get("addTrait"):
        h = next((x for x in house if x["id"] == e["addTrait"]), {"name": e["addTrait"], "good": False})
        fx.append(["Home gets " + h["name"], h.get("good", True)])
    if e.get("removeTrait"):
        h = next((x for x in house if x["id"] == e["removeTrait"]), {"name": e["removeTrait"]})
        fx.append(["Removes " + h["name"], True])
    if e.get("addTenantTrait"):
        fx.append(["Tenant becomes " + tname.get(e["addTenantTrait"], e["addTenantTrait"]), None])
    if e.get("removeTenantTrait"):
        fx.append(["Tenant is no longer " + tname.get(e["removeTenantTrait"], e["removeTenantTrait"]), None])
    if e.get("leave"):
        fx.append(["The tenant moves out", False])
    if e.get("dies"):
        fx.append(["The tenant dies", False])
    mods = []
    if e.get("requires"):
        mods.append("Only tenants who are " + " or ".join(tname.get(r, r) for r in e["requires"]))
    if e.get("damage"):
        mods.append("More common in cheap homes (x2 in a shack, x0.6 in an estate)")
    if e.get("crime"):
        mods.append("More common with low security (x1.5 at 0, x0.5 at 100)")
    if e.get("health"):
        mods.append("More common as health falls (x0.1 at 96+ health, x2.5 at 0)" if not e["good"]
                    else "Less common as health falls (x1.6 at 96+ health, x0.2 at 12 or less)")
    if e.get("sickOnly"):
        mods.append("Only while the tenant is sick (health below 60), more often the sicker they are")
    if e.get("maxHealth"):
        mods.append("Only below " + str(e["maxHealth"]) + " health, more often the lower it is")
    if e.get("sickMult"):
        mods.append("Up to x" + str(num(e["sickMult"])) + " when the tenant is sick")
    traits = []
    for t in tenant:
        m = (t.get("events") or {}).get(e["id"])
        if m is not None:
            traits.append([t["name"], m])
    reno = []
    for u in upgrades:
        m = (u.get("events") or {}).get(e["id"])
        if m is not None:
            reno.append([u["name"], m])
    lines = []
    for g in msgs.get(e["id"], []):
        who = ", ".join([tname.get(x, x) for x in g.get("traits", [])] + [FACTIONS.get(x, x) for x in g.get("factions", [])])
        lines.append([who or "Anyone else", g["lines"]])
    typ = e.get("type", "house")
    share = round(100 * e["weight"] / totals[(typ, e["good"])]) if plain_share(e) else None
    out_e.append({"id": e["id"], "name": e["_name"], "good": e["good"], "type": typ,
                  "kindName": kinds.get(e.get("kind"), e.get("kind", "")), "weight": e["weight"], "share": share,
                  "big": bool(e.get("big")), "text": e["text"].replace("$tenant", "[tenant]").replace("$home", "[home]"),
                  "fx": fx, "mods": mods, "traits": traits, "reno": reno, "lines": lines})

# ---------------- write pages ----------------
common_css = open(os.path.join(HERE, "common.css"), encoding="utf-8").read()
common_js = open(os.path.join(HERE, "common.js"), encoding="utf-8").read()


def page(tpl_name, out_name, data):
    tpl = open(os.path.join(HERE, tpl_name), encoding="utf-8").read()
    html = (tpl.replace("/*__CSS__*/", common_css).replace("/*__COMMON__*/", common_js)
            .replace("__DATA__", json.dumps(data, ensure_ascii=False)).replace("__VERSION__", VERSION))
    open(os.path.join(HERE, out_name), "w", encoding="utf-8").write(html)


page("traits.tpl.html", "traits.html", {"tenant": out_t, "house": out_h, "worlds": out_w, "like": LIKE, "dislike": DISLIKE})
page("events.tpl.html", "events.html", {"events": out_e, "house": ev_file["house"], "tenant": ev_file["tenant"]})
print(VERSION, len(out_t), "tenant traits,", len(out_h), "house traits,", len(out_w), "worlds,", len(out_e), "events")
print("unnamed conditions:", sorted({c for h in house for c in h.get("anyCondition", []) if c not in cond_names}))
