"""Index every lived-in market condition: its effect class and where that class's code is."""
import csv, glob, json, os, re, sys, zipfile

MODS = r"D:\Games\StarSector\mods"
CORE = r"D:\Games\StarSector\starsector-core"
API = os.path.join(os.path.dirname(os.path.abspath(__file__)), "api")
OUT = sys.argv[1]

enabled = set(json.loads(re.sub(r"#.*", "", open(os.path.join(MODS, "enabled_mods.json"), encoding="utf-8",
                                                    errors="replace").read()))["enabledMods"])

SKIP = re.compile(r"^(ore_|rare_ore_|volatiles_|organics_|farmland_|population_\d|uaf_|baby_seraphic|aotd_toolbox|"
                  r"GrandColonies_page|marketRetrofit|IndEvo_ressCond|IndEvo_RuinedInfra|IndEvo_CryoRevival|"
                  r"comm_relay|ai_core_admin|sprite_controller|terraforming_controller|RS_MarketCondition|"
                  r"sotf_habitatcondition|nex_faction_condition|aotd_handle|aotd_industry_syn|AoDFoodDemand|"
                  r"aotd_gp_mod|TechnologyBonusesApplier|aotd_tech_upkeep|aotd_growing_demand|bifrost_removal|"
                  r"US_unique_filter|US_hybrid|corruptofficials_cond|jdp_hubrisMarker|watterSupplyMining)")
PLANET_TYPES = {"uninhabitable", "ice", "desert", "arid", "water", "jungle", "terran", "barren_marginal", "twilight",
                "tundra", "cryovolcanic"}


def mod_folders():
    out = []
    for info in glob.glob(os.path.join(MODS, "*", "mod_info.json")):
        folder = os.path.dirname(info)
        m = re.search(r'"id"\s*:\s*"([^"]+)"', open(info, encoding="utf-8", errors="replace").read())
        if m and m.group(1) in enabled:
            out.append((m.group(1), folder))
    return out


def find_source(folder, cls):
    simple = cls.split(".")[-1]
    for ext in (".java", ".kt"):
        hits = [p for p in glob.glob(os.path.join(folder, "**", simple + ext), recursive=True) if ".git" not in p]
        if hits:
            want = cls.replace(".", os.sep)
            hits.sort(key=lambda p: (want not in p.replace("/", os.sep), len(p)))
            return hits[0]
    return None


def find_jar(folder, cls):
    entry = cls.replace(".", "/") + ".class"
    for jar in glob.glob(os.path.join(folder, "**", "*.jar"), recursive=True):
        try:
            with zipfile.ZipFile(jar) as z:
                if entry in z.namelist():
                    return jar
        except Exception:
            pass
    return None


rows = []


def scan(path, mod, folder):
    with open(path, encoding="utf-8", errors="replace", newline="") as f:
        for r in csv.DictReader(f):
            cid = (r.get("id") or "").strip()
            if not cid or cid.startswith("#") or SKIP.match(cid) or cid in PLANET_TYPES:
                continue
            cls = (r.get("script") or "").strip()
            src = jar = None
            if cls:
                if mod == "vanilla":
                    p = os.path.join(API, *cls.split(".")) + ".java"
                    src = p if os.path.isfile(p) else None
                else:
                    src = find_source(folder, cls)
                    if not src:
                        jar = find_jar(folder, cls)
            rows.append({"mod": mod, "id": cid, "name": (r.get("name") or "").strip(), "script": cls,
                         "source": src, "jar": jar, "desc": re.sub(r"\s+", " ", r.get("desc") or "").strip()})


scan(os.path.join(CORE, "data", "campaign", "market_conditions.csv"), "vanilla", CORE)
for mid, folder in mod_folders():
    p = os.path.join(folder, "data", "campaign", "market_conditions.csv")
    if os.path.isfile(p):
        scan(p, mid, folder)

seen = {}
for r in rows:
    seen.setdefault(r["id"], r)
rows = list(seen.values())
json.dump(rows, open(OUT, "w", encoding="utf-8"), indent=1)
print(len(rows), "conditions;", sum(1 for r in rows if r["source"]), "with source,",
      sum(1 for r in rows if r["jar"]), "in a jar only,", sum(1 for r in rows if not r["script"]), "with no script,",
      sum(1 for r in rows if r["script"] and not r["source"] and not r["jar"]), "script not found")
for r in rows:
    if r["script"] and not r["source"] and not r["jar"]:
        print("  not found:", r["mod"], r["id"], r["script"])
