"""Third ("wild") pictures: take them from the inbox and build the review page.

  python wild_review.py map <log.json>   rename art-source/wild-inbox/seq_NNN.jpg to <stem>_alt2.jpg in art-source,
                                         in the order the Gemini page logged its downloads (a JSON list of keys)
  python wild_review.py build            write art-source/wild-review/ (index.html, pics/, thumbs/) for the
                                         Artifact publish; prints the files that are new since the last build

The review page keeps Andrzej's choices in its own database (collection "verdicts", doc per stem).
"""
import glob
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
import artlib  # noqa: E402

HERE = os.path.dirname(os.path.abspath(__file__))
INBOX = os.path.join(artlib.ART, 'wild-inbox')
OUT = os.path.join(artlib.ART, 'wild-review')
WILD = os.path.join(artlib.ROOT, 'art-prompts-wild.json')


def stem_of(key):
    size, _, planet = key.partition('_')
    if key.startswith('small_house_'):
        size, planet = 'small_house', key[len('small_house_'):]
    return artlib.SIZE_FILE.get(size, size) + '_' + artlib.PLANET_FILE.get(planet, planet) + '_alt2'


def do_map(log_path):
    keys = json.load(open(log_path, encoding='utf-8'))
    seqs = sorted(glob.glob(os.path.join(INBOX, 'seq_*.jpg')))
    if len(seqs) != len(keys):
        sys.exit(f'{len(seqs)} pictures in the inbox but {len(keys)} logged downloads: check before renaming.')
    for f, key in zip(seqs, keys):
        stem = stem_of(key)
        dst = os.path.join(artlib.ART, stem + '.jpg')
        if os.path.exists(dst):
            os.makedirs(artlib.REJECTED, exist_ok=True)
            n = 1
            while os.path.exists(os.path.join(artlib.REJECTED, f'{stem}_{n}.jpg')):
                n += 1
            os.replace(dst, os.path.join(artlib.REJECTED, f'{stem}_{n}.jpg'))
        os.replace(f, dst)
        artlib.update(stem, status='new', comment='', fresh=False, flipped=False, event='made in the Gemini app (wild set)')
        print(os.path.basename(f), '->', stem + '.jpg')


def resized(src, dst, width, quality):
    from PIL import Image
    if os.path.exists(dst) and os.path.getmtime(dst) >= os.path.getmtime(src):
        return False
    im = Image.open(src).convert('RGB')
    im = im.resize((width, round(width * im.height / im.width)), Image.LANCZOS)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    im.save(dst, quality=quality, optimize=True)
    return True


def do_build():
    data = json.load(open(WILD, encoding='utf-8'))
    items, new = [], []
    for s in data['sizes']:
        for p in data['planets']:
            key = s['id'] + '_' + p['id']
            stem = stem_of(key)
            base = stem[:-len('_alt2')]
            src = artlib.find(stem)
            prev = [x for x in (base, base + '_alt') if artlib.find(x)]
            if src:
                if resized(src, os.path.join(OUT, 'pics', stem + '.jpg'), 1600, 84):
                    new.append('pics/' + stem + '.jpg')
                for x in prev:
                    if resized(artlib.find(x), os.path.join(OUT, 'thumbs', x + '.jpg'), 400, 78):
                        new.append('thumbs/' + x + '.jpg')
            items.append({'key': key, 'stem': stem, 'size': s['id'], 'sizeName': s['name'], 'planet': p['id'],
                          'planetName': p['name'], 'idea': data['ideas'][key], 'made': bool(src),
                          'v': int(os.path.getmtime(src)) if src else 0, 'prev': prev})
    manifest = {'sizes': data['sizes'], 'items': items}
    page = open(os.path.join(HERE, 'wild_review.html'), encoding='utf-8').read()
    page = page.replace('__MANIFEST__', json.dumps(manifest, ensure_ascii=False).replace('</', '<\\/'))
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, 'index.html'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(page)
    with open(os.path.join(OUT, 'new-files.json'), 'w', encoding='utf-8') as f:
        json.dump(new, f)
    print(f'{sum(1 for i in items if i["made"])} of {len(items)} made; {len(new)} new files')
    for n in new:
        print(n)


def do_verdicts(folder):
    """Copies the review page's choices (ArtifactData list with out_dir: one <stem>.json per picture) into
    review.json, so export_art.py leaves rejected pictures out."""
    n = 0
    for f in glob.glob(os.path.join(folder, '*.json')):
        stem = os.path.basename(f)[:-5]
        body = json.load(open(f, encoding='utf-8'))
        body = body.get('data', body)
        status = body.get('status')
        if status not in ('approved', 'rejected') or not artlib.find(stem):
            continue
        if artlib.load_review().get(stem, {}).get('status') != status:
            artlib.update(stem, status=status, comment=body.get('note', '') if status == 'rejected' else '',
                          event=status + ' on the review page')
            n += 1
    print(n, 'choices copied into review.json')


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == 'verdicts':
        do_verdicts(sys.argv[2])
    elif len(sys.argv) >= 3 and sys.argv[1] == 'map':
        do_map(sys.argv[2])
    elif len(sys.argv) >= 2 and sys.argv[1] == 'build':
        do_build()
    else:
        sys.exit(__doc__)
