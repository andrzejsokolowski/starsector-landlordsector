"""Landlordsector home condition icons (LLS-16, Andrzej 2026-11-16: "home conditions as icons like market conditions").

    python icons.py build     writes every icon to graphics/landlordsector/icons/ (128x128; the game draws them at the LunaLib icon size)
    python icons.py prompts   writes art-prompts-icons.txt and icon-prompts.json (the Gemini prompts page data)

Three kinds:
- script: grade icons (Location, Commute, Air, Noise from S+ to F) and size icons, drawn here in the flat style of the
  game's colony-size icons (graphics/icons/markets/population0N.png). Final art, no Gemini needed.
- gemini: one painted icon per house trait, plus Residential area and Lore world. Andrzej makes them in Gemini from the
  prompts and saves them as art-source/icons/<key>.png (or .jpg; the icon's name alone works too, like blooming.jpg). Until a file is there, build writes a placeholder
  (dark square, a border in the trait's color, its first letters).
- The game's own: heat, cold and radiation reuse graphics/icons/markets/hot, cold and irradiated; the nearby building
  uses its industry picture. Those are not made here.
"""
import io, json, os, re, sys

from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
MOD = os.path.abspath(os.path.join(HERE, "..", ".."))
CFG = os.path.join(MOD, "data", "config", "landlordsector")
OUT = os.path.join(MOD, "graphics", "landlordsector", "icons")
SRC = os.path.join(MOD, "art-source", "icons")
SIZE = 128  # 1.5.1 (Andrzej: "40x40 is pretty small. The icons are legible from 120x120")
U = SIZE / 40.0  # the drawings below are laid out on a 40 grid

GRADES = ["S+", "S", "A", "B", "C", "D", "E", "F"]
# Background per grade, like the colored squares of the game's colony-size icons: gold at the top, slate in the
# middle, rust and red at the bottom.
# 1.5.2 (Andrzej): "S+: Light blue, S: dark Green, A: brighter green, B: Yellow, C: orange, D: brown-ish, E: Light red,
# F: Deep red." Each a little dark, so the light letters stay readable.
GRADE_BG = {"S+": (52, 138, 186), "S": (30, 98, 50), "A": (66, 150, 58), "B": (176, 150, 28), "C": (196, 106, 30),
            "D": (112, 76, 44), "E": (196, 78, 70), "F": (118, 22, 24)}
# 1.5.3 (Andrzej: "COMM" reads as communications): full words, the font shrinks to fit when needed.
STATS = [("location", "LOCATION"), ("commute", "COMMUTE"), ("air", "AIR"), ("noise", "NOISE")]


def load(name):
    text = "\n".join(l for l in io.open(os.path.join(CFG, name), encoding="utf-8").read().splitlines()
                     if not l.strip().startswith("#"))
    return json.loads(re.sub(r",(\s*[}\]])", r"\1", text))


def grade_key(g):
    return g.lower().replace("+", "plus")


ICON_STYLE = """ICON STYLE (the same for every icon of this set):
- One square 1:1 picture used as a small icon in a retro sci-fi strategy game, next to the game's own planet condition icons. It is shown small, about 60 x 60 pixels, so: ONE clear subject, big simple shapes, a strong silhouette, high contrast. Nothing small may matter.
- Painted pixel art with visible pixels, 3 to 5 flat tones per material, thin dark outlines, one light from the upper left, a soft glow where something emits light.
- Full-bleed square scene that fills the whole picture: no frame, no border, no rounded corners, no text, no letters, no numbers, no symbols, no UI, no watermark.
- A dark, moody background (night blues, deep browns, near-black) and the subject lit by one warm or colored light, so it pops at a tiny size.
- Close or medium-close framing of one part of a home or what is right next to it. Not a wide landscape, not a whole house unless asked.

WORLD (the same for every icon): a grounded, lived-in far-future frontier colony seen the way 1970s and 1980s science fiction imagined it: prefab metal panels, rivets, chunky pipes and vents, amber and teal indicator lights, worn paint. Semi-realistic, never fantasy: no magic, no mechs, no walking or floating buildings, no creatures with glowing eyes, no cartoon faces, no people in close-up."""


def gemini_icons():
    traits = load("house_traits.json")["traits"]
    subjects = {
        "flowers": "a window box overflowing with bright flowers on a pale prefab metal wall, warm evening light",
        "garden": "a small lush patch of leafy plants growing beside a habitat door, soft green glow of a grow lamp",
        "ocean_view": "the view from a metal balcony railing over a calm ocean at dusk, one distant shuttle light in the sky",
        "lakeside": "a short metal jetty reaching into a perfectly still lake at dusk, its lamp reflected in the water",
        "mountain_view": "a wide rounded-rectangle window framing a jagged mountain range at sunset",
        "starview": "a big curved station window full of stars, with a ringed planet hanging outside",
        "recycled_air": "a grimy ceiling air vent breathing out stale grey haze, lit by a dim flickering amber lamp",
        "lava_lake": "the orange glow of a lava field behind a heat-warped metal wall, shimmering hot air",
        "frozen_pipes": "frost-covered pipes with icicles and a cracked frozen valve, cold blue light",
        "geothermal": "a chunky heat exchanger fed by a hot steam vent from the ground, warm pipes and rising steam",
        "damp": "dark green-black mold spreading over a wet corner of a metal wall, water dripping",
        "smog": "a window looking out into thick brown smog with dim factory stacks and red warning lights",
        "dust": "dust piled on a windowsill and blowing in through a gap, dry yellow light",
        "spaceport": "a heavy cargo freighter lifting off just above the rooftops, a roaring engine flare",
        "quiet_street": "an empty, calm colony street at night lit by a single warm streetlamp",
        "market_nearby": "a lively market stall with striped awnings, hanging lamps and stacked crates",
        "thin_walls": "a thin dented wall panel with a crack, light and shadows of a noisy neighbor showing through",
        "cramped": "a tiny room crammed full with a bunk, boxes and pipes, no free floor left",
        "leaky_roof": "water dripping from a patched metal roof into a dented bucket",
        "ruins": "a weathered old facade of stone and metal from a fallen age, with a worn ancient emblem shape",
        "fungus_cellar": "a damp cellar shelf of pale cultivated mushrooms under a dim hanging lamp (the mushrooms do not glow)",
        "lobster_pond": "a small pond with a big dark-red lobster climbing onto its stone edge",
        "clean": "a spotless, gleaming kitchen counter with a bright clean highlight",
        "dirty": "a filthy floor with trash, stains and a pile of dirty dishes",
        "infested": "small roach-like bugs crawling out of a crack in a wall",
        "asbestos": "a crumbling grey insulation panel behind a yellow and black hazard stripe",
        "spacious": "a large open room with a high ceiling and a huge bright window, airy and empty",
        "drafty": "cold wind blowing snow in through the gap under a door",
        "sunny": "strong sunbeams pouring through a window onto a warm floor",
        "underground": "a habitat corridor cut into solid rock, held up by reinforced metal arches, warm lamps",
        "fresh_air": "light curtains blowing in a fresh breeze at an open window with green hills outside",
        "graffiti": "a metal wall covered in colorful spray-paint shapes and drips, without any letters",
        "blossoms": "a branch of pink blossoms right in front of a habitat window",
    }
    out = []
    for t in traits:
        subj = subjects.get(t["id"]) or "the home's " + t["name"].lower() + " look"
        tone = ("Mood: inviting and pleasant, a place you would like to live." if t.get("good")
                else "Mood: gloomy and unpleasant, a flaw of the home.")
        out.append({"key": "trait_" + t["id"], "name": t["name"], "group": "House traits",
                    "good": t.get("good", False), "subject": subj, "tone": tone})
    out.append({"key": "area_residential", "name": "Residential area", "group": "Places", "good": None,
                "subject": "a quiet row of small prefab homes with warm lit windows at dusk",
                "tone": "Mood: calm and ordinary."})
    # 1.5.2 (Andrzej: "We can also make icons for the climate: Heat, cold, radiation"). One picture each; the build
    # adds 1 to 3 marks for the level (Hot, Very hot, Scorching). The game's own icons show until a file is there.
    out.append({"key": "climate_heat", "name": "Heat", "group": "Climate", "good": False,
                "subject": "the harsh orange glare of a scorching sun through a habitat window, hot air shimmering "
                           "over a sun-baked metal sill",
                "tone": "Mood: oppressive, too hot to live comfortably."})
    out.append({"key": "climate_cold", "name": "Cold", "group": "Climate", "good": False,
                "subject": "a small round habitat window thick with frost, a pale blue snowstorm outside",
                "tone": "Mood: bitter and cold, too cold to live comfortably."})
    out.append({"key": "climate_radiation", "name": "Radiation", "group": "Climate", "good": False,
                "subject": "a sealed habitat airlock under a flashing yellow hazard lamp, a sickly green haze in the "
                           "air outside its window",
                "tone": "Mood: dangerous and unhealthy, the air outside can hurt you."})
    for i in out:
        i["prompt"] = ICON_STYLE + "\n\nTHE ICON: " + i["name"] + ". Show " + i["subject"] + ".\n" + i["tone"] + \
            "\n\nSave it as " + i["key"] + ".png"
    return out


def font(size, bold=True):
    for f in (["consolab.ttf", "arialbd.ttf"] if bold else ["consola.ttf", "arial.ttf"]):
        p = os.path.join("C:/Windows/Fonts", f)
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def text_icon(path, big, small, bg, fg=(225, 230, 220)):
    """The game's colony-size style: a colored square, a big character and a small label."""
    im = Image.new("RGBA", (SIZE, SIZE), bg + (255,))
    d = ImageDraw.Draw(im)
    fb = font(int((26 if len(big) == 1 else 21) * U))
    w = d.textbbox((0, 0), big, font=fb)
    bx = SIZE - (w[2] - w[0]) - 4 * U - w[0]
    d.text((bx, 1 * U - w[1]), big, font=fb, fill=fg)
    fsize = int(9 * U)
    fs = font(fsize)
    longest = max([l for _, l in STATS] + [small], key=len)  # one label size on every icon
    while d.textbbox((0, 0), longest, font=fs)[2] > SIZE - 6 * U and fsize > 10:
        fsize -= 1
        fs = font(fsize)
    s = d.textbbox((0, 0), small, font=fs)
    d.text((3 * U - s[0], SIZE - (s[3] - s[1]) - 3 * U - s[1]), small, font=fs, fill=fg)
    im.save(path)


def placeholder(path, name, good):
    border = (70, 170, 90) if good is True else ((190, 70, 60) if good is False else (140, 145, 160))
    im = Image.new("RGBA", (SIZE, SIZE), (24, 30, 44, 255))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, SIZE - 1, SIZE - 1], outline=border, width=int(2 * U))
    letters = re.sub(r"[^A-Za-z]", "", name)[:3].upper()
    f = font(int(13 * U))
    b = d.textbbox((0, 0), letters, font=f)
    d.text(((SIZE - (b[2] - b[0])) / 2 - b[0], (SIZE - (b[3] - b[1])) / 2 - b[1]), letters, font=f, fill=border)
    im.save(path)


def from_art(src, path):
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    s = min(w, h)
    im = im.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s))
    im.resize((SIZE, SIZE), Image.LANCZOS).save(path)


def climate(src, key):
    """Three files from one picture: climate_<id>_1..3, with 1 to 3 marks in the lower right for the level."""
    base = Image.open(src).convert("RGBA")
    w, h = base.size
    s = min(w, h)
    base = base.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s)).resize((SIZE, SIZE), Image.LANCZOS)
    for n in (1, 2, 3):
        im = base.copy()
        d = ImageDraw.Draw(im)
        m, gap = int(7 * U), int(2 * U)
        for k in range(n):
            x1 = SIZE - int(3 * U) - k * (m + gap)
            y1 = SIZE - int(3 * U)
            d.rectangle([x1 - m, y1 - m, x1, y1], fill=(235, 90, 50, 255), outline=(20, 10, 8, 255), width=max(1, int(U)))
        im.save(os.path.join(OUT, "%s_%d.png" % (key, n)))


def build():
    os.makedirs(OUT, exist_ok=True)
    n_script = n_art = n_place = 0
    for stat, label in STATS:
        for g in GRADES:
            text_icon(os.path.join(OUT, "grade_%s_%s.png" % (stat, grade_key(g))), g, label, GRADE_BG[g])
            n_script += 1
    tiers = load("tiers.json")["tiers"]
    size_bg = [(60, 58, 52), (62, 66, 70), (44, 62, 76), (42, 92, 96), (98, 122, 38), (138, 109, 26)]
    for i, t in enumerate(tiers):
        text_icon(os.path.join(OUT, "size_%s.png" % t["id"]), str(i + 1), "SIZE", size_bg[min(i, 5)])
        n_script += 1
    for i in gemini_icons():
        dst = os.path.join(OUT, i["key"] + ".png")
        # Andrzej names files by the trait's name ("blooming.jpg"); the id ("flowers") and the key work too.
        names = [i["key"], i["name"].lower(), i["name"].lower().replace("-", "_").replace(" ", "_")]
        if i["key"].startswith("trait_"):
            names.append(i["key"][len("trait_"):])
        src = next((os.path.join(SRC, n + e) for n in names for e in (".png", ".jpg", ".jpeg", ".webp")
                    if os.path.exists(os.path.join(SRC, n + e))), None)
        if i["group"] == "Climate":
            # No placeholder: the game draws its own climate icon until Andrzej's is in.
            for n in (1, 2, 3):
                old = os.path.join(OUT, "%s_%d.png" % (i["key"], n))
                if not src and os.path.exists(old):
                    os.remove(old)
            if src:
                climate(src, i["key"])
                n_art += 1
            continue
        if src:
            from_art(src, dst)
            n_art += 1
        else:
            placeholder(dst, i["name"], i["good"])
            n_place += 1
    print("icons: %d made by script, %d from Andrzej's art, %d placeholders" % (n_script, n_art, n_place))


def prompts():
    items = gemini_icons()
    with io.open(os.path.join(MOD, "art-prompts-icons.txt"), "w", encoding="utf-8", newline="\n") as f:
        f.write("LANDLORDSECTOR - GEMINI PROMPTS FOR THE HOME CONDITION ICONS\n")
        f.write("=" * 60 + "\n\nOne prompt per icon. New Gemini chat for each. Save as the name on the last line into\n"
                "art-source/icons/. Claude crops the middle square and shrinks it to 128x128.\n\n")
        for i in items:
            f.write("#" * 100 + "\n### " + i["key"] + ".png  (" + i["name"] + ")\n\n" + i["prompt"] + "\n\n")
    json.dump({"style": ICON_STYLE, "icons": items}, io.open(os.path.join(HERE, "icon-prompts.json"), "w",
              encoding="utf-8", newline="\n"), ensure_ascii=False, indent=1)
    print(len(items), "prompts")


def page():
    """Builds icon-prompts.html (the prompts page artifact) next to this script."""
    prompts()
    data = json.load(io.open(os.path.join(HERE, "icon-prompts.json"), encoding="utf-8"))
    tpl = io.open(os.path.join(HERE, "icon_prompts.tpl.html"), encoding="utf-8").read()
    out = tpl.replace("__DATA__", json.dumps(data, ensure_ascii=False).replace("</", "<\\/"))
    io.open(os.path.join(HERE, "icon-prompts.html"), "w", encoding="utf-8", newline="\n").write(out)
    print("icon-prompts.html")


if __name__ == "__main__":
    {"build": build, "prompts": prompts, "page": page}[sys.argv[1] if len(sys.argv) > 1 else "build"]()
