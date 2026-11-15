"""Copies the finished pictures from art-source/ into the mod and rewrites art.json.

  python tools/art-maker/export_art.py

Each picture is cropped from the middle to 16 by 10 and saved as a 1024 x 640 JPG in
graphics/landlordsector/exteriors/<tier>_<planet>.jpg (variant 1) and <tier>_<planet>_2.jpg (the alt).
art.json lists both for every planet type that has them; a listing picks one at random.
1.1.0: the third, "wild" picture (<stem>_alt2 in art-source) goes to <tier>_<planet>_3.jpg and the "rare"
section, which a new home shows only "rareChance" of the time (Andrzej: "make them rarer").
Pictures marked rejected in review.json are left out. The tier placeholders (shack.png, ...) stay as
the "default" for planet types with no picture yet.
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
import artlib  # noqa: E402

ROOT = artlib.ROOT
OUT = os.path.join(ROOT, 'graphics', 'landlordsector', 'exteriors')
ART_JSON = os.path.join(ROOT, 'data', 'config', 'landlordsector', 'art.json')
REL = 'graphics/landlordsector/exteriors/'
W, H = 1024, 640

HEADER = """{
\t# Exterior pictures for the buy screen and the Manage page, by tier and planet type.
\t# Planet types (PlanetType.java): station, terran, jungle, water, arid, desert, tundra, frozen,
\t# cryovolcanic, barren, toxic, volcanic, irradiated, gas_giant.
\t# A planet type with no list uses the tier's "default" pictures. Each list may hold one or two
\t# pictures, and a listing picks one at random. Pictures are 1024 x 640 JPG (16 by 10), loaded
\t# only while a screen shows them. Written by tools/art-maker/export_art.py from art-source/.
\t# "rare" (1.1.0): unusual third pictures, shown instead of the usual ones on a "rareChance"
\t# share of new homes (0.15 = about 1 home in 7).
\t"rareChance":RARE_CHANCE,
\t"exteriors":"""
RARE_CHANCE = 0.15


def crop(path):
    im = Image.open(path).convert('RGB')
    w, h = im.size
    if w / h > W / H:
        nw = round(h * W / H)
        x = (w - nw) // 2
        im = im.crop((x, 0, x + nw, h))
    else:
        nh = round(w * H / W)
        y = (h - nh) // 2
        im = im.crop((0, y, w, y + nh))
    return im.resize((W, H), Image.LANCZOS)


def main():
    review = artlib.load_review()
    os.makedirs(OUT, exist_ok=True)
    tiers = {}
    rare = {}
    written = set()
    for h in artlib.homes():
        tier = h.size['id']
        entry = tiers.setdefault(tier, {'default': [REL + tier + '.png']})
        pics = []
        for stem, suffix in ((h.stem, ''), (h.alt_stem, '_2')):
            src = artlib.find(stem)
            if not src or review.get(stem, {}).get('status') == 'rejected':
                continue
            name = f'{tier}_{h.planet["id"]}{suffix}.jpg'
            crop(src).save(os.path.join(OUT, name), quality=88, optimize=True, progressive=False)
            written.add(name)
            pics.append(REL + name)
        if pics:
            entry[h.planet['id']] = pics
        src = artlib.find(h.alt2_stem)
        if src and review.get(h.alt2_stem, {}).get('status') != 'rejected':
            name = f'{tier}_{h.planet["id"]}_3.jpg'
            crop(src).save(os.path.join(OUT, name), quality=88, optimize=True, progressive=False)
            written.add(name)
            rare.setdefault(tier, {})[h.planet['id']] = [REL + name]
    # Old PNG exports of pictures that now have a JPG are removed; placeholders stay.
    removed = []
    for f in os.listdir(OUT):
        if f.endswith('.png') and '_' in f and f[:-4] + '.jpg' in written:
            os.remove(os.path.join(OUT, f))
            removed.append(f)
    body = json.dumps(tiers, indent='\t').replace('\n', '\n\t')
    rare_body = json.dumps(rare, indent='\t').replace('\n', '\n\t')
    with open(ART_JSON, 'w', encoding='utf-8', newline='\n') as f:
        f.write(HEADER.replace('RARE_CHANCE', str(RARE_CHANCE)) + body + ',\n\t"rare":' + rare_body + '\n}\n')
    total = sum(os.path.getsize(os.path.join(OUT, n)) for n in written)
    print(f'{len(written)} pictures written ({total / 1e6:.1f} MB), {len(removed)} old PNGs removed')


if __name__ == '__main__':
    main()
