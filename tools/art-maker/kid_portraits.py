"""Kid portraits (LLS-30): the Gemini prompts, and turning the pictures into the mod's 128x128 portraits.

  python kid_portraits.py prompts            prints the prompts as JSON (key, prompt), in making order
  python kid_portraits.py build <inbox>      <inbox>/<key>.(jpg|png) -> graphics/landlordsector/portraits/kids/<key>.png
                                             and data/config/settings.json ("lls_kid_portraits")
  python kid_portraits.py sheet <inbox> <out.png>   a contact sheet to check them

Keys were <stage>_<f|m>_1 for baby, toddler, child and teen: the kid seen from behind, no face, skin or hair
(2026-11-17). From the Adult stage the kid shows the random portrait from your faction picked at birth.

1.16.0 (LLS-46): the mod now ships Landlordsector Renewed's pictures instead, <stage>_1 to <stage>_4 (four clothes
colors, never by boy or girl). Running build again would replace them with the old <stage>_<f|m>_1 set.
"""
import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
OUT = os.path.join(ROOT, 'graphics', 'landlordsector', 'portraits', 'kids')
SETTINGS = os.path.join(ROOT, 'data', 'config', 'settings.json')

STYLE = ("Create an image. A square 1:1 character portrait for the space game Starsector, in the exact style of its "
         "in-game character portraits: a painted digital illustration, semi-realistic, fine painterly brushwork, "
         "dramatic side lighting with a cool rim light, muted retrofuturist colors, a dark background that fades to "
         "black at the edges. The kid is seen from BEHIND: the face is never visible, not even in profile, and no skin "
         "and no hair are visible anywhere (hood up or a knit hat, long sleeves, mittens or gloves). The size of the "
         "kid and the things around them make the age obvious. Grounded and realistic, a normal home on a space "
         "colony. No fantasy, no glowing effects, no text, no border, no logo, no signature. Subject: ")

# Andrzej, 2026-11-17: no faces ("race ambiguous"); spacesuits looked like grown-ups, so the kid is seen from behind.
# One picture per stage and sex; the clothes' color tells them apart.
WEAR = {'m': "slate blue with orange stripes", 'f': "dusty red with cream stripes"}

STAGES = [
    ('baby', "a baby about eight months old lying in a crib, seen from above and behind, wrapped in a soft blanket "
             "and wearing a knit hat, both {wear}, only the back of the hat and the blanket showing; above the crib "
             "hangs a mobile of little toy starships"),
    ('toddler', "a toddler about two years old sitting on the floor of a colony apartment, seen from behind, in a "
                "padded hooded onesie, {wear}, with the hood up and mittens, stacking big toy blocks next to a toy "
                "starship"),
    ('child', "a child about eight years old standing at a big round window looking out at a planet, seen from "
              "behind, wearing a hooded school jacket, {wear}, with the hood up, gloves and a small school backpack"),
    ('teen', "a lanky teenager about fifteen years old leaning on a railing on a colony walkway at night, seen from "
             "behind, wearing a hoodie, {wear}, with the hood up, big headphones over the hood and gloves, city lights "
             "below"),
]


def prompts():
    out = []
    for stage, text in STAGES:
        for sex in ('m', 'f'):
            out.append({'key': f'{stage}_{sex}_1', 'prompt': STYLE + text.format(wear=WEAR[sex]) + "."})
    return out


def find(inbox, key):
    for ext in ('.png', '.jpg', '.jpeg', '.webp'):
        p = os.path.join(inbox, key + ext)
        if os.path.exists(p):
            return p
    return None


def square(im):
    w, h = im.size
    s = min(w, h)
    return im.crop(((w - s) // 2, (h - s) // 2, (w - s) // 2 + s, (h - s) // 2 + s))


def build(inbox):
    from PIL import Image
    os.makedirs(OUT, exist_ok=True)
    found = {}
    for p in prompts():
        src = find(inbox, p['key'])
        if not src:
            continue
        im = square(Image.open(src).convert('RGB')).resize((128, 128), Image.LANCZOS)
        im.save(os.path.join(OUT, p['key'] + '.png'), optimize=True)
        found[p['key']] = 'graphics/landlordsector/portraits/kids/' + p['key'] + '.png'
    settings = {}
    if os.path.exists(SETTINGS):
        with open(SETTINGS, encoding='utf8') as f:
            settings = json.load(f)
    settings.setdefault('graphics', {})['lls_kid_portraits'] = found
    with open(SETTINGS, 'w', encoding='utf8', newline='\n') as f:
        json.dump(settings, f, indent='\t')
        f.write('\n')
    print(len(found), 'portraits')


def sheet(inbox, out):
    from PIL import Image, ImageDraw
    ps = prompts()
    cell = 192
    cols = 4
    rows = (len(ps) + cols - 1) // cols
    img = Image.new('RGB', (cols * cell, rows * (cell + 18)), (20, 20, 20))
    d = ImageDraw.Draw(img)
    for i, p in enumerate(ps):
        x, y = (i % cols) * cell, (i // cols) * (cell + 18)
        src = find(inbox, p['key'])
        if src:
            img.paste(square(Image.open(src).convert('RGB')).resize((cell, cell)), (x, y))
        d.text((x + 4, y + cell + 2), p['key'], fill=(230, 230, 230))
    img.save(out)


if __name__ == '__main__':
    cmd = sys.argv[1]
    if cmd == 'prompts':
        print(json.dumps(prompts(), ensure_ascii=False))
    elif cmd == 'build':
        build(sys.argv[2])
    elif cmd == 'sheet':
        sheet(sys.argv[2], sys.argv[3])
