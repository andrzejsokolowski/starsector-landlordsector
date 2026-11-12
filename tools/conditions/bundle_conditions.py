"""One text file per condition: description, hazard, and its effect code (source, or javap of the class)."""
import csv, glob, json, os, re, subprocess, sys, tempfile, zipfile

S = os.path.dirname(os.path.abspath(__file__))
MODS = r"D:\Games\StarSector\mods"
CORE = r"D:\Games\StarSector\starsector-core"
JAVAP = r"C:\Program Files (x86)\RedHatJava\java-21-openjdk-21.0.2.0.13-2.win.jdk.x86_64\bin\javap.exe"
rows = json.load(open(os.path.join(S, "cond_index.json"), encoding="utf-8"))
out_dir = os.path.join(S, "bundles")
os.makedirs(out_dir, exist_ok=True)

# Hazard per condition from every condition_gen_data.csv (vanilla first, mods override).
hazard = {}
for path in [os.path.join(CORE, "data", "campaign", "procgen", "condition_gen_data.csv")] + \
        glob.glob(os.path.join(MODS, "*", "data", "campaign", "procgen", "condition_gen_data.csv")):
    try:
        with open(path, encoding="utf-8", errors="replace", newline="") as f:
            for r in csv.DictReader(f):
                cid = (r.get("id") or "").strip()
                h = (r.get("hazard") or "").strip()
                if cid and h:
                    hazard[cid] = h
    except Exception:
        pass

# Classes the first pass could not find: look in every enabled mod's jars.
all_jars = glob.glob(os.path.join(MODS, "**", "*.jar"), recursive=True)


def jar_for(cls):
    entry = cls.replace(".", "/") + ".class"
    for jar in all_jars:
        try:
            with zipfile.ZipFile(jar) as z:
                if entry in z.namelist():
                    return jar
        except Exception:
            pass
    return None


def javap(jar, cls):
    tmp = tempfile.mkdtemp()
    entry = cls.replace(".", "/")
    with zipfile.ZipFile(jar) as z:
        names = [n for n in z.namelist() if n == entry + ".class" or n.startswith(entry + "$")]
        for n in names:
            z.extract(n, tmp)
    res = subprocess.run([JAVAP, "-c", "-p", "-cp", tmp, cls], capture_output=True, text=True, errors="replace")
    text = res.stdout
    # Keep it readable: drop pure stack shuffling lines.
    lines = [l for l in text.splitlines() if not re.match(r"\s+\d+: (aload|astore|iload|istore|fload|fstore|dup|pop|return|areturn)(_\d)?\s*$", l)]
    return "\n".join(lines)


generic = {"com.fs.starfarer.api.impl.campaign.econ.BaseHazardCondition": "Vanilla BaseHazardCondition: only adds its hazard value to the colony hazard rating.",
           "com.fs.starfarer.api.impl.campaign.econ.ResourceDepositsCondition": "Vanilla ResourceDepositsCondition: only changes resource output (and hazard if it has one)."}

for r in rows:
    cid = r["id"]
    code = ""
    if r["source"]:
        code = open(r["source"], encoding="utf-8", errors="replace").read()
    elif r["script"] in generic:
        code = generic[r["script"]]
    else:
        jar = r["jar"] or (jar_for(r["script"]) if r["script"] else None)
        if jar:
            try:
                code = javap(jar, r["script"])
            except Exception as e:
                code = "javap failed: %s" % e
        else:
            code = "CLASS NOT FOUND: " + r["script"]
    if len(code) > 24000:
        code = code[:24000] + "\n... [cut]"
    with open(os.path.join(out_dir, cid + ".txt"), "w", encoding="utf-8") as f:
        f.write("id: %s\nmod: %s\nname: %s\nhazard (condition_gen_data): %s\nscript: %s\ndescription: %s\n\n----- code -----\n%s\n"
                % (cid, r["mod"], r["name"], hazard.get(cid, "none"), r["script"], r["desc"], code))
print(len(rows), "bundles in", out_dir)
sizes = sorted((os.path.getsize(os.path.join(out_dir, f)), f) for f in os.listdir(out_dir))
print("total KB:", sum(s for s, _ in sizes) // 1024, "largest:", sizes[-3:])
